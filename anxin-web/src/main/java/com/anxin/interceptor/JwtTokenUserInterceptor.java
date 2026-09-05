package com.anxin.interceptor;

import com.anxin.enums.ResultCode;
import com.anxin.result.Result;
import com.anxin.threadlocal.BaseContext;
import com.anxin.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    private static final String TOKEN_NAME = "token";

    @Resource
    private TokenService tokenService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(TOKEN_NAME);

        try {
            Long userId = tokenService.verifyAccess(token);
            BaseContext.setCurrentId(userId);
            return true;
        } catch (Exception ex) {
            log.error("JWT 校验失败: {}", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Result<Void> result = Result.error(
                    ResultCode.LOGIN_EXPIRED.getCode(),
                    ResultCode.LOGIN_EXPIRED.getMsg());
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.remove();
    }
}
