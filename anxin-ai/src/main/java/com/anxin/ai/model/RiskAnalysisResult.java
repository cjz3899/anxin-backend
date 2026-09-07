package com.anxin.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RiskAnalysisResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String riskSummary;

    @Builder.Default
    private List<RiskDetailInfo> risks = new ArrayList<>();
}
