package com.anxin.interceptor;

import com.anxin.Util.JwtUtil;
import com.anxin.config.properties.JwtProperties;
import com.anxin.constant.JwtClaimsConstant;
import com.anxin.context.BaseContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户端（小程序）JWT 拦截器。
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    /** 请求头名称：小程序端统一用 "token" */
    private static final String TOKEN_NAME = "token";

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非 Controller 方法（静态资源等）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1. 从请求头取 token
        String token = request.getHeader(TOKEN_NAME);
        log.info("用户端令牌校验: {}", token);

        try {
            // 2. 解析 JWT，取出 userId
            Claims claims = JwtUtil.parseJWT(jwtProperties.secret(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户id：{}", userId);

            // 3. 放入 ThreadLocal，供业务层取用
            BaseContext.setCurrentId(userId);
            return true;
        } catch (Exception ex) {
            log.error("JWT 校验失败: {}", ex.getMessage());
            // 4. 未通过，返回 401
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束必须清理，防止线程池复用导致串数据/内存泄漏
        BaseContext.remove();
    }
}
