package com.anxin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 微信登录出参
 */
@Data
@Builder
public class UserLoginVO {

    /** 用户ID（数据库自增，序列化为字符串，防止前端 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** JWT 令牌 */
    private String token;

    /** 微信openid */
    private String openid;
}
