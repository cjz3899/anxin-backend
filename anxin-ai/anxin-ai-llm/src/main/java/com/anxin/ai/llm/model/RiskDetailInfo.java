package com.anxin.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 风险详情信息实体类
 * 这里是单条风险详情信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskDetailInfo {

    private String riskType;

    private String riskLevel;

    private String title;

    private String originalText;

    private String reason;

    private String impact;

    private String suggestion;
}
