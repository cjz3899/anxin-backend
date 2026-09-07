package com.anxin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RiskDetailVO {

    private String id;

    private String sectionId;

    private String riskType;

    private String riskLevel;

    private String title;

    private String originalText;

    private String reason;

    private String impact;

    private String suggestion;
}
