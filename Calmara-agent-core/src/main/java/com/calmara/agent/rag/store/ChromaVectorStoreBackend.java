package com.calmara.agent.rag.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ChromaVectorStoreBackend implements VectorStoreBackend {

    private static final String PROVIDER_NAME = "Chroma";
    private static final String API_VERSION = "/api/v1";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String chromaUrl;
    private final String collectionName;
    private final boolean enabled;
    private final int connectTimeout;
    private final int readTimeout;
    private final int retryCount;
    private final long retryDelay;

    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong lastHealthCheckTime = new AtomicLong(0);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);

    public ChromaVectorStoreBackend(RestTemplate restTemplate, ObjectMapper objectMapper,
                                    String chromaUrl, String collectionName, boolean enabled,
                                    int connectTimeout, int readTimeout, int retryCount, long retryDelay) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.chromaUrl = chromaUrl;
        this.collectionName = collectionName;
        this.enabled = enabled;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.retryCount = retryCount;
        this.retryDelay = retryDelay;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public void initialize() {
        if (!enabled) {
            log.warn("Chroma is disabled, skipping initialization");
            return;
        }

        log.info("Initializing Chroma client: url={}, collection={}", chromaUrl, collectionName);

        int attempts = 0;
        while (attempts < retryCount) {
            try {
                if (testConnection()) {
                    ensureCollectionExists();
                    healthy.set(true);
                    initialized.set(true);
                    log.info("Chroma client initialized successfully");
                    return;
                }
            } catch (Exception e) {
                attempts++;
                log.warn("Chroma initialization attempt {}/{} failed: {}", attempts, retryCount, e.getMessage());
                if (attempts < retryCount) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Chroma initialization failed after {} attempts", retryCount);
        healthy.set(false);
        initialized.set(true);
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Chroma backend");
        healthy.set(false);
        initialized.set(false);
    }

    private boolean testConnection() {
        try {
            String url = chromaUrl + API_VERSION + "/heartbeat";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Chroma connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private void ensureCollectionExists() {
        try {
            String getUrl = chromaUrl + API_VERSION + "/collections/" + collectionName;
            ResponseEntity<Map> response = restTemplate.getForEntity(getUrl, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Chroma Collection already exists: {}", collectionName);
                return;
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Collection does not exist, creating: {}", collectionName);
        } catch (Exception e) {
            log.warn("Failed to check collection: {}", e.getMessage());
        }

        createCollection();
    }

    private void createCollection() {
        try {
            String url = chromaUrl + API_VERSION + "/collections";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", collectionName);
            requestBody.put("metadata", Map.of(
                    "description", "Calmara Psychological Knowledge Vector Store",
                    "created_by", "calmara-system"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Chroma Collection created: {}", collectionName);
            } else {
                throw new RuntimeException("Failed to create collection: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to create Chroma collection", e);
            throw new RuntimeException("Cannot create collection: " + e.getMessage(), e);
        }
    }

    @Override
    public void add(String id, String content, float[] embedding, Map<String, Object> metadata) {
        if (!isAvailable()) {
            log.warn("Chroma not available, skipping document add");
            return;
        }

        try {
            String url = chromaUrl + API_VERSION + "/collections/" + collectionName + "/add";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", List.of(id));
            requestBody.put("documents", List.of(content));
            requestBody.put("embeddings", List.of(floatArrayToList(embedding)));

            Map<String, Object> meta = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            meta.put("content_length", content.length());
            meta.put("added_at", System.currentTimeMillis());
            requestBody.put("metadatas", List.of(meta));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                successfulOperations.incrementAndGet();
                log.debug("Document added successfully: id={}", id);
            } else {
                failedOperations.incrementAndGet();
                log.error("Failed to add document: id={}, status={}", id, response.getStatusCode());
            }

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("Failed to add document to Chroma: id={}, error={}", id, e.getMessage());
            handleConnectionError(e);
        }
    }

    @Override
    public void addBatch(List<String> ids, List<String> contents, List<float[]> embeddings, List<Map<String, Object>> metadatas) {
        if (!isAvailable()) {
            log.warn("Chroma not available, skipping batch add");
            return;
        }

        if (ids.size() != contents.size() || ids.size() != embeddings.size()) {
            throw new IllegalArgumentException("ids, contents, embeddings size mismatch");
        }

        try {
            String url = chromaUrl + API_VERSION + "/collections/" + collectionName + "/add";

            List<List<Float>> embeddingList = embeddings.stream()
                    .map(this::floatArrayToList)
                    .toList();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", ids);
            requestBody.put("documents", contents);
            requestBody.put("embeddings", embeddingList);
            requestBody.put("metadatas", metadatas);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                successfulOperations.addAndGet(ids.size());
                log.info("Batch add successful: count={}", ids.size());
            } else {
                failedOperations.addAndGet(ids.size());
                log.error("Batch add failed: status={}", response.getStatusCode());
            }

        } catch (Exception e) {
            failedOperations.addAndGet(ids.size());
            log.error("Failed to batch add to Chroma: {}", e.getMessage());
            handleConnectionError(e);
        }
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK, double similarityThreshold, Map<String, Object> filter) {
        if (!isAvailable()) {
            return List.of();
        }

        try {
            String url = chromaUrl + API_VERSION + "/collections/" + collectionName + "/query";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query_embeddings", List.of(floatArrayToList(queryEmbedding)));
            requestBody.put("n_results", topK);
            requestBody.put("include", List.of("documents", "metadatas", "distances"));

            if (filter != null && !filter.isEmpty()) {
                requestBody.put("where", filter);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                successfulOperations.incrementAndGet();
                return parseQueryResponse(response.getBody(), similarityThreshold);
            }

            failedOperations.incrementAndGet();
            log.error("Chroma query failed: status={}", response.getStatusCode());
            return List.of();

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("Chroma query exception: {}", e.getMessage());
            handleConnectionError(e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchResult> parseQueryResponse(Map<String, Object> response, double similarityThreshold) {
        List<SearchResult> results = new ArrayList<>();

        try {
            List<List<String>> docs = (List<List<String>>) response.get("documents");
            List<List<Map<String, Object>>> metas = (List<List<Map<String, Object>>>) response.get("metadatas");
            List<List<Double>> dists = (List<List<Double>>) response.get("distances");
            List<List<String>> ids = (List<List<String>>) response.get("ids");

            if (docs == null || docs.isEmpty()) {
                return results;
            }

            List<String> documents = docs.get(0);
            List<Map<String, Object>> metadatas = metas != null && !metas.isEmpty() ? metas.get(0) : List.of();
            List<Double> distances = dists != null && !dists.isEmpty() ? dists.get(0) : List.of();
            List<String> idList = ids != null && !ids.isEmpty() ? ids.get(0) : List.of();

            for (int i = 0; i < documents.size(); i++) {
                String id = i < idList.size() ? idList.get(i) : "doc_" + i;
                String content = documents.get(i);
                double distance = i < distances.size() ? distances.get(i) : 1.0;
                double similarity = 1.0 - distance;
                Map<String, Object> metadata = i < metadatas.size() ? metadatas.get(i) : Map.of();

                if (similarity >= similarityThreshold) {
                    results.add(SearchResult.of(id, content, similarity, metadata));
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse Chroma query response", e);
        }

        return results;
    }

    @Override
    public boolean delete(String id) {
        if (!isAvailable()) {
            return false;
        }

        try {
            String url = chromaUrl + API_VERSION + "/collections/" + collectionName + "/delete";

            Map<String, Object> requestBody = Map.of("ids", List.of(id));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            successfulOperations.incrementAndGet();
            return true;

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("Failed to delete document: id={}, error={}", id, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteBatch(List<String> ids) {
        if (!isAvailable()) {
            return false;
        }

        try {
            String url = chromaUrl + API_VERSION + "/collections/" + collectionName + "/delete";

            Map<String, Object> requestBody = Map.of("ids", ids);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            successfulOperations.addAndGet(ids.size());
            return true;

        } catch (Exception e) {
            failedOperations.addAndGet(ids.size());
            log.error("Failed to batch delete: error={}", e.getMessage());
            return false;
        }
    }

    @Override
    public long count() {
        if (!isAvailable()) {
            return 0;
        }

        try {
            String url = chromaUrl + API_VERSION + "/collections/" + collectionName;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object count = response.getBody().get("count");
                if (count instanceof Number) {
                    return ((Number) count).longValue();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get document count: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public boolean isAvailable() {
        return enabled && healthy.get() && initialized.get();
    }

    @Override
    public boolean isHealthy() {
        return healthy.get();
    }

    @Scheduled(fixedRate = 30000)
    public void healthCheck() {
        if (!enabled) {
            return;
        }

        boolean wasHealthy = healthy.get();
        boolean nowHealthy = testConnection();

        if (nowHealthy && !wasHealthy) {
            log.info("Chroma service recovered");
            if (!initialized.get()) {
                ensureCollectionExists();
            }
        } else if (!nowHealthy && wasHealthy) {
            log.error("Chroma service unavailable! System will degrade");
        }

        healthy.set(nowHealthy);
        lastHealthCheckTime.set(System.currentTimeMillis());
    }

    private void handleConnectionError(Exception e) {
        if (e instanceof ResourceAccessException || e instanceof HttpServerErrorException) {
            healthy.set(false);
            log.error("Chroma connection error, marking as unhealthy: {}", e.getMessage());
        }
    }

    private List<Float> floatArrayToList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name", PROVIDER_NAME);
        stats.put("enabled", enabled);
        stats.put("healthy", healthy.get());
        stats.put("initialized", initialized.get());
        stats.put("url", chromaUrl);
        stats.put("collectionName", collectionName);
        stats.put("documentCount", count());
        stats.put("successfulOperations", successfulOperations.get());
        stats.put("failedOperations", failedOperations.get());
        stats.put("lastHealthCheckTime", lastHealthCheckTime.get());
        return stats;
    }

    @Override
    public Optional<Object> getNativeClient() {
        return Optional.of(restTemplate);
    }
}
