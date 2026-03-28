package com.calmara.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskLevelTest {

    @Test
    void testFromScoreHigh() {
        RiskLevel level = RiskLevel.fromScore(2.5);
        assertEquals(RiskLevel.HIGH, level);
    }

    @Test
    void testFromScoreMedium() {
        RiskLevel level = RiskLevel.fromScore(1.5);
        assertEquals(RiskLevel.MEDIUM, level);
    }

    @Test
    void testFromScoreLow() {
        RiskLevel level = RiskLevel.fromScore(0.5);
        assertEquals(RiskLevel.LOW, level);
    }

    @Test
    void testIsHigh() {
        assertTrue(RiskLevel.HIGH.isHigh());
        assertFalse(RiskLevel.MEDIUM.isHigh());
        assertFalse(RiskLevel.LOW.isHigh());
    }

    @Test
    void testIsMediumOrHigh() {
        assertTrue(RiskLevel.HIGH.isMediumOrHigh());
        assertTrue(RiskLevel.MEDIUM.isMediumOrHigh());
        assertFalse(RiskLevel.LOW.isMediumOrHigh());
    }
}
