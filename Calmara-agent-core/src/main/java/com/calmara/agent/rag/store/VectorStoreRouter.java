package com.calmara.agent.rag.store;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class VectorStoreRouter {

    private final List<VectorStoreBackend> backends;
    private final Map<String, VectorStoreBackend> backendByName;
    private final VectorStoreBackend primaryBackend;
    private final VectorStoreBackend fallbackBackend;
    private final FailoverStrategy failoverStrategy;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public VectorStoreRouter(List<VectorStoreBackend> backends,
                             String primaryBackendName,
                             String fallbackBackendName,
                             FailoverStrategy failoverStrategy) {
        this.backends = new ArrayList<>(backends);
        this.backendByName = new ConcurrentHashMap<>();
        this.failoverStrategy = failoverStrategy != null ? failoverStrategy : FailoverStrategy.PRIMARY_FALLBACK;

        for (VectorStoreBackend backend : backends) {
            backendByName.put(backend.getName(), backend);
        }

        this.primaryBackend = primaryBackendName != null ?
                backendByName.get(primaryBackendName) :
                (backends.isEmpty() ? null : backends.get(0));

        this.fallbackBackend = fallbackBackendName != null ?
                backendByName.get(fallbackBackendName) :
                findFallbackBackend();

        log.info("VectorStoreRouter initialized with {} backends: primary={}, fallback={}, strategy={}",
                backends.size(),
                primaryBackend != null ? primaryBackend.getName() : "none",
                fallbackBackend != null ? fallbackBackend.getName() : "none",
                this.failoverStrategy);
    }

    private VectorStoreBackend findFallbackBackend() {
        return backends.stream()
                .filter(b -> b != primaryBackend)
                .filter(VectorStoreBackend::isAvailable)
                .findFirst()
                .orElse(null);
    }

    public VectorStoreBackend getBackend() {
        if (primaryBackend != null && primaryBackend.isAvailable()) {
            return primaryBackend;
        }

        if (fallbackBackend != null && fallbackBackend.isAvailable()) {
            log.warn("Primary backend unavailable, using fallback: {}", fallbackBackend.getName());
            return fallbackBackend;
        }

        List<VectorStoreBackend> available = getAvailableBackends();
        if (available.isEmpty()) {
            throw new IllegalStateException("No vector store backends available");
        }

        return switch (failoverStrategy) {
            case ROUND_ROBIN -> {
                int index = Math.abs(roundRobinIndex.getAndIncrement() % available.size());
                yield available.get(index);
            }
            case PRIMARY_FALLBACK -> available.get(0);
        };
    }

    public VectorStoreBackend getBackend(String name) {
        VectorStoreBackend backend = backendByName.get(name);
        if (backend == null) {
            throw new IllegalArgumentException("Unknown backend: " + name);
        }
        return backend;
    }

    public List<VectorStoreBackend> getAvailableBackends() {
        return backends.stream()
                .filter(VectorStoreBackend::isAvailable)
                .toList();
    }

    public List<VectorStoreBackend> getAllBackends() {
        return Collections.unmodifiableList(backends);
    }

    public void add(String id, String content, float[] embedding, Map<String, Object> metadata) {
        for (VectorStoreBackend backend : backends) {
            if (backend.isAvailable()) {
                try {
                    backend.add(id, content, embedding, metadata);
                } catch (Exception e) {
                    log.error("Failed to add document to backend {}: {}", backend.getName(), e.getMessage());
                }
            }
        }
    }

    public void addBatch(List<String> ids, List<String> contents, List<float[]> embeddings, List<Map<String, Object>> metadatas) {
        for (VectorStoreBackend backend : backends) {
            if (backend.isAvailable()) {
                try {
                    backend.addBatch(ids, contents, embeddings, metadatas);
                } catch (Exception e) {
                    log.error("Failed to batch add to backend {}: {}", backend.getName(), e.getMessage());
                }
            }
        }
    }

    public List<VectorStoreBackend.SearchResult> search(float[] queryEmbedding, int topK, double similarityThreshold, Map<String, Object> filter) {
        List<Exception> errors = new ArrayList<>();

        for (int i = 0; i < backends.size(); i++) {
            VectorStoreBackend backend = getBackend();
            if (!backend.isAvailable()) {
                continue;
            }

            try {
                List<VectorStoreBackend.SearchResult> results = backend.search(queryEmbedding, topK, similarityThreshold, filter);
                if (!results.isEmpty()) {
                    return results;
                }
            } catch (Exception e) {
                log.warn("Search failed on backend {}: {}", backend.getName(), e.getMessage());
                errors.add(e);
            }
        }

        if (!errors.isEmpty()) {
            log.error("All backends failed for search");
        }
        return List.of();
    }

    public boolean delete(String id) {
        boolean anySuccess = false;
        for (VectorStoreBackend backend : backends) {
            if (backend.isAvailable()) {
                try {
                    if (backend.delete(id)) {
                        anySuccess = true;
                    }
                } catch (Exception e) {
                    log.error("Failed to delete from backend {}: {}", backend.getName(), e.getMessage());
                }
            }
        }
        return anySuccess;
    }

    public long count() {
        VectorStoreBackend backend = getBackend();
        return backend != null ? backend.count() : 0;
    }

    public boolean isAvailable() {
        return backends.stream().anyMatch(VectorStoreBackend::isAvailable);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("strategy", failoverStrategy.name());
        stats.put("totalBackends", backends.size());
        stats.put("availableBackends", getAvailableBackends().size());
        stats.put("primaryBackend", primaryBackend != null ? primaryBackend.getName() : "none");
        stats.put("fallbackBackend", fallbackBackend != null ? fallbackBackend.getName() : "none");

        List<Map<String, Object>> backendStats = new ArrayList<>();
        for (VectorStoreBackend backend : backends) {
            backendStats.add(backend.getStats());
        }
        stats.put("backends", backendStats);

        return stats;
    }

    public void initializeAll() {
        for (VectorStoreBackend backend : backends) {
            try {
                backend.initialize();
            } catch (Exception e) {
                log.error("Failed to initialize backend {}: {}", backend.getName(), e.getMessage());
            }
        }
    }

    public void shutdownAll() {
        for (VectorStoreBackend backend : backends) {
            try {
                backend.shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down backend {}: {}", backend.getName(), e.getMessage());
            }
        }
    }

    public enum FailoverStrategy {
        PRIMARY_FALLBACK,
        ROUND_ROBIN
    }
}
