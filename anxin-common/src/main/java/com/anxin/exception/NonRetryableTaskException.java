package com.anxin.exception;

/**
 * 任务不可重试的失败（业务性终止，重试无意义），由消费端直接置 FAILED
 */
public class NonRetryableTaskException extends BaseException {

    public NonRetryableTaskException(String message) {
        super(message);
    }
}
