package com.anxin.config;

import com.anxin.interceptor.JwtTokenUserInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册用户端 JWT 拦截器。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/api/user/**")
                // 登录不需要 token，放行；register（完善资料）需要 token，走拦截器
                .excludePathPatterns("/api/user/login");
    }
}
