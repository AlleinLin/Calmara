package com.calmara.model.dto;

import com.calmara.model.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EmotionResultTest {

    @Test
    void testNormalFactory() {
        EmotionResult result = EmotionResult.normal();

        assertEquals("正常", result.getLabel());
        assertEquals(0.0, result.getScore());
        assertEquals("default", result.getSource());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void testFromTextFactory() {
        EmotionResult result = EmotionResult.fromText("some text");

        assertEquals("正常", result.getLabel());
        assertEquals(0.0, result.getScore());
        assertEquals("text", result.getSource());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void testIsHighRisk() {
        EmotionResult highRisk = EmotionResult.builder()
                .label("高风险")
                .score(4.0)
                .riskLevel(RiskLevel.HIGH)
                .build();

        EmotionResult lowRisk = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .riskLevel(RiskLevel.LOW)
                .build();

        assertTrue(highRisk.isHighRisk());
        assertFalse(lowRisk.isHighRisk());
    }

    @Test
    void testIsMediumOrHighRisk() {
        EmotionResult highRisk = EmotionResult.builder()
                .riskLevel(RiskLevel.HIGH)
                .build();

        EmotionResult mediumRisk = EmotionResult.builder()
                .riskLevel(RiskLevel.MEDIUM)
                .build();

        EmotionResult lowRisk = EmotionResult.builder()
                .riskLevel(RiskLevel.LOW)
                .build();

        assertTrue(highRisk.isMediumOrHighRisk());
        assertTrue(mediumRisk.isMediumOrHighRisk());
        assertFalse(lowRisk.isMediumOrHighRisk());
    }
}
