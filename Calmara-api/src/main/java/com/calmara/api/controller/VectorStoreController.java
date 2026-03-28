package com.calmara.api.controller;

import com.calmara.agent.rag.VectorStore;
import com.calmara.agent.rag.store.VectorStoreBackend;
import com.calmara.agent.rag.store.VectorStoreRouter;
import com.calmara.agent.rag.store.ChromaVectorStoreBackend;
import com.calmara.common.Result;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vector-store")
public class VectorStoreController {

    private final VectorStoreRouter storeRouter;
    private final VectorStore vectorStore;

    public VectorStoreController(VectorStoreRouter storeRouter, VectorStore vectorStore) {
        this.storeRouter = storeRouter;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/status")
    public Result<VectorStoreStatusResponse> getStatus() {
        VectorStoreStatusResponse status = new VectorStoreStatusResponse();
        status.setAvailable(vectorStore.isAvailable());
        status.setDocumentCount(vectorStore.getDocumentCount());

        Map<String, Object> health = vectorStore.getHealth();
        status.setHealth(health);

        return Result.success(status);
    }

    @GetMapping("/stats")
    public Result<VectorStore.VectorStoreStats> getVectorStoreStats() {
        return Result.success(vectorStore.getStats());
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> getHealth() {
        return Result.success(vectorStore.getHealth());
    }

    @GetMapping("/backends")
    public Result<List<BackendInfo>> getBackends() {
        List<BackendInfo> backends = storeRouter.getAllBackends().stream()
                .map(this::toBackendInfo)
                .toList();
        return Result.success(backends);
    }

    @GetMapping("/diagnostics")
    public Result<Map<String, Object>> getDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        diagnostics.put("vectorStore", vectorStore.getStats());
        diagnostics.put("health", vectorStore.getHealth());
        diagnostics.put("backends", storeRouter.getStats());

        return Result.success(diagnostics);
    }

    @PostMapping("/clear-cache")
    public Result<String> clearCache() {
        vectorStore.clearCache();
        return Result.success("Cache cleared successfully");
    }

    private BackendInfo toBackendInfo(VectorStoreBackend backend) {
        BackendInfo info = new BackendInfo();
        info.setName(backend.getName());
        info.setAvailable(backend.isAvailable());
        info.setHealthy(backend.isHealthy());
        info.setDocumentCount(backend.count());
        return info;
    }

    @Data
    public static class VectorStoreStatusResponse {
        private boolean available;
        private long documentCount;
        private Map<String, Object> health;
    }

    @Data
    public static class BackendInfo {
        private String name;
        private boolean available;
        private boolean healthy;
        private long documentCount;
    }
}
