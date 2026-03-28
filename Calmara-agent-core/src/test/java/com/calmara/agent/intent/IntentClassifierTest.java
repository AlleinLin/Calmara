package com.calmara.agent.intent;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.IntentType;
import com.calmara.model.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntentClassifierTest {

    @Mock
    private ChatClient chatClient;

    private IntentClassifier intentClassifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        intentClassifier = new IntentClassifier(chatClient);
    }

    @Test
    void testClassify_HighRiskEmotion_ReturnsRisk() {
        EmotionResult highRiskEmotion = EmotionResult.builder()
                .label("高风险")
                .score(4.0)
                .riskLevel(RiskLevel.HIGH)
                .build();

        IntentType result = intentClassifier.classify("我很难受", highRiskEmotion);

        assertEquals(IntentType.RISK, result);
        verify(chatClient, never()).call(any(Prompt.class));
    }

    @Test
    void testClassify_ChatIntent() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(mockGeneration.getOutput()).thenReturn(new org.springframework.ai.chat.messages.AssistantMessage("CHAT"));
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        EmotionResult normalEmotion = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .riskLevel(RiskLevel.LOW)
                .build();

        IntentType result = intentClassifier.classify("今天天气怎么样", normalEmotion);

        assertEquals(IntentType.CHAT, result);
    }

    @Test
    void testClassify_ConsultIntent() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(mockGeneration.getOutput()).thenReturn(new org.springframework.ai.chat.messages.AssistantMessage("CONSULT"));
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        EmotionResult normalEmotion = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .riskLevel(RiskLevel.MEDIUM)
                .build();

        IntentType result = intentClassifier.classify("我最近很焦虑", normalEmotion);

        assertEquals(IntentType.CONSULT, result);
    }

    @Test
    void testClassify_MediumRiskUpgradesToConsult() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(mockGeneration.getOutput()).thenReturn(new org.springframework.ai.chat.messages.AssistantMessage("CHAT"));
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        EmotionResult mediumRiskEmotion = EmotionResult.builder()
                .label("焦虑")
                .score(1.5)
                .riskLevel(RiskLevel.MEDIUM)
                .build();

        IntentType result = intentClassifier.classify("随便聊聊", mediumRiskEmotion);

        assertEquals(IntentType.CONSULT, result);
    }

    @Test
    void testClassify_ExceptionReturnsDefault() {
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("Test error"));

        EmotionResult normalEmotion = EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .riskLevel(RiskLevel.LOW)
                .build();

        IntentType result = intentClassifier.classify("测试", normalEmotion);

        assertEquals(IntentType.CONSULT, result);
    }
}
