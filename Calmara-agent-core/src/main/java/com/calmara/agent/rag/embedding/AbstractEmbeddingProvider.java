package com.calmara.agent.rag.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public abstract class AbstractEmbeddingProvider implements EmbeddingProvider {

    protected final Cache<String, float[]> embeddingCache;
    protected final MeterRegistry meterRegistry;
    protected final AtomicLong totalRequests = new AtomicLong(0);
    protected final AtomicLong successfulRequests = new AtomicLong(0);
    protected final AtomicLong failedRequests = new AtomicLong(0);
    protected final AtomicLong cacheHits = new AtomicLong(0);
    protected final AtomicLong cacheMisses = new AtomicLong(0);

    private Counter requestCounter;
    private Counter successCounter;
    private Counter failureCounter;
    private Timer latencyTimer;

    protected AbstractEmbeddingProvider(int cacheMaxSize, Duration cacheExpireAfterWrite, MeterRegistry meterRegistry) {
        this.embeddingCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(cacheExpireAfterWrite)
                .recordStats()
                .build();
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    private void initMetrics() {
        if (meterRegistry != null) {
            String providerName = getName().toLowerCase().replace("-", "_");
            this.requestCounter = Counter.builder("embedding_requests_total")
                    .tag("provider", providerName)
                    .description("Total embedding requests")
                    .register(meterRegistry);
            this.successCounter = Counter.builder("embedding_requests_success")
                    .tag("provider", providerName)
                    .description("Successful embedding requests")
                    .register(meterRegistry);
            this.failureCounter = Counter.builder("embedding_requests_failed")
                    .tag("provider", providerName)
                    .description("Failed embedding requests")
                    .register(meterRegistry);
            this.latencyTimer = Timer.builder("embedding_latency")
                    .tag("provider", providerName)
                    .description("Embedding request latency")
                    .register(meterRegistry);
        }
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[getDimension()];
        }

        totalRequests.incrementAndGet();
        if (requestCounter != null) {
            requestCounter.increment();
        }

        String cacheKey = generateCacheKey(text);
        float[] cached = embeddingCache.getIfPresent(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            log.debug("Cache hit for text hash: {}", cacheKey.substring(0, 8));
            return cached;
        }
        cacheMisses.incrementAndGet();

        long startTime = System.currentTimeMillis();
        try {
            float[] embedding = doEmbed(text);
            embeddingCache.put(cacheKey, embedding);
            successfulRequests.incrementAndGet();
            if (successCounter != null) {
                successCounter.increment();
            }
            if (latencyTimer != null) {
                latencyTimer.record(Duration.ofMillis(System.currentTimeMillis() - startTime));
            }
            return embedding;
        } catch (Exception e) {
            failedRequests.incrementAndGet();
            if (failureCounter != null) {
                failureCounter.increment();
            }
            log.error("Embedding failed for provider {}: {}", getName(), e.getMessage());
            throw e;
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        totalRequests.addAndGet(texts.size());
        if (requestCounter != null) {
            requestCounter.increment(texts.size());
        }

        long startTime = System.currentTimeMillis();
        try {
            List<float[]> results = doEmbedBatch(texts);

            for (int i = 0; i < texts.size(); i++) {
                String cacheKey = generateCacheKey(texts.get(i));
                if (i < results.size()) {
                    embeddingCache.put(cacheKey, results.get(i));
                }
            }

            successfulRequests.addAndGet(texts.size());
            if (successCounter != null) {
                successCounter.increment(texts.size());
            }
            if (latencyTimer != null) {
                latencyTimer.record(Duration.ofMillis(System.currentTimeMillis() - startTime));
            }
            return results;
        } catch (Exception e) {
            failedRequests.addAndGet(texts.size());
            if (failureCounter != null) {
                failureCounter.increment(texts.size());
            }
            log.error("Batch embedding failed for provider {}: {}", getName(), e.getMessage());
            throw e;
        }
    }

    protected abstract float[] doEmbed(String text);

    protected List<float[]> doEmbedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(doEmbed(text));
        }
        return results;
    }

    protected String generateCacheKey(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    @Override
    public Map<String, Object> getStats() {
        var cacheStats = embeddingCache.stats();
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("provider", getName());
        stats.put("dimension", getDimension());
        stats.put("available", isAvailable());
        stats.put("totalRequests", totalRequests.get());
        stats.put("successfulRequests", successfulRequests.get());
        stats.put("failedRequests", failedRequests.get());
        stats.put("cacheHits", cacheHits.get());
        stats.put("cacheMisses", cacheMisses.get());
        stats.put("cacheHitRate", cacheStats.hitRate());
        stats.put("cacheSize", embeddingCache.estimatedSize());
        stats.put("cacheEvictions", cacheStats.evictionCount());
        return stats;
    }

    public void clearCache() {
        embeddingCache.invalidateAll();
        log.info("Embedding cache cleared for provider: {}", getName());
    }

    public long getCacheSize() {
        return embeddingCache.estimatedSize();
    }
}
