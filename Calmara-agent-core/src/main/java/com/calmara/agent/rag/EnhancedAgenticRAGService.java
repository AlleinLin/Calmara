package com.calmara.agent.rag;

import com.calmara.agent.rag.Document;
import com.calmara.common.JsonUtils;
import com.calmara.model.dto.EmotionResult;
import com.calmara.model.dto.RAGContext;
import com.calmara.model.dto.RetrieveDecision;
import com.calmara.model.dto.ValidationResult;
import com.calmara.model.enums.IntentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class EnhancedAgenticRAGService {

    private static final int MAX_ITERATIONS = 5;
    private static final int MAX_RETRIEVALS = 3;
    
    private static final String PLANNER_PROMPT = """
            你是一个专业的任务规划Agent。分析用户问题，制定检索和回答策略。
            
            用户问题：%s
            用户情绪：%s
            意图类型：%s
            
            请分析问题复杂度，并制定执行计划。输出JSON格式：
            {
                "complexity": "simple|medium|complex",
                "sub_questions": ["子问题1", "子问题2"],
                "retrieval_strategy": "single|multi|iterative",
                "reasoning_steps": ["步骤1", "步骤2"],
                "estimated_iterations": 1-3
            }
            """;
    
    private static final String QUERY_DECOMPOSITION_PROMPT = """
            将以下复杂问题分解为多个简单的子问题，每个子问题可以独立检索。
            
            原问题：%s
            
            输出JSON数组格式：
            ["子问题1", "子问题2", ...]
            """;
    
    private static final String RETRIEVAL_DECISION_PROMPT = """
            你是一个检索决策Agent。判断是否需要检索知识库来回答问题。
            
            问题：%s
            已检索次数：%d
            已有上下文：%s
            
            判断标准：
            1. 问题是否需要专业知识？
            2. 已有上下文是否足够回答？
            3. 是否需要更多细节？
            
            输出JSON格式：
            {
                "need_retrieval": true/false,
                "query": "检索关键词（如果需要检索）",
                "reason": "决策理由",
                "confidence": 0.0-1.0
            }
            """;
    
    private static final String ANSWER_GENERATION_PROMPT = """
            你是一个专业的校园心理咨询助手。基于以下信息生成回答。
            
            【用户问题】
            %s
            
            【用户情绪】
            %s
            
            【检索到的知识】
            %s
            
            【对话历史】
            %s
            
            【回答要求】
            1. 语气温和、有同理心
            2. 基于检索内容回答，不编造
            3. 如果是高风险情况，提供危机干预建议
            4. 保持专业性和安全性
            
            请生成回答：
            """;
    
    private static final String ANSWER_VALIDATION_PROMPT = """
            你是一个回答质量评估Agent。评估生成的回答是否合格。
            
            【原问题】
            %s
            
            【生成的回答】
            %s
            
            【参考资料】
            %s
            
            【评估标准】
            1. 相关性：回答是否与问题相关
            2. 准确性：回答是否基于参考资料，无幻觉
            3. 完整性：回答是否完整解答了问题
            4. 安全性：回答是否安全，无危险建议
            5. 专业性：回答语气是否温和专业
            
            输出JSON格式：
            {
                "is_valid": true/false,
                "scores": {
                    "relevance": 0.0-1.0,
                    "accuracy": 0.0-1.0,
                    "completeness": 0.0-1.0,
                    "safety": 0.0-1.0,
                    "professionalism": 0.0-1.0
                },
                "issues": ["问题1", "问题2"],
                "suggestions": "改进建议",
                "rewrite_query": "如果需要重新检索，提供新的查询词"
            }
            """;
    
    private static final String SELF_REFLECTION_PROMPT = """
            你是一个自我反思Agent。分析之前的检索和回答过程，提出改进建议。
            
            【原始问题】
            %s
            
            【执行历史】
            %s
            
            【当前回答】
            %s
            
            请分析：
            1. 检索策略是否有效？
            2. 是否遗漏了重要信息？
            3. 如何改进？
            
            输出JSON格式：
            {
                "analysis": "分析结果",
                "missing_info": ["缺失信息1", "缺失信息2"],
                "improvement_actions": ["改进动作1", "改进动作2"],
                "should_continue": true/false
            }
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    
    private final Map<String, AgentState> sessionStates = new ConcurrentHashMap<>();

    public EnhancedAgenticRAGService(ChatClient chatClient, 
                                     VectorStore vectorStore,
                                     ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    public Flux<String> executeRAG(String query, RAGContext context) {
        return Flux.create(emitter -> {
            String sessionId = context.getSessionId();
            AgentState state = new AgentState(sessionId, query, context);
            sessionStates.put(sessionId, state);
            
            try {
                PlanResult plan = planExecution(query, context);
                state.setPlan(plan);
                log.info("[{}] 执行计划: complexity={}, sub_questions={}", 
                        sessionId, plan.getComplexity(), plan.getSubQuestions().size());
                
                List<String> allContexts = new ArrayList<>();
                
                if ("complex".equals(plan.getComplexity()) && !plan.getSubQuestions().isEmpty()) {
                    for (String subQuestion : plan.getSubQuestions()) {
                        if (state.getIterationCount() >= MAX_ITERATIONS) break;
                        
                        String subContext = processSubQuestion(subQuestion, state);
                        allContexts.add(subContext);
                        state.incrementIteration();
                    }
                } else {
                    String context2 = processWithIterativeRetrieval(query, state);
                    allContexts.add(context2);
                }
                
                String combinedContext = String.join("\n\n---\n\n", allContexts);
                
                String answer = generateAnswer(query, combinedContext, context);
                
                ValidationResult validation = validateAnswer(answer, query, combinedContext);
                state.setLastValidation(validation);
                
                if (!validation.isValid() && state.getIterationCount() < MAX_ITERATIONS) {
                    log.info("[{}] 回答验证未通过，执行自我修正", sessionId);
                    answer = selfCorrect(query, answer, combinedContext, state);
                }
                
                for (char c : answer.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                
                state.setFinalAnswer(answer);
                emitter.complete();
                
            } catch (Exception e) {
                log.error("[{}] RAG执行失败", sessionId, e);
                String fallback = generateFallbackAnswer(query, context);
                for (char c : fallback.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();
            } finally {
                sessionStates.remove(sessionId);
            }
        });
    }

    private PlanResult planExecution(String query, RAGContext context) {
        try {
            String promptText = String.format(PLANNER_PROMPT, 
                    query,
                    context.getEmotionResult() != null ? context.getEmotionResult().getLabel() : "未知",
                    context.getIntentType() != null ? context.getIntentType().name() : "CONSULT");
            
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
            String response = chatClient.call(prompt).getResult().getOutput().getContent();
            
            String json = extractJson(response);
            return objectMapper.readValue(json, PlanResult.class);
            
        } catch (Exception e) {
            log.warn("规划解析失败，使用默认计划", e);
            return new PlanResult();
        }
    }

    private String processSubQuestion(String subQuestion, AgentState state) {
        log.debug("[{}] 处理子问题: {}", state.getSessionId(), subQuestion);
        
        RetrieveDecision decision = decideRetrieval(subQuestion, state);
        
        if (decision.shouldRetrieve()) {
            List<Document> docs = vectorStore.similaritySearch(decision.getQuery(), 5);
            state.addRetrievedDocs(docs);
            return docs.stream()
                    .map(Document::getContent)
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElse("");
        }
        
        return "";
    }

    private String processWithIterativeRetrieval(String query, AgentState state) {
        StringBuilder contextBuilder = new StringBuilder();
        int retrievalCount = 0;
        
        while (retrievalCount < MAX_RETRIEVALS && state.getIterationCount() < MAX_ITERATIONS) {
            RetrieveDecision decision = decideRetrieval(query, state);
            
            if (!decision.shouldRetrieve()) {
                log.debug("[{}] 不再需要检索", state.getSessionId());
                break;
            }
            
            List<Document> docs = vectorStore.similaritySearch(decision.getQuery(), 5);
            
            if (docs.isEmpty()) {
                log.debug("[{}] 未检索到相关文档", state.getSessionId());
                break;
            }
            
            state.addRetrievedDocs(docs);
            
            for (Document doc : docs) {
                contextBuilder.append(doc.getContent()).append("\n\n");
            }
            
            retrievalCount++;
            state.incrementIteration();
            
            log.debug("[{}] 完成第{}次检索，获得{}个文档", 
                    state.getSessionId(), retrievalCount, docs.size());
        }
        
        return contextBuilder.toString();
    }

    private RetrieveDecision decideRetrieval(String query, AgentState state) {
        try {
            String existingContext = state.getRetrievedDocs().stream()
                    .map(Document::getContent)
                    .reduce((a, b) -> a.substring(0, Math.min(500, a.length())) + "...")
                    .orElse("无");
            
            String promptText = String.format(RETRIEVAL_DECISION_PROMPT,
                    query, state.getRetrievalCount(), existingContext);
            
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
            String response = chatClient.call(prompt).getResult().getOutput().getContent();
            
            String json = extractJson(response);
            RetrievalDecisionResult result = objectMapper.readValue(json, RetrievalDecisionResult.class);
            
            return RetrieveDecision.builder()
                    .action(result.isNeedRetrieval() 
                            ? RetrieveDecision.RetrieveAction.RETRIEVE 
                            : RetrieveDecision.RetrieveAction.ANSWER)
                    .query(result.getQuery())
                    .thought(result.getReason())
                    .build();
            
        } catch (Exception e) {
            log.warn("检索决策失败，默认执行检索", e);
            return RetrieveDecision.builder()
                    .action(RetrieveDecision.RetrieveAction.RETRIEVE)
                    .query(query)
                    .thought("决策失败，默认检索")
                    .build();
        }
    }

    private String generateAnswer(String query, String context, RAGContext ragContext) {
        String history = formatHistory(ragContext.getHistory());
        
        String promptText = String.format(ANSWER_GENERATION_PROMPT,
                query,
                ragContext.getEmotionResult() != null ? ragContext.getEmotionResult().getLabel() : "未知",
                context.isEmpty() ? "无相关资料" : context,
                history);
        
        Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
        return chatClient.call(prompt).getResult().getOutput().getContent();
    }

    private ValidationResult validateAnswer(String answer, String query, String context) {
        try {
            String promptText = String.format(ANSWER_VALIDATION_PROMPT, query, answer, context);
            
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
            String response = chatClient.call(prompt).getResult().getOutput().getContent();
            
            String json = extractJson(response);
            ValidationResponse validation = objectMapper.readValue(json, ValidationResponse.class);
            
            double avgScore = validation.getScores().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.5);
            
            boolean isValid = validation.isIsValid() && avgScore >= 0.6;
            
            if (!isValid) {
                log.warn("回答验证未通过: issues={}, avgScore={}", 
                        validation.getIssues(), avgScore);
            }
            
            return ValidationResult.builder()
                    .valid(isValid)
                    .reason(String.join("; ", validation.getIssues()))
                    .build();
            
        } catch (Exception e) {
            log.warn("验证解析失败，默认通过", e);
            return ValidationResult.valid();
        }
    }

    private String selfCorrect(String query, String previousAnswer, String context, AgentState state) {
        try {
            String historyJson = objectMapper.writeValueAsString(state.getExecutionHistory());
            
            String promptText = String.format(SELF_REFLECTION_PROMPT, query, historyJson, previousAnswer);
            
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));
            String response = chatClient.call(prompt).getResult().getOutput().getContent();
            
            String json = extractJson(response);
            ReflectionResult reflection = objectMapper.readValue(json, ReflectionResult.class);
            
            if (reflection.isShouldContinue() && !reflection.getMissingInfo().isEmpty()) {
                for (String missing : reflection.getMissingInfo()) {
                    if (state.getRetrievalCount() < MAX_RETRIEVALS) {
                        List<Document> docs = vectorStore.similaritySearch(missing, 3);
                        state.addRetrievedDocs(docs);
                        context += "\n\n" + docs.stream()
                                .map(Document::getContent)
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse("");
                    }
                }
            }
            
            return generateAnswer(query, context, state.getContext());
            
        } catch (Exception e) {
            log.warn("自我修正失败，返回原回答", e);
            return previousAnswer;
        }
    }

    private String generateFallbackAnswer(String query, RAGContext context) {
        String emotion = context.getEmotionResult() != null ? context.getEmotionResult().getLabel() : "未知";
        
        if ("高风险".equals(emotion) || 
            (context.getEmotionResult() != null && context.getEmotionResult().getScore() >= 3.0)) {
            return "我听到了你内心的痛苦，感谢你愿意和我分享。如果你正在经历困难的时刻，" +
                   "我强烈建议你尽快和信任的人或专业心理咨询师聊聊。你的感受很重要，你值得被帮助。";
        }
        
        return "感谢你分享你的感受。每个人的情绪都会有起伏，这是完全正常的。" +
               "如果你愿意，可以告诉我更多，我会尽力帮助你。";
    }

    private String formatHistory(List<RAGContext.ChatMessageDTO> history) {
        if (history == null || history.isEmpty()) {
            return "无历史对话";
        }
        
        StringBuilder sb = new StringBuilder();
        for (RAGContext.ChatMessageDTO msg : history) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    public AgentState getState(String sessionId) {
        return sessionStates.get(sessionId);
    }

    @Data
    public static class AgentState {
        private final String sessionId;
        private final String originalQuery;
        private final RAGContext context;
        private PlanResult plan;
        private List<Document> retrievedDocs = new ArrayList<>();
        private List<Map<String, Object>> executionHistory = new ArrayList<>();
        private ValidationResult lastValidation;
        private String finalAnswer;
        @lombok.Getter(lombok.AccessLevel.NONE)
        private AtomicInteger iterationCount = new AtomicInteger(0);
        @lombok.Getter(lombok.AccessLevel.NONE)
        private AtomicInteger retrievalCount = new AtomicInteger(0);
        
        public AgentState(String sessionId, String originalQuery, RAGContext context) {
            this.sessionId = sessionId;
            this.originalQuery = originalQuery;
            this.context = context;
        }
        
        public void addRetrievedDocs(List<Document> docs) {
            this.retrievedDocs.addAll(docs);
            this.retrievalCount.incrementAndGet();
            Map<String, Object> entry = new HashMap<>();
            entry.put("action", "retrieval");
            entry.put("doc_count", docs.size());
            entry.put("timestamp", System.currentTimeMillis());
            executionHistory.add(entry);
        }
        
        public void incrementIteration() {
            iterationCount.incrementAndGet();
        }
        
        public int getIterationCount() {
            return iterationCount.get();
        }
        
        public int getRetrievalCount() {
            return retrievalCount.get();
        }
    }

    @Data
    public static class PlanResult {
        private String complexity = "simple";
        private List<String> subQuestions = new ArrayList<>();
        private String retrievalStrategy = "single";
        private List<String> reasoningSteps = new ArrayList<>();
        private int estimatedIterations = 1;
    }

    @Data
    public static class RetrievalDecisionResult {
        private boolean needRetrieval = true;
        private String query = "";
        private String reason = "";
        private double confidence = 0.5;
    }

    @Data
    public static class ValidationResponse {
        private boolean isIsValid = true;
        private Map<String, Double> scores = new HashMap<>();
        private List<String> issues = new ArrayList<>();
        private String suggestions = "";
        private String rewriteQuery = "";
    }

    @Data
    public static class ReflectionResult {
        private String analysis = "";
        private List<String> missingInfo = new ArrayList<>();
        private List<String> improvementActions = new ArrayList<>();
        private boolean shouldContinue = false;
    }
}
