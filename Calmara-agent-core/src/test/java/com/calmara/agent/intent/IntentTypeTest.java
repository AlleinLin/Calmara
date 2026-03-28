package com.calmara.agent.intent;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.IntentType;
import com.calmara.model.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntentTypeTest {

    @Test
    void testChatShouldNotRecordOrAlert() {
        IntentType chat = IntentType.CHAT;
        assertFalse(chat.shouldRecord());
        assertFalse(chat.shouldAlert());
    }

    @Test
    void testConsultShouldRecordButNotAlert() {
        IntentType consult = IntentType.CONSULT;
        assertTrue(consult.shouldRecord());
        assertFalse(consult.shouldAlert());
    }

    @Test
    void testRiskShouldRecordAndAlert() {
        IntentType risk = IntentType.RISK;
        assertTrue(risk.shouldRecord());
        assertTrue(risk.shouldAlert());
    }

    @Test
    void testEmotionResultHighRiskShouldTriggerRiskIntent() {
        EmotionResult highRiskEmotion = EmotionResult.builder()
                .label("高风险")
                .score(4.0)
                .riskLevel(RiskLevel.HIGH)
                .build();

        assertTrue(highRiskEmotion.isHighRisk());
        assertEquals(RiskLevel.HIGH, highRiskEmotion.getRiskLevel());
    }
}
