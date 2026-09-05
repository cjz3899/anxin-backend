package com.anxin.service.impl;

import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
import com.anxin.entity.User;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.mapper.UserMapper;
import com.anxin.service.IUserService;
import com.anxin.service.WxService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private WxService wxService;

    @Override
    public User wxlogin(LoginDTO dto) {
        String openid = wxService.code2Session(dto.getCode());

        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setStatus(1);
            try {
                save(user);
            } catch (DuplicateKeyException e) {
                user = getOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
            }
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ServiceException(ResultCode.ACCOUNT_FROZEN);
        }
        return user;
    }

    private User updateProfile(Long id, String nickname, String avatar) {
        User user = getById(id);
        if (user == null) {
            throw new ServiceException(ResultCode.USER_NOT_EXIST);
        }
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        user.setUpdatedTime(LocalDateTime.now());
        updateById(user);
        return user;
    }

    @Override
    public UserVO profile(ProfileDTO dto) {
        Long userId = BaseContext.getCurrentId();
        User user = updateProfile(userId, dto.getNickname(), dto.getAvatar());
        return UserVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }
}
