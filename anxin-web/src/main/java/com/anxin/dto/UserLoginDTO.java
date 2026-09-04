package com.anxin.dto;

import lombok.Data;

/**
 * 微信登录入参
 */
@Data
public class UserLoginDTO {
    /** 前端 wx.login() 拿到的临时凭证 code */
    private String code;
}
