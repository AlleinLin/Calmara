package com.calmara.agent.rag.config;

import com.calmara.agent.rag.embedding.*;
import com.calmara.agent.rag.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Configuration
@Data
public class VectorStoreConfig {

    @Value("${calmara.embedding.provider:ollama}")
    private String defaultProvider;

    @Value("${calmara.embedding.cache.max-size:100000}")
    private int embeddingCacheMaxSize;

    @Value("${calmara.embedding.cache.expire-after-write:6h}")
    private Duration embeddingCacheExpire;

    @Value("${calmara.embedding.dimension:1024}")
    private int embeddingDimension;

    @Value("${calmara.embedding.openai.api-key:}")
    private String openaiApiKey;

    @Value("${calmara.embedding.openai.model:text-embedding-3-small}")
    private String openaiModel;

    @Value("${calmara.embedding.openai.url:https://api.openai.com/v1/embeddings}")
    private String openaiUrl;

    @Value("${calmara.embedding.ollama.url:http://localhost:11434/api/embeddings}")
    private String ollamaUrl;

    @Value("${calmara.embedding.ollama.model:bge-m3}")
    private String ollamaModel;

    @Value("${calmara.embedding.qwen.api-key:}")
    private String qwenApiKey;

    @Value("${calmara.embedding.qwen.model:text-embedding-v3}")
    private String qwenModel;

    @Value("${calmara.embedding.qwen.url:https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding}")
    private String qwenUrl;

    @Value("${calmara.embedding.bge-m3.url:http://localhost:33330/embed}")
    private String bgeM3Url;

    @Value("${calmara.embedding.bge-m3.dense:true}")
    private boolean bgeM3DenseEnabled;

    @Value("${calmara.embedding.bge-m3.sparse:false}")
    private boolean bgeM3SparseEnabled;

    @Value("${calmara.embedding.bge-m3.colbert:false}")
    private boolean bgeM3ColbertEnabled;

    @Value("${calmara.embedding.router.strategy:PRIMARY_FALLBACK}")
    private String routerStrategy;

    @Value("${calmara.vector.store.primary:chroma}")
    private String primaryVectorStore;

    @Value("${calmara.vector.store.fallback:inmemory}")
    private String fallbackVectorStore;

    @Value("${calmara.vector.store.strategy:PRIMARY_FALLBACK}")
    private String vectorStoreStrategy;

    @Value("${calmara.chroma.url:http://localhost:8000}")
    private String chromaUrl;

    @Value("${calmara.chroma.collection-name:psychological_knowledge}")
    private String chromaCollectionName;

    @Value("${calmara.chroma.enabled:true}")
    private boolean chromaEnabled;

    @Value("${calmara.chroma.connect-timeout:5000}")
    private int chromaConnectTimeout;

    @Value("${calmara.chroma.read-timeout:30000}")
    private int chromaReadTimeout;

    @Value("${calmara.chroma.retry-count:3}")
    private int chromaRetryCount;

    @Value("${calmara.chroma.retry-delay:1000}")
    private long chromaRetryDelay;

    @Value("${calmara.milvus.host:localhost}")
    private String milvusHost;

    @Value("${calmara.milvus.port:19530}")
    private int milvusPort;

    @Value("${calmara.milvus.database:default}")
    private String milvusDatabase;

    @Value("${calmara.milvus.collection-name:psychological_knowledge}")
    private String milvusCollectionName;

    @Value("${calmara.milvus.index-type:IVF_FLAT}")
    private String milvusIndexType;

    @Value("${calmara.milvus.metric-type:COSINE}")
    private String milvusMetricType;

    @Value("${calmara.milvus.enabled:false}")
    private boolean milvusEnabled;

    @Value("${calmara.vector.memory.max-size:100000}")
    private int inMemoryMaxSize;

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnProperty(prefix = "calmara.embedding.openai", name = "api-key")
    public OpenAIEmbeddingProvider openAIEmbeddingProvider(RestTemplate restTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return new OpenAIEmbeddingProvider(
                restTemplate, objectMapper,
                openaiApiKey, openaiModel, openaiUrl, embeddingDimension,
                embeddingCacheMaxSize, embeddingCacheExpire, meterRegistry
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "calmara.embedding.ollama", name = "url")
    public OllamaEmbeddingProvider ollamaEmbeddingProvider(RestTemplate restTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return new OllamaEmbeddingProvider(
                restTemplate, objectMapper,
                ollamaModel, ollamaUrl, embeddingDimension,
                embeddingCacheMaxSize, embeddingCacheExpire, meterRegistry
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "calmara.embedding.qwen", name = "api-key")
    public QwenEmbeddingProvider qwenEmbeddingProvider(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        return new QwenEmbeddingProvider(
                restTemplate, qwenApiKey, qwenModel, qwenUrl, embeddingDimension,
                embeddingCacheMaxSize, embeddingCacheExpire, meterRegistry
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "calmara.embedding.bge-m3", name = "url")
    public BgeM3EmbeddingProvider bgeM3EmbeddingProvider(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        return new BgeM3EmbeddingProvider(
                restTemplate, bgeM3Url, embeddingDimension,
                bgeM3DenseEnabled, bgeM3SparseEnabled, bgeM3ColbertEnabled,
                embeddingCacheMaxSize, embeddingCacheExpire, meterRegistry
        );
    }

    @Bean
    @Primary
    public EmbeddingProviderRouter embeddingProviderRouter(
            Optional<OpenAIEmbeddingProvider> openAIProvider,
            Optional<OllamaEmbeddingProvider> ollamaProvider,
            Optional<QwenEmbeddingProvider> qwenProvider,
            Optional<BgeM3EmbeddingProvider> bgeM3Provider) {

        List<EmbeddingProvider> providers = new ArrayList<>();
        Map<String, Integer> weights = new HashMap<>();

        openAIProvider.ifPresent(p -> {
            providers.add(p);
            weights.put(p.getName(), 10);
        });

        ollamaProvider.ifPresent(p -> {
            providers.add(p);
            weights.put(p.getName(), 8);
        });

        qwenProvider.ifPresent(p -> {
            providers.add(p);
            weights.put(p.getName(), 9);
        });

        bgeM3Provider.ifPresent(p -> {
            providers.add(p);
            weights.put(p.getName(), 10);
        });

        if (providers.isEmpty()) {
            throw new IllegalStateException("At least one embedding provider must be configured");
        }

        EmbeddingProviderRouter.FailoverStrategy strategy =
                EmbeddingProviderRouter.FailoverStrategy.valueOf(routerStrategy.toUpperCase());

        return new EmbeddingProviderRouter(providers, weights, strategy);
    }

    @Bean
    @ConditionalOnProperty(prefix = "calmara.chroma", name = "enabled", havingValue = "true")
    public ChromaVectorStoreBackend chromaVectorStoreBackend(RestTemplate restTemplate, ObjectMapper objectMapper) {
        return new ChromaVectorStoreBackend(
                restTemplate, objectMapper,
                chromaUrl, chromaCollectionName, chromaEnabled,
                chromaConnectTimeout, chromaReadTimeout,
                chromaRetryCount, chromaRetryDelay
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "calmara.milvus", name = "enabled", havingValue = "true")
    public MilvusVectorStoreBackend milvusVectorStoreBackend() {
        return new MilvusVectorStoreBackend(
                milvusHost, milvusPort, milvusDatabase, milvusCollectionName,
                embeddingDimension, milvusIndexType, milvusMetricType, milvusEnabled
        );
    }

    @Bean
    public InMemoryVectorStoreBackend inMemoryVectorStoreBackend() {
        return new InMemoryVectorStoreBackend(inMemoryMaxSize);
    }

    @Bean
    @Primary
    public VectorStoreRouter vectorStoreRouter(
            Optional<ChromaVectorStoreBackend> chromaBackend,
            Optional<MilvusVectorStoreBackend> milvusBackend,
            InMemoryVectorStoreBackend inMemoryBackend) {

        List<VectorStoreBackend> backends = new ArrayList<>();

        chromaBackend.ifPresent(backends::add);
        milvusBackend.ifPresent(backends::add);
        backends.add(inMemoryBackend);

        VectorStoreRouter.FailoverStrategy strategy =
                VectorStoreRouter.FailoverStrategy.valueOf(vectorStoreStrategy.toUpperCase());

        return new VectorStoreRouter(backends, primaryVectorStore, fallbackVectorStore, strategy);
    }
}
