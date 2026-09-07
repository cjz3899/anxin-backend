package com.anxin.ai.model;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum RiskType {
    AUTO_RENEWAL("自动续约"),
    BREACH_OF_CONTRACT("违约责任"),
    EXEMPTION_CLAUSE("免责条款"),
    PAYMENT_CLAUSE("付款条款"),
    REFUND_CLAUSE("退款条款"),
    COMPENSATION_LIABILITY("赔偿责任"),
    UNILATERAL_TERMINATION("单方面解除"),
    LIABILITY_LIMITATION("责任限制"),
    DISPUTE_RESOLUTION("争议解决"),
    JURISDICTION("管辖权"),
    INTELLECTUAL_PROPERTY("知识产权"),
    CONFIDENTIALITY("保密义务"),
    CONTRACT_TERMINATION("合同解除"),
    MATERIAL_OBLIGATION("重要义务"),
    USER_RESPONSIBILITY("用户责任"),
    RESTRICTIVE_CLAUSE("限制性条款"),
    PENALTY_CLAUSE("处罚条款"),
    RISK_WARNING("风险提示"),
    ABNORMAL_CLAUSE("异常约定"),
    OTHER("其他");

    private final String label;

    RiskType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static String candidateLabels() {
        return Arrays.stream(values())
                .map(RiskType::getLabel)
                .collect(Collectors.joining("、"));
    }
}
