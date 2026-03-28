package com.calmara.agent.rag.embedding;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class QwenEmbeddingProvider extends AbstractEmbeddingProvider {

    private static final String PROVIDER_NAME = "Qwen";
    private static final String DEFAULT_MODEL = "text-embedding-v3";
    private static final String DEFAULT_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    private static final int DEFAULT_DIMENSION = 1024;

    private static final Map<String, Integer> MODEL_DIMENSIONS = Map.of(
            "text-embedding-v1", 1536,
            "text-embedding-v2", 1536,
            "text-embedding-v3", 1024,
            "text-embedding-v3-large", 1024
    );

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final int dimension;
    private final AtomicBoolean available = new AtomicBoolean(false);

    public QwenEmbeddingProvider(RestTemplate restTemplate, String apiKey, String model,
                                 String apiUrl, Integer dimension, int cacheMaxSize,
                                 Duration cacheExpireAfterWrite, MeterRegistry meterRegistry) {
        super(cacheMaxSize, cacheExpireAfterWrite, meterRegistry);
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.apiUrl = apiUrl != null ? apiUrl : DEFAULT_URL;

        if (dimension != null) {
            this.dimension = dimension;
        } else {
            this.dimension = MODEL_DIMENSIONS.getOrDefault(this.model, DEFAULT_DIMENSION);
        }

        if (this.apiKey != null && !this.apiKey.isBlank()) {
            this.available.set(true);
            log.info("Qwen Embedding Provider initialized: model={}, dimension={}", this.model, this.dimension);
        } else {
            log.warn("Qwen API key not configured, provider will be unavailable");
        }
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    protected float[] doEmbed(String text) {
        if (!available.get()) {
            throw new IllegalStateException("Qwen Embedding Provider is not available - API key not configured");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> input = new HashMap<>();
            input.put("texts", List.of(text));

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("text_type", "query");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);
            requestBody.put("parameters", parameters);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            throw new RuntimeException("Qwen API returned non-success status: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("Qwen Embedding API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to call Qwen Embedding API", e);
        }
    }

    @Override
    protected List<float[]> doEmbedBatch(List<String> texts) {
        if (!available.get()) {
            throw new IllegalStateException("Qwen Embedding Provider is not available");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> input = new HashMap<>();
            input.put("texts", texts);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("text_type", "query");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);
            requestBody.put("parameters", parameters);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseBatchResponse(response.getBody());
            }

            throw new RuntimeException("Qwen API returned non-success status: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("Qwen Batch Embedding API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to call Qwen Embedding API", e);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parseResponse(Map<String, Object> response) {
        try {
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            if (output != null) {
                List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
                if (embeddings != null && !embeddings.isEmpty()) {
                    List<Double> embeddingList = (List<Double>) embeddings.get(0).get("embedding");
                    float[] embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = embeddingList.get(i).floatValue();
                    }
                    return embedding;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Qwen response", e);
        }
        return new float[dimension];
    }

    @SuppressWarnings("unchecked")
    private List<float[]> parseBatchResponse(Map<String, Object> response) {
        List<float[]> results = new ArrayList<>();
        try {
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            if (output != null) {
                List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
                if (embeddings != null) {
                    embeddings.sort(Comparator.comparingInt(e -> (Integer) e.get("text_index")));
                    for (Map<String, Object> emb : embeddings) {
                        List<Double> embeddingList = (List<Double>) emb.get("embedding");
                        float[] embedding = new float[embeddingList.size()];
                        for (int i = 0; i < embeddingList.size(); i++) {
                            embedding[i] = embeddingList.get(i).floatValue();
                        }
                        results.add(embedding);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Qwen batch response", e);
        }
        return results;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public boolean isAvailable() {
        return available.get() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public EmbeddingProviderHealth health() {
        if (!isAvailable()) {
            return EmbeddingProviderHealth.down("API key not configured");
        }
        return EmbeddingProviderHealth.up();
    }
}
