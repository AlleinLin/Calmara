package com.calmara.admin.controller;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.calmara.admin.service.AutoFinetuneService;
import com.calmara.admin.service.FinetuneConfigService;
import com.calmara.admin.service.RemoteServerService;
import com.calmara.common.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/finetune")
public class FinetuneController {

    @Autowired
    private AutoFinetuneService autoFinetuneService;

    @Autowired
    private FinetuneConfigService finetuneConfigService;

    @Autowired
    private RemoteServerService remoteServerService;

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        return Result.success(autoFinetuneService.getTrainingStatus());
    }

    @GetMapping("/state")
    public Result<AutoFinetuneService.FinetuneState> getState() {
        return Result.success(autoFinetuneService.loadState());
    }

    @PostMapping("/trigger")
    public Result<String> triggerFinetune(@RequestBody(required = false) Map<String, String> params) {
        if (autoFinetuneService.isTrainingInProgress()) {
            return Result.error(400, "训练已在进行中");
        }

        String reason = params != null ? params.get("reason") : "手动触发";
        autoFinetuneService.triggerFinetune(reason);
        return Result.success("微调已触发");
    }

    @PostMapping("/stop")
    public Result<String> stopFinetune() {
        autoFinetuneService.stopTraining();
        return Result.success("已发送停止信号");
    }

    @GetMapping("/config")
    public Result<FinetuneConfigEntity> getConfig() {
        return Result.success(finetuneConfigService.getConfig());
    }

    @PostMapping("/config")
    public Result<String> updateConfig(@RequestBody FinetuneConfigEntity config) {
        finetuneConfigService.saveConfig(config);
        return Result.success("配置已更新");
    }

    @PatchMapping("/config")
    public Result<String> updatePartialConfig(@RequestBody Map<String, Object> updates) {
        finetuneConfigService.updatePartialConfig(updates);
        return Result.success("配置已部分更新");
    }

    @PostMapping("/config/reset")
    public Result<String> resetConfig() {
        finetuneConfigService.resetToDefault();
        return Result.success("配置已重置为默认值");
    }

    @GetMapping("/config/trigger")
    public Result<FinetuneConfigEntity.TriggerConfig> getTriggerConfig() {
        return Result.success(finetuneConfigService.getConfig().getTrigger());
    }

    @PostMapping("/config/trigger")
    public Result<String> updateTriggerConfig(@RequestBody FinetuneConfigEntity.TriggerConfig config) {
        FinetuneConfigEntity fullConfig = finetuneConfigService.getConfig();
        fullConfig.setTrigger(config);
        finetuneConfigService.saveConfig(fullConfig);
        return Result.success("触发器配置已更新");
    }

    @GetMapping("/config/resource")
    public Result<FinetuneConfigEntity.ResourceControlConfig> getResourceConfig() {
        return Result.success(finetuneConfigService.getConfig().getResourceControl());
    }

    @PostMapping("/config/resource")
    public Result<String> updateResourceConfig(@RequestBody FinetuneConfigEntity.ResourceControlConfig config) {
        FinetuneConfigEntity fullConfig = finetuneConfigService.getConfig();
        fullConfig.setResourceControl(config);
        finetuneConfigService.saveConfig(fullConfig);
        return Result.success("资源配置已更新");
    }

    @GetMapping("/config/training")
    public Result<FinetuneConfigEntity.TrainingStrategyConfig> getTrainingConfig() {
        return Result.success(finetuneConfigService.getConfig().getTrainingStrategy());
    }

    @PostMapping("/config/training")
    public Result<String> updateTrainingConfig(@RequestBody FinetuneConfigEntity.TrainingStrategyConfig config) {
        FinetuneConfigEntity fullConfig = finetuneConfigService.getConfig();
        fullConfig.setTrainingStrategy(config);
        finetuneConfigService.saveConfig(fullConfig);
        return Result.success("训练策略配置已更新");
    }

    @GetMapping("/config/remote-server")
    public Result<FinetuneConfigEntity.RemoteServerConfig> getRemoteServerConfig() {
        return Result.success(finetuneConfigService.getConfig().getRemoteServer());
    }

    @PostMapping("/config/remote-server")
    public Result<String> updateRemoteServerConfig(@RequestBody FinetuneConfigEntity.RemoteServerConfig config) {
        FinetuneConfigEntity fullConfig = finetuneConfigService.getConfig();
        fullConfig.setRemoteServer(config);
        finetuneConfigService.saveConfig(fullConfig);
        return Result.success("远程服务器配置已更新");
    }

    @PostMapping("/remote/test-connection")
    public Result<RemoteServerService.ConnectionTestResult> testRemoteConnection(
            @RequestBody FinetuneConfigEntity.RemoteServerConfig config) {
        log.info("测试远程服务器连接: {}:{}", config.getHost(), config.getPort());
        RemoteServerService.ConnectionTestResult result = remoteServerService.testConnection(config);
        return Result.success(result);
    }

    @PostMapping("/remote/test-connection/current")
    public Result<RemoteServerService.ConnectionTestResult> testCurrentRemoteConnection() {
        FinetuneConfigEntity.RemoteServerConfig config = finetuneConfigService.getConfig().getRemoteServer();
        if (!config.isEnabled() || config.getHost() == null || config.getHost().isEmpty()) {
            return Result.error(400, "远程服务器未配置或未启用");
        }
        
        RemoteServerService.ConnectionTestResult result = remoteServerService.testConnection(config);
        return Result.success(result);
    }

    @PostMapping("/remote/execute")
    public Result<RemoteServerService.CommandResult> executeRemoteCommand(
            @RequestBody RemoteCommandRequest request) {
        FinetuneConfigEntity.RemoteServerConfig config = finetuneConfigService.getConfig().getRemoteServer();
        if (!config.isEnabled()) {
            return Result.error(400, "远程服务器未启用");
        }
        
        RemoteServerService.CommandResult result = remoteServerService.executeRemoteCommand(config, request.getCommand());
        return Result.success(result);
    }

    @PostMapping("/remote/upload")
    public Result<RemoteServerService.UploadResult> uploadToRemote(
            @RequestBody UploadRequest request) {
        FinetuneConfigEntity.RemoteServerConfig config = finetuneConfigService.getConfig().getRemoteServer();
        if (!config.isEnabled()) {
            return Result.error(400, "远程服务器未启用");
        }
        
        RemoteServerService.UploadResult result = remoteServerService.uploadFiles(
                config, request.getLocalPath(), request.getRemotePath());
        return Result.success(result);
    }

    @PostMapping("/remote/download")
    public Result<RemoteServerService.DownloadResult> downloadFromRemote(
            @RequestBody DownloadRequest request) {
        FinetuneConfigEntity.RemoteServerConfig config = finetuneConfigService.getConfig().getRemoteServer();
        if (!config.isEnabled()) {
            return Result.error(400, "远程服务器未启用");
        }
        
        RemoteServerService.DownloadResult result = remoteServerService.downloadFiles(
                config, request.getRemotePath(), request.getLocalPath());
        return Result.success(result);
    }

    @GetMapping("/config/notification")
    public Result<FinetuneConfigEntity.NotificationConfig> getNotificationConfig() {
        return Result.success(finetuneConfigService.getConfig().getNotification());
    }

    @PostMapping("/config/notification")
    public Result<String> updateNotificationConfig(@RequestBody FinetuneConfigEntity.NotificationConfig config) {
        FinetuneConfigEntity fullConfig = finetuneConfigService.getConfig();
        fullConfig.setNotification(config);
        finetuneConfigService.saveConfig(fullConfig);
        return Result.success("通知配置已更新");
    }

    @GetMapping("/config/evaluation")
    public Result<FinetuneConfigEntity.EvaluationConfig> getEvaluationConfig() {
        return Result.success(finetuneConfigService.getConfig().getEvaluation());
    }

    @PostMapping("/config/evaluation")
    public Result<String> updateEvaluationConfig(@RequestBody FinetuneConfigEntity.EvaluationConfig config) {
        FinetuneConfigEntity fullConfig = finetuneConfigService.getConfig();
        fullConfig.setEvaluation(config);
        finetuneConfigService.saveConfig(fullConfig);
        return Result.success("评估配置已更新");
    }

    @GetMapping("/config/model-management")
    public Result<FinetuneConfigEntity.ModelManagementConfig> getModelManagementConfig() {
        return Result.success(finetuneConfigService.getConfig().getModelManagement());
    }

    @PostMapping("/config/model-management")
    public Result<String> updateModelManagementConfig(@RequestBody FinetuneConfigEntity.ModelManagementConfig config) {
        FinetuneConfigEntity fullConfig = finetuneConfigService.getConfig();
        fullConfig.setModelManagement(config);
        finetuneConfigService.saveConfig(fullConfig);
        return Result.success("模型管理配置已更新");
    }

    @Data
    public static class RemoteCommandRequest {
        private String command;
    }

    @Data
    public static class UploadRequest {
        private String localPath;
        private String remotePath;
    }

    @Data
    public static class DownloadRequest {
        private String remotePath;
        private String localPath;
    }
}
