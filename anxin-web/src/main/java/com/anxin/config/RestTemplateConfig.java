package com.anxin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置类
 * RestTemplate作用：提供多种便捷方法来执行HTTP请求，能够方便获取HTTP响应
 */
@Configuration
public class RestTemplateConfig {
    //配置RestTemplate
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        //设置连接超时
        factory.setConnectTimeout(5000);
        //设置读取超时
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }
}
