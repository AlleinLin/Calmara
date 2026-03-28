package com.calmara.infrastructure.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Component
public class ConfigGenerator {

    @Value("${calmara.config.auto-generate:true}")
    private boolean autoGenerate;

    @Value("${calmara.config.env-file:./.env}")
    private String envFilePath;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final Properties envProperties = new Properties();

    @PostConstruct
    public void init() {
        if (!autoGenerate) {
            log.info("配置自动生成已禁用");
            return;
        }

        loadExistingEnvFile();
        generateMissingConfigs();
        saveEnvFile();
        applyToSystemProperties();
    }

    private void loadExistingEnvFile() {
        Path path = Paths.get(envFilePath);
        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                envProperties.load(is);
                log.info("加载现有配置文件: {}", envFilePath);
            } catch (IOException e) {
                log.warn("加载配置文件失败: {}", e.getMessage());
            }
        }
    }

    private void generateMissingConfigs() {
        log.info("检查并生成缺失的配置...");

        if (!envProperties.containsKey("JWT_SECRET") || needsRegenerate("JWT_SECRET")) {
            String jwtSecret = generateSecureString(64);
            envProperties.setProperty("JWT_SECRET", jwtSecret);
            log.info("✓ 生成JWT密钥");
        }

        if (!envProperties.containsKey("DB_PASSWORD")) {
            String dbPassword = generateSecureString(16);
            envProperties.setProperty("DB_PASSWORD", dbPassword);
            log.info("✓ 生成数据库密码");
        }

        if (!envProperties.containsKey("REDIS_PASSWORD")) {
            String redisPassword = generateSecureString(16);
            envProperties.setProperty("REDIS_PASSWORD", redisPassword);
            log.info("✓ 生成Redis密码");
        }

        if (!envProperties.containsKey("ADMIN_PASSWORD")) {
            String adminPassword = generateSecureString(12);
            envProperties.setProperty("ADMIN_PASSWORD", adminPassword);
            log.info("✓ 生成管理员密码");
        }

        if (!envProperties.containsKey("ENCRYPTION_KEY")) {
            String encryptionKey = generateSecureString(32);
            envProperties.setProperty("ENCRYPTION_KEY", encryptionKey);
            log.info("✓ 生成加密密钥");
        }

        if (!envProperties.containsKey("SESSION_SECRET")) {
            String sessionSecret = generateSecureString(32);
            envProperties.setProperty("SESSION_SECRET", sessionSecret);
            log.info("✓ 生成会话密钥");
        }

        setDefaultValues();
    }

    private void setDefaultValues() {
        setIfAbsent("DB_HOST", "localhost");
        setIfAbsent("DB_PORT", "3306");
        setIfAbsent("DB_NAME", "calmara");
        setIfAbsent("DB_USERNAME", "root");

        setIfAbsent("REDIS_HOST", "localhost");
        setIfAbsent("REDIS_PORT", "6379");

        setIfAbsent("CHROMA_URL", "http://localhost:8000");
        setIfAbsent("CHROMA_COLLECTION", "psychological_knowledge");
        setIfAbsent("CHROMA_ENABLED", "true");

        setIfAbsent("OLLAMA_URL", "http://localhost:11434");
        setIfAbsent("OLLAMA_MODEL", "qwen2.5:7b-chat");

        setIfAbsent("EMBEDDING_PROVIDER", "local");
        setIfAbsent("EMBEDDING_DIMENSION", "1536");

        setIfAbsent("WHISPER_URL", "http://localhost:9000/asr");
        setIfAbsent("MEDIAPIPE_URL", "http://localhost:8001/analyze");

        setIfAbsent("SERVER_PORT", "8080");
        setIfAbsent("CORS_ALLOWED_ORIGINS", "http://localhost:3000,http://localhost:8080");

        setIfAbsent("SERVICES_AUTO_START", "true");
        setIfAbsent("KNOWLEDGE_AUTO_LOAD", "true");
    }

    private void setIfAbsent(String key, String value) {
        if (!envProperties.containsKey(key)) {
            envProperties.setProperty(key, value);
        }
    }

    private boolean needsRegenerate(String key) {
        String value = envProperties.getProperty(key);
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (key.equals("JWT_SECRET")) {
            return value.contains("default") || value.contains("example") || value.length() < 32;
        }
        return false;
    }

    private String generateSecureString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private void saveEnvFile() {
        try {
            Path path = Paths.get(envFilePath);
            Files.createDirectories(path.getParent());

            StringBuilder content = new StringBuilder();
            content.append("# Calmara 自动生成的配置文件\n");
            content.append("# 生成时间: ").append(new Date()).append("\n");
            content.append("# 警告: 请勿将此文件提交到版本控制系统\n\n");

            content.append("# ========== 数据库配置 ==========\n");
            appendProperty(content, "DB_HOST");
            appendProperty(content, "DB_PORT");
            appendProperty(content, "DB_NAME");
            appendProperty(content, "DB_USERNAME");
            appendProperty(content, "DB_PASSWORD");
            content.append("\n");

            content.append("# ========== Redis配置 ==========\n");
            appendProperty(content, "REDIS_HOST");
            appendProperty(content, "REDIS_PORT");
            appendProperty(content, "REDIS_PASSWORD");
            content.append("\n");

            content.append("# ========== 安全配置 ==========\n");
            appendProperty(content, "JWT_SECRET");
            appendProperty(content, "ENCRYPTION_KEY");
            appendProperty(content, "SESSION_SECRET");
            appendProperty(content, "ADMIN_PASSWORD");
            content.append("\n");

            content.append("# ========== Chroma向量数据库 ==========\n");
            appendProperty(content, "CHROMA_URL");
            appendProperty(content, "CHROMA_COLLECTION");
            appendProperty(content, "CHROMA_ENABLED");
            content.append("\n");

            content.append("# ========== Ollama配置 ==========\n");
            appendProperty(content, "OLLAMA_URL");
            appendProperty(content, "OLLAMA_MODEL");
            content.append("\n");

            content.append("# ========== 嵌入服务配置 ==========\n");
            appendProperty(content, "EMBEDDING_PROVIDER");
            appendProperty(content, "EMBEDDING_DIMENSION");
            content.append("\n");

            content.append("# ========== 外部服务 ==========\n");
            appendProperty(content, "WHISPER_URL");
            appendProperty(content, "MEDIAPIPE_URL");
            content.append("\n");

            content.append("# ========== OpenAI配置 (可选) ==========\n");
            appendProperty(content, "OPENAI_API_KEY", "");
            content.append("\n");

            content.append("# ========== SMTP邮件配置 (可选) ==========\n");
            appendProperty(content, "SMTP_HOST", "smtp.example.com");
            appendProperty(content, "SMTP_PORT", "587");
            appendProperty(content, "SMTP_USERNAME", "");
            appendProperty(content, "SMTP_PASSWORD", "");
            content.append("\n");

            content.append("# ========== 服务配置 ==========\n");
            appendProperty(content, "SERVER_PORT");
            appendProperty(content, "CORS_ALLOWED_ORIGINS");
            appendProperty(content, "SERVICES_AUTO_START");
            appendProperty(content, "KNOWLEDGE_AUTO_LOAD");

            Files.writeString(path, content.toString(), StandardCharsets.UTF_8);
            log.info("配置文件已保存: {}", path.toAbsolutePath());

        } catch (IOException e) {
            log.error("保存配置文件失败", e);
        }
    }

    private void appendProperty(StringBuilder sb, String key) {
        appendProperty(sb, key, envProperties.getProperty(key, ""));
    }

    private void appendProperty(StringBuilder sb, String key, String value) {
        sb.append(key).append("=").append(value).append("\n");
    }

    private void applyToSystemProperties() {
        for (String key : envProperties.stringPropertyNames()) {
            String value = envProperties.getProperty(key);
            if (System.getProperty(key) == null && System.getenv(key) == null) {
                System.setProperty(key, value);
            }
        }
        log.info("配置已应用到系统属性");
    }

    public String getProperty(String key) {
        return envProperties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return envProperties.getProperty(key, defaultValue);
    }

    public void setProperty(String key, String value) {
        envProperties.setProperty(key, value);
        saveEnvFile();
        System.setProperty(key, value);
    }

    public Map<String, String> getAllProperties() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : envProperties.stringPropertyNames()) {
            result.put(key, envProperties.getProperty(key));
        }
        return result;
    }

    public ConfigInfo getConfigInfo() {
        ConfigInfo info = new ConfigInfo();
        info.setEnvFilePath(envFilePath);
        info.setAutoGenerate(autoGenerate);
        info.setConfigCount(envProperties.size());
        info.setGenerated(!Files.exists(Paths.get(envFilePath)));

        List<String> required = Arrays.asList(
                "JWT_SECRET", "DB_PASSWORD", "REDIS_PASSWORD",
                "CHROMA_URL", "OLLAMA_URL"
        );

        List<String> missing = new ArrayList<>();
        List<String> configured = new ArrayList<>();

        for (String key : required) {
            String value = envProperties.getProperty(key);
            if (value == null || value.isEmpty()) {
                missing.add(key);
            } else {
                configured.add(key);
            }
        }

        info.setMissingConfigs(missing);
        info.setConfiguredKeys(configured);
        info.setAllConfigured(missing.isEmpty());

        return info;
    }

    @Data
    public static class ConfigInfo {
        private String envFilePath;
        private boolean autoGenerate;
        private boolean generated;
        private int configCount;
        private List<String> missingConfigs;
        private List<String> configuredKeys;
        private boolean allConfigured;
    }
}
