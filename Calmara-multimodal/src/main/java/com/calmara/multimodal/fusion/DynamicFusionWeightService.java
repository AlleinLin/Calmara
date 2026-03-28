package com.calmara.multimodal.fusion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DynamicFusionWeightService {

    private static final String WEIGHT_CONFIG_KEY = "calmara:fusion:weights";
    private static final String WEIGHT_HISTORY_KEY = "calmara:fusion:weight_history";
    private static final String PERFORMANCE_METRICS_KEY = "calmara:fusion:performance";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private volatile FusionWeights currentWeights;
    private final Map<String, List<PerformanceMetric>> performanceHistory = new ConcurrentHashMap<>();
    
    public DynamicFusionWeightService(RedisTemplate<String, Object> redisTemplate,
                                      ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.currentWeights = loadWeights();
        
        if (this.currentWeights == null) {
            this.currentWeights = FusionWeights.getDefault();
            saveWeights(this.currentWeights);
        }
        
        log.info("情绪融合权重服务初始化完成: {}", currentWeights);
    }

    public FusionWeights getCurrentWeights() {
        return currentWeights;
    }

    public void updateWeights(FusionWeights newWeights) {
        if (!newWeights.isValid()) {
            throw new IllegalArgumentException("权重配置无效: 权重和必须为1.0");
        }
        
        FusionWeights oldWeights = this.currentWeights;
        this.currentWeights = newWeights;
        
        saveWeights(newWeights);
        recordWeightChange(oldWeights, newWeights);
        
        log.info("更新融合权重: {} -> {}", oldWeights, newWeights);
    }

    public void updateWeight(String modality, double weight) {
        FusionWeights.FusionWeightsBuilder builder = currentWeights.toBuilder();
        
        switch (modality.toLowerCase()) {
            case "text":
                builder.textWeight(weight);
                break;
            case "audio":
                builder.audioWeight(weight);
                break;
            case "visual":
                builder.visualWeight(weight);
                break;
            case "video":
                builder.videoWeight(weight);
                break;
            default:
                throw new IllegalArgumentException("未知的模态类型: " + modality);
        }
        
        FusionWeights newWeights = builder.build();
        updateWeights(newWeights);
    }

    public FusionWeights getWeightsForContext(FusionContext context) {
        FusionWeights baseWeights = currentWeights;
        
        if (context.isHighRisk()) {
            return adjustForHighRisk(baseWeights);
        }
        
        if (context.getConfidence() < 0.5) {
            return adjustForLowConfidence(baseWeights);
        }
        
        if (context.getAvailableModalities().size() == 1) {
            return adjustForSingleModality(baseWeights, context.getAvailableModalities());
        }
        
        return baseWeights;
    }

    private FusionWeights adjustForHighRisk(FusionWeights base) {
        return FusionWeights.builder()
                .textWeight(base.getTextWeight() * 0.8)
                .audioWeight(base.getAudioWeight() * 1.2)
                .visualWeight(base.getVisualWeight() * 1.2)
                .videoWeight(base.getVideoWeight() * 1.2)
                .riskBoostFactor(1.5)
                .build()
                .normalize();
    }

    private FusionWeights adjustForLowConfidence(FusionWeights base) {
        return FusionWeights.builder()
                .textWeight(base.getTextWeight() * 1.2)
                .audioWeight(base.getAudioWeight() * 0.9)
                .visualWeight(base.getVisualWeight() * 0.9)
                .videoWeight(base.getVideoWeight() * 0.9)
                .build()
                .normalize();
    }

    private FusionWeights adjustForSingleModality(FusionWeights base, Set<String> available) {
        String modality = available.iterator().next();
        FusionWeights.FusionWeightsBuilder builder = FusionWeights.builder();
        
        switch (modality.toLowerCase()) {
            case "text":
                return builder.textWeight(1.0).audioWeight(0.0).visualWeight(0.0).videoWeight(0.0).build();
            case "audio":
                return builder.textWeight(0.0).audioWeight(1.0).visualWeight(0.0).videoWeight(0.0).build();
            case "visual":
                return builder.textWeight(0.0).audioWeight(0.0).visualWeight(1.0).videoWeight(0.0).build();
            case "video":
                return builder.textWeight(0.0).audioWeight(0.0).visualWeight(0.0).videoWeight(1.0).build();
            default:
                return base;
        }
    }

    public void recordPerformance(String sessionId, String modality, 
                                  double predictedScore, double actualScore) {
        double error = Math.abs(predictedScore - actualScore);
        PerformanceMetric metric = new PerformanceMetric(
                sessionId, modality, predictedScore, actualScore, error, LocalDateTime.now()
        );
        
        performanceHistory.computeIfAbsent(modality, k -> new ArrayList<>()).add(metric);
        
        if (performanceHistory.get(modality).size() > 1000) {
            performanceHistory.get(modality).remove(0);
        }
        
        try {
            String json = objectMapper.writeValueAsString(metric);
            redisTemplate.opsForList().rightPush(
                    PERFORMANCE_METRICS_KEY + ":" + modality, json);
        } catch (JsonProcessingException e) {
            log.error("记录性能指标失败", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void autoOptimizeWeights() {
        log.info("开始自动优化融合权重...");
        
        Map<String, Double> modalityErrors = new HashMap<>();
        
        for (Map.Entry<String, List<PerformanceMetric>> entry : performanceHistory.entrySet()) {
            String modality = entry.getKey();
            List<PerformanceMetric> metrics = entry.getValue();
            
            if (metrics.size() < 10) {
                continue;
            }
            
            double avgError = metrics.stream()
                    .skip(Math.max(0, metrics.size() - 100))
                    .mapToDouble(PerformanceMetric::getError)
                    .average()
                    .orElse(0.5);
            
            modalityErrors.put(modality, avgError);
        }
        
        if (modalityErrors.size() < 2) {
            log.info("性能数据不足，跳过权重优化");
            return;
        }
        
        FusionWeights optimized = optimizeWeights(modalityErrors);
        
        if (optimized.isValid()) {
            updateWeights(optimized);
            log.info("自动优化权重完成: {}", optimized);
        }
    }

    private FusionWeights optimizeWeights(Map<String, Double> errors) {
        double textError = errors.getOrDefault("text", 0.5);
        double audioError = errors.getOrDefault("audio", 0.5);
        double visualError = errors.getOrDefault("visual", 0.5);
        double videoError = errors.getOrDefault("video", 0.5);
        
        double textWeight = currentWeights.getTextWeight() * (1 - textError * 0.5);
        double audioWeight = currentWeights.getAudioWeight() * (1 - audioError * 0.5);
        double visualWeight = currentWeights.getVisualWeight() * (1 - visualError * 0.5);
        double videoWeight = currentWeights.getVideoWeight() * (1 - videoError * 0.5);
        
        double total = textWeight + audioWeight + visualWeight + videoWeight;
        if (total > 0) {
            textWeight /= total;
            audioWeight /= total;
            visualWeight /= total;
            videoWeight /= total;
        }
        
        return FusionWeights.builder()
                .textWeight(textWeight)
                .audioWeight(audioWeight)
                .visualWeight(visualWeight)
                .videoWeight(videoWeight)
                .build();
    }

    private FusionWeights loadWeights() {
        try {
            Object data = redisTemplate.opsForValue().get(WEIGHT_CONFIG_KEY);
            if (data != null) {
                return objectMapper.readValue(data.toString(), FusionWeights.class);
            }
        } catch (Exception e) {
            log.error("加载权重配置失败", e);
        }
        return null;
    }

    private void saveWeights(FusionWeights weights) {
        try {
            String json = objectMapper.writeValueAsString(weights);
            redisTemplate.opsForValue().set(WEIGHT_CONFIG_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("保存权重配置失败", e);
        }
    }

    private void recordWeightChange(FusionWeights oldWeights, FusionWeights newWeights) {
        try {
            WeightChangeRecord record = new WeightChangeRecord(
                    oldWeights, newWeights, LocalDateTime.now(), "manual"
            );
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForList().rightPush(WEIGHT_HISTORY_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("记录权重变更失败", e);
        }
    }

    public WeightStatistics getWeightStatistics() {
        Map<String, Double> currentWeightsMap = new HashMap<>();
        currentWeightsMap.put("text", currentWeights.getTextWeight());
        currentWeightsMap.put("audio", currentWeights.getAudioWeight());
        currentWeightsMap.put("visual", currentWeights.getVisualWeight());
        currentWeightsMap.put("video", currentWeights.getVideoWeight());
        
        Map<String, Double> averageErrors = new HashMap<>();
        for (Map.Entry<String, List<PerformanceMetric>> entry : performanceHistory.entrySet()) {
            double avgError = entry.getValue().stream()
                    .skip(Math.max(0, entry.getValue().size() - 100))
                    .mapToDouble(PerformanceMetric::getError)
                    .average()
                    .orElse(0.0);
            averageErrors.put(entry.getKey(), avgError);
        }
        
        return WeightStatistics.builder()
                .currentWeights(currentWeightsMap)
                .averageErrors(averageErrors)
                .totalRecords(performanceHistory.values().stream()
                        .mapToInt(List::size)
                        .sum())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FusionWeights {
        @JsonProperty("text_weight")
        private double textWeight;
        
        @JsonProperty("audio_weight")
        private double audioWeight;
        
        @JsonProperty("visual_weight")
        private double visualWeight;
        
        @JsonProperty("video_weight")
        private double videoWeight;
        
        @JsonProperty("risk_boost_factor")
        @Builder.Default
        private double riskBoostFactor = 1.0;
        
        @JsonProperty("confidence_threshold")
        @Builder.Default
        private double confidenceThreshold = 0.6;
        
        public static FusionWeights getDefault() {
            return FusionWeights.builder()
                    .textWeight(0.1)
                    .audioWeight(0.4)
                    .visualWeight(0.4)
                    .videoWeight(0.1)
                    .riskBoostFactor(1.0)
                    .confidenceThreshold(0.6)
                    .build();
        }
        
        public boolean isValid() {
            double sum = textWeight + audioWeight + visualWeight + videoWeight;
            return Math.abs(sum - 1.0) < 0.001;
        }
        
        public FusionWeights normalize() {
            double sum = textWeight + audioWeight + visualWeight + videoWeight;
            if (sum == 0) {
                return getDefault();
            }
            return FusionWeights.builder()
                    .textWeight(textWeight / sum)
                    .audioWeight(audioWeight / sum)
                    .visualWeight(visualWeight / sum)
                    .videoWeight(videoWeight / sum)
                    .riskBoostFactor(riskBoostFactor)
                    .confidenceThreshold(confidenceThreshold)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FusionContext {
        private String sessionId;
        private boolean highRisk;
        private double confidence;
        private Set<String> availableModalities;
        private Map<String, Object> metadata;
        
        public static FusionContext defaultContext() {
            return FusionContext.builder()
                    .highRisk(false)
                    .confidence(1.0)
                    .availableModalities(Set.of("text", "audio", "visual"))
                    .metadata(new HashMap<>())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetric {
        private String sessionId;
        private String modality;
        private double predictedScore;
        private double actualScore;
        private double error;
        private LocalDateTime timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightChangeRecord {
        private FusionWeights oldWeights;
        private FusionWeights newWeights;
        private LocalDateTime timestamp;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightStatistics {
        private Map<String, Double> currentWeights;
        private Map<String, Double> averageErrors;
        private int totalRecords;
        private LocalDateTime lastUpdated;
    }
}
