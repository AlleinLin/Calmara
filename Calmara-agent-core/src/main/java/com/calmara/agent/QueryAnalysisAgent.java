package com.calmara.agent;

import com.calmara.model.enums.IntentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class QueryAnalysisAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            # 角色：查询分析Agent

            你是一名专业的心理咨询对话分析师。你的任务是对用户的输入进行深入分析，提取关键信息，为后续处理提供基础。

            ## 核心职责
            1. **语义理解**：准确理解用户表达的真实含义
            2. **情绪关键词提取**：识别表达情绪状态的词汇
            3. **意图初步判断**：判断用户是想聊天、咨询还是存在风险
            4. **上下文关联**：结合对话历史理解当前状态

            ## 分析维度
            - 表面意图：用户表面上在说什么
            - 深层需求：用户真正想要什么
            - 情绪线索：积极、消极、中性
            - 风险信号：是否包含自伤、自残、绝望等关键词

            ## 输出格式（严格JSON）
            {
                "surface_intent": "表面意图描述",
                "deep_need": "深层需求描述",
                "emotion_keywords": ["关键词1", "关键词2"],
                "emotion_tone": "positive|negative|neutral",
                "risk_signals": ["风险信号1"],
                "analysis_confidence": 0.0-1.0
            }

            用户输入：%s
            """;

    private final ChatClient chatClient;

    public QueryAnalysisAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getAgentName() {
        return "QueryAnalysisAgent";
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentResponse execute(String input, AgentContext context) {
        try {
            String promptText = String.format(SYSTEM_PROMPT, input);
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim();

            log.info("QueryAnalysisAgent完成分析: {}", response);

            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("完成查询语义分析和意图理解")
                    .action("ANALYZE")
                    .content(response)
                    .build();

        } catch (Exception e) {
            log.error("QueryAnalysisAgent执行失败", e);
            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("分析失败，使用默认值")
                    .action("ANALYZE")
                    .content("{\"analysis_confidence\": 0.0}")
                    .build();
        }
    }
}
