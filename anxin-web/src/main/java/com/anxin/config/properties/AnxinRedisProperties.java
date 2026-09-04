package com.anxin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 配置（对应 yml 中 anxin.redis 前缀）
 */
@ConfigurationProperties(prefix = "anxin.redis")
public record AnxinRedisProperties(
        String host,
        Integer port,
        String password) {
}
