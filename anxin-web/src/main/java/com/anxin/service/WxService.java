package com.anxin.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.anxin.Util.HttpClient;
import com.anxin.config.properties.WechatProperties;
import com.anxin.exception.BaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信服务：封装小程序登录第一步 —— 用前端临时 code 换取 openid。
 */
@Service
public class WxService {

    @Autowired
    private WechatProperties wechatProperties;

    /**
     * 调用微信 jscode2session 接口，用 code 换取 openid。
     */
    public String code2Session(String code) {
        if (code == null || code.isEmpty()) {
            throw new BaseException("code 不能为空");
        }

        Map<String, String> params = new HashMap<>();
        params.put("appid", wechatProperties.appid());
        params.put("secret", wechatProperties.secret());
        params.put("js_code", code);
        params.put("grant_type", wechatProperties.grantType());

        String resp = HttpClient.doGet(wechatProperties.jscode2sessionUrl(), params);
        JSONObject json = JSONUtil.parseObj(resp);

        Integer errcode = json.getInt("errcode");
        if (errcode != null && errcode != 0) {
            throw new BaseException("微信登录失败: " + json.getStr("errmsg"));
        }
        return json.getStr("openid");
    }
}
