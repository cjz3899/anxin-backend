package com.anxin.constant;

public class RedisKeyConstant {

    public static final String LOGIN_ACCESS_PREFIX = "login:access:";

    public static final String LOGIN_REFRESH_PREFIX = "login:refresh:";

    /**
     * 微信 access_token（Redis TTL 缓存，TTL 即过期时间）
     */
    public static final String WECHAT_ACCESS_TOKEN_KEY = "wechat:access_token";
}
