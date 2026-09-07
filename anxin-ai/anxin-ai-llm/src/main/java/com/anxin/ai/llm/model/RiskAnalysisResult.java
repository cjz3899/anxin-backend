package com.anxin.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * 风险分析结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskAnalysisResult {

    private String riskSummary;

    private Integer highCount;

    private Integer mediumCount;

    private Integer lowCount;

    // 风险详情信息列表
    private List<RiskDetailInfo> risks;
}
