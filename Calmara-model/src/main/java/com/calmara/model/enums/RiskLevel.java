package com.calmara.model.enums;

import lombok.Getter;

@Getter
public enum RiskLevel {
    LOW(0, 1.0, "低风险"),
    MEDIUM(1, 2.0, "中风险"),
    HIGH(2, Double.MAX_VALUE, "高风险");

    private final int level;
    private final double threshold;
    private final String description;

    RiskLevel(int level, double threshold, String description) {
        this.level = level;
        this.threshold = threshold;
        this.description = description;
    }

    public static RiskLevel fromScore(double score) {
        if (score >= 2.0) {
            return HIGH;
        } else if (score >= 1.0) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }

    public boolean isHigh() {
        return this == HIGH;
    }

    public boolean isMediumOrHigh() {
        return this == MEDIUM || this == HIGH;
    }
}
