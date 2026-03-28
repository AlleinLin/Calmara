package com.calmara.agent;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.IntentType;
import com.calmara.model.enums.RiskLevel;
import lombok.Data;

@Data
public class AgentResponse {
    private String agentName;
    private String thought;
    private String action;
    private String content;
    private EmotionResult emotionResult;
    private RiskLevel riskLevel;
    private IntentType intent;
    private boolean isRiskCase;

    public static AgentResponseBuilder builder() {
        return new AgentResponseBuilder();
    }

    public static class AgentResponseBuilder {
        private String agentName;
        private String thought;
        private String action;
        private String content;
        private EmotionResult emotionResult;
        private RiskLevel riskLevel;
        private IntentType intent;
        private boolean isRiskCase;

        public AgentResponseBuilder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public AgentResponseBuilder thought(String thought) {
            this.thought = thought;
            return this;
        }

        public AgentResponseBuilder action(String action) {
            this.action = action;
            return this;
        }

        public AgentResponseBuilder content(String content) {
            this.content = content;
            return this;
        }

        public AgentResponseBuilder emotionResult(EmotionResult emotionResult) {
            this.emotionResult = emotionResult;
            return this;
        }

        public AgentResponseBuilder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public AgentResponseBuilder intent(IntentType intent) {
            this.intent = intent;
            return this;
        }

        public AgentResponseBuilder isRiskCase(boolean isRiskCase) {
            this.isRiskCase = isRiskCase;
            return this;
        }

        public AgentResponse build() {
            AgentResponse response = new AgentResponse();
            response.agentName = this.agentName;
            response.thought = this.thought;
            response.action = this.action;
            response.content = this.content;
            response.emotionResult = this.emotionResult;
            response.riskLevel = this.riskLevel;
            response.intent = this.intent;
            response.isRiskCase = this.isRiskCase;
            return response;
        }
    }
}
