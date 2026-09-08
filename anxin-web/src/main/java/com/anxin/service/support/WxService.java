package com.anxin.service.support;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.anxin.config.properties.WechatProperties;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.util.HttpClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信服务：封装小程序登录第一步 —— 用前端临时 code 换取 openid。
 */
@Service
public class WxService {

    @Resource
    private WechatProperties wechatProperties;

    /**
     * 调用微信 jscode2session 接口，用 code 换取 openid。
     */
    public String code2Session(String code) {
        if (isBlank(wechatProperties.appid()) || isBlank(wechatProperties.secret())) {
            throw new ServiceException(ResultCode.WECHAT_AUTH_FAILED.getCode(),
                    ResultCode.WECHAT_AUTH_FAILED.getMsg() + ": 服务端未配置 WECHAT_APPID/WECHAT_SECRET");
        }

        Map<String, String> params = new HashMap<>();
        params.put("appid", wechatProperties.appid());
        params.put("secret", wechatProperties.secret());
        params.put("js_code", code);
        params.put("grant_type", wechatProperties.grantType());

        String resp = HttpClient.doGet(wechatProperties.jscode2sessionUrl(), params);
        if (isBlank(resp)) {
            throw new ServiceException(ResultCode.WECHAT_AUTH_FAILED.getCode(),
                    ResultCode.WECHAT_AUTH_FAILED.getMsg() + ": jscode2session 无响应，请检查服务端网络和微信配置");
        }

        JSONObject json;
        try {
            json = JSONUtil.parseObj(resp);
        } catch (RuntimeException e) {
            throw new ServiceException(ResultCode.WECHAT_AUTH_FAILED.getCode(),
                    ResultCode.WECHAT_AUTH_FAILED.getMsg() + ": jscode2session 返回格式异常");
        }

        Integer errcode = json.getInt("errcode");
        if (errcode != null && errcode != 0) {
            throw new ServiceException(ResultCode.WECHAT_AUTH_FAILED.getCode(),
                    ResultCode.WECHAT_AUTH_FAILED.getMsg() + ": " + json.getStr("errmsg"));
        }
        String openid = json.getStr("openid");
        if (isBlank(openid)) {
            throw new ServiceException(ResultCode.WECHAT_AUTH_FAILED.getCode(),
                    ResultCode.WECHAT_AUTH_FAILED.getMsg() + ": jscode2session 未返回 openid");
        }
        return openid;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
