package com.anxin.service;

import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
import com.anxin.entity.User;
import com.anxin.vo.AvatarVO;
import com.anxin.vo.UserVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户业务接口。
 */
public interface IUserService extends IService<User> {

    /**
     * 微信登录：code 换 openid，首登自动注册，返回用户
     */
    User wxlogin(LoginDTO dto);

    UserVO profile(ProfileDTO dto);

    /**
     * 头像上传：校验大小（≤2MB）、真实类型（微信规范白名单 BMP/JPEG/JPG/GIF/PNG）与内容安全，
     * 通过后存储到 OSS 并返回永久 URL（本方法不落库，由 /api/user/profile 一并持久化）
     */
    AvatarVO uploadAvatar(MultipartFile file);
}
