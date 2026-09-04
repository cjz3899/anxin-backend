package com.anxin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 配置（对应 yml 中 anxin.mq 前缀）
 */
@ConfigurationProperties(prefix = "anxin.mq")
public record MqProperties(
        String nameServer,
        String producerGroup,
        String defaultTopic) {
}
