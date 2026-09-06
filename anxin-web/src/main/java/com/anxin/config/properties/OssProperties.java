package com.anxin.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置（对应 yml 中 anxin.oss 前缀）。
 */
@ConfigurationProperties(prefix = "anxin.oss")
public record OssProperties(
        String endpoint,
        String accessKeyId,
        String accessKeySecret,
        String bucket,
        String publicDomain) {
}
