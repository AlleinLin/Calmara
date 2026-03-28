package com.calmara.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class KnowledgeBaseLoader {

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;

    @Value("${calmara.knowledge.base-path:./knowledge-base}")
    private String knowledgeBasePath;

    @Value("${calmara.knowledge.auto-load:true}")
    private boolean autoLoad;

    @Value("${calmara.knowledge.max-documents:50000}")
    private int maxDocuments;

    @Value("${calmara.knowledge.hot-reload.enabled:true}")
    private boolean hotReloadEnabled;

    @Value("${calmara.knowledge.hot-reload.interval:300000}")
    private long hotReloadInterval;

    @Value("${calmara.knowledge.classpath-location:classpath:knowledge/}")
    private String classpathLocation;

    @Value("${calmara.knowledge.external-sources.enabled:false}")
    private boolean externalSourcesEnabled;

    private final Map<String, Long> loadedFileTimestamps = new ConcurrentHashMap<>();
    private final AtomicLong totalDocumentsLoaded = new AtomicLong(0);
    private volatile boolean initialized = false;

    public KnowledgeBaseLoader(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (!autoLoad) {
            log.info("Knowledge base auto-load is disabled");
            return;
        }

        if (initialized) {
            return;
        }

        loadKnowledgeBase();
        initialized = true;
    }

    public int loadKnowledgeBase() {
        log.info("Starting knowledge base loading from: {}", knowledgeBasePath);
        log.info("Max documents limit: {}", maxDocuments);

        int loadedCount = 0;

        loadedCount += loadFromClasspath();

        loadedCount += loadFromFileSystem();

        totalDocumentsLoaded.set(loadedCount);
        log.info("Knowledge base loading completed: {} documents loaded", loadedCount);

        return loadedCount;
    }

    private int loadFromClasspath() {
        int loadedCount = 0;

        try {
            Resource[] resources = resourceResolver.getResources(classpathLocation + "**/*.json");

            for (Resource resource : resources) {
                if (loadedCount >= maxDocuments) {
                    break;
                }

                try {
                    String content = new String(resource.getInputStream().readAllBytes());
                    int count = loadJsonContent(content, resource.getFilename(), maxDocuments - loadedCount);
                    loadedCount += count;
                    log.info("Loaded {} documents from classpath: {}", count, resource.getFilename());
                } catch (Exception e) {
                    log.warn("Failed to load classpath resource: {}", resource.getFilename(), e);
                }
            }

        } catch (IOException e) {
            log.debug("No classpath knowledge files found: {}", e.getMessage());
        }

        return loadedCount;
    }

    private int loadFromFileSystem() {
        Path knowledgePath = Paths.get(knowledgeBasePath);
        if (!Files.exists(knowledgePath)) {
            log.info("Knowledge base directory does not exist: {}", knowledgeBasePath);
            return 0;
        }

        int loadedCount = 0;

        try (Stream<Path> paths = Files.walk(knowledgePath)) {
            List<Path> jsonFiles = paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.toString().contains("external"))
                    .sorted(this::getFilePriority)
                    .collect(Collectors.toList());

            log.info("Found {} knowledge files", jsonFiles.size());

            for (Path jsonFile : jsonFiles) {
                if (loadedCount >= maxDocuments) {
                    log.info("Reached max document limit, stopping load");
                    break;
                }

                try {
                    String fileKey = jsonFile.toAbsolutePath().toString();
                    long lastModified = Files.getLastModifiedTime(jsonFile).toMillis();

                    if (loadedFileTimestamps.containsKey(fileKey) &&
                            loadedFileTimestamps.get(fileKey) >= lastModified) {
                        log.debug("File unchanged, skipping: {}", jsonFile.getFileName());
                        continue;
                    }

                    String content = Files.readString(jsonFile);
                    int count = loadJsonContent(content, jsonFile.getFileName().toString(), maxDocuments - loadedCount);
                    loadedCount += count;

                    loadedFileTimestamps.put(fileKey, lastModified);
                    log.info("Loaded {} documents from: {}", count, jsonFile.getFileName());

                } catch (Exception e) {
                    log.error("Failed to load knowledge file: {}", jsonFile, e);
                }
            }

        } catch (IOException e) {
            log.error("Failed to walk knowledge base directory", e);
        }

        return loadedCount;
    }

    private int getFilePriority(Path p1, Path p2) {
        int priority1 = calculatePriority(p1.getFileName().toString());
        int priority2 = calculatePriority(p2.getFileName().toString());
        return Integer.compare(priority1, priority2);
    }

    private int calculatePriority(String filename) {
        if (filename.contains("merged")) return 0;
        if (filename.contains("soulchat")) return 1;
        if (filename.contains("psychology")) return 2;
        if (filename.contains("default")) return 3;
        return 4;
    }

    private int loadJsonContent(String content, String filename, int remaining) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }

        int loadedCount = 0;

        try {
            KnowledgeDocument[] documents = objectMapper.readValue(content, KnowledgeDocument[].class);

            int toLoad = Math.min(documents.length, remaining);
            List<Document> batch = new ArrayList<>();

            for (int i = 0; i < toLoad; i++) {
                KnowledgeDocument doc = documents[i];
                if (doc.getTitle() != null && doc.getContent() != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    if (doc.getCategory() != null) {
                        metadata.put("category", doc.getCategory());
                    }
                    if (doc.getKeywords() != null) {
                        metadata.put("keywords", doc.getKeywords());
                    }
                    metadata.put("source", filename);

                    Document vectorDoc = new Document(doc.getTitle(), doc.getContent(), metadata);
                    batch.add(vectorDoc);
                    loadedCount++;

                    if (batch.size() >= 100) {
                        vectorStore.addDocuments(batch);
                        batch.clear();
                        log.debug("Batch loaded: {} documents", loadedCount);
                    }
                }
            }

            if (!batch.isEmpty()) {
                vectorStore.addDocuments(batch);
            }

        } catch (Exception e) {
            log.warn("Failed to parse JSON array, trying list format: {}", e.getMessage());

            try {
                List<?> dataList = objectMapper.readValue(content, List.class);
                List<Document> batch = new ArrayList<>();

                for (int i = 0; i < Math.min(dataList.size(), remaining); i++) {
                    Object item = dataList.get(i);
                    if (item instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        String title = map.get("title") != null ? map.get("title").toString() : "文档" + i;
                        String docContent = map.get("content") != null ? map.get("content").toString() : "";

                        if (!docContent.isEmpty()) {
                            Map<String, Object> metadata = new HashMap<>();
                            if (map.get("category") != null) {
                                metadata.put("category", map.get("category").toString());
                            }
                            metadata.put("source", filename);

                            Document vectorDoc = new Document(title, docContent, metadata);
                            batch.add(vectorDoc);
                            loadedCount++;

                            if (batch.size() >= 100) {
                                vectorStore.addDocuments(batch);
                                batch.clear();
                            }
                        }
                    }
                }

                if (!batch.isEmpty()) {
                    vectorStore.addDocuments(batch);
                }

            } catch (Exception ex) {
                log.error("Failed to parse file: {}", filename, ex);
            }
        }

        return loadedCount;
    }

    @Scheduled(fixedDelayString = "${calmara.knowledge.hot-reload.interval:300000}")
    public void hotReload() {
        if (!hotReloadEnabled || !initialized) {
            return;
        }

        log.debug("Checking for knowledge base updates...");

        Path knowledgePath = Paths.get(knowledgeBasePath);
        if (!Files.exists(knowledgePath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(knowledgePath)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::checkAndReloadFile);

        } catch (IOException e) {
            log.warn("Failed to check for knowledge base updates: {}", e.getMessage());
        }
    }

    private void checkAndReloadFile(Path filePath) {
        try {
            String fileKey = filePath.toAbsolutePath().toString();
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();

            Long previousTimestamp = loadedFileTimestamps.get(fileKey);
            if (previousTimestamp == null || previousTimestamp < lastModified) {
                log.info("Detected change in knowledge file: {}", filePath.getFileName());

                String content = Files.readString(filePath);
                int count = loadJsonContent(content, filePath.getFileName().toString(), maxDocuments);

                loadedFileTimestamps.put(fileKey, lastModified);
                totalDocumentsLoaded.addAndGet(count);

                log.info("Hot reloaded {} documents from: {}", count, filePath.getFileName());
            }

        } catch (Exception e) {
            log.warn("Failed to check/reload file: {}", filePath, e);
        }
    }

    public int loadCustomKnowledge(String jsonContent) {
        try {
            KnowledgeDocument[] documents = objectMapper.readValue(jsonContent, KnowledgeDocument[].class);

            List<Document> batch = new ArrayList<>();
            for (KnowledgeDocument doc : documents) {
                Map<String, Object> metadata = new HashMap<>();
                if (doc.getCategory() != null) {
                    metadata.put("category", doc.getCategory());
                }
                if (doc.getKeywords() != null) {
                    metadata.put("keywords", doc.getKeywords());
                }
                metadata.put("source", "custom");

                Document vectorDoc = new Document(doc.getTitle(), doc.getContent(), metadata);
                batch.add(vectorDoc);
            }

            vectorStore.addDocuments(batch);
            totalDocumentsLoaded.addAndGet(documents.length);

            log.info("Loaded {} custom documents", documents.length);
            return documents.length;

        } catch (Exception e) {
            log.error("Failed to load custom knowledge", e);
            return 0;
        }
    }

    public int loadFromUrl(String url, String format) {
        if (!externalSourcesEnabled) {
            log.warn("External sources are disabled");
            return 0;
        }

        log.info("Loading knowledge from external URL: {}", url);

        try {
            String content;
            if (url.startsWith("http://") || url.startsWith("https://")) {
                content = fetchFromHttpUrl(url);
            } else {
                log.error("Unsupported URL scheme: {}", url);
                return 0;
            }

            if (content == null || content.isBlank()) {
                log.warn("Empty content from URL: {}", url);
                return 0;
            }

            String normalizedFormat = format != null ? format.toLowerCase() : "json";
            int loadedCount = switch (normalizedFormat) {
                case "json" -> loadJsonContent(content, "url:" + url.hashCode(), maxDocuments);
                case "csv" -> loadCsvContent(content, "url:" + url.hashCode());
                case "txt", "text" -> loadTextContent(content, "url:" + url.hashCode());
                default -> {
                    log.warn("Unsupported format: {}, trying JSON", format);
                    yield loadJsonContent(content, "url:" + url.hashCode(), maxDocuments);
                }
            };

            totalDocumentsLoaded.addAndGet(loadedCount);
            log.info("Loaded {} documents from URL: {}", loadedCount, url);
            return loadedCount;

        } catch (Exception e) {
            log.error("Failed to load knowledge from URL: {}", url, e);
            return 0;
        }
    }

    private String fetchFromHttpUrl(String url) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) 
                    new java.net.URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("User-Agent", "Calmara-KnowledgeLoader/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.error("HTTP request failed: {} {}", responseCode, connection.getResponseMessage());
                return null;
            }

            try (java.io.InputStream is = connection.getInputStream();
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                         new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.error("Failed to fetch from URL: {}", url, e);
            return null;
        }
    }

    private int loadCsvContent(String content, String source) {
        try {
            List<Document> batch = new ArrayList<>();
            String[] lines = content.split("\n");
            String[] headers = null;

            for (int i = 0; i < lines.length && batch.size() < maxDocuments; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] fields = parseCsvLine(line);

                if (i == 0) {
                    headers = fields;
                    continue;
                }

                if (headers == null || fields.length < 2) continue;

                String title = fields[0];
                StringBuilder contentBuilder = new StringBuilder();
                for (int j = 1; j < fields.length && j < headers.length; j++) {
                    if (!fields[j].isEmpty()) {
                        contentBuilder.append(headers[j]).append(": ").append(fields[j]).append("\n");
                    }
                }

                if (!title.isEmpty() && contentBuilder.length() > 0) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", source);
                    metadata.put("format", "csv");
                    batch.add(new Document(title, contentBuilder.toString(), metadata));
                }

                if (batch.size() >= 100) {
                    vectorStore.addDocuments(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                vectorStore.addDocuments(batch);
            }

            return batch.size() + (int) Math.ceil((double) batch.size() / 100) * 100;
        } catch (Exception e) {
            log.error("Failed to parse CSV content", e);
            return 0;
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());

        return fields.toArray(new String[0]);
    }

    private int loadTextContent(String content, String source) {
        try {
            String[] paragraphs = content.split("\n\n");
            List<Document> batch = new ArrayList<>();

            for (int i = 0; i < paragraphs.length && batch.size() < maxDocuments; i++) {
                String paragraph = paragraphs[i].trim();
                if (paragraph.length() < 50) continue;

                String title = "文档片段 " + (i + 1);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", source);
                metadata.put("format", "text");
                metadata.put("index", i);

                batch.add(new Document(title, paragraph, metadata));

                if (batch.size() >= 100) {
                    vectorStore.addDocuments(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                vectorStore.addDocuments(batch);
            }

            return batch.size();
        } catch (Exception e) {
            log.error("Failed to parse text content", e);
            return 0;
        }
    }

    public KnowledgeBaseStats getStats() {
        KnowledgeBaseStats stats = new KnowledgeBaseStats();
        stats.setTotalDocuments(totalDocumentsLoaded.get());
        stats.setLoadedFiles(loadedFileTimestamps.size());
        stats.setLastReloadTime(Instant.now());
        stats.setKnowledgeBasePath(knowledgeBasePath);
        stats.setHotReloadEnabled(hotReloadEnabled);
        return stats;
    }

    public void clearCache() {
        loadedFileTimestamps.clear();
        log.info("Knowledge base file cache cleared");
    }

    @Data
    public static class KnowledgeDocument {
        private String id;
        private String title;
        private String content;
        private String category;
        private List<String> keywords;
    }

    @Data
    public static class KnowledgeBaseStats {
        private long totalDocuments;
        private int loadedFiles;
        private Instant lastReloadTime;
        private String knowledgeBasePath;
        private boolean hotReloadEnabled;
    }
}
