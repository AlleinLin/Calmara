package com.calmara.agent.rag;

import com.calmara.common.JsonUtils;
import com.calmara.model.dto.RetrieveDecision;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RetrieveDecider {

    private static final String DECIDE_PROMPT = """
            你是一个专业的校园心理咨询智能体，必须严格按照以下步骤执行多步推理：

            步骤1：理解用户问题与情绪状态
            步骤2：判断是否需要查询心理知识库（Chroma）
               - 需要：返回 action = "RETRIEVE"
               - 不需要：返回 action = "ANSWER"
            步骤3：如果需要检索，生成精准的检索关键词

            用户问题：%s

            【输出格式：严格JSON，不要其他任何内容】
            {
                "thought": "你的思考过程",
                "action": "RETRIEVE | ANSWER",
                "query": "检索关键词（不需要则为空）"
            }
            """;

    private final ChatClient chatClient;

    public RetrieveDecider(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public RetrieveDecision decide(String query) {
        try {
            String promptText = String.format(DECIDE_PROMPT, query);
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim();

            String json = extractJson(response);
            DecisionResponse decision = JsonUtils.fromJson(json, DecisionResponse.class);

            log.info("检索决策: thought='{}', action={}, query='{}'",
                    decision.getThought(), decision.getAction(), decision.getQuery());

            return RetrieveDecision.builder()
                    .action("RETRIEVE".equalsIgnoreCase(decision.getAction())
                            ? RetrieveDecision.RetrieveAction.RETRIEVE
                            : RetrieveDecision.RetrieveAction.ANSWER)
                    .query(decision.getQuery())
                    .thought(decision.getThought())
                    .build();

        } catch (Exception e) {
            log.error("检索决策解析失败，默认执行检索", e);
            return RetrieveDecision.builder()
                    .action(RetrieveDecision.RetrieveAction.RETRIEVE)
                    .query(query)
                    .thought("解析失败，默认检索")
                    .build();
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    @Data
    private static class DecisionResponse {
        private String thought;
        private String action;
        private String query;
    }
}
