package com.calmara.infrastructure.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class PathConfig {

    @Value("${calmara.base-path:./}")
    private String basePath;

    @Value("${calmara.data-path:./data}")
    private String dataPath;

    @Value("${calmara.logs-path:./logs}")
    private String logsPath;

    @Value("${calmara.temp-path:./temp}")
    private String tempPath;

    @Value("${calmara.models-path:./models}")
    private String modelsPath;

    @Value("${calmara.knowledge-path:./knowledge-base}")
    private String knowledgePath;

    @Value("${calmara.finetune.base-path:./llm}")
    private String finetuneBasePath;

    @Value("${calmara.finetune.config-path:./llm/config}")
    private String finetuneConfigPath;

    @Value("${calmara.finetune.output-path:./llm/output}")
    private String finetuneOutputPath;

    @Value("${calmara.finetune.scripts-path:./llm/scripts}")
    private String finetuneScriptsPath;

    @Value("${calmara.reports-path:./data/reports}")
    private String reportsPath;

    @Value("${calmara.exports-path:./data/exports}")
    private String exportsPath;

    private Path basePathResolved;
    private Path dataPathResolved;
    private Path logsPathResolved;
    private Path tempPathResolved;

    @PostConstruct
    public void init() {
        basePathResolved = resolveAbsolutePath(basePath);
        dataPathResolved = resolveAbsolutePath(dataPath);
        logsPathResolved = resolveAbsolutePath(logsPath);
        tempPathResolved = resolveAbsolutePath(tempPath);

        createDirectories();
        log.info("路径配置初始化完成: basePath={}", basePathResolved.toAbsolutePath());
    }

    private Path resolveAbsolutePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path);
    }

    private void createDirectories() {
        try {
            Files.createDirectories(dataPathResolved);
            Files.createDirectories(logsPathResolved);
            Files.createDirectories(tempPathResolved);
            Files.createDirectories(getModelsPath());
            Files.createDirectories(getKnowledgePath());
            Files.createDirectories(getFinetuneBasePath());
            Files.createDirectories(getFinetuneConfigPath());
            Files.createDirectories(getFinetuneOutputPath());
            Files.createDirectories(getFinetuneScriptsPath());
            Files.createDirectories(getReportsPath());
            Files.createDirectories(getExportsPath());
        } catch (Exception e) {
            log.warn("创建目录失败: {}", e.getMessage());
        }
    }

    public Path getBasePath() {
        return basePathResolved;
    }

    public Path getDataPath() {
        return dataPathResolved;
    }

    public Path getLogsPath() {
        return logsPathResolved;
    }

    public Path getTempPath() {
        return tempPathResolved;
    }

    public Path getModelsPath() {
        return resolveAbsolutePath(modelsPath);
    }

    public Path getKnowledgePath() {
        return resolveAbsolutePath(knowledgePath);
    }

    public Path getFinetuneBasePath() {
        return resolveAbsolutePath(finetuneBasePath);
    }

    public Path getFinetuneConfigPath() {
        return resolveAbsolutePath(finetuneConfigPath);
    }

    public Path getFinetuneOutputPath() {
        return resolveAbsolutePath(finetuneOutputPath);
    }

    public Path getFinetuneScriptsPath() {
        return resolveAbsolutePath(finetuneScriptsPath);
    }

    public Path getReportsPath() {
        return resolveAbsolutePath(reportsPath);
    }

    public Path getExportsPath() {
        return resolveAbsolutePath(exportsPath);
    }

    public Path getConfigFile(String filename) {
        return getFinetuneConfigPath().resolve(filename);
    }

    public Path getStateFile(String filename) {
        return getFinetuneConfigPath().resolve(filename);
    }

    public Path getTrainingScript(String scriptName) {
        return getFinetuneScriptsPath().resolve(scriptName);
    }

    public Path getOutputModelPath(String modelName) {
        return getFinetuneOutputPath().resolve(modelName);
    }

    public Path resolve(String relativePath) {
        return basePathResolved.resolve(relativePath);
    }

    public Path resolveData(String relativePath) {
        return dataPathResolved.resolve(relativePath);
    }

    public Path resolveTemp(String relativePath) {
        return tempPathResolved.resolve(relativePath);
    }

    public String getBasePathString() {
        return basePathResolved.toString();
    }

    public PathInfo getPathInfo() {
        PathInfo info = new PathInfo();
        info.setBasePath(basePathResolved.toAbsolutePath().toString());
        info.setDataPath(dataPathResolved.toAbsolutePath().toString());
        info.setLogsPath(logsPathResolved.toAbsolutePath().toString());
        info.setTempPath(tempPathResolved.toAbsolutePath().toString());
        info.setModelsPath(getModelsPath().toAbsolutePath().toString());
        info.setKnowledgePath(getKnowledgePath().toAbsolutePath().toString());
        info.setFinetuneBasePath(getFinetuneBasePath().toAbsolutePath().toString());
        info.setReportsPath(getReportsPath().toAbsolutePath().toString());
        return info;
    }

    @Data
    public static class PathInfo {
        private String basePath;
        private String dataPath;
        private String logsPath;
        private String tempPath;
        private String modelsPath;
        private String knowledgePath;
        private String finetuneBasePath;
        private String reportsPath;
    }
}
