package com.calmara.model.dto;

import com.calmara.model.enums.IntentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGContext {
    private String sessionId;
    private Long userId;
    private List<ChatMessageDTO> history;
    private EmotionResult emotionResult;
    private IntentType intentType;
    private int maxRetries;
    private int currentRetry;

    public static RAGContext create(Long userId, String sessionId) {
        return RAGContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .maxRetries(3)
                .currentRetry(0)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageDTO {
        private String role;
        private String content;
        private String emotionLabel;
    }
}
