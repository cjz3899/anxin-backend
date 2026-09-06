package com.anxin.enums;

import lombok.Getter;

/**
 * 分析任务状态（对应 analysis_task.status 的 TINYINT 0~3）。
 */
@Getter
public enum TaskStatus {
    PENDING(0),
    PROCESSING(1),
    SUCCESS(2),
    FAILED(3);

    private final int code;

    TaskStatus(int code) {
        this.code = code;
    }

    public static TaskStatus fromCode(int code) {
        for (TaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知任务状态 code=" + code);
    }
}
