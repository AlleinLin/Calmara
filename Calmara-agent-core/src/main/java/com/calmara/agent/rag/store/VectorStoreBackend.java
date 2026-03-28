package com.calmara.agent.rag.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VectorStoreBackend {

    String getName();

    void add(String id, String content, float[] embedding, Map<String, Object> metadata);

    void addBatch(List<String> ids, List<String> contents, List<float[]> embeddings, List<Map<String, Object>> metadatas);

    List<SearchResult> search(float[] queryEmbedding, int topK, double similarityThreshold, Map<String, Object> filter);

    boolean delete(String id);

    boolean deleteBatch(List<String> ids);

    long count();

    boolean isAvailable();

    boolean isHealthy();

    void initialize();

    void shutdown();

    Map<String, Object> getStats();

    Optional<Object> getNativeClient();

    record SearchResult(
            String id,
            String content,
            double score,
            Map<String, Object> metadata
    ) {
        public static SearchResult of(String id, String content, double score) {
            return new SearchResult(id, content, score, Map.of());
        }

        public static SearchResult of(String id, String content, double score, Map<String, Object> metadata) {
            return new SearchResult(id, content, score, metadata != null ? metadata : Map.of());
        }
    }
}
