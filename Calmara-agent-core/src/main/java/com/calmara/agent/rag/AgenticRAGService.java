package com.calmara.agent.rag;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.dto.RAGContext;
import com.calmara.model.dto.RetrieveDecision;
import com.calmara.model.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgenticRAGService {

    private static final String GENERATE_PROMPT = """
            你是一个专业的校园心理咨询助手。请基于以下知识库内容，温和、专业地回答用户的问题。

            【知识库内容】
            %s

            【用户问题】
            %s

            【用户当前情绪】
            %s

            【回答要求】
            1. 语气温和、有同理心
            2. 基于知识库内容回答，不要编造
            3. 如果是高风险情况，提供危机干预资源
            4. 建议寻求专业帮助

            请回答：
            """;

    private final QueryRewriter queryRewriter;
    private final RetrieveDecider retrieveDecider;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ResultValidator resultValidator;

    public AgenticRAGService(QueryRewriter queryRewriter,
                             RetrieveDecider retrieveDecider,
                             VectorStore vectorStore,
                             ChatClient chatClient,
                             ResultValidator resultValidator) {
        this.queryRewriter = queryRewriter;
        this.retrieveDecider = retrieveDecider;
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.resultValidator = resultValidator;
    }

    public Flux<String> executeRAG(String query, RAGContext context) {
        return Flux.create(emitter -> {
            try {
                String currentQuery = query;
                int retryCount = 0;
                String retrievedContext = "";

                while (retryCount < context.getMaxRetries()) {
                    log.info("RAG执行第{}轮, query='{}'", retryCount + 1, currentQuery);

                    String rewrittenQuery = queryRewriter.rewrite(currentQuery);

                    RetrieveDecision decision = retrieveDecider.decide(rewrittenQuery);

                    if (decision.shouldRetrieve()) {
                        List<Document> docs = vectorStore.similaritySearch(decision.getQuery(), 5);
                        retrievedContext = docs.stream()
                                .map(Document::getContent)
                                .collect(Collectors.joining("\n\n"));
                        log.info("检索到{}个文档", docs.size());
                    }

                    String promptText = String.format(GENERATE_PROMPT,
                            retrievedContext.isEmpty() ? "无相关资料" : retrievedContext,
                            query,
                            context.getEmotionResult() != null ? context.getEmotionResult().getLabel() : "未知");

                    Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
                    String answer = chatClient.call(prompt)
                            .getResult()
                            .getOutput()
                            .getContent();

                    ValidationResult validation = resultValidator.validate(answer, query, retrievedContext);

                    if (validation.isValid()) {
                        for (char c : answer.toCharArray()) {
                            emitter.next(String.valueOf(c));
                        }
                        emitter.complete();
                        return;
                    } else {
                        log.warn("回答校验不通过: {}, 进行第{}次重试", validation.getReason(), retryCount + 1);
                        currentQuery = query + " (请避免: " + validation.getReason() + ")";
                        retryCount++;
                    }
                }

                String fallbackAnswer = "非常抱歉，我暂时无法很好地回答这个问题。建议您联系专业的心理咨询师。";
                for (char c : fallbackAnswer.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();

            } catch (Exception e) {
                log.error("RAG执行失败", e);
                emitter.error(e);
            }
        });
    }

    public String simpleAnswer(String query, EmotionResult emotionResult) {
        String promptText = "你是Calmara，一个专业的校园心理咨询智能体。请温和、专业地回答用户的问题。\n\n用户问题：" + query + "\n\n回答：";
        Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
        return chatClient.call(prompt)
                .getResult()
                .getOutput()
                .getContent();
    }
}
