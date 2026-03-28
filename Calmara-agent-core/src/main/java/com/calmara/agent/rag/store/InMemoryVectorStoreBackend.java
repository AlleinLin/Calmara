package com.calmara.agent.rag.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public class InMemoryVectorStoreBackend implements VectorStoreBackend {

    private static final String PROVIDER_NAME = "InMemory";
    private static final int DEFAULT_MAX_SIZE = 100000;
    private static final int HNSW_M = 16;
    private static final int HNSW_EF_CONSTRUCTION = 200;
    private static final int HNSW_EF_SEARCH_DEFAULT = 50;

    private final Map<String, DocumentEntry> documentStore;
    private final HNSWIndex hnswIndex;
    private final int maxSize;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final Cache<String, float[]> embeddingCache;

    public InMemoryVectorStoreBackend(int maxSize) {
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.documentStore = new ConcurrentHashMap<>();
        this.hnswIndex = new HNSWIndex(HNSW_M, HNSW_EF_CONSTRUCTION);
        this.embeddingCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            log.info("InMemory Vector Store initialized with max size: {}", maxSize);
        }
    }

    @Override
    public void shutdown() {
        documentStore.clear();
        hnswIndex.clear();
        embeddingCache.invalidateAll();
        initialized.set(false);
        log.info("InMemory Vector Store shutdown");
    }

    @Override
    public void add(String id, String content, float[] embedding, Map<String, Object> metadata) {
        if (documentStore.size() >= maxSize) {
            log.warn("InMemory store reached max size {}, evicting oldest entries", maxSize);
            evictOldest(Math.max(1, maxSize / 10));
        }

        DocumentEntry entry = new DocumentEntry(id, content, embedding.clone(), metadata, System.currentTimeMillis());
        documentStore.put(id, entry);
        hnswIndex.add(id, embedding);
        embeddingCache.put(id, embedding);
        successfulOperations.incrementAndGet();

        log.debug("Document added to memory store: id={}", id);
    }

    @Override
    public void addBatch(List<String> ids, List<String> contents, List<float[]> embeddings, List<Map<String, Object>> metadatas) {
        if (ids.size() != contents.size() || ids.size() != embeddings.size()) {
            throw new IllegalArgumentException("ids, contents, embeddings size mismatch");
        }

        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String content = contents.get(i);
            float[] embedding = embeddings.get(i);
            Map<String, Object> metadata = i < metadatas.size() ? metadatas.get(i) : Map.of();

            if (documentStore.size() >= maxSize) {
                evictOldest(Math.max(1, maxSize / 10));
            }

            DocumentEntry entry = new DocumentEntry(id, content, embedding.clone(), metadata, timestamp);
            documentStore.put(id, entry);
            hnswIndex.add(id, embedding);
            embeddingCache.put(id, embedding);
        }

        successfulOperations.addAndGet(ids.size());
        log.info("Batch add to memory store: count={}", ids.size());
    }

    private void evictOldest(int count) {
        documentStore.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().timestamp))
                .limit(count)
                .map(Map.Entry::getKey)
                .forEach(id -> {
                    documentStore.remove(id);
                    hnswIndex.remove(id);
                    embeddingCache.invalidate(id);
                });
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK, double similarityThreshold, Map<String, Object> filter) {
        int ef = Math.max(HNSW_EF_SEARCH_DEFAULT, topK * 2);
        List<HNSWIndex.SearchResult> hnswResults = hnswIndex.search(queryEmbedding, topK * 2, ef);

        List<SearchResult> results = new ArrayList<>();
        for (HNSWIndex.SearchResult hr : hnswResults) {
            if (results.size() >= topK) {
                break;
            }

            DocumentEntry entry = documentStore.get(hr.id);
            if (entry == null) {
                continue;
            }

            if (filter != null && !filter.isEmpty() && !matchesFilter(entry.metadata, filter)) {
                continue;
            }

            double similarity = hr.similarity;
            if (similarity >= similarityThreshold) {
                results.add(SearchResult.of(hr.id, entry.content, similarity, entry.metadata));
            }
        }

        successfulOperations.incrementAndGet();
        return results;
    }

    private boolean matchesFilter(Map<String, Object> metadata, Map<String, Object> filter) {
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object value = metadata.get(entry.getKey());
            if (value == null || !value.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean delete(String id) {
        DocumentEntry removed = documentStore.remove(id);
        if (removed != null) {
            hnswIndex.remove(id);
            embeddingCache.invalidate(id);
            successfulOperations.incrementAndGet();
            return true;
        }
        failedOperations.incrementAndGet();
        return false;
    }

    @Override
    public boolean deleteBatch(List<String> ids) {
        boolean allSuccess = true;
        for (String id : ids) {
            if (!delete(id)) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    @Override
    public long count() {
        return documentStore.size();
    }

    @Override
    public boolean isAvailable() {
        return initialized.get();
    }

    @Override
    public boolean isHealthy() {
        return initialized.get();
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name", PROVIDER_NAME);
        stats.put("available", isAvailable());
        stats.put("healthy", isHealthy());
        stats.put("documentCount", documentStore.size());
        stats.put("maxSize", maxSize);
        stats.put("successfulOperations", successfulOperations.get());
        stats.put("failedOperations", failedOperations.get());
        stats.put("indexStats", hnswIndex.getStats());
        return stats;
    }

    @Override
    public Optional<Object> getNativeClient() {
        return Optional.of(documentStore);
    }

    public void clear() {
        documentStore.clear();
        hnswIndex.clear();
        embeddingCache.invalidateAll();
        log.info("InMemory Vector Store cleared");
    }

    private record DocumentEntry(
            String id,
            String content,
            float[] embedding,
            Map<String, Object> metadata,
            long timestamp
    ) {}

    private static class HNSWIndex {
        private final int m;
        private final int efConstruction;
        private final Map<String, float[]> vectors = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> adjacencyList = new ConcurrentHashMap<>();
        private final List<String> nodeOrder = new ArrayList<>();
        private final Random random = new Random(42);
        private String entryPoint = null;
        private int maxLevel = 0;

        public HNSWIndex(int m, int efConstruction) {
            this.m = m;
            this.efConstruction = efConstruction;
        }

        public synchronized void add(String id, float[] vector) {
            vectors.put(id, vector.clone());
            adjacencyList.put(id, ConcurrentHashMap.newKeySet());

            if (entryPoint == null) {
                entryPoint = id;
                maxLevel = 0;
                nodeOrder.add(id);
                return;
            }

            int nodeLevel = randomLevel();
            maxLevel = Math.max(maxLevel, nodeLevel);

            connectToNeighbors(id, vector);

            nodeOrder.add(id);

            if (entryPoint == null || nodeLevel > maxLevel) {
                entryPoint = id;
            }
        }

        private int randomLevel() {
            double r = random.nextDouble();
            int level = 0;
            while (r < 1.0 / Math.exp(level * Math.log(2.0)) && level < 16) {
                level++;
            }
            return level;
        }

        private void connectToNeighbors(String id, float[] vector) {
            List<Neighbor> neighbors = new ArrayList<>();

            for (String otherId : vectors.keySet()) {
                if (otherId.equals(id)) continue;

                float[] otherVector = vectors.get(otherId);
                double similarity = cosineSimilarity(vector, otherVector);
                neighbors.add(new Neighbor(otherId, similarity));
            }

            neighbors.sort((a, b) -> Double.compare(b.similarity, a.similarity));

            int connections = Math.min(m, neighbors.size());
            for (int i = 0; i < connections; i++) {
                String neighborId = neighbors.get(i).id;
                adjacencyList.get(id).add(neighborId);
                adjacencyList.get(neighborId).add(id);
            }
        }

        public synchronized void remove(String id) {
            vectors.remove(id);
            Set<String> neighbors = adjacencyList.remove(id);
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    Set<String> neighborConnections = adjacencyList.get(neighbor);
                    if (neighborConnections != null) {
                        neighborConnections.remove(id);
                    }
                }
            }
            nodeOrder.remove(id);

            if (entryPoint != null && entryPoint.equals(id)) {
                entryPoint = nodeOrder.isEmpty() ? null : nodeOrder.get(0);
            }
        }

        public synchronized void clear() {
            vectors.clear();
            adjacencyList.clear();
            nodeOrder.clear();
            entryPoint = null;
            maxLevel = 0;
        }

        public List<SearchResult> search(float[] query, int k, int ef) {
            List<SearchResult> results = new ArrayList<>();

            if (vectors.isEmpty() || entryPoint == null) {
                return results;
            }

            PriorityQueue<Neighbor> candidates = new PriorityQueue<>(
                    (a, b) -> Double.compare(a.similarity, b.similarity));

            PriorityQueue<Neighbor> resultsQueue = new PriorityQueue<>(
                    (a, b) -> Double.compare(b.similarity, a.similarity));

            Set<String> visited = ConcurrentHashMap.newKeySet();

            float[] entryVector = vectors.get(entryPoint);
            if (entryVector == null) {
                entryPoint = nodeOrder.isEmpty() ? null : nodeOrder.get(0);
                if (entryPoint == null) return results;
                entryVector = vectors.get(entryPoint);
                if (entryVector == null) return results;
            }

            double entrySimilarity = cosineSimilarity(query, entryVector);
            candidates.add(new Neighbor(entryPoint, entrySimilarity));
            visited.add(entryPoint);

            while (!candidates.isEmpty() && resultsQueue.size() < ef) {
                Neighbor current = candidates.poll();

                if (!resultsQueue.isEmpty() && current.similarity < resultsQueue.peek().similarity) {
                    break;
                }

                resultsQueue.add(current);

                Set<String> neighbors = adjacencyList.get(current.id);
                if (neighbors != null) {
                    for (String neighborId : neighbors) {
                        if (!visited.contains(neighborId)) {
                            visited.add(neighborId);
                            float[] neighborVector = vectors.get(neighborId);
                            if (neighborVector != null) {
                                double similarity = cosineSimilarity(query, neighborVector);
                                candidates.add(new Neighbor(neighborId, similarity));
                            }
                        }
                    }
                }
            }

            while (!resultsQueue.isEmpty() && results.size() < k) {
                Neighbor neighbor = resultsQueue.poll();
                results.add(new SearchResult(neighbor.id, neighbor.similarity));
            }

            return results;
        }

        private double cosineSimilarity(float[] a, float[] b) {
            if (a.length != b.length) {
                return 0.0;
            }

            double dotProduct = 0.0;
            double normA = 0.0;
            double normB = 0.0;

            for (int i = 0; i < a.length; i++) {
                dotProduct += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }

            if (normA == 0 || normB == 0) {
                return 0.0;
            }

            return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        }

        public Map<String, Object> getStats() {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("vectorCount", vectors.size());
            stats.put("m", m);
            stats.put("efConstruction", efConstruction);
            stats.put("maxLevel", maxLevel);
            stats.put("entryPoint", entryPoint);
            int totalConnections = adjacencyList.values().stream()
                    .mapToInt(Set::size)
                    .sum();
            stats.put("totalConnections", totalConnections);
            stats.put("avgConnections", vectors.isEmpty() ? 0 : totalConnections / vectors.size());
            return stats;
        }

        public record SearchResult(String id, double similarity) {}

        private record Neighbor(String id, double similarity) {}
    }
}
