package com.calmara.agent;

import com.calmara.agent.intent.IntentClassifier;
import com.calmara.agent.rag.EnhancedAgenticRAGService;
import com.calmara.agent.AgentContext;
import com.calmara.agent.AgentResponse;
import com.calmara.agent.RiskAssessmentAgent;
import com.calmara.common.session.RedisSessionManager;
import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.IntentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EnhancedMultiAgentCoordinator {

    private static final int MAX_AGENT_ITERATIONS = 5;
    private static final long AGENT_TIMEOUT_MS = 30000;
    
    private final ChatClient chatClient;
    private final IntentClassifier intentClassifier;
    private final EnhancedAgenticRAGService ragService;
    private final RiskAssessmentAgent riskAgent;
    private final RedisSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    private final ExecutorService agentExecutor = Executors.newFixedThreadPool(10);
    private final Map<String, AgentExecutionContext> activeContexts = new ConcurrentHashMap<>();

    public EnhancedMultiAgentCoordinator(ChatClient chatClient,
                                         IntentClassifier intentClassifier,
                                         EnhancedAgenticRAGService ragService,
                                         RiskAssessmentAgent riskAgent,
                                         RedisSessionManager sessionManager,
                                         ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.intentClassifier = intentClassifier;
        this.ragService = ragService;
        this.riskAgent = riskAgent;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    public Flux<String> coordinate(String input, EmotionResult emotion, 
                                   String sessionId, String userId, IntentType intent) {
        return Flux.create(emitter -> {
            AgentExecutionContext context = AgentExecutionContext.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .input(input)
                    .emotion(emotion)
                    .intent(intent)
                    .startTime(LocalDateTime.now())
                    .build();
            
            activeContexts.put(sessionId, context);
            
            try {
                log.info("[MultiAgent:{}] 开始协调执行, intent={}", sessionId, intent);
                
                AgentPlan plan = createExecutionPlan(context);
                context.setPlan(plan);
                
                List<AgentTask> tasks = plan.getTasks();
                Map<String, AgentResult> results = new ConcurrentHashMap<>();
                
                for (AgentTask task : tasks) {
                    if (emitter.isCancelled()) {
                        log.info("[MultiAgent:{}] 流已取消", sessionId);
                        break;
                    }
                    
                    AgentResult result = executeAgentTask(task, context, results);
                    results.put(task.getAgentName(), result);
                    
                    context.addExecutionLog(task.getAgentName(), result);
                    
                    if (!result.isSuccess() && task.isRequired()) {
                        log.warn("[MultiAgent:{}] 必要任务失败: {}", sessionId, task.getAgentName());
                        break;
                    }
                    
                    if (result.shouldStop()) {
                        log.info("[MultiAgent:{}] Agent请求停止执行", sessionId);
                        break;
                    }
                }
                
                String finalResponse = synthesizeResults(context, results);
                
                for (char c : finalResponse.toCharArray()) {
                    if (!emitter.isCancelled()) {
                        emitter.next(String.valueOf(c));
                    }
                }
                
                sessionManager.addChatMessage(sessionId, 
                        RedisSessionManager.ChatMessage.builder()
                                .role("user")
                                .content(input)
                                .intent(intent != null ? intent.name() : null)
                                .emotion(emotion != null ? emotion.getLabel() : null)
                                .emotionScore(emotion != null ? emotion.getScore() : null)
                                .build());
                
                sessionManager.addChatMessage(sessionId, 
                        RedisSessionManager.ChatMessage.builder()
                                .role("assistant")
                                .content(finalResponse)
                                .build());
                
                emitter.complete();
                log.info("[MultiAgent:{}] 协调执行完成", sessionId);
                
            } catch (Exception e) {
                log.error("[MultiAgent:{}] 协调执行失败", sessionId, e);
                String fallback = generateFallbackResponse(input, emotion, intent);
                for (char c : fallback.toCharArray()) {
                    emitter.next(String.valueOf(c));
                }
                emitter.complete();
            } finally {
                activeContexts.remove(sessionId);
            }
        });
    }

    private AgentPlan createExecutionPlan(AgentExecutionContext context) {
        IntentType intent = context.getIntent();
        EmotionResult emotion = context.getEmotion();
        
        List<AgentTask> tasks = new ArrayList<>();
        
        tasks.add(AgentTask.builder()
                .agentName("QueryAnalysisAgent")
                .taskType("analysis")
                .required(true)
                .priority(1)
                .build());
        
        switch (intent) {
            case RISK:
                tasks.add(AgentTask.builder()
                        .agentName("RiskAssessmentAgent")
                        .taskType("assessment")
                        .required(true)
                        .priority(2)
                        .build());
                
                tasks.add(AgentTask.builder()
                        .agentName("CrisisInterventionAgent")
                        .taskType("intervention")
                        .required(true)
                        .priority(3)
                        .build());
                break;
                
            case CONSULT:
                tasks.add(AgentTask.builder()
                        .agentName("RAGRetrievalAgent")
                        .taskType("retrieval")
                        .required(true)
                        .priority(2)
                        .build());
                
                tasks.add(AgentTask.builder()
                        .agentName("CounselingAgent")
                        .taskType("counseling")
                        .required(true)
                        .priority(3)
                        .build());
                break;
                
            case CHAT:
            default:
                tasks.add(AgentTask.builder()
                        .agentName("ChatAgent")
                        .taskType("chat")
                        .required(true)
                        .priority(2)
                        .build());
                break;
        }
        
        if (emotion != null && emotion.getScore() >= 2.0) {
            tasks.add(AgentTask.builder()
                    .agentName("EmotionTrackingAgent")
                    .taskType("tracking")
                    .required(false)
                    .priority(4)
                    .build());
        }
        
        tasks.add(AgentTask.builder()
                .agentName("ResponseSynthesisAgent")
                .taskType("synthesis")
                .required(true)
                .priority(5)
                .build());
        
        return AgentPlan.builder()
                .tasks(tasks)
                .strategy(intent == IntentType.RISK ? "sequential" : "parallel")
                .build();
    }

    private AgentResult executeAgentTask(AgentTask task, 
                                         AgentExecutionContext context,
                                         Map<String, AgentResult> previousResults) {
        String agentName = task.getAgentName();
        log.debug("[MultiAgent:{}] 执行Agent: {}", context.getSessionId(), agentName);
        
        long startTime = System.currentTimeMillis();
        
        try {
            CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(() -> {
                switch (agentName) {
                    case "QueryAnalysisAgent":
                        return executeQueryAnalysis(context, previousResults);
                    case "RiskAssessmentAgent":
                        return executeRiskAssessment(context, previousResults);
                    case "CrisisInterventionAgent":
                        return executeCrisisIntervention(context, previousResults);
                    case "RAGRetrievalAgent":
                        return executeRAGRetrieval(context, previousResults);
                    case "CounselingAgent":
                        return executeCounseling(context, previousResults);
                    case "ChatAgent":
                        return executeChat(context, previousResults);
                    case "EmotionTrackingAgent":
                        return executeEmotionTracking(context, previousResults);
                    case "ResponseSynthesisAgent":
                        return executeResponseSynthesis(context, previousResults);
                    default:
                        return AgentResult.failure("未知Agent: " + agentName);
                }
            }, agentExecutor);
            
            AgentResult result = future.get(AGENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            
            return result;
            
        } catch (TimeoutException e) {
            log.error("[MultiAgent:{}] Agent超时: {}", context.getSessionId(), agentName);
            return AgentResult.failure("Agent执行超时: " + agentName);
        } catch (Exception e) {
            log.error("[MultiAgent:{}] Agent执行失败: {}", context.getSessionId(), agentName, e);
            return AgentResult.failure("Agent执行失败: " + e.getMessage());
        }
    }

    private AgentResult executeQueryAnalysis(AgentExecutionContext context,
                                             Map<String, AgentResult> previousResults) {
        String prompt = String.format("""
                分析以下用户输入，提取关键信息：
                
                用户输入：%s
                用户情绪：%s (分数: %.2f)
                意图类型：%s
                
                请输出JSON格式：
                {
                    "main_topic": "主要话题",
                    "keywords": ["关键词1", "关键词2"],
                    "sentiment": "positive/negative/neutral",
                    "urgency": 1-5,
                    "context_needed": true/false,
                    "suggested_approach": "建议的处理方式"
                }
                """,
                context.getInput(),
                context.getEmotion().getLabel(),
                context.getEmotion().getScore(),
                context.getIntent().name()
        );
        
        try {
            Prompt p = new Prompt(List.of(new UserMessage(prompt)));
            String response = chatClient.call(p).getResult().getOutput().getContent();
            
            Map<String, Object> data = new HashMap<>();
            data.put("analysis", response);
            data.put("urgency", extractUrgency(response));
            
            return AgentResult.success(data);
        } catch (Exception e) {
            return AgentResult.failure("查询分析失败: " + e.getMessage());
        }
    }

    private AgentResult executeRiskAssessment(AgentExecutionContext context,
                                              Map<String, AgentResult> previousResults) {
        try {
            AgentContext agentContext = new AgentContext();
            agentContext.setEmotionResult(context.getEmotion());
            agentContext.setSessionId(context.getSessionId());
            
            AgentResponse response = riskAgent.execute(context.getInput(), agentContext);
            
            double riskScore = 0.0;
            String riskLevel = "LOW";
            
            if (response.getContent() != null) {
                String content = response.getContent();
                if (content.contains("\"risk_score\"")) {
                    try {
                        int start = content.indexOf("\"risk_score\"");
                        int colonPos = content.indexOf(":", start);
                        int end = content.indexOf(",", colonPos);
                        if (end == -1) end = content.indexOf("}", colonPos);
                        String scoreStr = content.substring(colonPos + 1, end).trim();
                        riskScore = Double.parseDouble(scoreStr);
                    } catch (Exception ignored) {}
                }
                
                if (content.contains("HIGH")) {
                    riskLevel = "HIGH";
                } else if (content.contains("MEDIUM")) {
                    riskLevel = "MEDIUM";
                }
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("riskScore", riskScore);
            data.put("riskLevel", riskLevel);
            data.put("needsIntervention", riskScore >= 3.0);
            data.put("assessmentContent", response.getContent());
            
            return AgentResult.success(data);
        } catch (Exception e) {
            return AgentResult.failure("风险评估失败: " + e.getMessage());
        }
    }

    private AgentResult executeCrisisIntervention(AgentExecutionContext context,
                                                  Map<String, AgentResult> previousResults) {
        AgentResult riskResult = previousResults.get("RiskAssessmentAgent");
        double riskScore = 3.0;
        
        if (riskResult != null && riskResult.getData() != null) {
            Object score = riskResult.getData().get("riskScore");
            if (score instanceof Number) {
                riskScore = ((Number) score).doubleValue();
            }
        }
        
        String interventionPrompt = String.format("""
                作为危机干预专家，针对以下情况提供干预建议：
                
                用户输入：%s
                情绪状态：%s (分数: %.2f)
                风险评分：%.2f
                
                请提供：
                1. 即时安全评估
                2. 情绪稳定建议
                3. 资源推荐
                4. 后续跟进计划
                
                注意：语气要温和、专业，避免可能引起负面反应的表述。
                """,
                context.getInput(),
                context.getEmotion().getLabel(),
                context.getEmotion().getScore(),
                riskScore
        );
        
        try {
            Prompt p = new Prompt(List.of(new UserMessage(interventionPrompt)));
            String intervention = chatClient.call(p).getResult().getOutput().getContent();
            
            Map<String, Object> data = new HashMap<>();
            data.put("intervention", intervention);
            data.put("shouldStop", true);
            
            return AgentResult.success(data);
        } catch (Exception e) {
            return AgentResult.failure("危机干预失败: " + e.getMessage());
        }
    }

    private AgentResult executeRAGRetrieval(AgentExecutionContext context,
                                            Map<String, AgentResult> previousResults) {
        AgentResult analysisResult = previousResults.get("QueryAnalysisAgent");
        String query = context.getInput();
        
        if (analysisResult != null && analysisResult.getData() != null) {
            Object keywords = analysisResult.getData().get("keywords");
            if (keywords instanceof List && !((List<?>) keywords).isEmpty()) {
                query = ((List<?>) keywords).stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(" "));
            }
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("query", query);
        data.put("retrieved", true);
        
        return AgentResult.success(data);
    }

    private AgentResult executeCounseling(AgentExecutionContext context,
                                          Map<String, AgentResult> previousResults) {
        String ragContext = "";
        AgentResult ragResult = previousResults.get("RAGRetrievalAgent");
        if (ragResult != null && ragResult.getData() != null) {
            ragContext = ragResult.getData().toString();
        }
        
        String counselingPrompt = String.format("""
                作为专业心理咨询师，基于以下信息提供建议：
                
                用户问题：%s
                用户情绪：%s
                参考知识：%s
                
                请提供专业、温和、有同理心的回复。
                """,
                context.getInput(),
                context.getEmotion().getLabel(),
                ragContext.isEmpty() ? "无特定参考" : ragContext
        );
        
        try {
            Prompt p = new Prompt(List.of(new UserMessage(counselingPrompt)));
            String counseling = chatClient.call(p).getResult().getOutput().getContent();
            
            Map<String, Object> data = new HashMap<>();
            data.put("counseling", counseling);
            
            return AgentResult.success(data);
        } catch (Exception e) {
            return AgentResult.failure("心理咨询失败: " + e.getMessage());
        }
    }

    private AgentResult executeChat(AgentExecutionContext context,
                                    Map<String, AgentResult> previousResults) {
        String chatPrompt = String.format("""
                作为友好的聊天助手，与用户进行自然对话：
                
                用户输入：%s
                用户情绪：%s
                
                请提供轻松、友好的回复。
                """,
                context.getInput(),
                context.getEmotion().getLabel()
        );
        
        try {
            Prompt p = new Prompt(List.of(new UserMessage(chatPrompt)));
            String response = chatClient.call(p).getResult().getOutput().getContent();
            
            Map<String, Object> data = new HashMap<>();
            data.put("response", response);
            
            return AgentResult.success(data);
        } catch (Exception e) {
            return AgentResult.failure("聊天失败: " + e.getMessage());
        }
    }

    private AgentResult executeEmotionTracking(AgentExecutionContext context,
                                               Map<String, AgentResult> previousResults) {
        try {
            RedisSessionManager.EmotionTrend trend = sessionManager.analyzeEmotionTrend(
                    context.getSessionId(), 10);
            
            sessionManager.addEmotionHistory(
                    context.getSessionId(),
                    RedisSessionManager.EmotionHistoryEntry.of(
                            context.getEmotion().getLabel(),
                            context.getEmotion().getScore(),
                            context.getEmotion().getConfidence(),
                            context.getEmotion().getSource(),
                            context.getEmotion().getFeatures() != null 
                                    ? context.getEmotion().getFeatures().entrySet().stream()
                                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() instanceof Number ? ((Number) e.getValue()).doubleValue() : 0.0))
                                    : null
                    )
            );
            
            Map<String, Object> data = new HashMap<>();
            data.put("trend", trend.getTrend());
            data.put("averageScore", trend.getAverageScore());
            data.put("changeRate", trend.getChangeRate());
            
            return AgentResult.success(data);
        } catch (Exception e) {
            return AgentResult.failure("情绪追踪失败: " + e.getMessage());
        }
    }

    private AgentResult executeResponseSynthesis(AgentExecutionContext context,
                                                 Map<String, AgentResult> previousResults) {
        StringBuilder synthesis = new StringBuilder();
        
        AgentResult crisisResult = previousResults.get("CrisisInterventionAgent");
        if (crisisResult != null && crisisResult.isSuccess()) {
            Object intervention = crisisResult.getData().get("intervention");
            if (intervention != null) {
                return AgentResult.success(Map.of("response", intervention.toString()));
            }
        }
        
        AgentResult counselingResult = previousResults.get("CounselingAgent");
        if (counselingResult != null && counselingResult.isSuccess()) {
            Object counseling = counselingResult.getData().get("counseling");
            if (counseling != null) {
                return AgentResult.success(Map.of("response", counseling.toString()));
            }
        }
        
        AgentResult chatResult = previousResults.get("ChatAgent");
        if (chatResult != null && chatResult.isSuccess()) {
            Object response = chatResult.getData().get("response");
            if (response != null) {
                return AgentResult.success(Map.of("response", response.toString()));
            }
        }
        
        return AgentResult.success(Map.of("response", "感谢你的分享，我会尽力帮助你。"));
    }

    private String synthesizeResults(AgentExecutionContext context,
                                     Map<String, AgentResult> results) {
        AgentResult synthesisResult = results.get("ResponseSynthesisAgent");
        
        if (synthesisResult != null && synthesisResult.isSuccess()) {
            Object response = synthesisResult.getData().get("response");
            if (response != null) {
                return response.toString();
            }
        }
        
        return generateFallbackResponse(
                context.getInput(), 
                context.getEmotion(), 
                context.getIntent()
        );
    }

    private String generateFallbackResponse(String input, EmotionResult emotion, IntentType intent) {
        if (emotion != null && emotion.getScore() >= 3.0) {
            return "我听到了你的感受，感谢你愿意和我分享。如果你正在经历困难的时刻，" +
                   "我建议你尽快和信任的人或专业心理咨询师聊聊。你的感受很重要。";
        }
        
        return "感谢你的分享。我会尽力帮助你。能告诉我更多吗？";
    }

    private int extractUrgency(String analysis) {
        if (analysis == null) return 3;
        if (analysis.contains("\"urgency\": 5") || analysis.contains("\"urgency\":5")) return 5;
        if (analysis.contains("\"urgency\": 4") || analysis.contains("\"urgency\":4")) return 4;
        if (analysis.contains("\"urgency\": 2") || analysis.contains("\"urgency\":2")) return 2;
        if (analysis.contains("\"urgency\": 1") || analysis.contains("\"urgency\":1")) return 1;
        return 3;
    }

    public AgentExecutionContext getContext(String sessionId) {
        return activeContexts.get(sessionId);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentExecutionContext {
        private String sessionId;
        private String userId;
        private String input;
        private EmotionResult emotion;
        private IntentType intent;
        private AgentPlan plan;
        private LocalDateTime startTime;
        @Builder.Default
        private List<ExecutionLog> executionLogs = new ArrayList<>();
        
        public void addExecutionLog(String agentName, AgentResult result) {
            executionLogs.add(new ExecutionLog(agentName, result, LocalDateTime.now()));
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentPlan {
        @Builder.Default
        private List<AgentTask> tasks = new ArrayList<>();
        private String strategy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentTask {
        private String agentName;
        private String taskType;
        private boolean required;
        private int priority;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentResult {
        private boolean success;
        private String message;
        private Map<String, Object> data;
        private long executionTimeMs;
        private boolean shouldStop;
        
        public boolean shouldStop() {
            return shouldStop;
        }
        
        public static AgentResult success(Map<String, Object> data) {
            AgentResult result = new AgentResult();
            result.success = true;
            result.data = data;
            result.shouldStop = false;
            return result;
        }
        
        public static AgentResult failure(String message) {
            AgentResult result = new AgentResult();
            result.success = false;
            result.message = message;
            result.shouldStop = false;
            return result;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionLog {
        private String agentName;
        private AgentResult result;
        private LocalDateTime timestamp;
    }
}
