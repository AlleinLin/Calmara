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
public class BgeM3EmbeddingProvider extends AbstractEmbeddingProvider {

    private static final String PROVIDER_NAME = "BGE-M3";
    private static final int DEFAULT_DIMENSION = 1024;
    private static final String DEFAULT_API_URL = "http://localhost:33330/embed";

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final int dimension;
    private final AtomicBoolean available = new AtomicBoolean(false);
    private final boolean denseEnabled;
    private final boolean sparseEnabled;
    private final boolean colbertEnabled;

    public BgeM3EmbeddingProvider(RestTemplate restTemplate, String apiUrl, Integer dimension,
                                  boolean denseEnabled, boolean sparseEnabled, boolean colbertEnabled,
                                  int cacheMaxSize, Duration cacheExpireAfterWrite,
                                  MeterRegistry meterRegistry) {
        super(cacheMaxSize, cacheExpireAfterWrite, meterRegistry);
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl != null ? apiUrl : DEFAULT_API_URL;
        this.dimension = dimension != null ? dimension : DEFAULT_DIMENSION;
        this.denseEnabled = denseEnabled;
        this.sparseEnabled = sparseEnabled;
        this.colbertEnabled = colbertEnabled;

        checkAvailability();
        log.info("BGE-M3 Embedding Provider initialized: url={}, dimension={}, dense={}, sparse={}, colbert={}",
                this.apiUrl, this.dimension, denseEnabled, sparseEnabled, colbertEnabled);
    }

    private void checkAvailability() {
        try {
            String healthUrl = apiUrl.replace("/embed", "/health");
            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                available.set(true);
                log.info("BGE-M3 embedding service is available at {}", apiUrl);
            }
        } catch (Exception e) {
            log.warn("BGE-M3 embedding service not available: {}", e.getMessage());
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
            throw new IllegalStateException("BGE-M3 Embedding Provider is not available");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("texts", List.of(text));
            requestBody.put("dense", denseEnabled);
            requestBody.put("sparse", sparseEnabled);
            requestBody.put("colbert", colbertEnabled);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseDenseResponse(response.getBody());
            }

            throw new RuntimeException("BGE-M3 API returned non-success status: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("BGE-M3 Embedding API call failed: {}", e.getMessage());
            available.set(false);
            throw new RuntimeException("Failed to call BGE-M3 Embedding API", e);
        }
    }

    @Override
    protected List<float[]> doEmbedBatch(List<String> texts) {
        if (!available.get()) {
            throw new IllegalStateException("BGE-M3 Embedding Provider is not available");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("texts", texts);
            requestBody.put("dense", denseEnabled);
            requestBody.put("sparse", sparseEnabled);
            requestBody.put("colbert", colbertEnabled);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseBatchDenseResponse(response.getBody());
            }

            throw new RuntimeException("BGE-M3 API returned non-success status: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("BGE-M3 Batch Embedding API call failed: {}", e.getMessage());
            available.set(false);
            throw new RuntimeException("Failed to call BGE-M3 Embedding API", e);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parseDenseResponse(Map<String, Object> response) {
        try {
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            if (embeddings != null && !embeddings.isEmpty()) {
                List<Double> embeddingList = embeddings.get(0);
                float[] embedding = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i).floatValue();
                }
                return embedding;
            }
        } catch (Exception e) {
            log.error("Failed to parse BGE-M3 response", e);
        }
        return new float[dimension];
    }

    @SuppressWarnings("unchecked")
    private List<float[]> parseBatchDenseResponse(Map<String, Object> response) {
        List<float[]> results = new ArrayList<>();
        try {
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            if (embeddings != null) {
                for (List<Double> embeddingList : embeddings) {
                    float[] embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = embeddingList.get(i).floatValue();
                    }
                    results.add(embedding);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse BGE-M3 batch response", e);
        }
        return results;
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
            return EmbeddingProviderHealth.down("BGE-M3 service not reachable");
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

    public boolean isDenseEnabled() {
        return denseEnabled;
    }

    public boolean isSparseEnabled() {
        return sparseEnabled;
    }

    public boolean isColbertEnabled() {
        return colbertEnabled;
    }
}
