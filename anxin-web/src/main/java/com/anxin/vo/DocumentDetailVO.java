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
public class DocumentDetailVO {
    private String id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;
    private String summary;
    /**
     * 整体风险等级：HIGH/MEDIUM/LOW（服务端推导），null 表示暂无分析结果
     */
    private String riskLevel;
    private String latestTaskId;
    private String taskStatus;
    private String errorMessage;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
