package com.calmara.model.dto;

import com.calmara.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionResult {
    private String label;
    private Double score;
    private Double confidence;
    private String source;
    private RiskLevel riskLevel;
    private String reasoning;
    private LocalDateTime timestamp;
    private Map<String, Object> features;
    private Map<String, Object> metadata;

    public static EmotionResult normal() {
        return EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("default")
                .riskLevel(RiskLevel.LOW)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static EmotionResult fromText(String text) {
        return EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("text")
                .riskLevel(RiskLevel.LOW)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public boolean isHighRisk() {
        return RiskLevel.HIGH.equals(this.riskLevel);
    }

    public boolean isMediumOrHighRisk() {
        return this.riskLevel != null && this.riskLevel.isMediumOrHigh();
    }
}
