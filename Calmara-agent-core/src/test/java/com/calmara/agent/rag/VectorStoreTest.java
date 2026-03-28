package com.calmara.agent.rag;

import com.calmara.agent.rag.embedding.*;
import com.calmara.agent.rag.store.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VectorStoreTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private VectorStore vectorStore;
    private InMemoryVectorStoreBackend memoryBackend;
    private EmbeddingProviderRouter embeddingRouter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        memoryBackend = new InMemoryVectorStoreBackend(10000);
        memoryBackend.initialize();

        VectorStoreRouter storeRouter = new VectorStoreRouter(
                List.of(memoryBackend),
                "InMemory",
                null,
                VectorStoreRouter.FailoverStrategy.PRIMARY_FALLBACK
        );

        OllamaEmbeddingProvider ollamaProvider = new OllamaEmbeddingProvider(
                restTemplate, objectMapper,
                "nomic-embed-text",
                "http://localhost:11434/api/embeddings",
                768, 10000, java.time.Duration.ofHours(1),
                meterRegistry
        );

        embeddingRouter = new EmbeddingProviderRouter(
                List.of(ollamaProvider),
                java.util.Map.of("Ollama", 1),
                EmbeddingProviderRouter.FailoverStrategy.PRIMARY_FALLBACK
        );

        vectorStore = new VectorStore(
                storeRouter,
                embeddingRouter,
                redisTemplate,
                objectMapper,
                meterRegistry
        );
    }

    @Test
    void testAddDocument() {
        Document doc = new Document("测试文档", "这是一个测试文档的内容，用于测试向量存储功能。");

        vectorStore.addDocument(doc);

        assertTrue(vectorStore.getDocumentCount() >= 1);
    }

    @Test
    void testAddDocuments() {
        List<Document> docs = List.of(
                new Document("文档1", "这是第一个测试文档的内容。"),
                new Document("文档2", "这是第二个测试文档的内容。"),
                new Document("文档3", "这是第三个测试文档的内容。")
        );

        vectorStore.addDocuments(docs);

        assertTrue(vectorStore.getDocumentCount() >= 3);
    }

    @Test
    void testGetStats() {
        VectorStore.VectorStoreStats stats = vectorStore.getStats();

        assertNotNull(stats);
        assertNotNull(stats.getEmbeddingProviders());
        assertNotNull(stats.getVectorStores());
    }

    @Test
    void testGetHealth() {
        java.util.Map<String, Object> health = vectorStore.getHealth();

        assertNotNull(health);
        assertNotNull(health.get("status"));
        assertNotNull(health.get("backends"));
    }

    @Test
    void testIsAvailable() {
        boolean available = vectorStore.isAvailable();

        assertTrue(available);
    }
}
