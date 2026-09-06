package com.anxin.service.support;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.anxin.config.properties.WechatProperties;
import com.anxin.constant.RedisKeyConstant;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * 微信 access_token 服务。
 * <p>
 * access_token 全局唯一：同一 appid 同一时刻只有一个有效 token，重新获取会使旧 token 约 5 分钟内失效。
 * 因此使用 Redis 缓存（TTL 略短于 expires_in，自动提前过期触发刷新），多实例部署不会互相顶掉 token。
 */
@Slf4j
@Service
public class WxAccessTokenService {

    @Resource
    private WechatProperties wechatProperties;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取 access_token：优先读 Redis 缓存，未命中（含已过期）时重新获取并写入缓存。
     */
    public String getAccessToken() {
        String accessToken = stringRedisTemplate.opsForValue().get(RedisKeyConstant.WECHAT_ACCESS_TOKEN_KEY);
        if (accessToken != null) {
            return accessToken;
        }
        return refreshAccessToken();
    }

    /**
     * 刷新并缓存 access_token
     */
    public synchronized String refreshAccessToken() {
        //双重检测：等待锁期间可能已有其它线程刷新完成
        String cached = stringRedisTemplate.opsForValue().get(RedisKeyConstant.WECHAT_ACCESS_TOKEN_KEY);
        if (cached != null) {
            return cached;
        }
        String url = UriComponentsBuilder.fromUriString(wechatProperties.tokenUrl())
                .queryParam("grant_type", wechatProperties.grantType())
                .queryParam("appid", wechatProperties.appid())
                .queryParam("secret", wechatProperties.secret())
                .build()
                .encode()
                .toUriString();
        String resp;
        try {
            // 第一次向微信发出HTTP GET请求获取access_token
            resp = restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.error("微信 access_token 请求失败 url : {}", wechatProperties.tokenUrl(), e);
            throw new ServiceException(ResultCode.WECHAT_SECURITY_ERROR.getCode(),
                    "微信 access_token 获取失败，请稍后重试");
        }
        JSONObject json = JSONUtil.parseObj(resp);
        Integer errcode = json.getInt("errcode");
        if (errcode != null && errcode != 0) {
            log.error("微信 access_token 返回错误 errcode : {}, errmsg : {}", errcode, json.getStr("errmsg"));
            throw new ServiceException(ResultCode.WECHAT_SECURITY_ERROR.getCode(),
                    "微信 access_token 获取失败：" + json.getStr("errmsg"));
        }
        String accessToken = json.getStr("access_token");
        int expiresIn = json.getInt("expires_in", 7200);
        // TTL 提前约 200 秒过期，保证下次请求时旧 token 已被新 token 替换，避免 40001
        long ttlSeconds = Math.max(expiresIn - 200, 60);
        stringRedisTemplate.opsForValue().set(RedisKeyConstant.WECHAT_ACCESS_TOKEN_KEY, accessToken,
                Duration.ofSeconds(ttlSeconds));
        log.info("微信 access_token 刷新成功，TTL : {}s", ttlSeconds);
        return accessToken;
    }

    /**
     * 清除本地缓存（如接口返回 40001 token 已失效时调用，强制下一次重新获取）。
     */
    public void evict() {
        stringRedisTemplate.delete(RedisKeyConstant.WECHAT_ACCESS_TOKEN_KEY);
    }
}
