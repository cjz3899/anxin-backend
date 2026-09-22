package com.anxin.service.support;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PolicyConditions;
import com.anxin.config.properties.OssProperties;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.vo.UploadCredentialVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * 阿里云 OSS 存储服务：对象名由系统生成（UUID + 真实后缀），
 * 上传调用方已完成大小、真实类型与内容安全校验
 */
@Slf4j
@Service
public class OssStorageService {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private OSS ossClient;

    @Resource
    private OssProperties ossProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 上传文件到 OSS（bucket 公共读）。
     *
     * @param bytes    文件二进制内容（已通过大小/类型/内容安全校验）
     * @param mime     文件真实 MIME（由 Tika 检测），写入对象元数据保证 content-type 正确
     * @param category 目录分类，如 avatars / documents
     * @param ext      文件真实后缀（如 jpg/pdf），由 Tika 检测结果映射而来
     * @return OSS 对象 key，形如 category/yyyyMMdd/{uuid}.{ext}
     */
    public String upload(byte[] bytes, String mime, String category, String ext) {
        String key = buildKey(category, null, ext);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(mime);
        try {
            ossClient.putObject(ossProperties.bucket(), key, new ByteArrayInputStream(bytes), metadata);
        } catch (OSSException | ClientException e) {
            log.error("OSS 上传失败 bucket : {}, key : {}", ossProperties.bucket(), key, e);
            throw new ServiceException(ResultCode.FILE_SAVE_FAILED);
        }
        return key;
    }

    /**
     * 对象名统一在这里生成：category/[subDir/]yyyyMMdd/{uuid}.{ext}，不拼接任何用户输入的文件名
     */
    public String buildKey(String category, String subDir, String ext) {
        String prefix = subDir == null || subDir.isBlank()
                ? category
                : category + "/" + subDir;
        return prefix + "/" + LocalDate.now().format(DAY_FORMATTER) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
    }

    /**
     * 签发 OSS 表单直传凭证（PostPolicy + V1 签名，供 wx.uploadFile 使用）。
     * key 与大小范围都写进 policy，客户端无法改成别的对象名或绕过上限
     *
     * @param valid 凭证有效期，过期后 OSS 侧直接拒绝
     */
    public UploadCredentialVO createPostCredential(String key, long maxBytes, Duration valid) {
        Date expiration = Date.from(Instant.now().plus(valid));
        try {
            PolicyConditions conditions = new PolicyConditions();
            conditions.addConditionItem(PolicyConditions.COND_KEY, key);
            conditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, maxBytes);
            conditions.addConditionItem(PolicyConditions.COND_SUCCESS_ACTION_STATUS, "200");
            String policy = ossClient.generatePostPolicy(expiration, conditions);
            /**
             * generatePostPolicy 返回的是策略原文，表单里的 policy 字段必须是它的 base64；
             * 而签名要按原文算，两者不能传同一个串
             */
            String encodedPolicy = BinaryUtil.toBase64String(policy.getBytes(StandardCharsets.UTF_8));
            return UploadCredentialVO.builder()
                    .host(bucketHost())
                    .key(key)
                    .policy(encodedPolicy)
                    .signature(ossClient.calculatePostSignature(policy))
                    .accessKeyId(ossProperties.accessKeyId())
                    .expire(expiration.toInstant().getEpochSecond())
                    .maxBytes(maxBytes)
                    .build();
        } catch (OSSException | ClientException e) {
            log.error("OSS 直传凭证签发失败 key : {}", key, e);
            throw new ServiceException(ResultCode.FILE_SAVE_FAILED);
        }
    }

    /**
     * 取直传对象的实际大小（对象不存在视为上传未完成）
     */
    public long headSize(String key) {
        try {
            return ossClient.getObjectMetadata(ossProperties.bucket(), key).getContentLength();
        } catch (OSSException | ClientException e) {
            log.error("OSS 对象元信息获取失败 key : {}", key, e);
            throw new ServiceException(ResultCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 只读对象头部若干字节用于魔数检测，避免为了识别类型而拉回整个文件
     */
    public byte[] readHead(String key, int limit) {
        try (OSSObject object = ossClient.getObject(new GetObjectRequest(ossProperties.bucket(), key).withRange(0, limit - 1L));
             InputStream in = object.getObjectContent()) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.error("OSS 对象头部读取失败 key : {}", key, e);
            throw new ServiceException(ResultCode.FILE_DOWNLOAD_IO_ERROR);
        } catch (OSSException | ClientException e) {
            log.error("OSS 对象头部读取异常 key : {}", key, e);
            throw new ServiceException(ResultCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 按 key 删除对象，校验不通过时清掉直传进来的脏文件
     */
    public void deleteByKey(String key) {
        try {
            ossClient.deleteObject(ossProperties.bucket(), key);
        } catch (Exception e) {
            log.warn("OSS 对象删除失败 key : {}", key, e);
        }
    }

    /**
     * 从OSS中下载
     */
    public byte[] download(String fileUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.error("OSS 文件下载失败 url : {}，http 状态码 : {}", fileUrl, response.statusCode());
                throw new ServiceException(ResultCode.FILE_DOWNLOAD_FAILED);
            }
            return response.body();
        } catch (IOException e) {
            log.error("OSS 文件下载异常 url : {}", fileUrl, e);
            throw new ServiceException(ResultCode.FILE_DOWNLOAD_IO_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ResultCode.FILE_DOWNLOAD_INTERRUPTED);
        }
    }

    /**
     * 按公共读 URL 删除 OSS 对象（key 从 URL path 中提取），失败仅告警不抛出
     */
    public void delete(String fileUrl) {
        try {
            /**
             * TODO 优化：从 URL 中提取 key 的逻辑可以优化
             * getPath() 会做 URL 解码，如果 key 里含特殊字符（如空格、中文、+），解码后可能和原始 key 不一致，导致删不到
             * 上传时就保存 object key 到数据库，删除时直接用 key，而不是每次从 URL 反解析
             */
            String key = new java.net.URI(fileUrl).getPath();
            if (key.startsWith("/")) {
                key = key.substring(1);
            }
            ossClient.deleteObject(ossProperties.bucket(), key);
        } catch (Exception e) {
            log.warn("OSS 对象删除失败 url : {}", fileUrl, e);
        }
    }

    /**
     * 由对象 key 生成可公开访问的永久 URL：
     * 配置了 public-domain 时使用自定义域名，否则使用默认域名 https://{bucket}.{endpoint}/{key}。
     */
    public String toUrl(String key) {
        String domain = ossProperties.publicDomain();
        if (domain != null && !domain.isBlank()) {
            return domain.replaceAll("/+$", "") + "/" + key;
        }
        return bucketHost() + "/" + key;
    }

    /**
     * 默认公网访问域名，表单直传的 url 与公共读 URL 都用它
     */
    private String bucketHost() {
        return "https://" + ossProperties.bucket() + "." + stripScheme(ossProperties.endpoint());
    }

    private String stripScheme(String endpoint) {
        return endpoint.replaceFirst("^https?://", "");
    }
}
