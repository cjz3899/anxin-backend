package com.anxin.service.support;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.anxin.config.properties.WechatProperties;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
     * multipart 请求体手工拼装为字节数组并显式携带 Content-Length：
     * 微信网关不接受 Transfer-Encoding: chunked（返回 412），
     * 而 Spring 的 multipart 流式编码无法预知长度，会退化为 chunked。
     */
    private int doCheckImage(byte[] imageBytes, String accessToken) {
        String boundary = "----AnxinSecBoundary" + System.nanoTime();
        byte[] body = buildMultipartBody(imageBytes, boundary);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary));
        headers.setContentLength(body.length);
        String resp;
        try {
            String url = wechatProperties.imgSecCheckUrl() + "?access_token=" + accessToken;
            resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException e) {
            log.error("imgSecCheck 请求失败 url : {}", wechatProperties.imgSecCheckUrl(), e);
            throw new ServiceException(ResultCode.WECHAT_SECURITY_ERROR);
        }
        JSONObject json = JSONUtil.parseObj(resp);
        return json.getInt("errcode", ERR_OK);
    }

    /**
     * 组装 imgSecCheck 要求的 multipart 表单体（字段名 media，文件名 image.jpg）
     */
    private byte[] buildMultipartBody(byte[] imageBytes, String boundary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"media\"; filename=\"image.jpg\"\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Length: " + imageBytes.length + "\r\n\r\n";
        byte[] headBytes;
        try {
            headBytes = head.getBytes(StandardCharsets.UTF_8);
            out.write(headBytes);
            out.write(imageBytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ServiceException(ResultCode.WECHAT_SECURITY_ERROR);
        }
        return out.toByteArray();
    }
}
