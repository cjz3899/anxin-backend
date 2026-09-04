package com.anxin.service;

import com.anxin.dto.UserLoginDTO;
import com.anxin.entity.User;

/**
 * 用户业务接口。
 */
public interface UserService {

    /**
     * 微信登录：code 换 openid，首登自动注册，返回用户。
     */
    User wxlogin(UserLoginDTO dto);

    /**
     * 完善用户资料（注册补充昵称/头像）。
     */
    User updateProfile(Long id, String nickname, String avatar);
}
