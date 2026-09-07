package com.anxin.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RiskDetailInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String sectionNo;

    private String riskType;

    private RiskLevel riskLevel;

    private String title;

    private String originalText;

    private String reason;

    private String impact;

    private String suggestion;

    private Integer pageNo;
}
