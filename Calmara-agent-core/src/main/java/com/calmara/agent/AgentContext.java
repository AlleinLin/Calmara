package com.calmara.agent;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.IntentType;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentContext {
    private String sessionId;
    private String originalQuery;
    private String rewrittenQuery;
    private IntentType intent;
    private EmotionResult emotionResult;
    private List<String> retrievedDocuments;
    private Map<String, String> chatHistory;
    private AgentResponse previousAgentResponse;
    private String finalResponse;

    public static AgentContextBuilder builder() {
        return new AgentContextBuilder();
    }

    public static class AgentContextBuilder {
        private String sessionId;
        private String originalQuery;
        private String rewrittenQuery;
        private IntentType intent;
        private EmotionResult emotionResult;
        private List<String> retrievedDocuments;
        private Map<String, String> chatHistory;
        private AgentResponse previousAgentResponse;
        private String finalResponse;

        public AgentContextBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public AgentContextBuilder originalQuery(String originalQuery) {
            this.originalQuery = originalQuery;
            return this;
        }

        public AgentContextBuilder rewrittenQuery(String rewrittenQuery) {
            this.rewrittenQuery = rewrittenQuery;
            return this;
        }

        public AgentContextBuilder intent(IntentType intent) {
            this.intent = intent;
            return this;
        }

        public AgentContextBuilder emotionResult(EmotionResult emotionResult) {
            this.emotionResult = emotionResult;
            return this;
        }

        public AgentContextBuilder retrievedDocuments(List<String> retrievedDocuments) {
            this.retrievedDocuments = retrievedDocuments;
            return this;
        }

        public AgentContextBuilder chatHistory(Map<String, String> chatHistory) {
            this.chatHistory = chatHistory;
            return this;
        }

        public AgentContextBuilder previousAgentResponse(AgentResponse previousAgentResponse) {
            this.previousAgentResponse = previousAgentResponse;
            return this;
        }

        public AgentContextBuilder finalResponse(String finalResponse) {
            this.finalResponse = finalResponse;
            return this;
        }

        public AgentContext build() {
            AgentContext context = new AgentContext();
            context.sessionId = this.sessionId;
            context.originalQuery = this.originalQuery;
            context.rewrittenQuery = this.rewrittenQuery;
            context.intent = this.intent;
            context.emotionResult = this.emotionResult;
            context.retrievedDocuments = this.retrievedDocuments;
            context.chatHistory = this.chatHistory;
            context.previousAgentResponse = this.previousAgentResponse;
            context.finalResponse = this.finalResponse;
            return context;
        }
    }
}
