package com.calmara.agent;

import com.calmara.model.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ResponseGenerationAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            # 角色：响应生成Agent（决策层）

            你是Calmara系统的最终响应生成器。你的职责是整合所有Agent的分析结果，生成最终响应。

            ## 核心职责
            1. 整合QueryAnalysisAgent的分析结果
            2. 整合RiskAssessmentAgent的风险评估
            3. 整合ConsultationAgent的咨询内容
            4. 根据综合判断生成最终响应

            ## 决策逻辑

            ### 最终意图判定
            - 如果RiskAssessmentAgent判定为HIGH → 最终意图=RISK
            - 如果QueryAnalysisAgent分析有中高风险信号 → 最终意图=RISK
            - 如果是心理咨询类内容 → 最终意图=CONSULT
            - 如果是日常闲聊 → 最终意图=CHAT

            ### 响应策略
            - **RISK**：提供危机干预资源，强调求助重要性，保持陪伴
            - **CONSULT**：整合专业咨询内容，提供支持性回应
            - **CHAT**：轻松友好地回应，适时引导至更有意义的话题

            ## 各Agent结果汇总
            查询分析: %s
            风险评估: %s
            咨询内容: %s

            用户原始输入: %s

            ## 输出格式
            {
                "final_intent": "CHAT|CONSULT|RISK",
                "response_content": "最终回复内容",
                "should_alert": true|false,
                "alert_reason": "如果需要预警，说明原因"
            }

            严格JSON格式输出，不要输出其他内容。
            """;

    private final ChatClient chatClient;

    public ResponseGenerationAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getAgentName() {
        return "ResponseGenerationAgent";
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentResponse execute(String input, AgentContext context) {
        try {
            AgentResponse queryAnalysis = context.getPreviousAgentResponse();
            String queryAnalysisResult = queryAnalysis != null ? queryAnalysis.getContent() : "无查询分析结果";

            String riskAssessmentResult = "无风险评估结果";
            if (context.getEmotionResult() != null) {
                riskAssessmentResult = String.format("情绪分数=%.2f, 风险等级=%s",
                        context.getEmotionResult().getScore(),
                        context.getEmotionResult().getRiskLevel());
            }

            String consultationResult = context.getFinalResponse() != null
                    ? context.getFinalResponse()
                    : "无咨询内容";

            String promptText = String.format(SYSTEM_PROMPT,
                    queryAnalysisResult,
                    riskAssessmentResult,
                    consultationResult,
                    input);
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim();

            log.info("ResponseGenerationAgent完成最终响应生成");

            boolean shouldAlert = response.contains("should_alert") && response.contains("true");
            RiskLevel riskLevel = determineFinalRiskLevel(response);

            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("完成多Agent结果整合与最终决策")
                    .action("GENERATE")
                    .content(extractContent(response))
                    .riskLevel(riskLevel)
                    .isRiskCase(shouldAlert || riskLevel == RiskLevel.HIGH)
                    .build();

        } catch (Exception e) {
            log.error("ResponseGenerationAgent执行失败", e);
            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("响应生成失败")
                    .action("GENERATE")
                    .content("感谢你的分享。如果你有任何困扰或需要帮助，请随时告诉我。")
                    .riskLevel(RiskLevel.LOW)
                    .isRiskCase(false)
                    .build();
        }
    }

    private RiskLevel determineFinalRiskLevel(String response) {
        if (response.contains("RISK")) {
            return RiskLevel.HIGH;
        } else if (response.contains("CONSULT")) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String extractContent(String jsonResponse) {
        int contentStart = jsonResponse.indexOf("\"response_content\"");
        if (contentStart < 0) {
            return jsonResponse;
        }

        int colonPos = jsonResponse.indexOf(":", contentStart);
        if (colonPos < 0) {
            return jsonResponse;
        }

        int startQuote = jsonResponse.indexOf("\"", colonPos + 1);
        if (startQuote < 0) {
            return jsonResponse;
        }

        int endQuote = startQuote + 1;
        while (endQuote < jsonResponse.length()) {
            char c = jsonResponse.charAt(endQuote);
            if (c == '\\') {
                endQuote += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            endQuote++;
        }

        return jsonResponse.substring(startQuote + 1, endQuote);
    }
}
