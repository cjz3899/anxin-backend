package com.anxin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录入参
 */
@Data
public class LoginDTO {
    /**
     * 前端 wx.login() 拿到的临时凭证 code
     */
    @NotBlank(message = "code 不能为空")
    private String code;
}
