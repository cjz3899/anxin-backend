package com.anxin.service;

import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
import com.anxin.entity.User;
import com.anxin.vo.UserVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户业务接口。
 */
public interface IUserService extends IService<User> {

    /**
     * 微信登录：code 换 openid，首登自动注册，返回用户。
     */
    User wxlogin(LoginDTO dto);

    UserVO profile(ProfileDTO dto);
}
