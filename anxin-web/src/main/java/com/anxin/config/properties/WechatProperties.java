package com.anxin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序配置（对应 yml 中 wechat 前缀）。
 */
@ConfigurationProperties(prefix = "wechat")
public record WechatProperties(
        String appid,
        String secret,
        String grantType,
        String jscode2sessionUrl) {
}
