package com.calmara.agent.rag;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.dto.RAGContext;
import com.calmara.model.dto.RetrieveDecision;
import com.calmara.model.enums.IntentType;
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
public class RouterRAGService {

    private static final String CHAT_PROMPT = """
            你是Calmara，一个温暖友好的校园心理咨询助手。用户想和你闲聊，请轻松友好地回应。
            
            用户说：%s
            
            回应要求：
            1. 轻松、友好的语气
            2. 可以适时引导到心理健康话题
            3. 保持简短，不要过于正式
            
            请回应：
            """;

    private static final String PROFESSIONAL_PROMPT = """
            你是一个专业的校园心理咨询助手。请基于以下知识库内容，提供专业的心理支持。
            
            【知识库内容】
            %s
            
            【用户问题】
            %s
            
            【用户情绪】
            %s
            
            【回答要求】
            1. 基于知识库内容，专业且有同理心
            2. 提供具体的建议和资源
            3. 如果是高风险，提供危机干预热线
            4. 建议寻求专业帮助
            
            请回答：
            """;

    private final RetrieveDecider retrieveDecider;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final QueryRewriter queryRewriter;

    public RouterRAGService(RetrieveDecider retrieveDecider,
                            VectorStore vectorStore,
                            ChatClient chatClient,
                            QueryRewriter queryRewriter) {
        this.retrieveDecider = retrieveDecider;
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.queryRewriter = queryRewriter;
    }

    public Flux<String> route(String query, RAGContext context) {
        return Flux.create(emitter -> {
            try {
                log.info("RouterRAG开始路由: query='{}'", query);

                RetrieveDecision decision = retrieveDecider.decide(query);
                
                log.info("路由决策: action={}, thought={}", 
                        decision.getAction(), decision.getThought());

                String response;
                
                if (decision.getAction() == RetrieveDecision.RetrieveAction.ANSWER) {
                    log.info("路由到闲聊模式");
                    response = handleChat(query);
                } else {
                    log.info("路由到专业RAG模式");
                    response = handleProfessional(query, context);
                }

                for (char c : response.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();

            } catch (Exception e) {
                log.error("RouterRAG路由失败", e);
                String fallback = "抱歉，我遇到了一些问题。请稍后再试，或者联系专业的心理咨询师。";
                for (char c : fallback.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();
            }
        });
    }

    private String handleChat(String query) {
        String promptText = String.format(CHAT_PROMPT, query);
        Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
        
        return chatClient.call(prompt)
                .getResult()
                .getOutput()
                .getContent();
    }

    private String handleProfessional(String query, RAGContext context) {
        String rewrittenQuery = queryRewriter.rewrite(query);
        
        List<Document> docs = vectorStore.similaritySearch(rewrittenQuery, 5);
        String retrievedContext = docs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
        
        log.info("检索到{}个文档", docs.size());

        String emotionInfo = context.getEmotionResult() != null 
                ? String.format("%s (分数: %.2f)", 
                        context.getEmotionResult().getLabel(), 
                        context.getEmotionResult().getScore())
                : "未知";

        String promptText = String.format(PROFESSIONAL_PROMPT,
                retrievedContext.isEmpty() ? "无相关资料" : retrievedContext,
                query,
                emotionInfo);

        Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
        
        return chatClient.call(prompt)
                .getResult()
                .getOutput()
                .getContent();
    }

    public String routeSync(String query, EmotionResult emotionResult) {
        log.info("RouterRAG同步路由: query='{}'", query);

        RetrieveDecision decision = retrieveDecider.decide(query);
        
        RAGContext context = RAGContext.builder()
                .emotionResult(emotionResult)
                .maxRetries(1)
                .build();

        if (decision.getAction() == RetrieveDecision.RetrieveAction.ANSWER) {
            return handleChat(query);
        } else {
            return handleProfessional(query, context);
        }
    }

    public RoutingResult analyzeRouting(String query) {
        RetrieveDecision decision = retrieveDecider.decide(query);
        
        return RoutingResult.builder()
                .action(decision.getAction().name())
                .thought(decision.getThought())
                .suggestedQuery(decision.getQuery())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class RoutingResult {
        private String action;
        private String thought;
        private String suggestedQuery;
    }
}
