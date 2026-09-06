package com.anxin.service.support;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.anxin.config.properties.WechatProperties;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 微信内容安全服务
 */
@Slf4j
@Service
public class WxSecurityService {

    /**
     * 微信返回：正常
     */
    private static final int ERR_OK = 0;

    /**
     * 微信返回：内容含有违法违规内容
     */
    private static final int ERR_VIOLATION = 87014;

    /**
     * 微信返回：access_token 无效或已过期（多实例会出现）
     */
    private static final int ERR_INVALID_TOKEN = 40001;

    @Resource
    private WechatProperties wechatProperties;

    @Resource
    private WxAccessTokenService wxAccessTokenService;

    @Resource
    private RestTemplate restTemplate;

    /**
     * 图片同步内容安全审核
     * 适用于 ≤4MB 的图片（含头像），必须在文件进入 OSS / 数据库之前于上传请求线程内同步调用，
     * 违规内容在此被拦截；不要把本方法丢进异步队列——违规结果回来时文件早已入库，属于补救而非拦截
     *
     * @param imageBytes 图片二进制内容
     */
    public void checkImage(byte[] imageBytes) {
        int errcode = doCheckImage(imageBytes, wxAccessTokenService.getAccessToken());
        if (errcode == ERR_INVALID_TOKEN) {
            // 缓存的 token 可能已被其它实例刷新顶掉：清除缓存后重试一次
            log.warn("imgSecCheck 返回 40001，清除 access_token 缓存后重试一次");
            wxAccessTokenService.evict();
            errcode = doCheckImage(imageBytes, wxAccessTokenService.getAccessToken());
        }
        if (errcode == ERR_OK) {
            return;
        }
        if (errcode == ERR_VIOLATION) {
            log.warn("图片内容违规，已拦截");
            throw new ServiceException(ResultCode.CONTENT_VIOLATION);
        }
        log.error("imgSecCheck 校验失败 errcode : {}", errcode);
        throw new ServiceException(ResultCode.WECHAT_SECURITY_ERROR);
    }

    /**
     * 执行一次 imgSecCheck 请求，返回微信的 errcode。
     */
    private int doCheckImage(byte[] imageBytes, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // ByteArrayResource 必须重写 getFilename()，否则 multipart 编码时抛 “No filename available”
        ByteArrayResource media = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "image.jpg";
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("media", media);
        String resp;
        try {
            String url = wechatProperties.imgSecCheckUrl() + "?access_token=" + accessToken;
            resp = restTemplate.postForObject(url, new HttpEntity<>(form, headers), String.class);
        } catch (RestClientException e) {
            log.error("imgSecCheck 请求失败 url : {}", wechatProperties.imgSecCheckUrl(), e);
            throw new ServiceException(ResultCode.WECHAT_SECURITY_ERROR);
        }
        JSONObject json = JSONUtil.parseObj(resp);
        return json.getInt("errcode", ERR_OK);
    }

    /**
     * 文档 / 超 4MB 图片的微信异步审核提交
     *
     * @param fileBytes  文件二进制内容
     * @param mime       文件真实 MIME（当前仅用于日志，后续提交素材时使用）
     * @param documentId 关联的文件 ID（仅用于日志/回调关联）
     */
    public void checkMediaAsync(byte[] fileBytes, String mime, Long documentId) {
        //TODO 骨架：当前 mock 放行（仅记日志），接入真实流程需要：
        // 将文件上传为微信临时素材或提供公网可访问的 media_url；
        // 调用 media_check_async 提交，微信通过回调（需公网回调地址）通知结果
        // 回调中若违规，将 document / analysis_task 置为失败并删除 OSS 文件
        log.warn("checkMediaAsync 暂为骨架（mock 放行），documentId : {}, mime : {}, size : {}",
                documentId, mime, fileBytes.length);
    }
}
