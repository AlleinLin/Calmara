package com.calmara.agent;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MultiAgentCoordinator {

    private final QueryAnalysisAgent queryAnalysisAgent;
    private final RiskAssessmentAgent riskAssessmentAgent;
    private final ConsultationAgent consultationAgent;
    private final ResponseGenerationAgent responseGenerationAgent;
    private final ChatClient chatClient;

    public MultiAgentCoordinator(QueryAnalysisAgent queryAnalysisAgent,
                                  RiskAssessmentAgent riskAssessmentAgent,
                                  ConsultationAgent consultationAgent,
                                  ResponseGenerationAgent responseGenerationAgent,
                                  ChatClient chatClient) {
        this.queryAnalysisAgent = queryAnalysisAgent;
        this.riskAssessmentAgent = riskAssessmentAgent;
        this.consultationAgent = consultationAgent;
        this.responseGenerationAgent = responseGenerationAgent;
        this.chatClient = chatClient;
    }

    public Flux<String> process(String userInput, EmotionResult emotionResult, Map<String, String> chatHistory) {
        return Flux.create(emitter -> {
            try {
                log.info("=== MultiAgent处理开始 ===");
                log.info("用户输入: {}", userInput);
                log.info("情绪状态: {}", emotionResult != null ? emotionResult.getLabel() : "无");

                AgentContext context = AgentContext.builder()
                        .originalQuery(userInput)
                        .emotionResult(emotionResult)
                        .chatHistory(chatHistory != null ? chatHistory : new HashMap<>())
                        .build();

                AgentResponse step1 = queryAnalysisAgent.execute(userInput, context);
                context.setPreviousAgentResponse(step1);
                log.info("Step 1/4: QueryAnalysisAgent完成");

                AgentResponse step2 = riskAssessmentAgent.execute(userInput, context);
                context.setPreviousAgentResponse(step2);
                context.setEmotionResult(mergeWithRiskAssessment(emotionResult, step2));
                log.info("Step 2/4: RiskAssessmentAgent完成, 风险等级={}", step2.getRiskLevel());

                AgentResponse step3 = null;
                if (step2.getRiskLevel() != RiskLevel.HIGH) {
                    step3 = consultationAgent.execute(userInput, context);
                    context.setFinalResponse(step3.getContent());
                    log.info("Step 3/4: ConsultationAgent完成");
                } else {
                    log.info("Step 3/4: 跳过ConsultationAgent (高风险直接进入决策)");
                }

                AgentResponse step4 = responseGenerationAgent.execute(userInput, context);
                String finalResponse = step4.getContent();

                if (step4.isRiskCase()) {
                    log.warn("=== 高风险预警触发 ===");
                    log.warn("用户输入: {}", userInput);
                    log.warn("风险等级: {}", step4.getRiskLevel());
                }

                for (char c : finalResponse.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();

                log.info("=== MultiAgent处理完成 ===");

            } catch (Exception e) {
                log.error("MultiAgent处理失败", e);
                String fallback = "感谢你的分享。遇到困难是很正常的，愿意说出来已经是迈出了重要的一步。如果你感到不适，请及时联系心理求助热线：400-161-9995。";
                for (char c : fallback.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();
            }
        });
    }

    private EmotionResult mergeWithRiskAssessment(EmotionResult originalEmotion, AgentResponse riskResponse) {
        if (originalEmotion == null) {
            return EmotionResult.builder()
                    .label("待评估")
                    .score(0.0)
                    .riskLevel(riskResponse.getRiskLevel())
                    .source("multi_agent_merged")
                    .build();
        }

        if (riskResponse.getRiskLevel() == RiskLevel.HIGH) {
            return EmotionResult.builder()
                    .label(originalEmotion.getLabel())
                    .score(originalEmotion.getScore())
                    .riskLevel(RiskLevel.HIGH)
                    .source("multi_agent_merged")
                    .build();
        }

        return originalEmotion;
    }

    public String processSimple(String userInput, EmotionResult emotionResult) {
        StringBuilder response = new StringBuilder();

        response.append("【QueryAnalysisAgent分析】\n");
        AgentContext context = AgentContext.builder()
                .originalQuery(userInput)
                .emotionResult(emotionResult)
                .chatHistory(new HashMap<>())
                .build();
        AgentResponse step1 = queryAnalysisAgent.execute(userInput, context);
        response.append(step1.getContent()).append("\n\n");

        response.append("【RiskAssessmentAgent评估】\n");
        context.setPreviousAgentResponse(step1);
        AgentResponse step2 = riskAssessmentAgent.execute(userInput, context);
        response.append(step2.getContent());

        if (step2.getRiskLevel() != RiskLevel.HIGH) {
            response.append("\n\n【ConsultationAgent回复】\n");
            context.setEmotionResult(mergeWithRiskAssessment(emotionResult, step2));
            AgentResponse step3 = consultationAgent.execute(userInput, context);
            response.append(step3.getContent());
        } else {
            response.append("\n\n【高风险预警】已触发危机干预流程");
        }

        return response.toString();
    }
}
