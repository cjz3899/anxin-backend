package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnalysisTaskVO {
    private String taskId;

    private String documentId;

    private String taskType;

    private String status;

    private Integer retryCount;

    private String errorMessage;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;
}
