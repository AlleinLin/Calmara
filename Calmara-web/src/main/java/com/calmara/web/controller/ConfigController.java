package com.calmara.web.controller;

import com.calmara.common.Result;
import com.calmara.infrastructure.config.ConfigGenerator;
import com.calmara.infrastructure.config.ConfigGenerator.ConfigInfo;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigGenerator configGenerator;

    public ConfigController(ConfigGenerator configGenerator) {
        this.configGenerator = configGenerator;
    }

    @GetMapping("/info")
    public Result<ConfigInfo> getConfigInfo() {
        return Result.success(configGenerator.getConfigInfo());
    }

    @GetMapping("/all")
    public Result<Map<String, String>> getAllConfigs() {
        Map<String, String> configs = configGenerator.getAllProperties();
        
        Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (key.contains("PASSWORD") || key.contains("SECRET") || key.contains("KEY")) {
                masked.put(key, "******");
            } else {
                masked.put(key, value);
            }
        }
        
        return Result.success(masked);
    }

    @GetMapping("/value/{key}")
    public Result<String> getConfigValue(@PathVariable String key) {
        String value = configGenerator.getProperty(key);
        if (value == null) {
            return Result.error(404, "配置项不存在: " + key);
        }

        if (key.contains("PASSWORD") || key.contains("SECRET") || key.contains("KEY")) {
            return Result.success("******");
        }

        return Result.success(value);
    }

    @PostMapping("/update")
    public Result<String> updateConfig(@RequestBody ConfigUpdateRequest request) {
        if (request.getKey() == null || request.getKey().isEmpty()) {
            return Result.error(400, "配置键不能为空");
        }

        String key = request.getKey().toUpperCase();
        
        if (!isValidConfigKey(key)) {
            return Result.error(400, "无效的配置键: " + key);
        }

        configGenerator.setProperty(key, request.getValue());
        return Result.success("配置已更新");
    }

    @PostMapping("/regenerate")
    public Result<Map<String, String>> regenerateSecrets(@RequestBody(required = false) List<String> keys) {
        Map<String, String> regenerated = new LinkedHashMap<>();

        if (keys == null || keys.isEmpty()) {
            keys = Arrays.asList("JWT_SECRET", "ENCRYPTION_KEY", "SESSION_SECRET");
        }

        for (String key : keys) {
            String upperKey = key.toUpperCase();
            if (upperKey.contains("SECRET") || upperKey.contains("KEY") || upperKey.contains("PASSWORD")) {
                int length = upperKey.contains("JWT") ? 64 : 32;
                String newValue = generateSecureString(length);
                configGenerator.setProperty(upperKey, newValue);
                regenerated.put(upperKey, "已重新生成");
            }
        }

        return Result.success(regenerated);
    }

    @GetMapping("/export")
    public Result<String> exportConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Calmara 配置导出\n");
        sb.append("# 导出时间: ").append(new Date()).append("\n\n");

        Map<String, String> configs = configGenerator.getAllProperties();
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        return Result.success(sb.toString());
    }

    private boolean isValidConfigKey(String key) {
        Set<String> validPrefixes = Set.of(
                "DB_", "REDIS_", "CHROMA_", "OLLAMA_", "OPENAI_",
                "JWT_", "SMTP_", "SERVER_", "CORS_", "WHISPER_",
                "MEDIAPIPE_", "EMBEDDING_", "SERVICES_", "KNOWLEDGE_",
                "ADMIN_", "ENCRYPTION_", "SESSION_"
        );

        for (String prefix : validPrefixes) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    private String generateSecureString(int length) {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Data
    public static class ConfigUpdateRequest {
        private String key;
        private String value;
    }
}
