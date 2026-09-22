package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OSS 表单直传凭证：小程序拿到后直接 wx.uploadFile 传给 OSS，文件字节不经过服务端
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadCredentialVO {
    /**
     * 上传地址，即 wx.uploadFile 的 url
     */
    private String host;

    /**
     * 服务端定死的对象名，客户端只能原样回填到表单 key 字段
     */
    private String key;

    /**
     * base64 编码的 PostPolicy
     */
    private String policy;

    private String signature;

    private String accessKeyId;

    /**
     * 凭证过期时间（秒级时间戳），供前端算倒计时
     */
    private long expire;

    /**
     * 本次允许的最大字节数，PostPolicy 已约束，超限 OSS 直接拒绝
     */
    private long maxBytes;
}
