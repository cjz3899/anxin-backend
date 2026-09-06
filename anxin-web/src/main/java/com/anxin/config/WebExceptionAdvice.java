package com.anxin.config;

import com.anxin.enums.ResultCode;
import com.anxin.exception.ArgumentError;
import com.anxin.exception.ServiceException;
import com.anxin.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {


    @ExceptionHandler(ServiceException.class)
    public Result<String> serviceExceptionHandler(HttpServletRequest request, ServiceException exception) {
        log.error("业务异常 method : {} url : {} query : {}", request.getMethod(), getRequestUrl(request), getRequestQuery(request), exception);
        Integer code = exception.getCode() == null
                ? ResultCode.SYSTEM_ERROR.getCode()
                : exception.getCode();
        return Result.error(code, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<List<ArgumentError>> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        log.error("参数验证异常 method : {} url : {} query : {}", request.getMethod(), getRequestUrl(request), getRequestQuery(request), ex);
        BindingResult bindingResult = ex.getBindingResult();
        List<ArgumentError> argumentErrorList = bindingResult.getFieldErrors()
                .stream()
                .map(fieldError -> {
                    ArgumentError argumentError = new ArgumentError();
                    argumentError.setArgumentName(fieldError.getField());
                    argumentError.setMessage(fieldError.getDefaultMessage());
                    return argumentError;
                })
                .collect(Collectors.toList());
        return Result.of(ResultCode.PARAM_ERROR.getCode(), ResultCode.PARAM_ERROR.getMsg(), argumentErrorList);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<String> missingServletRequestPartExceptionHandler(HttpServletRequest request, MissingServletRequestPartException ex) {
        log.error("缺少请求参数 method : {} url : {} query : {}", request.getMethod(), getRequestUrl(request), getRequestQuery(request), ex);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "缺少参数：" + ex.getRequestPartName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<String> maxUploadSizeExceededExceptionHandler(HttpServletRequest request, MaxUploadSizeExceededException ex) {
        log.error("上传文件超出大小限制 method : {} url : {}", request.getMethod(), getRequestUrl(request), ex);
        return Result.error(ResultCode.FILE_SIZE_EXCEEDED.getCode(), "文件大小超出限制");
    }

    @ExceptionHandler(Throwable.class)
    public Result<String> defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("全局异常 method : {} url : {} query : {}", request.getMethod(), getRequestUrl(request), getRequestQuery(request), throwable);
        return Result.error(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMsg());
    }

    private String getRequestUrl(HttpServletRequest request) {
        return request.getRequestURL().toString();
    }

    private String getRequestQuery(HttpServletRequest request) {
        return request.getQueryString();
    }
}
