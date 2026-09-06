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
import com.anxin.vo.LoginVO;
import com.anxin.vo.UserVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
