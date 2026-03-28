package com.calmara.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class AutoFinetuneService {

    @Value("${calmara.finetune.enabled:true}")
    private boolean finetuneEnabled;

    @Value("${calmara.finetune.min-new-documents:1000}")
    private int minNewDocuments;

    @Value("${calmara.finetune.min-days-since-last:7}")
    private int minDaysSinceLast;

    @Value("${calmara.finetune.max-cpu-percent:70}")
    private int maxCpuPercent;

    @Value("${calmara.finetune.max-memory-percent:85}")
    private int maxMemoryPercent;

    @Value("${calmara.finetune.max-training-hours:6}")
    private int maxTrainingHours;

    @Value("${calmara.finetune.training-script:llm/scripts/train_lora_enhanced.py}")
    private String trainingScript;

    @Value("${calmara.finetune.config-file:llm/config/training_config.json}")
    private String configFile;

    @Value("${calmara.finetune.state-file:llm/config/finetune_state.json}")
    private String stateFile;

    @Value("${calmara.finetune.log-file:logs/finetune.log}")
    private String logFile;

    @Value("${calmara.finetune.remote.enabled:false}")
    private boolean remoteEnabled;

    @Value("${calmara.finetune.remote.host:}")
    private String remoteHost;

    @Value("${calmara.finetune.remote.port:22}")
    private int remotePort;

    @Value("${calmara.finetune.remote.username:}")
    private String remoteUsername;

    @Value("${calmara.finetune.remote.private-key-path:}")
    private String remotePrivateKeyPath;

    @Value("${calmara.finetune.remote.remote-model-path:}")
    private String remoteModelPath;

    @Value("${calmara.finetune.base-path:./llm}")
    private String finetuneBasePath;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    private final AtomicBoolean isTraining = new AtomicBoolean(false);
    private final Map<String, Object> trainingStatus = new ConcurrentHashMap<>();
    private Process trainingProcess;
    private FinetuneConfig currentConfig;

    public AutoFinetuneService() {
        trainingStatus.put("status", "idle");
        trainingStatus.put("enabled", true);
    }

    @Scheduled(fixedRate = 3600000)
    public void checkAndTriggerFinetune() {
        if (!finetuneEnabled) {
            log.debug("自动微调已禁用");
            return;
        }

        if (isTraining.get()) {
            log.debug("训练进行中，跳过检查");
            return;
        }

        log.info("检查是否需要触发自动微调...");

        try {
            FinetuneState state = loadState();
            int currentDocCount = knowledgeBaseService.getStats().getTotalDocuments();
            int newDocs = currentDocCount - state.getLastDocumentCount();

            LocalDateTime lastTraining = state.getLastTrainingTime();
            long daysSinceLast = lastTraining != null 
                    ? ChronoUnit.DAYS.between(lastTraining, LocalDateTime.now()) 
                    : Long.MAX_VALUE;

            String reason = null;
            if (newDocs >= minNewDocuments) {
                reason = String.format("新增文档数达到阈值: %d >= %d", newDocs, minNewDocuments);
            } else if (daysSinceLast >= minDaysSinceLast && newDocs > 0) {
                reason = String.format("距上次训练已过%d天(阈值%d天)", daysSinceLast, minDaysSinceLast);
            }

            if (reason != null) {
                if (checkSystemResources()) {
                    log.info("触发自动微调: {}", reason);
                    triggerFinetune(reason);
                } else {
                    log.warn("系统资源不足，推迟微调");
                    state.setPostponedCount(state.getPostponedCount() + 1);
                    state.setLastPostponedReason("系统资源不足");
                    saveState(state);
                }
            } else {
                log.debug("无需微调: 新增文档={}, 距上次训练={}天", newDocs, daysSinceLast);
            }

        } catch (Exception e) {
            log.error("检查微调条件失败", e);
        }
    }

    public boolean checkSystemResources() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                    (com.sun.management.OperatingSystemMXBean) 
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();

            double cpuLoad = osBean.getCpuLoad() * 100;
            long totalMemory = Runtime.getRuntime().maxMemory();
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            double memoryPercent = (double) usedMemory / totalMemory * 100;

            log.debug("系统资源: CPU={:.1f}%, Memory={:.1f}%", cpuLoad, memoryPercent);

            boolean resourcesOk = cpuLoad < maxCpuPercent && memoryPercent < maxMemoryPercent;
            
            trainingStatus.put("cpuUsage", cpuLoad);
            trainingStatus.put("memoryUsage", memoryPercent);
            trainingStatus.put("resourcesOk", resourcesOk);
            
            return resourcesOk;
        } catch (Exception e) {
            log.warn("无法获取系统资源信息", e);
            return true;
        }
    }

    public synchronized void triggerFinetune(String reason) {
        if (isTraining.get()) {
            log.warn("训练已在进行中");
            return;
        }

        try {
            isTraining.set(true);
            trainingStatus.put("status", "preparing");
            trainingStatus.put("reason", reason);
            trainingStatus.put("startTime", LocalDateTime.now().toString());
            trainingStatus.put("progress", 0);

            FinetuneState state = loadState();
            state.setLastTrainingTime(LocalDateTime.now());
            state.setTrainingCount(state.getTrainingCount() + 1);
            saveState(state);

            log.info("开始自动微调流程: {}", reason);

            if (remoteEnabled && remoteHost != null && !remoteHost.isEmpty()) {
                executeRemoteTraining();
            } else {
                executeLocalTraining();
            }

        } catch (Exception e) {
            log.error("启动微调失败", e);
            trainingStatus.put("status", "failed");
            trainingStatus.put("error", e.getMessage());
            isTraining.set(false);
        }
    }

    private void executeLocalTraining() throws Exception {
        log.info("执行本地训练...");
        trainingStatus.put("mode", "local");

        prepareTrainingData();
        startTrainingProcess();
    }

    private void executeRemoteTraining() throws Exception {
        log.info("执行远程训练...");
        trainingStatus.put("mode", "remote");
        trainingStatus.put("remoteHost", remoteHost);

        prepareTrainingData();

        uploadDataToRemote();
        startRemoteTraining();
        downloadModelFromRemote();
    }

    private void prepareTrainingData() throws Exception {
        log.info("准备训练数据...");
        trainingStatus.put("status", "preparing_data");
        trainingStatus.put("progress", 10);

        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path scriptPath = baseDir.resolve("scripts/prepare_training_data.py");

        ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
        pb.directory(baseDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("数据准备失败，退出码: " + exitCode);
        }

        log.info("训练数据准备完成");
        trainingStatus.put("progress", 20);
    }

    private void startTrainingProcess() throws Exception {
        log.info("启动训练进程...");
        trainingStatus.put("status", "training");
        trainingStatus.put("progress", 30);

        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path scriptPath = baseDir.resolve(trainingScript);
        Path configPath = baseDir.resolve(configFile);

        List<String> command = new ArrayList<>();
        command.add("python");
        command.add(scriptPath.toString());
        command.add("--config");
        command.add(configPath.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(baseDir.toFile());
        pb.redirectErrorStream(true);

        File logDir = baseDir.resolve("logs").toFile();
        logDir.mkdirs();
        File logOutputFile = baseDir.resolve(logFile).toFile();
        pb.redirectOutput(logOutputFile);

        trainingProcess = pb.start();
        trainingStatus.put("pid", trainingProcess.pid());
        trainingStatus.put("logFile", logFile);

        new Thread(() -> monitorTrainingProcess()).start();
    }

    private void uploadDataToRemote() throws Exception {
        log.info("上传数据到远程服务器...");
        trainingStatus.put("status", "uploading_data");
        trainingStatus.put("progress", 25);

        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path privateKey = Paths.get(remotePrivateKeyPath);

        List<String> command = Arrays.asList(
                "scp", "-i", privateKey.toString(), "-P", String.valueOf(remotePort),
                "-r", "dataset", 
                remoteUsername + "@" + remoteHost + ":" + remoteModelPath + "/dataset"
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(baseDir.toFile());
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("上传数据失败，退出码: " + exitCode);
        }

        log.info("数据上传完成");
        trainingStatus.put("progress", 30);
    }

    private void startRemoteTraining() throws Exception {
        log.info("启动远程训练...");
        trainingStatus.put("status", "remote_training");
        trainingStatus.put("progress", 40);

        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path privateKey = Paths.get(remotePrivateKeyPath);

        String remoteCommand = String.format(
                "cd %s && python scripts/train_lora_enhanced.py --config config/training_config.json",
                remoteModelPath
        );

        List<String> command = Arrays.asList(
                "ssh", "-i", privateKey.toString(), "-p", String.valueOf(remotePort),
                remoteUsername + "@" + remoteHost,
                remoteCommand
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(baseDir.toFile());
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("远程训练失败，退出码: " + exitCode);
        }

        log.info("远程训练完成");
        trainingStatus.put("progress", 80);
    }

    private void downloadModelFromRemote() throws Exception {
        log.info("从远程服务器下载模型...");
        trainingStatus.put("status", "downloading_model");
        trainingStatus.put("progress", 85);

        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path privateKey = Paths.get(remotePrivateKeyPath);

        List<String> command = Arrays.asList(
                "scp", "-i", privateKey.toString(), "-P", String.valueOf(remotePort),
                "-r", remoteUsername + "@" + remoteHost + ":" + remoteModelPath + "/output/latest",
                "output/"
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(baseDir.toFile());
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("下载模型失败，退出码: " + exitCode);
        }

        log.info("模型下载完成");
        onTrainingComplete();
    }

    private void monitorTrainingProcess() {
        try {
            int exitCode = trainingProcess.waitFor();

            FinetuneState state = loadState();
            state.setLastDocumentCount(knowledgeBaseService.getStats().getTotalDocuments());

            if (exitCode == 0) {
                log.info("训练完成成功");
                trainingStatus.put("status", "evaluating");
                trainingStatus.put("progress", 90);
                
                boolean evaluationPassed = evaluateModel();
                
                if (evaluationPassed) {
                    onTrainingComplete();
                    state.setLastSuccessfulTraining(LocalDateTime.now());
                } else {
                    trainingStatus.put("status", "evaluation_failed");
                    trainingStatus.put("error", "模型评估未通过");
                }
            } else {
                log.error("训练失败，退出码: {}", exitCode);
                trainingStatus.put("status", "failed");
                trainingStatus.put("exitCode", exitCode);
            }

            saveState(state);

        } catch (Exception e) {
            log.error("监控训练进程失败", e);
            trainingStatus.put("status", "error");
            trainingStatus.put("error", e.getMessage());
        } finally {
            isTraining.set(false);
            trainingProcess = null;
        }
    }

    private boolean evaluateModel() {
        log.info("评估模型性能...");
        try {
            Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
            Path evalScript = baseDir.resolve("scripts/evaluate_model.py");

            if (!Files.exists(evalScript)) {
                log.warn("评估脚本不存在，跳过评估");
                return true;
            }

            ProcessBuilder pb = new ProcessBuilder("python", evalScript.toString());
            pb.directory(baseDir.toFile());
            Process process = pb.start();
            int exitCode = process.waitFor();

            return exitCode == 0;
        } catch (Exception e) {
            log.warn("模型评估失败，默认通过: {}", e.getMessage());
            return true;
        }
    }

    private void onTrainingComplete() {
        log.info("微调完成，更新状态");
        trainingStatus.put("status", "completed");
        trainingStatus.put("progress", 100);
        trainingStatus.put("endTime", LocalDateTime.now().toString());

        try {
            FinetuneState state = loadState();
            state.setLastSuccessfulTraining(LocalDateTime.now());
            saveState(state);
        } catch (Exception e) {
            log.error("更新训练状态失败", e);
        }
    }

    public Map<String, Object> getTrainingStatus() {
        return new HashMap<>(trainingStatus);
    }

    public boolean isTrainingInProgress() {
        return isTraining.get();
    }

    public void stopTraining() {
        if (trainingProcess != null && trainingProcess.isAlive()) {
            trainingProcess.destroy();
            log.info("已发送停止信号给训练进程");
            trainingStatus.put("status", "stopped");
            isTraining.set(false);
        }
    }

    public void updateConfig(FinetuneConfig config) {
        this.finetuneEnabled = config.isEnabled();
        this.minNewDocuments = config.getMinNewDocuments();
        this.minDaysSinceLast = config.getMinDaysSinceLast();
        this.maxCpuPercent = config.getMaxCpuPercent();
        this.maxMemoryPercent = config.getMaxMemoryPercent();
        this.currentConfig = config;
        
        trainingStatus.put("enabled", finetuneEnabled);
        log.info("微调配置已更新: enabled={}, minNewDocs={}, minDays={}", 
                finetuneEnabled, minNewDocuments, minDaysSinceLast);
    }

    public FinetuneConfig getCurrentConfig() {
        FinetuneConfig config = new FinetuneConfig();
        config.setEnabled(finetuneEnabled);
        config.setMinNewDocuments(minNewDocuments);
        config.setMinDaysSinceLast(minDaysSinceLast);
        config.setMaxCpuPercent(maxCpuPercent);
        config.setMaxMemoryPercent(maxMemoryPercent);
        return config;
    }

    public FinetuneState loadState() {
        Path statePath = Paths.get(finetuneBasePath, stateFile).toAbsolutePath();
        if (Files.exists(statePath)) {
            try {
                return objectMapper.readValue(statePath.toFile(), FinetuneState.class);
            } catch (Exception e) {
                log.warn("加载状态文件失败，使用默认状态", e);
            }
        }
        return new FinetuneState();
    }

    private void saveState(FinetuneState state) {
        try {
            Path statePath = Paths.get(finetuneBasePath, stateFile).toAbsolutePath();
            Files.createDirectories(statePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), state);
        } catch (Exception e) {
            log.error("保存状态文件失败", e);
        }
    }

    @Data
    public static class FinetuneState {
        private int lastDocumentCount = 0;
        private LocalDateTime lastTrainingTime = null;
        private LocalDateTime lastSuccessfulTraining = null;
        private int trainingCount = 0;
        private int postponedCount = 0;
        private String lastPostponedReason = null;
        private String lastModelError = null;
        private double lastTrainingLoss = 0.0;
    }

    @Data
    public static class FinetuneConfig {
        private boolean enabled = true;
        private int minNewDocuments = 1000;
        private int minDaysSinceLast = 7;
        private int maxCpuPercent = 70;
        private int maxMemoryPercent = 85;
        private int maxTrainingHours = 6;
        private String remoteHost = "";
        private int remotePort = 22;
        private String remoteUsername = "";
        private String remotePrivateKeyPath = "";
        private String remoteModelPath = "";
    }
}
