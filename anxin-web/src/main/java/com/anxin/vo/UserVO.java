package com.anxin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 用户信息出参（注册/完善资料返回）。
 */
@Data
@Builder
public class UserVO {

    /** 用户ID（数据库自增，序列化为字符串，防止前端 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;


    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;
}
