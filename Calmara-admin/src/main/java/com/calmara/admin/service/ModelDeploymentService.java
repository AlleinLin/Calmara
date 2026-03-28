package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class ModelDeploymentService {

    @Value("${calmara.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${calmara.ollama.model-name:calmara-lora}")
    private String defaultModelName;

    @Value("${calmara.finetune.base-path:./llm}")
    private String finetuneBasePath;

    private final ObjectMapper objectMapper;
    private final WebhookNotificationService webhookService;
    private final TrainingTaskQueueService taskQueueService;
    
    private final Map<String, DeploymentRecord> deploymentHistory = new ConcurrentHashMap<>();
    private final Map<String, ModelVersion> modelVersions = new ConcurrentHashMap<>();

    public ModelDeploymentService(ObjectMapper objectMapper,
                                   WebhookNotificationService webhookService,
                                   TrainingTaskQueueService taskQueueService) {
        this.objectMapper = objectMapper;
        this.webhookService = webhookService;
        this.taskQueueService = taskQueueService;
    }

    public DeploymentResult deployModel(String trainingId, String modelPath, 
                                         FinetuneConfigEntity.ModelManagementConfig config) {
        log.info("开始部署模型: trainingId={}, modelPath={}", trainingId, modelPath);
        
        String deploymentId = UUID.randomUUID().toString();
        DeploymentRecord record = new DeploymentRecord();
        record.setDeploymentId(deploymentId);
        record.setTrainingId(trainingId);
        record.setModelPath(modelPath);
        record.setStartedAt(LocalDateTime.now());
        record.setStatus(DeploymentStatus.IN_PROGRESS);
        
        deploymentHistory.put(deploymentId, record);
        
        try {
            Path modelDir = Paths.get(modelPath);
            if (!Files.exists(modelDir)) {
                throw new DeploymentException("模型目录不存在: " + modelPath);
            }
            
            if (config.isBackupBeforeUpdate()) {
                backupCurrentModel();
            }
            
            String ggufPath = findOrCreateGgufModel(modelDir);
            if (ggufPath == null) {
                throw new DeploymentException("未找到GGUF模型文件");
            }
            
            String modelfileContent = generateModelfile(ggufPath);
            Path modelfile = createModelfile(modelfileContent, modelDir);
            
            String newModelName = generateModelName();
            boolean createSuccess = createOllamaModel(newModelName, modelfile);
            
            if (!createSuccess) {
                throw new DeploymentException("创建Ollama模型失败");
            }
            
            if (config.isKeepBestOnly()) {
                cleanupOldModels(config.getMaxCheckpoints());
            }
            
            ModelVersion version = new ModelVersion();
            version.setModelName(newModelName);
            version.setModelPath(modelPath);
            version.setDeployedAt(LocalDateTime.now());
            version.setVersion(generateVersion());
            modelVersions.put(newModelName, version);
            
            record.setStatus(DeploymentStatus.SUCCESS);
            record.setCompletedAt(LocalDateTime.now());
            record.setModelName(newModelName);
            record.setVersion(version.getVersion());
            
            log.info("模型部署成功: deploymentId={}, modelName={}", deploymentId, newModelName);
            
            webhookService.notifyModelDeployed(trainingId, newModelName, version.getVersion());
            
            return DeploymentResult.success(deploymentId, newModelName, version.getVersion());
            
        } catch (Exception e) {
            log.error("模型部署失败: {}", e.getMessage());
            
            record.setStatus(DeploymentStatus.FAILED);
            record.setCompletedAt(LocalDateTime.now());
            record.setError(e.getMessage());
            
            if (config.isBackupBeforeUpdate()) {
                rollbackModel();
            }
            
            return DeploymentResult.failure(deploymentId, e.getMessage());
        }
    }

    private String findOrCreateGgufModel(Path modelDir) throws Exception {
        Optional<Path> ggufFile = Files.walk(modelDir)
                .filter(p -> p.toString().endsWith(".gguf"))
                .findFirst();
        
        if (ggufFile.isPresent()) {
            return ggufFile.get().toString();
        }
        
        Path adapterFile = modelDir.resolve("adapter_model.safetensors");
        if (Files.exists(adapterFile)) {
            log.info("检测到LoRA适配器，需要转换为GGUF格式");
            return convertToGguf(modelDir);
        }
        
        return null;
    }

    private String convertToGguf(Path modelDir) throws Exception {
        log.info("开始转换模型为GGUF格式...");
        
        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path convertScript = baseDir.resolve("scripts/export_gguf.py");
        
        if (!Files.exists(convertScript)) {
            log.warn("转换脚本不存在，返回模型目录路径");
            return modelDir.toString();
        }
        
        ProcessBuilder pb = new ProcessBuilder(
                "python", convertScript.toString(),
                "--model-path", modelDir.toString(),
                "--output-path", modelDir.resolve("model.gguf").toString()
        );
        pb.directory(baseDir.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode == 0) {
            log.info("GGUF转换成功");
            return modelDir.resolve("model.gguf").toString();
        } else {
            log.warn("GGUF转换失败，使用原始模型路径");
            return modelDir.toString();
        }
    }

    private String generateModelfile(String modelPath) {
        return """
                FROM %s
                
                SYSTEM \"\"\"你是Calmara，一个专业的校园心理咨询智能体。你需要：
                1. 温和、专业地回应用户的心理困扰
                2. 准确识别用户的情绪状态
                3. 在发现高风险情况时立即标记
                4. 基于心理学专业知识提供建议
                \"\"\"
                
                PARAMETER temperature 0.7
                PARAMETER top_p 0.9
                PARAMETER repeat_penalty 1.1
                PARAMETER num_ctx 4096
                
                TEMPLATE \"\"\"{{ .System }}
                
                用户: {{ .Prompt }}
                助手: \"\"\"
                """.formatted(modelPath);
    }

    private Path createModelfile(String content, Path modelDir) throws IOException {
        Path modelfile = modelDir.resolve("Modelfile");
        Files.writeString(modelfile, content);
        log.info("Modelfile已创建: {}", modelfile);
        return modelfile;
    }

    private boolean createOllamaModel(String modelName, Path modelfile) {
        try {
            log.info("创建Ollama模型: {}", modelName);
            
            ProcessBuilder pb = new ProcessBuilder(
                    "ollama", "create", modelName,
                    "-f", modelfile.toString()
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Ollama] {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Ollama模型创建成功: {}", modelName);
                return true;
            } else {
                log.error("Ollama模型创建失败，退出码: {}", exitCode);
                return false;
            }
            
        } catch (Exception e) {
            log.error("创建Ollama模型异常: {}", e.getMessage());
            return false;
        }
    }

    private String generateModelName() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return String.format("%s-%s", defaultModelName, timestamp);
    }

    private String generateVersion() {
        return "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"));
    }

    private void backupCurrentModel() {
        log.info("备份当前模型...");
        
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "list");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(defaultModelName)) {
                        String backupName = defaultModelName + "-backup-" + 
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                        
                        ProcessBuilder copyPb = new ProcessBuilder(
                                "ollama", "cp", defaultModelName, backupName);
                        copyPb.start().waitFor();
                        
                        log.info("模型已备份: {}", backupName);
                        break;
                    }
                }
            }
            
            process.waitFor();
            
        } catch (Exception e) {
            log.warn("备份模型失败: {}", e.getMessage());
        }
    }

    private void rollbackModel() {
        log.info("回滚模型...");
        
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "list");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                String latestBackup = null;
                
                while ((line = reader.readLine()) != null) {
                    if (line.contains(defaultModelName + "-backup-")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length > 0) {
                            latestBackup = parts[0];
                        }
                    }
                }
                
                if (latestBackup != null) {
                    ProcessBuilder copyPb = new ProcessBuilder(
                            "ollama", "cp", latestBackup, defaultModelName);
                    copyPb.start().waitFor();
                    log.info("模型已回滚到: {}", latestBackup);
                }
            }
            
            process.waitFor();
            
        } catch (Exception e) {
            log.error("回滚模型失败: {}", e.getMessage());
        }
    }

    private void cleanupOldModels(int maxCheckpoints) {
        log.info("清理旧模型，保留最近{}个", maxCheckpoints);
        
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "list");
            Process process = pb.start();
            
            List<String> modelList = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(defaultModelName + "-")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length > 0) {
                            modelList.add(parts[0]);
                        }
                    }
                }
            }
            
            process.waitFor();
            
            if (modelList.size() > maxCheckpoints) {
                modelList.sort(Collections.reverseOrder());
                
                for (int i = maxCheckpoints; i < modelList.size(); i++) {
                    String modelToDelete = modelList.get(i);
                    log.info("删除旧模型: {}", modelToDelete);
                    
                    ProcessBuilder deletePb = new ProcessBuilder(
                            "ollama", "rm", modelToDelete);
                    deletePb.start().waitFor();
                }
            }
            
        } catch (Exception e) {
            log.warn("清理旧模型失败: {}", e.getMessage());
        }
    }

    public List<DeploymentRecord> getDeploymentHistory() {
        return new ArrayList<>(deploymentHistory.values());
    }

    public Optional<DeploymentRecord> getDeployment(String deploymentId) {
        return Optional.ofNullable(deploymentHistory.get(deploymentId));
    }

    public List<ModelVersion> getModelVersions() {
        return new ArrayList<>(modelVersions.values());
    }

    public DeploymentResult deployLatestModel() {
        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path latestModelPath = baseDir.resolve("output/latest");
        
        if (!Files.exists(latestModelPath)) {
            return DeploymentResult.failure("auto", "未找到最新模型");
        }
        
        FinetuneConfigEntity.ModelManagementConfig config = new FinetuneConfigEntity.ModelManagementConfig();
        return deployModel("auto", latestModelPath.toString(), config);
    }

    public boolean testModel(String modelName) {
        try {
            log.info("测试模型: {}", modelName);
            
            ProcessBuilder pb = new ProcessBuilder(
                    "ollama", "run", modelName, "你好");
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            
            boolean success = exitCode == 0 && output.length() > 0;
            log.info("模型测试{}: {}", success ? "成功" : "失败", modelName);
            
            return success;
            
        } catch (Exception e) {
            log.error("测试模型失败: {}", e.getMessage());
            return false;
        }
    }

    public enum DeploymentStatus {
        IN_PROGRESS, SUCCESS, FAILED
    }

    @Data
    public static class DeploymentRecord {
        private String deploymentId;
        private String trainingId;
        private String modelPath;
        private String modelName;
        private String version;
        private DeploymentStatus status;
        private String error;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class ModelVersion {
        private String modelName;
        private String modelPath;
        private String version;
        private LocalDateTime deployedAt;
    }

    @Data
    public static class DeploymentResult {
        private boolean success;
        private String deploymentId;
        private String modelName;
        private String version;
        private String error;

        public static DeploymentResult success(String deploymentId, String modelName, String version) {
            DeploymentResult result = new DeploymentResult();
            result.setSuccess(true);
            result.setDeploymentId(deploymentId);
            result.setModelName(modelName);
            result.setVersion(version);
            return result;
        }

        public static DeploymentResult failure(String deploymentId, String error) {
            DeploymentResult result = new DeploymentResult();
            result.setSuccess(false);
            result.setDeploymentId(deploymentId);
            result.setError(error);
            return result;
        }
    }

    public static class DeploymentException extends RuntimeException {
        public DeploymentException(String message) {
            super(message);
        }
    }
}
