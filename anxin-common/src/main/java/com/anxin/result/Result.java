package com.anxin.result;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /** 状态码：1 成功，0 失败 */
    private Integer code;
    /** 提示信息 */
    private String msg;
    /** 响应数据 */
    private T data;

    // ==================== 工厂方法 ====================

    /** 无数据成功响应，默认消息 */
    public static <T> Result<T> success() {
        return new Result<>(1, "操作成功", null);
    }

    /** 成功响应，携带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(1, "操作成功", data);
    }

    /** 成功响应，自定义消息 + 数据 */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(1, msg, data);
    }

    /** 无数据成功响应，仅带自定义消息 */
    public static Result<Void> successMsg(String msg) {
        return new Result<>(1, msg, null);
    }

    /** 失败响应，默认 code=0 */
    public static <T> Result<T> error(String msg) {
        return new Result<>(0, msg, null);
    }

    /** 失败响应，自定义 code */
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }

    /** 完全自定义响应 */
    public static <T> Result<T> of(Integer code, String msg, T data) {
        return new Result<>(code, msg, data);
    }
}
