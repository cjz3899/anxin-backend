package com.anxin.service.support;

/**
 * 整体风险等级推导：高>0 → HIGH，否则中>0 → MEDIUM，否则 → LOW（无风险归为低）。
 * 各级数量只作为推导依据，不返回前端。
 */
public class RiskLevelCalculator {

    private RiskLevelCalculator() {
    }

    public static String derive(Integer highCount, Integer mediumCount, Integer lowCount) {
        if (count(highCount) > 0) {
            return "HIGH";
        }
        if (count(mediumCount) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }
}
