package com.calmara.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class QueryRewriter {

    private static final String REWRITE_PROMPT = """
            你是一个查询优化器。请将用户的问题重写为更适合检索心理知识库的查询词。

            原始问题：%s

            要求：
            1. 提取关键词
            2. 添加相关的心理学术语
            3. 输出简洁的检索词，不要完整句子
            4. 只输出检索词，不要其他内容
            """;

    private final ChatClient chatClient;

    public QueryRewriter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String rewrite(String originalQuery) {
        try {
            String promptText = String.format(REWRITE_PROMPT, originalQuery);
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String rewritten = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim();

            log.info("Query重写: '{}' -> '{}'", originalQuery, rewritten);
            return rewritten;

        } catch (Exception e) {
            log.warn("Query重写失败，使用原始查询", e);
            return originalQuery;
        }
    }
}
