package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class WebhookNotificationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FinetuneConfigService configService;
    
    private final ExecutorService webhookExecutor = Executors.newFixedThreadPool(3);
    private final Map<String, WebhookRecord> webhookHistory = new ConcurrentHashMap<>();
    private final Queue<WebhookTask> retryQueue = new ConcurrentLinkedQueue<>();
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000;

    public WebhookNotificationService(RestTemplate restTemplate, 
                                       ObjectMapper objectMapper,
                                       FinetuneConfigService configService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.configService = configService;
    }

    public void notifyTrainingStarted(String trainingId, String reason, Map<String, Object> metadata) {
        WebhookPayload payload = WebhookPayload.builder()
                .event("training.started")
                .trainingId(trainingId)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .data(Map.of(
                        "reason", reason,
                        "metadata", metadata != null ? metadata : Collections.emptyMap()
                ))
                .build();
        
        sendWebhookAsync(payload);
    }

    public void notifyTrainingProgress(String trainingId, int progress, String stage, 
                                        int currentEpoch, int totalEpochs, double currentLoss) {
        WebhookPayload payload = WebhookPayload.builder()
                .event("training.progress")
                .trainingId(trainingId)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .data(Map.of(
                        "progress", progress,
                        "stage", stage,
                        "currentEpoch", currentEpoch,
                        "totalEpochs", totalEpochs,
                        "currentLoss", currentLoss
                ))
                .build();
        
        sendWebhookAsync(payload);
    }

    public void notifyTrainingCompleted(String trainingId, String modelPath, 
                                         Map<String, Object> metrics) {
        WebhookPayload payload = WebhookPayload.builder()
                .event("training.completed")
                .trainingId(trainingId)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .data(Map.of(
                        "modelPath", modelPath,
                        "metrics", metrics != null ? metrics : Collections.emptyMap(),
                        "status", "success"
                ))
                .build();
        
        sendWebhookAsync(payload);
    }

    public void notifyTrainingFailed(String trainingId, String error, String stackTrace) {
        WebhookPayload payload = WebhookPayload.builder()
                .event("training.failed")
                .trainingId(trainingId)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .data(Map.of(
                        "error", error,
                        "stackTrace", stackTrace != null ? stackTrace : "",
                        "status", "failed"
                ))
                .build();
        
        sendWebhookAsync(payload);
    }

    public void notifyModelDeployed(String trainingId, String modelName, String modelVersion) {
        WebhookPayload payload = WebhookPayload.builder()
                .event("model.deployed")
                .trainingId(trainingId)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .data(Map.of(
                        "modelName", modelName,
                        "modelVersion", modelVersion,
                        "deployedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ))
                .build();
        
        sendWebhookAsync(payload);
    }

    public void notifyEvaluationResult(String trainingId, boolean passed, 
                                        Map<String, Object> evaluationMetrics) {
        WebhookPayload payload = WebhookPayload.builder()
                .event("evaluation.result")
                .trainingId(trainingId)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .data(Map.of(
                        "passed", passed,
                        "metrics", evaluationMetrics != null ? evaluationMetrics : Collections.emptyMap()
                ))
                .build();
        
        sendWebhookAsync(payload);
    }

    public void notifyGpuAlert(String trainingId, String alertType, 
                                double temperature, double memoryUsage) {
        WebhookPayload payload = WebhookPayload.builder()
                .event("gpu.alert")
                .trainingId(trainingId)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .data(Map.of(
                        "alertType", alertType,
                        "temperature", temperature,
                        "memoryUsage", memoryUsage
                ))
                .build();
        
        sendWebhookAsync(payload);
    }

    private void sendWebhookAsync(WebhookPayload payload) {
        FinetuneConfigEntity.NotificationConfig notificationConfig = 
                configService.getConfig().getNotification();
        
        String webhookUrl = notificationConfig.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Webhook URL未配置，跳过通知");
            return;
        }
        
        webhookExecutor.submit(() -> {
            sendWebhookWithRetry(webhookUrl, payload, 0);
        });
    }

    private void sendWebhookWithRetry(String webhookUrl, WebhookPayload payload, int retryCount) {
        String webhookId = UUID.randomUUID().toString();
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-ID", webhookId);
            headers.set("X-Event-Type", payload.getEvent());
            headers.set("User-Agent", "Calmara-Webhook/1.0");
            
            String body = objectMapper.writeValueAsString(payload);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            
            log.debug("发送Webhook: url={}, event={}, id={}", webhookUrl, payload.getEvent(), webhookId);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl, 
                    HttpMethod.POST, 
                    entity, 
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                recordWebhookSuccess(webhookId, webhookUrl, payload, response);
                log.info("Webhook发送成功: event={}, id={}", payload.getEvent(), webhookId);
            } else {
                handleWebhookFailure(webhookId, webhookUrl, payload, 
                        "HTTP " + response.getStatusCode(), retryCount);
            }
            
        } catch (Exception e) {
            handleWebhookFailure(webhookId, webhookUrl, payload, e.getMessage(), retryCount);
        }
    }

    private void handleWebhookFailure(String webhookId, String webhookUrl, 
                                       WebhookPayload payload, String error, int retryCount) {
        log.warn("Webhook发送失败: event={}, id={}, error={}, retry={}", 
                payload.getEvent(), webhookId, error, retryCount);
        
        if (retryCount < MAX_RETRIES) {
            scheduleRetry(webhookUrl, payload, retryCount + 1);
        } else {
            recordWebhookFailure(webhookId, webhookUrl, payload, error);
            log.error("Webhook最终失败: event={}, id={}", payload.getEvent(), webhookId);
        }
    }

    private void scheduleRetry(String webhookUrl, WebhookPayload payload, int nextRetryCount) {
        webhookExecutor.submit(() -> {
            try {
                Thread.sleep(RETRY_DELAY_MS * nextRetryCount);
                sendWebhookWithRetry(webhookUrl, payload, nextRetryCount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void recordWebhookSuccess(String webhookId, String url, 
                                       WebhookPayload payload, ResponseEntity<String> response) {
        WebhookRecord record = new WebhookRecord();
        record.setWebhookId(webhookId);
        record.setUrl(url);
        record.setEvent(payload.getEvent());
        record.setSuccess(true);
        record.setStatusCode(response.getStatusCode().value());
        record.setTimestamp(LocalDateTime.now());
        
        webhookHistory.put(webhookId, record);
    }

    private void recordWebhookFailure(String webhookId, String url, 
                                       WebhookPayload payload, String error) {
        WebhookRecord record = new WebhookRecord();
        record.setWebhookId(webhookId);
        record.setUrl(url);
        record.setEvent(payload.getEvent());
        record.setSuccess(false);
        record.setError(error);
        record.setTimestamp(LocalDateTime.now());
        
        webhookHistory.put(webhookId, record);
    }

    public List<WebhookRecord> getWebhookHistory(int limit) {
        return webhookHistory.values().stream()
                .sorted(Comparator.comparing(WebhookRecord::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    public Optional<WebhookRecord> getWebhookRecord(String webhookId) {
        return Optional.ofNullable(webhookHistory.get(webhookId));
    }

    @Data
    @lombok.Builder
    public static class WebhookPayload {
        private String event;
        private String trainingId;
        private String timestamp;
        private Map<String, Object> data;
    }

    @Data
    public static class WebhookRecord {
        private String webhookId;
        private String url;
        private String event;
        private boolean success;
        private int statusCode;
        private String error;
        private LocalDateTime timestamp;
    }

    @Data
    private static class WebhookTask {
        private String webhookUrl;
        private WebhookPayload payload;
        private int retryCount;
    }
}
