package com.calmara.agent.rag;

import com.calmara.agent.rag.embedding.EmbeddingProviderRouter;
import com.calmara.agent.rag.store.VectorStoreBackend;
import com.calmara.agent.rag.store.VectorStoreRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VectorStore {

    private final VectorStoreRouter storeRouter;
    private final EmbeddingProviderRouter embeddingRouter;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${calmara.vector.search.top-k:5}")
    private int defaultTopK;

    @Value("${calmara.vector.search.similarity-threshold:0.3}")
    private double similarityThreshold;

    @Value("${calmara.vector.redis-cache.enabled:true}")
    private boolean redisCacheEnabled;

    @Value("${calmara.vector.redis-cache.ttl:24h}")
    private Duration redisCacheTtl;

    @Value("${calmara.vector.redis-cache.key-prefix:calmara:embedding:}")
    private String redisCacheKeyPrefix;

    private final Counter searchCounter;
    private final Counter addCounter;
    private final Timer searchTimer;
    private final Timer addTimer;

    private volatile boolean initialized = false;

    public VectorStore(VectorStoreRouter storeRouter,
                       EmbeddingProviderRouter embeddingRouter,
                       RedisTemplate<String, Object> redisTemplate,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry) {
        this.storeRouter = storeRouter;
        this.embeddingRouter = embeddingRouter;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        this.searchCounter = Counter.builder("vector_store_search_total")
                .description("Total vector store searches")
                .register(meterRegistry);

        this.addCounter = Counter.builder("vector_store_add_total")
                .description("Total documents added to vector store")
                .register(meterRegistry);

        this.searchTimer = Timer.builder("vector_store_search_duration")
                .description("Vector store search duration")
                .register(meterRegistry);

        this.addTimer = Timer.builder("vector_store_add_duration")
                .description("Vector store add duration")
                .register(meterRegistry);
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (!initialized) {
            storeRouter.initializeAll();
            initialized = true;
            log.info("VectorStore initialized with backends: {}",
                    storeRouter.getAllBackends().stream()
                            .map(VectorStoreBackend::getName)
                            .collect(Collectors.joining(", ")));
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        storeRouter.shutdownAll();
        log.info("VectorStore shutdown");
    }

    public List<Document> similaritySearch(String query, int topK) {
        searchCounter.increment();

        return searchTimer.record(() -> {
            log.debug("Vector search: query='{}', topK={}", query, topK);

            float[] queryEmbedding = getEmbeddingWithCache(query);

            List<VectorStoreBackend.SearchResult> results = storeRouter.search(
                    queryEmbedding, topK, similarityThreshold, null);

            if (results.isEmpty()) {
                log.debug("No results found, returning fallback document");
                return getFallbackDocuments();
            }

            return results.stream()
                    .map(r -> new Document(r.id(), r.content(), r.metadata()))
                    .collect(Collectors.toList());
        });
    }

    public List<Document> similaritySearch(String query) {
        return similaritySearch(query, defaultTopK);
    }

    public List<Document> similaritySearchWithFilter(String query, int topK, Map<String, Object> filter) {
        searchCounter.increment();

        return searchTimer.record(() -> {
            log.debug("Vector search with filter: query='{}', topK={}, filter={}", query, topK, filter);

            float[] queryEmbedding = getEmbeddingWithCache(query);

            List<VectorStoreBackend.SearchResult> results = storeRouter.search(
                    queryEmbedding, topK, similarityThreshold, filter);

            return results.stream()
                    .map(r -> new Document(r.id(), r.content(), r.metadata()))
                    .collect(Collectors.toList());
        });
    }

    private float[] getEmbeddingWithCache(String text) {
        if (redisCacheEnabled && redisTemplate != null) {
            try {
                String cacheKey = redisCacheKeyPrefix + text.hashCode();
                Object cached = redisTemplate.opsForValue().get(cacheKey);

                if (cached instanceof float[]) {
                    log.debug("Redis cache hit for embedding");
                    return (float[]) cached;
                }

                float[] embedding = embeddingRouter.embed(text);
                redisTemplate.opsForValue().set(cacheKey, embedding, redisCacheTtl.toSeconds(), TimeUnit.SECONDS);
                return embedding;

            } catch (Exception e) {
                log.warn("Redis cache error, falling back to direct embedding: {}", e.getMessage());
            }
        }

        return embeddingRouter.embed(text);
    }

    public void addDocument(Document document) {
        addDocument(document.getId(), document.getContent(), null);
    }

    public void addDocument(String id, String content, Map<String, Object> metadata) {
        addCounter.increment();

        addTimer.record(() -> {
            log.debug("Adding document: id={}", id);

            float[] embedding = embeddingRouter.embed(id + " " + content);

            Map<String, Object> meta = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            meta.put("title", id);
            meta.put("added_at", System.currentTimeMillis());

            storeRouter.add(id, content, embedding, meta);
        });
    }

    @Async
    public CompletableFuture<Void> addDocumentAsync(Document document) {
        addDocument(document);
        return CompletableFuture.completedFuture(null);
    }

    public void addDocuments(List<Document> documents) {
        log.info("Batch adding {} documents", documents.size());

        if (documents.size() > 10) {
            batchAddDocuments(documents);
        } else {
            documents.forEach(this::addDocument);
        }
    }

    private void batchAddDocuments(List<Document> documents) {
        List<String> ids = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();

        List<String> texts = documents.stream()
                .map(d -> d.getId() + " " + d.getContent())
                .collect(Collectors.toList());

        List<float[]> embeddingList = embeddingRouter.embedBatch(texts);

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            ids.add(doc.getId());
            contents.add(doc.getContent());
            embeddings.add(embeddingList.get(i));

            Map<String, Object> meta = doc.getMetadata() != null ?
                    new HashMap<>(doc.getMetadata()) : new HashMap<>();
            meta.put("title", doc.getId());
            meta.put("added_at", System.currentTimeMillis());
            metadatas.add(meta);
        }

        storeRouter.addBatch(ids, contents, embeddings, metadatas);
        addCounter.increment(documents.size());
        log.info("Batch add completed: {} documents", documents.size());
    }

    public boolean removeDocument(String id) {
        log.info("Removing document: id={}", id);
        return storeRouter.delete(id);
    }

    public int getDocumentCount() {
        return (int) storeRouter.count();
    }

    public boolean isAvailable() {
        return storeRouter.isAvailable();
    }

    public VectorStoreStats getStats() {
        VectorStoreStats stats = new VectorStoreStats();

        stats.setAvailable(storeRouter.isAvailable());
        stats.setDocumentCount(storeRouter.count());
        stats.setEmbeddingProviders(embeddingRouter.getStats());
        stats.setVectorStores(storeRouter.getStats());

        return stats;
    }

    public Map<String, Object> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", storeRouter.isAvailable() ? "UP" : "DOWN");
        health.put("initialized", initialized);

        List<Map<String, Object>> backendHealth = new ArrayList<>();
        for (VectorStoreBackend backend : storeRouter.getAllBackends()) {
            Map<String, Object> bh = new LinkedHashMap<>();
            bh.put("name", backend.getName());
            bh.put("available", backend.isAvailable());
            bh.put("healthy", backend.isHealthy());
            bh.put("documentCount", backend.count());
            backendHealth.add(bh);
        }
        health.put("backends", backendHealth);

        health.put("embeddingProviders", embeddingRouter.getStats());

        return health;
    }

    private List<Document> getFallbackDocuments() {
        log.info("Vector search returned no results, returning fallback document");
        return List.of(new Document("通用心理支持",
                "感谢您分享您的感受。每个人的情绪都会有起伏，这是完全正常的。\n" +
                "重要的是您愿意表达自己的感受，这本身就是一种积极的应对方式。\n" +
                "如果您感到困扰持续存在或影响日常生活，建议寻求专业心理咨询师的帮助。\n" +
                "专业的支持可以帮助您更好地理解和处理自己的情绪。"));
    }

    public void clearCache() {
        if (redisTemplate != null) {
            try {
                Set<String> keys = redisTemplate.keys(redisCacheKeyPrefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Failed to clear Redis cache: {}", e.getMessage());
            }
        }
        log.info("Vector store cache cleared");
    }

    @Data
    public static class VectorStoreStats {
        private boolean available;
        private long documentCount;
        private Map<String, Object> embeddingProviders;
        private Map<String, Object> vectorStores;
    }
}
