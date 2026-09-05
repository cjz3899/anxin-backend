package com.anxin.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类（基于 jjwt 0.12.x）。
 */
public class JwtUtil {

    /**
     * 将给定的密钥字符串拉伸为 256 位 HS256 密钥。
     */
    private static SecretKey deriveKey(String secretKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secretKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 生成 JWT（HS256 算法）。
     *
     * @param secretKey jwt 秘钥（不可暴露给前端）
     * @param ttlMillis 过期时间（毫秒）
     * @param claims    自定义声明，例如 userId / openid
     * @return token 字符串
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        SecretKey key = deriveKey(secretKey);

        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        return Jwts.builder()
                .claims(claims)
                .signWith(key, Jwts.SIG.HS256)
                .expiration(exp)
                .compact();
    }

    /**
     * 解析 JWT 并返回 Claims（过期会抛 ExpiredJwtException，签名不对会抛 SignatureException）。
     *
     * @param secretKey jwt 秘钥（必须和生成时一致）
     * @param token     加密后的 token
     * @return 声明内容
     */
    public static Claims parseJWT(String secretKey, String token) {
        SecretKey key = deriveKey(secretKey);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 便捷方法：从 token 中取出 userId（claim key 为 userId）
     */
    public static Long getUserId(String secretKey, String token) {
        Object id = parseJWT(secretKey, token).get("userId");
        return id == null ? null : Long.valueOf(id.toString());
    }
}
