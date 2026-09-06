package com.anxin.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.anxin.config.properties.OssProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置：全局复用一个线程安全的 OSSClient，应用关闭时自动 shutdown。
 */
@Configuration
public class OssClientConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties ossProperties) {
        String endpoint = ossProperties.endpoint();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(5000);
        configuration.setSocketTimeout(15000);
        return new OSSClientBuilder().build(
                endpoint,
                ossProperties.accessKeyId(),
                ossProperties.accessKeySecret(),
                configuration);
    }
}
