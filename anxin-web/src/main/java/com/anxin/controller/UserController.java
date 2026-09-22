package com.anxin.controller;

import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
import com.anxin.dto.RefreshDTO;
import com.anxin.dto.UploadConfirmDTO;
import com.anxin.dto.UploadCredentialDTO;
import com.anxin.entity.User;
import com.anxin.model.TokenPair;
import com.anxin.result.Result;
import com.anxin.service.IUserService;
import com.anxin.service.support.TokenService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.AvatarVO;
import com.anxin.vo.LoginVO;
import com.anxin.vo.UploadCredentialVO;
import com.anxin.vo.UserVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private IUserService userService;
    @Resource
    private TokenService tokenService;

    /**
     * 查询当前登录用户资料（需登录态 token），供“我的”页回显昵称与头像
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.me());
    }

    @PostMapping("/profile")
    public Result<UserVO> profile(@Valid @RequestBody ProfileDTO dto) {
        return Result.success(userService.profile(dto));
    }

    /**
     * 申请头像的 OSS 表单直传凭证（小程序用 wx.uploadFile 直传，字节不经过服务端）
     */
    @PostMapping("/avatar-credential")
    public Result<UploadCredentialVO> avatarCredential(@Valid @RequestBody UploadCredentialDTO dto) {
        return Result.success(userService.requestAvatarCredential(dto));
    }

    /**
     * 头像直传完成后的确认：校验 ≤2MB / 真实格式 / 微信内容安全，违规即删对象
     * URL 不在此落库，由前端连同昵称一起 POST /api/user/profile 持久化
     */
    @PostMapping("/avatar-confirm")
    public Result<AvatarVO> avatarConfirm(@Valid @RequestBody UploadConfirmDTO dto) {
        return Result.success("头像上传成功", userService.confirmAvatarUpload(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        User user = userService.wxlogin(dto);
        TokenPair pair = tokenService.generateLoginTokenPair(user.getId());
        LoginVO vo = LoginVO.builder()
                .id(user.getId())
                .accessToken(pair.getAccessToken())
                .refreshToken(pair.getRefreshToken())
                .build();
        return Result.success(vo);
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return Result.success(tokenService.refreshToken(dto.getRefreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout(BaseContext.getCurrentId());
        return Result.success();
    }
}
