package com.anxin.parser;

/**
 * 文档解析失败异常（包装 Tika 的受检异常）
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
