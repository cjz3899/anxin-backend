package com.anxin.handler;

import com.anxin.exception.BaseException;
import com.anxin.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，统一把异常转换为 Result JSON 返回。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常（BaseException），返回业务提示。
     */
    @ExceptionHandler(BaseException.class)
    public Result exceptionHandler(BaseException ex) {
        log.error("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获 SQL 唯一约束等异常。
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        log.error("SQL异常：{}", ex.getMessage());
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            return Result.error("数据已存在，请勿重复操作");
        }
        return Result.error("数据库操作异常");
    }

    /**
     * 兜底：捕获所有未处理的异常，返回 JSON 而非 HTML 500，
     * 防止线程池/第三方 API/Redis 等异常时暴露错误详情。
     */
    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception ex) {
        log.error("未知异常", ex);
        return Result.error(ex.getMessage() != null ? ex.getMessage() : "服务器内部错误");
    }
}
