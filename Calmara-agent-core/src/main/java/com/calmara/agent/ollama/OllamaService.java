package com.calmara.agent.ollama;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class OllamaService {

    private final WebClient webClient;

    @Value("${calmara.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${calmara.ollama.model:qwen2.5:7b-chat}")
    private String model;

    public OllamaService() {
        this.webClient = WebClient.builder()
                .build();
    }

    public Flux<String> generateStream(String prompt) {
        log.info("Ollama流式生成请求: model={}, prompt='{}...'", model, prompt.substring(0, Math.min(50, prompt.length())));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", true,
                "options", Map.of(
                        "temperature", 0.7,
                        "top_p", 0.9,
                        "num_ctx", 4096
                )
        );

        return webClient.post()
                .uri(baseUrl + "/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .map(this::extractContent)
                .doOnError(e -> log.error("Ollama生成失败", e))
                .timeout(Duration.ofSeconds(120));
    }

    public String generate(String prompt) {
        log.info("Ollama同步生成请求: model={}", model);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri(baseUrl + "/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            if (response != null && response.containsKey("response")) {
                String result = (String) response.get("response");
                log.info("Ollama生成成功: response='{}...'",
                        result.substring(0, Math.min(100, result.length())));
                return result;
            }

            throw new BusinessException(ErrorCode.MODEL_ERROR, "Ollama响应格式错误");

        } catch (Exception e) {
            log.error("Ollama调用失败", e);
            throw new BusinessException(ErrorCode.MODEL_ERROR, "模型调用失败: " + e.getMessage());
        }
    }

    public boolean isModelAvailable() {
        try {
            String response = webClient.get()
                    .uri(baseUrl + "/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return response != null && response.contains(model);
        } catch (Exception e) {
            log.warn("Ollama服务不可用", e);
            return false;
        }
    }

    private String extractContent(String jsonLine) {
        try {
            if (jsonLine == null || jsonLine.isBlank()) {
                return "";
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> response = mapper.readValue(jsonLine, Map.class);

            if (response.containsKey("response")) {
                return (String) response.get("response");
            }

            return "";

        } catch (Exception e) {
            log.warn("解析Ollama响应失败: {}", jsonLine);
            return "";
        }
    }

    public void pullModel() {
        log.info("开始拉取模型: {}", model);

        Map<String, Object> requestBody = Map.of("name", model);

        webClient.post()
                .uri(baseUrl + "/api/pull")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(s -> log.info("模型拉取完成: {}", model))
                .doOnError(e -> log.error("模型拉取失败", e))
                .block();
    }

    @Data
    public static class GenerateRequest {
        private String model;
        private String prompt;
        private boolean stream;
        private Map<String, Object> options;
    }
}
