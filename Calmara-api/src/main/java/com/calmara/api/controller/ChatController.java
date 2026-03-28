package com.calmara.api.controller;

import com.calmara.agent.MultiAgentCoordinator;
import com.calmara.agent.intent.IntentClassifier;
import com.calmara.agent.rag.AgenticRAGService;
import com.calmara.common.IdGenerator;
import com.calmara.common.JsonUtils;
import com.calmara.mcp.MCPCoordinator;
import com.calmara.model.dto.*;
import com.calmara.model.enums.IntentType;
import com.calmara.multimodal.MultiModalEmotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final MultiModalEmotionService emotionService;
    private final IntentClassifier intentClassifier;
    private final AgenticRAGService ragService;
    private final MultiAgentCoordinator multiAgentCoordinator;
    private final MCPCoordinator mcpCoordinator;

    @Value("${calmara.alert.recipients:counselor@calmara.edu}")
    private String alertRecipients;

    @Value("${calmara.chat.use-multi-agent:true}")
    private boolean useMultiAgent;

    public ChatController(MultiModalEmotionService emotionService,
                         IntentClassifier intentClassifier,
                         AgenticRAGService ragService,
                         MultiAgentCoordinator multiAgentCoordinator,
                         MCPCoordinator mcpCoordinator) {
        this.emotionService = emotionService;
        this.intentClassifier = intentClassifier;
        this.ragService = ragService;
        this.multiAgentCoordinator = multiAgentCoordinator;
        this.mcpCoordinator = mcpCoordinator;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile audio,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile video,
            @RequestParam(required = false) String sessionId,
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        String currentSessionId = sessionId != null ? sessionId : IdGenerator.generateSessionId();

        log.info("收到聊天请求: userId={}, sessionId={}, text='{}', useMultiAgent={}",
                userId, currentSessionId, text != null ? text.substring(0, Math.min(50, text.length())) : "null", useMultiAgent);

        return Flux.create(emitter -> {
            try {
                List<EmotionResult> emotionResults = new ArrayList<>();
                final String[] inputTextRef = {text};

                List<CompletableFuture<EmotionResult>> futures = new ArrayList<>();

                if (text != null && !text.isBlank()) {
                    futures.add(CompletableFuture.supplyAsync(() ->
                            emotionService.analyzeText(text)));
                }

                if (audio != null && !audio.isEmpty()) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        String transcribed = emotionService.transcribeAudio(audio);
                        inputTextRef[0] = transcribed;
                        return emotionService.analyzeText(transcribed);
                    }));
                }

                if (image != null && !image.isEmpty()) {
                    futures.add(CompletableFuture.supplyAsync(() ->
                            emotionService.analyzeImage(image)));
                }

                if (video != null && !video.isEmpty()) {
                    futures.add(CompletableFuture.supplyAsync(() ->
                            emotionService.analyzeVideo(video)));
                }

                for (CompletableFuture<EmotionResult> future : futures) {
                    try {
                        EmotionResult result = future.join();
                        if (result != null) {
                            emotionResults.add(result);
                        }
                    } catch (Exception e) {
                        log.error("情绪分析异常", e);
                    }
                }

                EmotionResult fusedEmotion = emotionService.fuseEmotions(emotionResults);

                emitter.next(ServerSentEvent.<String>builder()
                        .event("emotion")
                        .data(toJson(fusedEmotion))
                        .build());

                String inputText = inputTextRef[0] != null ? inputTextRef[0] : text;
                IntentType intent = intentClassifier.classify(inputText, fusedEmotion);
                log.info("用户意图: {}, 风险等级: {}", intent, fusedEmotion.getRiskLevel());

                if (intent == IntentType.CHAT) {
                    handleChatIntent(inputText, fusedEmotion, currentSessionId, emitter);
                } else if (useMultiAgent && (intent == IntentType.CONSULT || intent == IntentType.RISK)) {
                    handleWithMultiAgent(inputText, fusedEmotion, currentSessionId, userId, intent, emitter);
                } else {
                    handleWithRAG(inputText, fusedEmotion, currentSessionId, userId, intent, emitter);
                }

            } catch (Exception e) {
                log.error("聊天处理失败", e);
                emitter.error(e);
            }
        });
    }

    private void handleChatIntent(String inputText, EmotionResult fusedEmotion, 
                                   String sessionId, 
                                   FluxSink<ServerSentEvent<String>> emitter) {
        String reply = ragService.simpleAnswer(inputText, fusedEmotion);
        for (char c : reply.toCharArray()) {
            emitter.next(ServerSentEvent.<String>builder()
                    .event("message")
                    .data(String.valueOf(c))
                    .build());
        }
        emitter.next(ServerSentEvent.<String>builder()
                .event("done")
                .data("{\"sessionId\":\"" + sessionId + "\"}")
                .build());
        emitter.complete();
    }

    private void handleWithMultiAgent(String inputText, EmotionResult fusedEmotion,
                                       String sessionId, Long userId, IntentType intent,
                                       FluxSink<ServerSentEvent<String>> emitter) {
        log.info("使用MultiAgent模式处理: intent={}", intent);
        
        multiAgentCoordinator.process(inputText, fusedEmotion, new HashMap<>())
                .doOnNext(chunk -> emitter.next(ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build()))
                .doOnComplete(() -> {
                    if (intent.shouldRecord()) {
                        executeMCP(userId, sessionId, inputText, fusedEmotion, intent);
                    }
                    emitter.next(ServerSentEvent.<String>builder()
                            .event("done")
                            .data("{\"sessionId\":\"" + sessionId + "\",\"mode\":\"multi-agent\"}")
                            .build());
                    emitter.complete();
                })
                .doOnError(e -> {
                    log.error("MultiAgent处理失败，降级到RAG模式", e);
                    handleWithRAG(inputText, fusedEmotion, sessionId, userId, intent, emitter);
                })
                .subscribe();
    }

    private void handleWithRAG(String inputText, EmotionResult fusedEmotion,
                                String sessionId, Long userId, IntentType intent,
                                FluxSink<ServerSentEvent<String>> emitter) {
        log.info("使用RAG模式处理: intent={}", intent);
        
        RAGContext context = RAGContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .emotionResult(fusedEmotion)
                .intentType(intent)
                .maxRetries(3)
                .currentRetry(0)
                .build();

        ragService.executeRAG(inputText, context)
                .doOnNext(chunk -> emitter.next(ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build()))
                .doOnComplete(() -> {
                    if (intent.shouldRecord()) {
                        executeMCP(userId, sessionId, inputText, fusedEmotion, intent);
                    }
                    emitter.next(ServerSentEvent.<String>builder()
                            .event("done")
                            .data("{\"sessionId\":\"" + sessionId + "\",\"mode\":\"rag\"}")
                            .build());
                    emitter.complete();
                })
                .subscribe();
    }

    private void executeMCP(Long userId, String sessionId, String content,
                            EmotionResult emotion, IntentType intent) {
        try {
            Map<String, Object> excelParams = new HashMap<>();
            excelParams.put("userId", userId != null ? userId.toString() : "anonymous");
            excelParams.put("content", content != null ? content : "");
            excelParams.put("emotion", emotion != null ? emotion.getLabel() : "未知");
            excelParams.put("emotionScore", emotion != null ? emotion.getScore() : 0.0);
            excelParams.put("riskLevel", emotion != null ?
                    (emotion.getRiskLevel() != null ? emotion.getRiskLevel().name() : "LOW") : "LOW");

            MCPCommand excelCommand = MCPCommand.builder()
                    .tool("excel_writer")
                    .params(excelParams)
                    .build();

            mcpCoordinator.execute(JsonUtils.toJson(excelCommand));

            if (intent == IntentType.RISK) {
                List<String> recipients = parseRecipients(alertRecipients);

                Map<String, Object> emailParams = new HashMap<>();
                emailParams.put("userId", userId != null ? userId.toString() : "anonymous");
                emailParams.put("userName", userId != null ? "用户" + userId : "匿名用户");
                emailParams.put("emotion", emotion != null ? emotion.getLabel() : "高风险");
                emailParams.put("emotionScore", emotion != null ? emotion.getScore() : 4.0);
                emailParams.put("content", content != null ? content : "");
                emailParams.put("recipients", recipients);

                MCPCommand emailCommand = MCPCommand.builder()
                        .tool("mail_alert")
                        .params(emailParams)
                        .build();

                mcpCoordinator.execute(JsonUtils.toJson(emailCommand));
            }

        } catch (Exception e) {
            log.error("MCP执行失败", e);
        }
    }

    private List<String> parseRecipients(String recipientsConfig) {
        if (recipientsConfig == null || recipientsConfig.isBlank()) {
            return List.of("counselor@calmara.edu");
        }
        return Arrays.stream(recipientsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        return JsonUtils.toJson(obj);
    }
}
