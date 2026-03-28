package com.calmara.agent.rag.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class OllamaEmbeddingProvider extends AbstractEmbeddingProvider {

    private static final String PROVIDER_NAME = "Ollama";
    private static final String DEFAULT_MODEL = "nomic-embed-text";
    private static final String DEFAULT_URL = "http://localhost:11434/api/embeddings";
    private static final int DEFAULT_DIMENSION = 768;

    private static final Map<String, Integer> MODEL_DIMENSIONS = Map.of(
            "nomic-embed-text", 768,
            "nomic-embed-text-v2-moe", 768,
            "bge-m3", 1024,
            "bge-large", 1024,
            "mxbai-embed-large", 1024,
            "all-minilm", 384,
            "snowflake-arctic-embed", 1024
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiUrl;
    private final int dimension;
    private final AtomicBoolean available = new AtomicBoolean(false);

    public OllamaEmbeddingProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
                                   String model, String apiUrl, Integer dimension,
                                   int cacheMaxSize, Duration cacheExpireAfterWrite,
                                   MeterRegistry meterRegistry) {
        super(cacheMaxSize, cacheExpireAfterWrite, meterRegistry);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.apiUrl = apiUrl != null ? apiUrl : DEFAULT_URL;

        if (dimension != null) {
            this.dimension = dimension;
        } else {
            this.dimension = MODEL_DIMENSIONS.getOrDefault(this.model, DEFAULT_DIMENSION);
        }

        checkAvailability();
        log.info("Ollama Embedding Provider initialized: model={}, dimension={}, url={}",
                this.model, this.dimension, this.apiUrl);
    }

    private void checkAvailability() {
        try {
            String baseUrl = apiUrl.replace("/api/embeddings", "");
            String healthUrl = baseUrl + "/api/tags";
            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                available.set(true);
                log.info("Ollama server is available at {}", baseUrl);
            }
        } catch (Exception e) {
            log.warn("Ollama server not available: {}", e.getMessage());
            available.set(false);
        }
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    protected float[] doEmbed(String text) {
        if (!available.get()) {
            throw new IllegalStateException("Ollama Embedding Provider is not available");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", text);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            throw new RuntimeException("Ollama API returned non-success status: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("Ollama Embedding API call failed: {}", e.getMessage());
            available.set(false);
            throw new RuntimeException("Failed to call Ollama Embedding API", e);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parseResponse(Map<String, Object> response) {
        try {
            List<Double> embeddingList = (List<Double>) response.get("embedding");
            if (embeddingList != null) {
                float[] embedding = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i).floatValue();
                }
                return embedding;
            }
        } catch (Exception e) {
            log.error("Failed to parse Ollama response", e);
        }
        return new float[dimension];
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public boolean isAvailable() {
        return available.get();
    }

    @Override
    public EmbeddingProviderHealth health() {
        if (!isAvailable()) {
            return EmbeddingProviderHealth.down("Ollama server not reachable");
        }
        try {
            long start = System.currentTimeMillis();
            float[] test = doEmbed("test");
            long latency = System.currentTimeMillis() - start;
            return EmbeddingProviderHealth.up(latency);
        } catch (Exception e) {
            return EmbeddingProviderHealth.down("Health check failed", e);
        }
    }

    public void reconnect() {
        checkAvailability();
    }

    public String getModel() {
        return model;
    }
}
