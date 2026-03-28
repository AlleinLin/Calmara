package com.calmara.agent.rag;

import com.calmara.model.dto.ValidationResult;
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

class ResultValidatorTest {

    @Mock
    private ChatClient chatClient;

    private ResultValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ResultValidator(chatClient);
    }

    @Test
    void testValidate_ValidResponse() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(mockGeneration.getOutput()).thenReturn(
                new org.springframework.ai.chat.messages.AssistantMessage("VALID"));
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        ValidationResult result = validator.validate("回答内容", "问题", "参考资料");

        assertTrue(result.isValid());
    }

    @Test
    void testValidate_InvalidResponse() {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(mockGeneration.getOutput()).thenReturn(
                new org.springframework.ai.chat.messages.AssistantMessage("INVALID - 回答不相关"));
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        ValidationResult result = validator.validate("错误回答", "问题", "参考资料");

        assertFalse(result.isValid());
        assertNotNull(result.getReason());
    }

    @Test
    void testValidate_ExceptionReturnsValid() {
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("Test error"));

        ValidationResult result = validator.validate("回答", "问题", "资料");

        assertTrue(result.isValid());
    }
}
