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
        Map<String, String> params = new HashMap<>();
        params.put("appid", wechatProperties.appid());
        params.put("secret", wechatProperties.secret());
        params.put("js_code", code);
        params.put("grant_type", wechatProperties.grantType());

        String resp = HttpClient.doGet(wechatProperties.jscode2sessionUrl(), params);
        JSONObject json = JSONUtil.parseObj(resp);

        Integer errcode = json.getInt("errcode");
        if (errcode != null && errcode != 0) {
            throw new ServiceException(ResultCode.WECHAT_AUTH_FAILED.getCode(),
                    ResultCode.WECHAT_AUTH_FAILED.getMsg() + ": " + json.getStr("errmsg"));
        }
        return json.getStr("openid");
    }
}
