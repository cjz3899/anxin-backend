package com.anxin.config;

import com.anxin.interceptor.JwtTokenUserInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册用户端 JWT 拦截器。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/api/user/**")
                .excludePathPatterns("/api/user/login", "/api/user/refresh");
        // TODO 添加其他拦截器配置
    }
}
