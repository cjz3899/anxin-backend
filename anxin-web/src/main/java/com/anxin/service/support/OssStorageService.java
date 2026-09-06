package com.anxin.service.support;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.anxin.config.properties.OssProperties;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        String key = category + "/" + LocalDate.now().format(DAY_FORMATTER) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
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
     * 由对象 key 生成可公开访问的永久 URL：
     * 配置了 public-domain 时使用自定义域名，否则使用默认域名 https://{bucket}.{endpoint}/{key}。
     */
    public String toUrl(String key) {
        String domain = ossProperties.publicDomain();
        if (domain != null && !domain.isBlank()) {
            return domain.replaceAll("/+$", "") + "/" + key;
        }
        return "https://" + ossProperties.bucket() + "." + stripScheme(ossProperties.endpoint()) + "/" + key;
    }

    private String stripScheme(String endpoint) {
        return endpoint.replaceFirst("^https?://", "");
    }
}
