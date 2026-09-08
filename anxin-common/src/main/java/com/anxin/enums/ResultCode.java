package com.anxin.enums;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(0, "OK"),

    USER_NOT_EXIST(10001, "用户不存在"),

    WECHAT_AUTH_FAILED(10002, "微信登录失败"),

    PARAM_ERROR(10003, "参数校验失败"),

    SYSTEM_ERROR(10004, "系统错误，请稍后重试"),

    LOGIN_EXPIRED(10005, "登录已过期，请重新登录"),

    FILE_TYPE_NOT_SUPPORTED(10006, "不支持的文件类型"),

    FILE_SIZE_EXCEEDED(10007, "文件大小超出限制"),

    CONTENT_VIOLATION(10008, "内容违规，请勿上传"),

    WECHAT_SECURITY_ERROR(10009, "内容安全校验失败，请稍后重试"),

    FILE_SAVE_FAILED(10010, "文件保存失败"),

    FILE_DOWNLOAD_FAILED(10011, "文件下载失败，请检查文件是否存在或权限配置"),

    FILE_DOWNLOAD_IO_ERROR(10012, "文件下载IO异常，请检查网络或磁盘后重试"),

    FILE_DOWNLOAD_INTERRUPTED(10013, "下载被中断");

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
