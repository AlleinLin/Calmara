package com.calmara.api.controller;

import com.calmara.agent.rag.*;
import com.calmara.common.Result;
import com.calmara.model.dto.EmotionResult;
import com.calmara.model.dto.RAGContext;
import com.calmara.model.enums.RiskLevel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RAGController {

    private final SimpleRAGService simpleRAG;
    private final RouterRAGService routerRAG;
    private final AgenticRAGService agenticRAG;
    private final KnowledgeBaseLoader knowledgeBaseLoader;

    public RAGController(SimpleRAGService simpleRAG,
                         RouterRAGService routerRAG,
                         AgenticRAGService agenticRAG,
                         KnowledgeBaseLoader knowledgeBaseLoader) {
        this.simpleRAG = simpleRAG;
        this.routerRAG = routerRAG;
        this.agenticRAG = agenticRAG;
        this.knowledgeBaseLoader = knowledgeBaseLoader;
    }

    @PostMapping("/simple")
    public Result<RAGResponse> simpleQuery(@RequestBody RAGRequest request) {
        log.info("SimpleRAG查询: {}", request.getQuery());

        String answer = simpleRAG.query(request.getQuery());

        RAGResponse response = new RAGResponse();
        response.setQuery(request.getQuery());
        response.setAnswer(answer);
        response.setMode("SIMPLE_RAG");

        return Result.success(response);
    }

    @PostMapping(value = "/simple/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> simpleQueryStream(@RequestBody RAGRequest request) {
        log.info("SimpleRAG流式查询: {}", request.getQuery());

        return simpleRAG.queryStream(request.getQuery())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{\"status\":\"completed\"}")
                                .build()
                ));
    }

    @PostMapping("/router")
    public Result<RAGResponse> routerQuery(@RequestBody RAGRequest request) {
        log.info("RouterRAG查询: {}", request.getQuery());

        EmotionResult emotion = request.getEmotionResult() != null
                ? request.getEmotionResult()
                : EmotionResult.builder()
                        .label("正常")
                        .score(0.0)
                        .riskLevel(RiskLevel.LOW)
                        .build();

        String answer = routerRAG.routeSync(request.getQuery(), emotion);

        RouterRAGService.RoutingResult routing = routerRAG.analyzeRouting(request.getQuery());

        RAGResponse response = new RAGResponse();
        response.setQuery(request.getQuery());
        response.setAnswer(answer);
        response.setMode("ROUTER_RAG");
        response.setRoutingDecision(routing.getAction());
        response.setRoutingThought(routing.getThought());

        return Result.success(response);
    }

    @PostMapping(value = "/router/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> routerQueryStream(@RequestBody RAGRequest request) {
        log.info("RouterRAG流式查询: {}", request.getQuery());

        EmotionResult emotion = request.getEmotionResult() != null
                ? request.getEmotionResult()
                : EmotionResult.builder()
                        .label("正常")
                        .score(0.0)
                        .riskLevel(RiskLevel.LOW)
                        .build();

        RAGContext context = RAGContext.builder()
                .emotionResult(emotion)
                .maxRetries(1)
                .build();

        return routerRAG.route(request.getQuery(), context)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{\"status\":\"completed\"}")
                                .build()
                ));
    }

    @PostMapping("/agentic")
    public Result<RAGResponse> agenticQuery(@RequestBody RAGRequest request) {
        log.info("AgenticRAG查询: {}", request.getQuery());

        EmotionResult emotion = request.getEmotionResult() != null
                ? request.getEmotionResult()
                : EmotionResult.builder()
                        .label("正常")
                        .score(0.0)
                        .riskLevel(RiskLevel.LOW)
                        .build();

        RAGContext context = RAGContext.builder()
                .emotionResult(emotion)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .build();

        StringBuilder answer = new StringBuilder();
        agenticRAG.executeRAG(request.getQuery(), context)
                .doOnNext(answer::append)
                .blockLast();

        RAGResponse response = new RAGResponse();
        response.setQuery(request.getQuery());
        response.setAnswer(answer.toString());
        response.setMode("AGENTIC_RAG");

        return Result.success(response);
    }

    @PostMapping(value = "/agentic/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> agenticQueryStream(@RequestBody RAGRequest request) {
        log.info("AgenticRAG流式查询: {}", request.getQuery());

        EmotionResult emotion = request.getEmotionResult() != null
                ? request.getEmotionResult()
                : EmotionResult.builder()
                        .label("正常")
                        .score(0.0)
                        .riskLevel(RiskLevel.LOW)
                        .build();

        RAGContext context = RAGContext.builder()
                .emotionResult(emotion)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .build();

        return agenticRAG.executeRAG(request.getQuery(), context)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{\"status\":\"completed\"}")
                                .build()
                ));
    }

    @PostMapping("/compare")
    public Result<Map<String, RAGResponse>> compareRAGs(@RequestBody RAGRequest request) {
        log.info("对比三种RAG模式: {}", request.getQuery());

        Map<String, RAGResponse> results = new HashMap<>();

        long start = System.currentTimeMillis();
        String simpleAnswer = simpleRAG.query(request.getQuery());
        long simpleTime = System.currentTimeMillis() - start;

        RAGResponse simpleResponse = new RAGResponse();
        simpleResponse.setQuery(request.getQuery());
        simpleResponse.setAnswer(simpleAnswer);
        simpleResponse.setMode("SIMPLE_RAG");
        simpleResponse.setProcessingTimeMs(simpleTime);
        results.put("simple", simpleResponse);

        start = System.currentTimeMillis();
        EmotionResult emotion = request.getEmotionResult() != null
                ? request.getEmotionResult()
                : EmotionResult.builder().label("正常").score(0.0).riskLevel(RiskLevel.LOW).build();
        String routerAnswer = routerRAG.routeSync(request.getQuery(), emotion);
        long routerTime = System.currentTimeMillis() - start;

        RouterRAGService.RoutingResult routing = routerRAG.analyzeRouting(request.getQuery());
        RAGResponse routerResponse = new RAGResponse();
        routerResponse.setQuery(request.getQuery());
        routerResponse.setAnswer(routerAnswer);
        routerResponse.setMode("ROUTER_RAG");
        routerResponse.setRoutingDecision(routing.getAction());
        routerResponse.setRoutingThought(routing.getThought());
        routerResponse.setProcessingTimeMs(routerTime);
        results.put("router", routerResponse);

        start = System.currentTimeMillis();
        RAGContext context = RAGContext.builder()
                .emotionResult(emotion)
                .maxRetries(3)
                .build();
        StringBuilder agenticAnswer = new StringBuilder();
        agenticRAG.executeRAG(request.getQuery(), context)
                .doOnNext(agenticAnswer::append)
                .blockLast();
        long agenticTime = System.currentTimeMillis() - start;

        RAGResponse agenticResponse = new RAGResponse();
        agenticResponse.setQuery(request.getQuery());
        agenticResponse.setAnswer(agenticAnswer.toString());
        agenticResponse.setMode("AGENTIC_RAG");
        agenticResponse.setProcessingTimeMs(agenticTime);
        results.put("agentic", agenticResponse);

        return Result.success(results);
    }

    @PostMapping("/knowledge/reload")
    public Result<Integer> reloadKnowledge() {
        log.info("重新加载知识库");
        int count = knowledgeBaseLoader.loadKnowledgeBase();
        return Result.success(count);
    }

    @PostMapping("/knowledge/add")
    public Result<Boolean> addKnowledge(@RequestBody KnowledgeAddRequest request) {
        log.info("添加知识: {}", request.getTitle());
        
        Document doc = new Document(request.getTitle(), request.getContent());
        simpleRAG.addDocument(doc);
        
        return Result.success(true);
    }

    @Data
    public static class RAGRequest {
        private String query;
        private EmotionResult emotionResult;
        private Integer maxRetries;
    }

    @Data
    public static class RAGResponse {
        private String query;
        private String answer;
        private String mode;
        private String routingDecision;
        private String routingThought;
        private Long processingTimeMs;
    }

    @Data
    public static class KnowledgeAddRequest {
        private String title;
        private String content;
    }
}
