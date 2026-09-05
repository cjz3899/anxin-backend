package com.anxin.enums;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(0, "OK"),

    USER_NOT_EXIST(10001, "用户不存在"),

    ACCOUNT_FROZEN(10002, "账号已被冻结"),

    WECHAT_AUTH_FAILED(10003, "微信登录失败"),

    PARAM_ERROR(10004, "参数校验失败"),

    SYSTEM_ERROR(10005, "系统错误，请稍后重试"),

    LOGIN_EXPIRED(10006, "登录已过期，请重新登录");

    private final Integer code;

    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static String getMsg(Integer code) {
        for (ResultCode rc : ResultCode.values()) {
            if (rc.code.equals(code)) {
                return rc.msg;
            }
        }
        return "";
    }

    public static ResultCode getRc(Integer code) {
        for (ResultCode rc : ResultCode.values()) {
            if (rc.code.equals(code)) {
                return rc;
            }
        }
        return null;
    }
}
