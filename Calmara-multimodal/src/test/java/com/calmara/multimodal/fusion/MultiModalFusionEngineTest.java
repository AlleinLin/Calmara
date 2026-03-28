package com.calmara.multimodal.fusion;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MultiModalFusionEngineTest {

    private MultiModalFusionEngine fusionEngine;

    @BeforeEach
    void setUp() {
        fusionEngine = new MultiModalFusionEngine();
    }

    @Test
    void testFuse_AllNormal_ReturnsLowRisk() {
        EmotionResult text = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("text")
                .build();

        EmotionResult audio = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("audio")
                .build();

        EmotionResult visual = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("visual")
                .build();

        EmotionResult result = fusionEngine.fuse(text, audio, visual);

        assertEquals("正常", result.getLabel());
        assertEquals(0.0, result.getScore());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

    @Test
    void testFuse_HighVisualRisk_ReturnsHighRisk() {
        EmotionResult text = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("text")
                .build();

        EmotionResult audio = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .source("audio")
                .build();

        EmotionResult visual = EmotionResult.builder()
                .label("高风险")
                .score(4.0)
                .source("visual")
                .build();

        EmotionResult result = fusionEngine.fuse(text, audio, visual);

        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getScore() >= 2.0);
    }

    @Test
    void testFuse_OnlyTextInput_ReturnsCorrectResult() {
        EmotionResult text = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .source("text")
                .build();

        EmotionResult result = fusionEngine.fuse(text, null, null);

        assertEquals("焦虑", result.getLabel());
        assertEquals(2.0, result.getScore());
    }

    @Test
    void testFuse_NoInput_ReturnsDefault() {
        EmotionResult result = fusionEngine.fuse(null, null, null);

        assertEquals("正常", result.getLabel());
        assertEquals(0.0, result.getScore());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

    @Test
    void testFuse_WithList_AllInputs() {
        EmotionResult text = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("text")
                .build();

        EmotionResult audio = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .source("audio")
                .build();

        EmotionResult visual = EmotionResult.builder()
                .label("低落")
                .score(3.0)
                .source("visual")
                .build();

        EmotionResult result = fusionEngine.fuse(Arrays.asList(text, audio, visual));

        assertNotNull(result);
        assertEquals("fusion", result.getSource());
    }

    @Test
    void testFuse_WithList_EmptyList() {
        EmotionResult result = fusionEngine.fuse(Collections.emptyList());

        assertEquals("正常", result.getLabel());
        assertEquals(0.0, result.getScore());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

    @Test
    void testFuse_WeightedCalculation() {
        EmotionResult text = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .source("text")
                .build();

        EmotionResult audio = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("audio")
                .build();

        EmotionResult visual = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("visual")
                .build();

        EmotionResult result = fusionEngine.fuse(text, audio, visual);

        double expectedScore = (2.0 * 0.1 + 0.0 * 0.4 + 0.0 * 0.5) / 1.0;
        assertEquals(expectedScore, result.getScore(), 0.01);
    }

    @Test
    void testFuse_MediumRisk() {
        EmotionResult text = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .source("text")
                .build();

        EmotionResult audio = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .source("audio")
                .build();

        EmotionResult visual = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("visual")
                .build();

        EmotionResult result = fusionEngine.fuse(text, audio, visual);

        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }
}
