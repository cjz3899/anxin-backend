package com.anxin.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 user（纯 POJO，配合原生 MyBatis 注解 SQL）。
 */
@Data
public class User {


    private Long id;

    /** 微信openid 唯一标识 */
    private String openid;

    /** 用户昵称 */
    private String nickname;

    /** 用户头像 */
    private String avatar;

    /** 账号状态：0 冻结，1 正常 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
