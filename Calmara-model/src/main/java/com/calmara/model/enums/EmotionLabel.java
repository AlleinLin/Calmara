package com.calmara.model.enums;

import lombok.Getter;

@Getter
public enum EmotionLabel {
    NORMAL("正常", 0.0),
    ANXIOUS("焦虑", 2.0),
    DEPRESSED("低落", 3.0),
    HIGH_RISK("高风险", 4.0);

    private final String label;
    private final double score;

    EmotionLabel(String label, double score) {
        this.label = label;
        this.score = score;
    }

    public static EmotionLabel fromLabel(String label) {
        for (EmotionLabel emotion : values()) {
            if (emotion.label.equals(label)) {
                return emotion;
            }
        }
        return NORMAL;
    }

    public static EmotionLabel fromScore(double score) {
        if (score >= 4.0) {
            return HIGH_RISK;
        } else if (score >= 3.0) {
            return DEPRESSED;
        } else if (score >= 2.0) {
            return ANXIOUS;
        } else {
            return NORMAL;
        }
    }
}
