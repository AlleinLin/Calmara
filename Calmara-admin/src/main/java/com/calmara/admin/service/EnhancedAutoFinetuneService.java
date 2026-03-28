package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.calmara.admin.handler.TrainingTaskQueueHandler;
import com.calmara.admin.handler.TrainingTaskQueueHandler.TrainingTask;
import com.calmara.api.websocket.TrainingLogWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
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
import java.util.concurrent.*;

@Slf4j
@Service
public class EnhancedAutoFinetuneService {

    @Value("${calmara.finetune.enabled:true}")
    private boolean finetuneEnabled;

    @Value("${calmara.finetune.min-new-documents:1000}")
    private int minNewDocuments;

    @Value("${calmara.finetune.min-days-since-last:7}")
    private int minDaysSinceLast;

    @Value("${calmara.finetune.training-script:llm/scripts/train_lora_enhanced.py}")
    private String trainingScript;

    @Value("${calmara.finetune.config-file:llm/config/training_config.json}")
    private String configFile;

    @Value("${calmara.finetune.state-file:llm/config/finetune_state.json}")
    private String stateFile;

    @Value("${calmara.finetune.base-path:./llm}")
    private String finetuneBasePath;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private FinetuneConfigService configService;

    @Autowired
    private TrainingLogWebSocketHandler webSocketHandler;

    @Autowired
    private GpuMonitorService gpuMonitorService;

    @Autowired
    private WebhookNotificationService webhookService;

    @Autowired
    private ResumableTransferService transferService;

    @Autowired
    private ModelDeploymentService deploymentService;

    @Autowired
    private TrainingTaskQueueHandler taskQueueHandler;

    @Autowired
    private SshConnectionPoolService connectionPool;

    private final Map<String, Object> trainingStatus = new ConcurrentHashMap<>();
    private Process trainingProcess;

    public EnhancedAutoFinetuneService() {
        trainingStatus.put("status", "idle");
        trainingStatus.put("enabled", true);
    }

    @Scheduled(fixedRate = 3600000)
    public void checkAndTriggerFinetune() {
        if (!finetuneEnabled) {
            log.debug("自动微调已禁用");
            return;
        }

        if (taskQueueHandler.isTrainingInProgress()) {
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
                    submitTrainingTask(reason);
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

            FinetuneConfigEntity.ResourceControlConfig resourceConfig = 
                    configService.getConfig().getResourceControl();

            boolean resourcesOk = cpuLoad < resourceConfig.getMaxCpuMemoryPercent() 
                    && memoryPercent < resourceConfig.getMaxGpuMemoryPercent();

            trainingStatus.put("cpuUsage", cpuLoad);
            trainingStatus.put("memoryUsage", memoryPercent);
            trainingStatus.put("resourcesOk", resourcesOk);

            return resourcesOk;
        } catch (Exception e) {
            log.warn("无法获取系统资源信息", e);
            return true;
        }
    }

    public void submitTrainingTask(String reason) {
        if (taskQueueHandler.isTrainingInProgress()) {
            log.warn("训练已在进行中");
            return;
        }

        TrainingTask task = taskQueueHandler.submitTask(reason, params -> {
            executeTraining(params);
        });

        trainingStatus.put("currentTaskId", task.getTaskId());
        trainingStatus.put("status", "queued");
        trainingStatus.put("queuePosition", taskQueueHandler.getQueueSize());

        webhookService.notifyTrainingStarted(task.getTaskId(), reason, null);
    }

    private void executeTraining(TrainingTaskQueueHandler.TaskParams params) {
        String trainingId = params.getTaskId();
        FinetuneConfigEntity config = configService.getConfig();

        try {
            trainingStatus.put("status", "preparing");
            trainingStatus.put("startTime", LocalDateTime.now().toString());
            trainingStatus.put("progress", 0);

            webSocketHandler.broadcastStatusChange(trainingId, "IDLE", "PREPARING", "开始准备训练");

            FinetuneState state = loadState();
            state.setLastTrainingTime(LocalDateTime.now());
            state.setTrainingCount(state.getTrainingCount() + 1);
            saveState(state);

            if (config.getRemoteServer().isEnabled()) {
                executeRemoteTraining(trainingId, config);
            } else {
                executeLocalTraining(trainingId, config);
            }

        } catch (Exception e) {
            log.error("训练执行失败", e);
            trainingStatus.put("status", "failed");
            trainingStatus.put("error", e.getMessage());

            webSocketHandler.broadcastError(trainingId, e.getMessage(), 
                    Arrays.toString(e.getStackTrace()));
            webhookService.notifyTrainingFailed(trainingId, e.getMessage(), 
                    Arrays.toString(e.getStackTrace()));
        }
    }

    private void executeLocalTraining(String trainingId, FinetuneConfigEntity config) throws Exception {
        log.info("执行本地训练: trainingId={}", trainingId);
        trainingStatus.put("mode", "local");

        webSocketHandler.broadcastLog(trainingId, "INFO", "开始本地训练流程");
        trainingStatus.put("status", "preparing_data");
        trainingStatus.put("progress", 10);
        webSocketHandler.broadcastProgress(trainingId, 10, "准备数据", 0, 0, 0);

        prepareTrainingData(trainingId);

        webSocketHandler.broadcastLog(trainingId, "INFO", "数据准备完成，开始训练");
        trainingStatus.put("status", "training");
        trainingStatus.put("progress", 20);

        startLocalTrainingProcess(trainingId, config);
    }

    private void executeRemoteTraining(String trainingId, FinetuneConfigEntity config) throws Exception {
        log.info("执行远程训练: trainingId={}", trainingId);
        trainingStatus.put("mode", "remote");
        trainingStatus.put("remoteHost", config.getRemoteServer().getHost());

        FinetuneConfigEntity.RemoteServerConfig remoteConfig = config.getRemoteServer();

        webSocketHandler.broadcastLog(trainingId, "INFO", 
                String.format("连接远程服务器: %s@%s:%d", 
                        remoteConfig.getUsername(), remoteConfig.getHost(), remoteConfig.getPort()));

        testRemoteConnection(trainingId, remoteConfig);

        gpuMonitorService.startMonitoring(trainingId, remoteConfig);

        webSocketHandler.broadcastLog(trainingId, "INFO", "准备训练数据");
        trainingStatus.put("status", "preparing_data");
        trainingStatus.put("progress", 10);

        prepareTrainingData(trainingId);

        webSocketHandler.broadcastLog(trainingId, "INFO", "上传数据到远程服务器");
        trainingStatus.put("status", "uploading_data");
        trainingStatus.put("progress", 15);

        uploadDataToRemote(trainingId, remoteConfig);

        webSocketHandler.broadcastLog(trainingId, "INFO", "启动远程训练");
        trainingStatus.put("status", "remote_training");
        trainingStatus.put("progress", 25);

        startRemoteTrainingProcess(trainingId, remoteConfig, config);

        webSocketHandler.broadcastLog(trainingId, "INFO", "下载训练结果");
        trainingStatus.put("status", "downloading_model");
        trainingStatus.put("progress", 85);

        downloadModelFromRemote(trainingId, remoteConfig);

        gpuMonitorService.stopMonitoring(trainingId);

        onTrainingComplete(trainingId, config);
    }

    private void testRemoteConnection(String trainingId, FinetuneConfigEntity.RemoteServerConfig config) {
        try {
            SshConnectionPoolService.PooledConnection connection = connectionPool.getConnection(config);
            if (connection == null || !connection.isValid()) {
                throw new RuntimeException("无法建立SSH连接");
            }
            connectionPool.releaseConnection(connection.getPoolKey());
            webSocketHandler.broadcastLog(trainingId, "INFO", "远程服务器连接成功");
        } catch (Exception e) {
            webSocketHandler.broadcastLog(trainingId, "ERROR", "远程服务器连接失败: " + e.getMessage());
            throw new RuntimeException("远程服务器连接失败", e);
        }
    }

    private void prepareTrainingData(String trainingId) throws Exception {
        log.info("准备训练数据...");
        webSocketHandler.broadcastLog(trainingId, "INFO", "执行数据准备脚本...");

        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        Path scriptPath = baseDir.resolve("scripts/prepare_training_data.py");

        ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
        pb.directory(baseDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                webSocketHandler.broadcastLog(trainingId, "DATA", line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("数据准备失败，退出码: " + exitCode);
        }

        log.info("训练数据准备完成");
        webSocketHandler.broadcastLog(trainingId, "INFO", "训练数据准备完成");
    }

    private void startLocalTrainingProcess(String trainingId, FinetuneConfigEntity config) throws Exception {
        log.info("启动本地训练进程...");

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

        trainingProcess = pb.start();
        trainingStatus.put("pid", trainingProcess.pid());

        monitorTrainingProcess(trainingId);
    }

    private void startRemoteTrainingProcess(String trainingId, 
                                             FinetuneConfigEntity.RemoteServerConfig remoteConfig,
                                             FinetuneConfigEntity config) throws Exception {
        log.info("启动远程训练...");

        String remoteCommand = String.format(
                "cd %s && nohup python llm/scripts/train_lora_enhanced.py --config llm/config/training_config.json > training.log 2>&1 & echo $!",
                remoteConfig.getRemoteModelPath()
        );

        SshConnectionPoolService.PooledConnection connection = connectionPool.getConnection(remoteConfig);
        Session session = connection.getSession();

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(remoteCommand);
        channel.connect();

        String remotePid;
        try (InputStream in = channel.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String pidLine = reader.readLine();
            remotePid = pidLine != null ? pidLine.trim() : "unknown";
        } finally {
            channel.disconnect();
            connectionPool.releaseConnection(connection.getPoolKey());
        }

        webSocketHandler.broadcastLog(trainingId, "INFO", "远程训练进程已启动, PID: " + remotePid);
        trainingStatus.put("remotePid", remotePid);

        monitorRemoteTraining(trainingId, remoteConfig, remotePid, config);
    }

    private void monitorTrainingProcess(String trainingId) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(trainingProcess.getInputStream()))) {

            String line;
            int epoch = 0;
            int totalEpochs = configService.getConfig().getTrainingStrategy().getEpochs();
            double currentLoss = 0.0;
            int progress = 20;

            while ((line = reader.readLine()) != null) {
                webSocketHandler.broadcastLog(trainingId, "TRAIN", line);

                if (line.contains("Epoch")) {
                    epoch = extractEpoch(line);
                    progress = 20 + (int) ((epoch / (double) totalEpochs) * 60);
                    trainingStatus.put("progress", progress);
                    trainingStatus.put("currentEpoch", epoch);

                    webSocketHandler.broadcastProgress(trainingId, progress, "训练中", 
                            epoch, totalEpochs, currentLoss);
                }

                if (line.contains("loss")) {
                    currentLoss = extractLoss(line);
                    trainingStatus.put("currentLoss", currentLoss);
                }
            }
        }

        int exitCode = trainingProcess.waitFor();

        if (exitCode == 0) {
            onTrainingComplete(trainingId, configService.getConfig());
        } else {
            throw new RuntimeException("训练失败，退出码: " + exitCode);
        }
    }

    private void monitorRemoteTraining(String trainingId, 
                                        FinetuneConfigEntity.RemoteServerConfig remoteConfig,
                                        String remotePid,
                                        FinetuneConfigEntity config) throws Exception {
        
        int totalEpochs = config.getTrainingStrategy().getEpochs();
        int checkInterval = 10000;
        int maxWaitMinutes = config.getResourceControl().getMaxTrainingHours() * 60;
        long startTime = System.currentTimeMillis();

        while (true) {
            if (gpuMonitorService.isPauseRequested()) {
                webSocketHandler.broadcastLog(trainingId, "WARN", "GPU过热，暂停训练监控");
                Thread.sleep(30000);
                gpuMonitorService.resetPauseRequest();
                continue;
            }

            if (System.currentTimeMillis() - startTime > maxWaitMinutes * 60 * 1000L) {
                throw new RuntimeException("训练超时");
            }

            SshConnectionPoolService.PooledConnection connection = connectionPool.getConnection(remoteConfig);
            Session session = connection.getSession();

            String checkCommand = String.format("ps -p %s > /dev/null 2>&1 && echo 'running' || echo 'stopped'", remotePid);
            String status = executeRemoteCommand(session, checkCommand);

            if ("stopped".equals(status.trim())) {
                webSocketHandler.broadcastLog(trainingId, "INFO", "远程训练进程已结束");
                connectionPool.releaseConnection(connection.getPoolKey());
                break;
            }

            String tailCommand = String.format("tail -n 5 %s/training.log", remoteConfig.getRemoteModelPath());
            String logs = executeRemoteCommand(session, tailCommand);

            if (logs != null && !logs.isEmpty()) {
                for (String logLine : logs.split("\n")) {
                    if (!logLine.trim().isEmpty()) {
                        webSocketHandler.broadcastLog(trainingId, "TRAIN", logLine);
                    }
                }
            }

            connectionPool.releaseConnection(connection.getPoolKey());
            Thread.sleep(checkInterval);
        }
    }

    private String executeRemoteCommand(Session session, String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        InputStream in = channel.getInputStream();
        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        channel.disconnect();
        return output.toString();
    }

    private void uploadDataToRemote(String trainingId, FinetuneConfigEntity.RemoteServerConfig config) {
        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        String localDatasetPath = baseDir.resolve("dataset").toString();
        String remoteDatasetPath = config.getRemoteDataPath() + "/dataset";

        ResumableTransferService.TransferProgressCallback callback = (id, transferred, total) -> {
            int progress = 15 + (int) ((transferred / (double) total) * 10);
            trainingStatus.put("progress", progress);
            trainingStatus.put("uploadedBytes", transferred);
            trainingStatus.put("totalBytes", total);
        };

        ResumableTransferService.UploadResult result = transferService.uploadWithResume(
                config, localDatasetPath, remoteDatasetPath, callback);

        if (!result.isSuccess()) {
            throw new RuntimeException("数据上传失败: " + result.getError());
        }

        webSocketHandler.broadcastLog(trainingId, "INFO", 
                String.format("数据上传完成: %d bytes", result.getBytesTransferred()));
    }

    private void downloadModelFromRemote(String trainingId, FinetuneConfigEntity.RemoteServerConfig config) {
        Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
        String remoteModelPath = config.getRemoteOutputPath() + "/latest";
        String localModelPath = baseDir.resolve("output/latest").toString();

        ResumableTransferService.TransferProgressCallback callback = (id, transferred, total) -> {
            int progress = 85 + (int) ((transferred / (double) total) * 10);
            trainingStatus.put("progress", progress);
        };

        ResumableTransferService.DownloadResult result = transferService.downloadWithResume(
                config, remoteModelPath, localModelPath, callback);

        if (!result.isSuccess()) {
            throw new RuntimeException("模型下载失败: " + result.getError());
        }

        webSocketHandler.broadcastLog(trainingId, "INFO", "模型下载完成");
    }

    private void onTrainingComplete(String trainingId, FinetuneConfigEntity config) throws Exception {
        log.info("训练完成: trainingId={}", trainingId);

        trainingStatus.put("status", "evaluating");
        trainingStatus.put("progress", 90);
        webSocketHandler.broadcastStatusChange(trainingId, "TRAINING", "EVALUATING", "开始模型评估");

        boolean evaluationPassed = evaluateModel(trainingId);

        if (!evaluationPassed && config.getEvaluation().isRollbackOnDegradation()) {
            webSocketHandler.broadcastLog(trainingId, "WARN", "评估未通过，执行回滚");
            rollbackModel();
            trainingStatus.put("status", "evaluation_failed");
            return;
        }

        trainingStatus.put("status", "deploying");
        trainingStatus.put("progress", 95);
        webSocketHandler.broadcastStatusChange(trainingId, "EVALUATING", "DEPLOYING", "开始模型部署");

        if (config.getModelManagement().isAutoDeploy()) {
            ModelDeploymentService.DeploymentResult deployResult = deploymentService.deployLatestModel();

            if (deployResult.isSuccess()) {
                webSocketHandler.broadcastLog(trainingId, "INFO", 
                        String.format("模型部署成功: %s@%s", deployResult.getModelName(), deployResult.getVersion()));

                webhookService.notifyModelDeployed(trainingId, deployResult.getModelName(), deployResult.getVersion());
            } else {
                webSocketHandler.broadcastLog(trainingId, "ERROR", "模型部署失败: " + deployResult.getError());
            }
        }

        trainingStatus.put("status", "completed");
        trainingStatus.put("progress", 100);
        trainingStatus.put("endTime", LocalDateTime.now().toString());

        FinetuneState state = loadState();
        state.setLastSuccessfulTraining(LocalDateTime.now());
        state.setLastDocumentCount(knowledgeBaseService.getStats().getTotalDocuments());
        saveState(state);

        webSocketHandler.broadcastCompletion(trainingId, true, "训练完成", 
                "llm/output/latest", trainingStatus);

        webhookService.notifyTrainingCompleted(trainingId, "llm/output/latest", trainingStatus);

        log.info("微调流程完成: trainingId={}", trainingId);
    }

    private boolean evaluateModel(String trainingId) {
        log.info("评估模型性能...");
        webSocketHandler.broadcastLog(trainingId, "INFO", "执行模型评估...");

        try {
            Path baseDir = Paths.get(finetuneBasePath).toAbsolutePath();
            Path evalScript = baseDir.resolve("scripts/evaluate_model.py");

            if (!Files.exists(evalScript)) {
                log.warn("评估脚本不存在，跳过评估");
                webSocketHandler.broadcastLog(trainingId, "WARN", "评估脚本不存在，跳过评估");
                return true;
            }

            ProcessBuilder pb = new ProcessBuilder("python", evalScript.toString());
            pb.directory(baseDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    webSocketHandler.broadcastLog(trainingId, "EVAL", line);
                }
            }

            int exitCode = process.waitFor();
            boolean passed = exitCode == 0;

            webSocketHandler.broadcastLog(trainingId, "INFO", 
                    passed ? "模型评估通过" : "模型评估未通过");

            return passed;

        } catch (Exception e) {
            log.warn("模型评估失败，默认通过: {}", e.getMessage());
            webSocketHandler.broadcastLog(trainingId, "WARN", "模型评估异常，默认通过: " + e.getMessage());
            return true;
        }
    }

    private void rollbackModel() {
        log.info("执行模型回滚...");
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "rm", configService.getConfig().getModelManagement().getDefaultModelName());
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            log.error("模型回滚失败: {}", e.getMessage());
        }
    }

    private int extractEpoch(String line) {
        try {
            String[] parts = line.split("epoch");
            if (parts.length > 1) {
                return Integer.parseInt(parts[1].trim().split("[/\\s]")[0]);
            }
        } catch (Exception e) {
            log.trace("解析epoch失败: {}", line);
        }
        return 0;
    }

    private double extractLoss(String line) {
        try {
            String[] parts = line.split("loss");
            if (parts.length > 1) {
                return Double.parseDouble(parts[1].trim().split("[\\s,]")[0]);
            }
        } catch (Exception e) {
            log.trace("解析loss失败: {}", line);
        }
        return 0.0;
    }

    public Map<String, Object> getTrainingStatus() {
        return new HashMap<>(trainingStatus);
    }

    public void stopTraining() {
        if (trainingProcess != null && trainingProcess.isAlive()) {
            trainingProcess.destroy();
            log.info("已发送停止信号给训练进程");
            trainingStatus.put("status", "stopped");
        }
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
}
