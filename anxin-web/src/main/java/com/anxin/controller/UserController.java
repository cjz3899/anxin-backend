package com.anxin.controller;

import com.anxin.Util.JwtUtil;
import com.anxin.config.properties.JwtProperties;
import com.anxin.constant.JwtClaimsConstant;
import com.anxin.context.BaseContext;
import com.anxin.dto.RegisterDTO;
import com.anxin.dto.UserLoginDTO;
import com.anxin.entity.User;
import com.anxin.result.Result;
import com.anxin.service.UserService;
import com.anxin.vo.UserLoginVO;
import com.anxin.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户接口：微信登录 + 注册/完善资料。
 */
@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 微信登录：
     * code
     * → openid
     * → 首登自动注册
     * → 签发 JWT。
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO dto) {
        log.info("微信登录，code={}", dto.getCode());

        // 1. 微信登录（含首登自动注册）
        User user = userService.wxlogin(dto);

        // 2. 生成 JWT，claim 里放 userId
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(jwtProperties.secret(), jwtProperties.expiration(), claims);

        // 3. 返回 token + 用户信息
        UserLoginVO vo = UserLoginVO.builder()
                .id(user.getId())
                .token(token)
                .openid(user.getOpenid())
                .build();
        return Result.success(vo);
    }

    /**
     * 注册/完善资料：登录后携带 token，补充昵称/头像。
     */
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody RegisterDTO dto) {
        // 当前登录用户由 JwtTokenUserInterceptor 解析 token 后放入 BaseContext
        Long userId = BaseContext.getCurrentId();
        User user = userService.updateProfile(userId, dto.getNickname(), dto.getAvatar());

        UserVO vo = UserVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
        return Result.success(vo);
    }
}
