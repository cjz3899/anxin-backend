package com.anxin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（对应 yml 中 anxin.jwt 前缀）
 */
@ConfigurationProperties(prefix = "anxin.jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        Long accessExpiration,
        Long refreshExpiration) {
}
