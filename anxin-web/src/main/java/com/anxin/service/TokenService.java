package com.anxin.service;

import com.anxin.config.properties.JwtProperties;
import com.anxin.constant.JwtClaimsConstant;
import com.anxin.constant.RedisKeyConstant;
import com.anxin.entity.User;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.model.TokenPair;
import com.anxin.util.JwtUtil;
import com.anxin.vo.LoginVO;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class TokenService {

    @Resource
    private JwtProperties jwtProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    public TokenPair generateLoginTokenPair(Long userId) {
        TokenPair pair = new TokenPair();
        pair.setAccessToken(createToken(jwtProperties.accessSecret(), jwtProperties.accessExpiration(), userId, JwtClaimsConstant.TOKEN_TYPE_ACCESS));
        pair.setRefreshToken(createToken(jwtProperties.refreshSecret(), jwtProperties.refreshExpiration(), userId, JwtClaimsConstant.TOKEN_TYPE_REFRESH));

        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.LOGIN_ACCESS_PREFIX + userId,
                pair.getAccessToken(),
                Duration.ofMillis(jwtProperties.accessExpiration()));
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.LOGIN_REFRESH_PREFIX + userId,
                pair.getRefreshToken(),
                Duration.ofMillis(jwtProperties.refreshExpiration()));
        return pair;
    }

    public Long verifyAccess(String token) {
        Claims claims = JwtUtil.parseJWT(jwtProperties.accessSecret(), token);
        checkType(claims, JwtClaimsConstant.TOKEN_TYPE_ACCESS);
        Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());

        String cached = stringRedisTemplate.opsForValue().get(RedisKeyConstant.LOGIN_ACCESS_PREFIX + userId);
        if (cached == null || !cached.equals(token)) {
            throw new ServiceException(ResultCode.LOGIN_EXPIRED);
        }
        return userId;
    }

    public LoginVO refreshToken(String refreshToken) {
        Claims claims = JwtUtil.parseJWT(jwtProperties.refreshSecret(), refreshToken);
        checkType(claims, JwtClaimsConstant.TOKEN_TYPE_REFRESH);
        Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());

        String cached = stringRedisTemplate.opsForValue().get(RedisKeyConstant.LOGIN_REFRESH_PREFIX + userId);
        if (cached == null || !cached.equals(refreshToken)) {
            throw new ServiceException(ResultCode.LOGIN_EXPIRED);
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new ServiceException(ResultCode.USER_NOT_EXIST);
        }

        TokenPair pair = generateLoginTokenPair(userId);
        return LoginVO.builder()
                .id(user.getId())
                .accessToken(pair.getAccessToken())
                .refreshToken(pair.getRefreshToken())
                .build();
    }

    public void logout(Long userId) {
        stringRedisTemplate.delete(RedisKeyConstant.LOGIN_ACCESS_PREFIX + userId);
        stringRedisTemplate.delete(RedisKeyConstant.LOGIN_REFRESH_PREFIX + userId);
    }

    private String createToken(String secret, long expiration, Long userId, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, userId);
        claims.put(JwtClaimsConstant.TOKEN_TYPE, type);
        return JwtUtil.createJWT(secret, expiration, claims);
    }

    private void checkType(Claims claims, String expectedType) {
        if (!expectedType.equals(claims.get(JwtClaimsConstant.TOKEN_TYPE))) {
            throw new ServiceException(ResultCode.LOGIN_EXPIRED);
        }
    }
}
