package com.anxin.controller;

import com.anxin.dto.LoginDTO;
import com.anxin.dto.ProfileDTO;
import com.anxin.dto.RefreshDTO;
import com.anxin.entity.User;
import com.anxin.model.TokenPair;
import com.anxin.result.Result;
import com.anxin.service.IUserService;
import com.anxin.service.support.TokenService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.AvatarVO;
import com.anxin.vo.LoginVO;
import com.anxin.vo.UserVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private IUserService userService;
    @Resource
    private TokenService tokenService;

    @PostMapping("/profile")
    public Result<UserVO> profile(@Valid @RequestBody ProfileDTO dto) {
        return Result.success(userService.profile(dto));
    }

    /**
     * 头像上传（multipart 字段名 file，需登录态 token）：
     * 校验 ≤2MB / 格式白名单 / 微信内容安全 → 存 OSS → 返回永久 URL。
     * URL 不在此落库，由前端连同昵称一起 POST /api/user/profile 持久化。
     */
    @PostMapping("/avatar")
    public Result<AvatarVO> avatar(@RequestParam("file") MultipartFile file) {
        return Result.success("头像上传成功", userService.uploadAvatar(file));
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
        tokenService.logout(BaseContext.getCurrentId());
        return Result.success();
    }
}
