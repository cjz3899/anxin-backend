package com.anxin.dto;

import lombok.Data;

/**
 * 注册/完善资料入参
 */
@Data
public class RegisterDTO {
    /** 用户昵称 */
    private String nickname;
    /** 用户头像 */
    private String avatar;
}
