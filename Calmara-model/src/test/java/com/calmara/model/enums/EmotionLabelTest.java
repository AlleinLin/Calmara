package com.calmara.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmotionLabelTest {

    @Test
    void testFromLabel() {
        assertEquals(EmotionLabel.NORMAL, EmotionLabel.fromLabel("正常"));
        assertEquals(EmotionLabel.ANXIOUS, EmotionLabel.fromLabel("焦虑"));
        assertEquals(EmotionLabel.DEPRESSED, EmotionLabel.fromLabel("低落"));
        assertEquals(EmotionLabel.HIGH_RISK, EmotionLabel.fromLabel("高风险"));
    }

    @Test
    void testFromUnknownLabel() {
        assertEquals(EmotionLabel.NORMAL, EmotionLabel.fromLabel("未知"));
    }

    @Test
    void testFromScore() {
        assertEquals(EmotionLabel.NORMAL, EmotionLabel.fromScore(0.0));
        assertEquals(EmotionLabel.ANXIOUS, EmotionLabel.fromScore(2.0));
        assertEquals(EmotionLabel.DEPRESSED, EmotionLabel.fromScore(3.0));
        assertEquals(EmotionLabel.HIGH_RISK, EmotionLabel.fromScore(4.0));
    }

    @Test
    void testScoreValues() {
        assertEquals(0.0, EmotionLabel.NORMAL.getScore());
        assertEquals(2.0, EmotionLabel.ANXIOUS.getScore());
        assertEquals(3.0, EmotionLabel.DEPRESSED.getScore());
        assertEquals(4.0, EmotionLabel.HIGH_RISK.getScore());
    }
}
