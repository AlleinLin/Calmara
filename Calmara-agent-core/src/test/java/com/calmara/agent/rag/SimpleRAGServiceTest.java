package com.calmara.agent.rag;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.dto.RAGContext;
import com.calmara.model.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimpleRAGServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClient chatClient;

    private SimpleRAGService simpleRAG;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        simpleRAG = new SimpleRAGService(vectorStore, chatClient);
    }

    @Test
    void testQuery() {
        when(vectorStore.similaritySearch(anyString(), anyInt()))
                .thenReturn(java.util.List.of(new Document("test", "content")));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(mockGeneration.getOutput()).thenReturn(
                new org.springframework.ai.chat.messages.AssistantMessage("测试回答"));
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        String result = simpleRAG.query("测试问题");

        assertNotNull(result);
        verify(vectorStore, times(1)).similaritySearch(anyString(), anyInt());
    }

    @Test
    void testQueryStream() {
        when(vectorStore.similaritySearch(anyString(), anyInt()))
                .thenReturn(java.util.List.of());

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        when(mockGeneration.getOutput()).thenReturn(
                new org.springframework.ai.chat.messages.AssistantMessage("流式回答"));
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        StepVerifier.create(simpleRAG.queryStream("测试"))
                .expectNext("流", "式", "回", "答")
                .verifyComplete();
    }
}
