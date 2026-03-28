package com.calmara.agent.rag;

import com.calmara.model.dto.EmotionResult;
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
public class SimpleRAGService {

    private static final String RAG_PROMPT = """
            你是一个专业的校园心理咨询助手。请基于以下知识库内容回答用户问题。
            
            【知识库内容】
            %s
            
            【用户问题】
            %s
            
            【回答要求】
            1. 基于知识库内容回答，不要编造
            2. 语气温和、专业
            3. 如果知识库没有相关内容，诚实告知
            4. 建议寻求专业帮助
            
            请回答：
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public SimpleRAGService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    public String retrieve(String query, int topK) {
        log.info("SimpleRAG检索: query='{}', topK={}", query, topK);
        List<Document> docs = vectorStore.similaritySearch(query, topK);
        return docs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
    }

    public String generate(String query, String context) {
        String promptText = String.format(RAG_PROMPT, 
                context.isEmpty() ? "无相关资料" : context, 
                query);
        
        Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
        return chatClient.call(prompt)
                .getResult()
                .getOutput()
                .getContent();
    }

    public String query(String userQuery) {
        log.info("SimpleRAG查询: {}", userQuery);
        
        String context = retrieve(userQuery, 3);
        String answer = generate(userQuery, context);
        
        log.info("SimpleRAG完成: context长度={}, answer长度={}", 
                context.length(), answer.length());
        
        return answer;
    }

    public Flux<String> queryStream(String userQuery) {
        return Flux.create(emitter -> {
            try {
                String answer = query(userQuery);
                for (char c : answer.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("SimpleRAG流式查询失败", e);
                emitter.error(e);
            }
        });
    }

    public String queryWithContext(String userQuery, EmotionResult emotionResult) {
        log.info("SimpleRAG查询(带情绪): emotion={}", 
                emotionResult != null ? emotionResult.getLabel() : "无");
        
        String context = retrieve(userQuery, 3);
        
        String emotionContext = emotionResult != null 
                ? String.format("\n【用户情绪状态】%s (分数: %.2f)", 
                        emotionResult.getLabel(), emotionResult.getScore())
                : "";
        
        String enhancedQuery = userQuery + emotionContext;
        String answer = generate(enhancedQuery, context);
        
        return answer;
    }

    public void addDocument(Document doc) {
        vectorStore.addDocument(doc);
    }
}
