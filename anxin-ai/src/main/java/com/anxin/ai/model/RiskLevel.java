package com.anxin.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum RiskLevel {
    HIGH("高风险"),
    MEDIUM("中风险"),
    LOW("低风险");

    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    @JsonCreator
    public static RiskLevel parse(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        for (RiskLevel level : values()) {
            if (level.name().equalsIgnoreCase(v) || level.label.equals(v)) {
                return level;
            }
        }
        throw new IllegalArgumentException("无法识别的风险等级: " + value);
    }
}
