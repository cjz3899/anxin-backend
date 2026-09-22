package com.anxin.service;

import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
import com.anxin.dto.UploadConfirmDTO;
import com.anxin.dto.UploadCredentialDTO;
import com.anxin.entity.User;
import com.anxin.vo.AvatarVO;
import com.anxin.vo.UploadCredentialVO;
import com.anxin.vo.UserVO;
import com.baomidou.mybatisplus.extension.service.IService;

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
     * 查询当前登录用户资料（id/nickname/avatar），供前端“我的”页回显
     */
    UserVO me();

    /**
     * 签发头像的 OSS 表单直传凭证：头像字节不经过服务端（小程序侧请求体上限很小）
     */
    UploadCredentialVO requestAvatarCredential(UploadCredentialDTO dto);

    /**
     * 头像直传完成后的确认：校验大小（≤2MB）、真实类型（BMP/JPEG/JPG/GIF/PNG）与微信内容安全，
     * 任一不过即删除对象；通过后返回永久 URL（本方法不落库，由 /api/user/profile 一并持久化）
     */
    AvatarVO confirmAvatarUpload(UploadConfirmDTO dto);

    void logout(Long userId);
}
