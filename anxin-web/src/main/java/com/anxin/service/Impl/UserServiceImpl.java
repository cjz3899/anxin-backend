package com.anxin.service.Impl;

import com.anxin.dto.UserLoginDTO;
import com.anxin.entity.User;
import com.anxin.exception.BaseException;
import com.anxin.mapper.UserMapper;
import com.anxin.service.UserService;
import com.anxin.service.WxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户业务实现类。数据库操作使用原生 MyBatis（UserMapper 注解 SQL）。
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WxService wxService;

    @Override
    public User wxlogin(UserLoginDTO dto) {
        // 1. code 换 openid（调用微信服务）
        String openid = wxService.code2Session(dto.getCode());

        // 2. MyBatis：按 openid 查询
        User user = userMapper.selectByOpenid(openid);

        // 查不到则首次自动注册（id 由数据库自增生成）
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setStatus(1);
            LocalDateTime now = LocalDateTime.now();
            user.setCreatedTime(now);
            user.setUpdatedTime(now);
            userMapper.insert(user);
        }

        // 3. 状态校验（0 冻结）
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BaseException("账号已被冻结");
        }
        return user;
    }

    @Override
    public User updateProfile(Long id, String nickname, String avatar) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        user.setUpdatedTime(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }
}
