package com.calmara.agent.rag.embedding;

import java.util.List;
import java.util.Map;

public interface EmbeddingProvider {

    String getName();

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);

    int getDimension();

    boolean isAvailable();

    EmbeddingProviderHealth health();

    Map<String, Object> getStats();

    record EmbeddingProviderHealth(
            boolean healthy,
            String status,
            String message,
            long latencyMs,
            Map<String, Object> details
    ) {
        public static EmbeddingProviderHealth up() {
            return new EmbeddingProviderHealth(true, "UP", "Provider is healthy", 0, Map.of());
        }

        public static EmbeddingProviderHealth up(long latencyMs) {
            return new EmbeddingProviderHealth(true, "UP", "Provider is healthy", latencyMs, Map.of());
        }

        public static EmbeddingProviderHealth down(String message) {
            return new EmbeddingProviderHealth(false, "DOWN", message, 0, Map.of());
        }

        public static EmbeddingProviderHealth down(String message, Throwable cause) {
            return new EmbeddingProviderHealth(false, "DOWN", message + ": " + cause.getMessage(), 0, Map.of());
        }

        public static EmbeddingProviderHealth degraded(String message) {
            return new EmbeddingProviderHealth(true, "DEGRADED", message, 0, Map.of());
        }
    }
}
