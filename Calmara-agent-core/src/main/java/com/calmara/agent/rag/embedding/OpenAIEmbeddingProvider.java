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
public class OpenAIEmbeddingProvider extends AbstractEmbeddingProvider {

    private static final String PROVIDER_NAME = "OpenAI";
    private static final String DEFAULT_MODEL = "text-embedding-3-small";
    private static final String DEFAULT_URL = "https://api.openai.com/v1/embeddings";
    private static final int DEFAULT_DIMENSION = 1536;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final int dimension;
    private final AtomicBoolean available = new AtomicBoolean(false);

    public OpenAIEmbeddingProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
                                   String apiKey, String model, String apiUrl,
                                   Integer dimension, int cacheMaxSize,
                                   Duration cacheExpireAfterWrite, MeterRegistry meterRegistry) {
        super(cacheMaxSize, cacheExpireAfterWrite, meterRegistry);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.apiUrl = apiUrl != null ? apiUrl : DEFAULT_URL;
        this.dimension = dimension != null ? dimension : DEFAULT_DIMENSION;

        if (this.apiKey != null && !this.apiKey.isBlank()) {
            this.available.set(true);
            log.info("OpenAI Embedding Provider initialized: model={}, dimension={}", this.model, this.dimension);
        } else {
            log.warn("OpenAI API key not configured, provider will be unavailable");
        }
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    protected float[] doEmbed(String text) {
        if (!available.get()) {
            throw new IllegalStateException("OpenAI Embedding Provider is not available - API key not configured");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", text);
            requestBody.put("model", model);
            if (dimension != DEFAULT_DIMENSION) {
                requestBody.put("dimensions", dimension);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            }

            throw new RuntimeException("OpenAI API returned non-success status: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("OpenAI Embedding API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to call OpenAI Embedding API", e);
        }
    }

    @Override
    protected List<float[]> doEmbedBatch(List<String> texts) {
        if (!available.get()) {
            throw new IllegalStateException("OpenAI Embedding Provider is not available");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", texts);
            requestBody.put("model", model);
            if (dimension != DEFAULT_DIMENSION) {
                requestBody.put("dimensions", dimension);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseBatchResponse(response.getBody());
            }

            throw new RuntimeException("OpenAI API returned non-success status: " + response.getStatusCode());

        } catch (RestClientException e) {
            log.error("OpenAI Batch Embedding API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to call OpenAI Embedding API", e);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data != null && !data.isEmpty()) {
                List<Double> embeddingList = (List<Double>) data.get(0).get("embedding");
                float[] embedding = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i).floatValue();
                }
                return embedding;
            }
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
        }
        return new float[dimension];
    }

    @SuppressWarnings("unchecked")
    private List<float[]> parseBatchResponse(Map<String, Object> response) {
        List<float[]> results = new ArrayList<>();
        try {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
            if (dataList != null) {
                dataList.sort(Comparator.comparingInt(d -> (Integer) d.get("index")));
                for (Map<String, Object> data : dataList) {
                    List<Double> embeddingList = (List<Double>) data.get("embedding");
                    float[] embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = embeddingList.get(i).floatValue();
                    }
                    results.add(embedding);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse OpenAI batch response", e);
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
