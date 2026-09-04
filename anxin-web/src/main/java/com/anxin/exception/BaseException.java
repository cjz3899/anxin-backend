package com.anxin.exception;

/**
 * 业务异常基类。业务层抛出后由 GlobalExceptionHandler 统一捕获，
 * 返回 Result.error(消息)，避免把内部错误直接暴露。
 */
public class BaseException extends RuntimeException {

    public BaseException(String message) {
        super(message);
    }
}
