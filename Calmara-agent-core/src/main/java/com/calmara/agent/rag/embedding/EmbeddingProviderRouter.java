package com.calmara.agent.rag.embedding;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class EmbeddingProviderRouter {

    private final List<EmbeddingProvider> providers;
    private final Map<String, EmbeddingProvider> providerByName;
    private final Map<String, Integer> weights;
    private final FailoverStrategy failoverStrategy;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public EmbeddingProviderRouter(List<EmbeddingProvider> providers,
                                   Map<String, Integer> weights,
                                   FailoverStrategy failoverStrategy) {
        this.providers = new ArrayList<>(providers);
        this.providerByName = new ConcurrentHashMap<>();
        this.weights = weights != null ? new ConcurrentHashMap<>(weights) : new ConcurrentHashMap<>();
        this.failoverStrategy = failoverStrategy != null ? failoverStrategy : FailoverStrategy.ROUND_ROBIN;

        for (EmbeddingProvider provider : providers) {
            providerByName.put(provider.getName(), provider);
            if (!this.weights.containsKey(provider.getName())) {
                this.weights.put(provider.getName(), 1);
            }
        }

        log.info("EmbeddingProviderRouter initialized with {} providers: {}, strategy={}",
                providers.size(),
                providers.stream().map(EmbeddingProvider::getName).collect(Collectors.joining(", ")),
                this.failoverStrategy);
    }

    public EmbeddingProvider getProvider() {
        List<EmbeddingProvider> availableProviders = providers.stream()
                .filter(EmbeddingProvider::isAvailable)
                .toList();

        if (availableProviders.isEmpty()) {
            throw new IllegalStateException("No embedding providers available");
        }

        if (availableProviders.size() == 1) {
            return availableProviders.get(0);
        }

        return switch (failoverStrategy) {
            case ROUND_ROBIN -> selectRoundRobin(availableProviders);
            case WEIGHTED_RANDOM -> selectWeightedRandom(availableProviders);
            case WEIGHTED_ROUND_ROBIN -> selectWeightedRoundRobin(availableProviders);
            case PRIMARY_FALLBACK -> selectPrimaryFallback(availableProviders);
            case LEAST_LATENCY -> selectLeastLatency(availableProviders);
        };
    }

    public EmbeddingProvider getProvider(String name) {
        EmbeddingProvider provider = providerByName.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + name);
        }
        if (!provider.isAvailable()) {
            throw new IllegalStateException("Provider " + name + " is not available");
        }
        return provider;
    }

    private EmbeddingProvider selectRoundRobin(List<EmbeddingProvider> available) {
        int index = Math.abs(roundRobinIndex.getAndIncrement() % available.size());
        return available.get(index);
    }

    private EmbeddingProvider selectWeightedRandom(List<EmbeddingProvider> available) {
        int totalWeight = available.stream()
                .mapToInt(p -> weights.getOrDefault(p.getName(), 1))
                .sum();

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (EmbeddingProvider provider : available) {
            currentWeight += weights.getOrDefault(provider.getName(), 1);
            if (random < currentWeight) {
                return provider;
            }
        }

        return available.get(0);
    }

    private EmbeddingProvider selectWeightedRoundRobin(List<EmbeddingProvider> available) {
        int totalWeight = available.stream()
                .mapToInt(p -> weights.getOrDefault(p.getName(), 1))
                .sum();

        int index = Math.abs(roundRobinIndex.getAndIncrement() % totalWeight);
        int currentWeight = 0;

        for (EmbeddingProvider provider : available) {
            currentWeight += weights.getOrDefault(provider.getName(), 1);
            if (index < currentWeight) {
                return provider;
            }
        }

        return available.get(0);
    }

    private EmbeddingProvider selectPrimaryFallback(List<EmbeddingProvider> available) {
        return available.get(0);
    }

    private EmbeddingProvider selectLeastLatency(List<EmbeddingProvider> available) {
        return available.stream()
                .min(Comparator.comparingLong(p -> {
                    EmbeddingProvider.EmbeddingProviderHealth health = p.health();
                    return health.latencyMs();
                }))
                .orElse(available.get(0));
    }

    public float[] embed(String text) {
        List<EmbeddingProvider> triedProviders = new ArrayList<>();
        Exception lastException = null;

        for (int i = 0; i < providers.size(); i++) {
            EmbeddingProvider provider = getProvider();
            if (triedProviders.contains(provider)) {
                continue;
            }

            try {
                return provider.embed(text);
            } catch (Exception e) {
                log.warn("Embedding provider {} failed: {}", provider.getName(), e.getMessage());
                triedProviders.add(provider);
                lastException = e;
            }
        }

        throw new RuntimeException("All embedding providers failed", lastException);
    }

    public List<float[]> embedBatch(List<String> texts) {
        List<EmbeddingProvider> triedProviders = new ArrayList<>();
        Exception lastException = null;

        for (int i = 0; i < providers.size(); i++) {
            EmbeddingProvider provider = getProvider();
            if (triedProviders.contains(provider)) {
                continue;
            }

            try {
                return provider.embedBatch(texts);
            } catch (Exception e) {
                log.warn("Batch embedding provider {} failed: {}", provider.getName(), e.getMessage());
                triedProviders.add(provider);
                lastException = e;
            }
        }

        throw new RuntimeException("All embedding providers failed", lastException);
    }

    public int getDimension() {
        EmbeddingProvider provider = getProvider();
        return provider.getDimension();
    }

    public boolean isAvailable() {
        return providers.stream().anyMatch(EmbeddingProvider::isAvailable);
    }

    public List<EmbeddingProvider> getAllProviders() {
        return Collections.unmodifiableList(providers);
    }

    public List<EmbeddingProvider> getAvailableProviders() {
        return providers.stream()
                .filter(EmbeddingProvider::isAvailable)
                .toList();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("strategy", failoverStrategy.name());
        stats.put("totalProviders", providers.size());
        stats.put("availableProviders", getAvailableProviders().size());

        List<Map<String, Object>> providerStats = new ArrayList<>();
        for (EmbeddingProvider provider : providers) {
            Map<String, Object> ps = new LinkedHashMap<>();
            ps.put("name", provider.getName());
            ps.put("available", provider.isAvailable());
            ps.put("dimension", provider.getDimension());
            ps.put("weight", weights.getOrDefault(provider.getName(), 1));
            ps.put("stats", provider.getStats());
            providerStats.add(ps);
        }
        stats.put("providers", providerStats);

        return stats;
    }

    public void setWeight(String providerName, int weight) {
        weights.put(providerName, weight);
        log.info("Set weight for provider {} to {}", providerName, weight);
    }

    public enum FailoverStrategy {
        ROUND_ROBIN,
        WEIGHTED_RANDOM,
        WEIGHTED_ROUND_ROBIN,
        PRIMARY_FALLBACK,
        LEAST_LATENCY
    }
}
