package com.calmara.agent;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RiskAssessmentAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            # 角色：风险评估Agent

            你是一名专业的心理危机评估专家。你的任务是基于多模态情绪融合结果和对话内容，进行综合风险评估。

            ## 重要说明
            - 多模态情绪融合（表情+语气+文本）只是初筛，容易误判
            - 看剧激动、吐槽发泄等会被打出高分，但不属于真实风险
            - 你的判断需要结合：情绪分数 + 对话内容 + 上下文
            - 只有内容与情绪同时高风险，才是真正需要干预的情况

            ## 风险评估维度

            ### 高风险判断标准（需同时满足）
            1. 情绪融合分数 >= 2.0
            2. 对话内容涉及：自杀念头、自残计划、绝望无助、严重抑郁症状
            3. 有明确的自我伤害意图或行为描述

            ### 中等风险判断标准
            1. 情绪分数 1.0-2.0
            2. 内容涉及：轻度抑郁、焦虑障碍、压力过大、情绪失控
            3. 有求助意愿

            ### 低风险/正常
            1. 情绪分数 < 1.0
            2. 内容为日常倾诉、压力抱怨、情绪发泄
            3. 无自伤风险信号

            ## 输出格式（严格JSON）
            {
                "risk_level": "HIGH|MEDIUM|LOW",
                "risk_score": 0.0-4.0,
                "risk_factors": ["风险因素1", "风险因素2"],
                "protective_factors": ["保护因素1"],
                "risk_description": "风险描述",
                "need_intervention": true|false,
                "intervention_urgency": "URGENT|MODERATE|NONE"
            }

            ## 上下文信息
            用户当前情绪（多模态融合）: %s
            用户输入: %s
            """;

    private final ChatClient chatClient;

    public RiskAssessmentAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getAgentName() {
        return "RiskAssessmentAgent";
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentResponse execute(String input, AgentContext context) {
        try {
            EmotionResult emotionResult = context.getEmotionResult();
            String emotionInfo = emotionResult != null
                    ? String.format("分数=%.2f, 情绪标签=%s, 风险等级=%s",
                        emotionResult.getScore(),
                        emotionResult.getLabel(),
                        emotionResult.getRiskLevel())
                    : "无多模态数据";

            String promptText = String.format(SYSTEM_PROMPT, emotionInfo, input);
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim();

            RiskLevel riskLevel = parseRiskLevel(response);

            log.info("RiskAssessmentAgent完成评估: riskLevel={}", riskLevel);

            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("完成综合风险评估")
                    .action("ASSESS")
                    .content(response)
                    .riskLevel(riskLevel)
                    .isRiskCase(riskLevel == RiskLevel.HIGH)
                    .build();

        } catch (Exception e) {
            log.error("RiskAssessmentAgent执行失败", e);
            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("评估失败，使用默认安全值")
                    .action("ASSESS")
                    .riskLevel(RiskLevel.MEDIUM)
                    .isRiskCase(false)
                    .content("{\"risk_level\": \"MEDIUM\"}")
                    .build();
        }
    }

    private RiskLevel parseRiskLevel(String response) {
        if (response.contains("\"risk_level\"") && response.contains("HIGH")) {
            return RiskLevel.HIGH;
        } else if (response.contains("MEDIUM")) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
