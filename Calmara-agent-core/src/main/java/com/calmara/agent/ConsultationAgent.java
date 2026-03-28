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
public class ConsultationAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            # 角色：心理咨询Agent

            你是一名温暖、专业的校园心理咨询师。基于专业知识库和用户情况，提供专业的心理支持。

            ## 咨询原则
            1. **共情优先**：首先认可用户的感受，让用户感到被理解
            2. **专业支撑**：基于心理学原理给出建议
            3. **非指导性**：不直接告诉用户该怎么做，而是引导思考
            4. **资源导向**：适时提供可用的心理资源

            ## 咨询技术
            - 情感反映：准确反映用户的情绪状态
            - 开放式提问：引导用户深入探索问题
            - 正常化：将用户的反应定义为正常的应对方式
            - 赋能：强调用户的优势和应对能力

            ## 知识库内容（如有）
            %s

            ## 用户信息
            用户输入: %s
            情绪状态: %s
            对话历史: %s

            ## 输出要求
            提供一段温柔、专业的心理咨询回复，包含：
            1. 对用户感受的共情
            2. 基于知识库的专业建议
            3. 适时的提问引导
            4. 正向的赋能话语

            只输出回复内容，不要输出其他格式。
            """;

    private final ChatClient chatClient;

    public ConsultationAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getAgentName() {
        return "ConsultationAgent";
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentResponse execute(String input, AgentContext context) {
        try {
            String contextStr = context.getRetrievedDocuments() != null && !context.getRetrievedDocuments().isEmpty()
                    ? String.join("\n\n", context.getRetrievedDocuments())
                    : "无相关专业知识库内容";

            String emotionInfo = context.getEmotionResult() != null
                    ? context.getEmotionResult().getLabel() + " (分数=" + context.getEmotionResult().getScore() + ")"
                    : "未知";

            String history = context.getChatHistory() != null && !context.getChatHistory().isEmpty()
                    ? context.getChatHistory().entrySet().stream()
                        .map(e -> "用户: " + e.getKey() + "\n助手: " + e.getValue())
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("无历史记录")
                    : "无历史记录";

            String promptText = String.format(SYSTEM_PROMPT, contextStr, input, emotionInfo, history);
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim();

            log.info("ConsultationAgent完成咨询回复");

            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("完成专业心理咨询回复")
                    .action("CONSULT")
                    .content(response)
                    .intent(IntentType.CONSULT)
                    .build();

        } catch (Exception e) {
            log.error("ConsultationAgent执行失败", e);
            return AgentResponse.builder()
                    .agentName(getAgentName())
                    .thought("咨询失败，返回温和的默认回复")
                    .action("CONSULT")
                    .content("感谢你愿意和我分享你的感受。我理解你现在可能感到很困扰，能够表达出来本身就是很重要的一步。请告诉我更多关于你的情况，我会尽力帮助你。")
                    .intent(IntentType.CONSULT)
                    .build();
        }
    }
}
