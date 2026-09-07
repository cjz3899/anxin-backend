package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskReportVO {

    private String documentId;

    private String taskId;

    private String riskSummary;

    private Integer highCount;

    private Integer mediumCount;

    private Integer lowCount;

    private List<RiskDetailVO> risks;
}
