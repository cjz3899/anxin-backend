package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskReportVO {

    private String documentId;

    private String taskId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;

    private String riskSummary;

    /**
     * 整体风险等级：HIGH/MEDIUM/LOW（服务端由高/中/低数量推导，各级数量不返回前端）
     */
    private String riskLevel;

    /**
     * 共发现的风险问题数量（图4 顶部卡片：共发现 N 项风险问题）
     */
    private Integer riskCount;

    private List<RiskDetailVO> risks;
}
