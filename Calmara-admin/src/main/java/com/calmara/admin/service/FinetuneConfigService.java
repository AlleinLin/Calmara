package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class FinetuneConfigService {

    @Value("${calmara.finetune.config-file:llm/config/auto_finetune_config.json}")
    private String configFilePath;

    @Value("${calmara.finetune.base-path:./llm}")
    private String finetuneBasePath;

    private final ObjectMapper objectMapper;
    private FinetuneConfigEntity cachedConfig;

    public FinetuneConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FinetuneConfigEntity getConfig() {
        if (cachedConfig != null) {
            return cachedConfig;
        }
        return loadConfig();
    }

    public FinetuneConfigEntity loadConfig() {
        Path configPath = getConfigPath();
        
        if (Files.exists(configPath)) {
            try {
                Map<String, Object> rootConfig = objectMapper.readValue(configPath.toFile(), Map.class);
                Map<String, Object> autoFinetuneConfig = (Map<String, Object>) rootConfig.get("auto_finetune");
                
                if (autoFinetuneConfig != null) {
                    cachedConfig = objectMapper.convertValue(autoFinetuneConfig, FinetuneConfigEntity.class);
                    return cachedConfig;
                }
            } catch (Exception e) {
                log.warn("加载配置文件失败，使用默认配置: {}", e.getMessage());
            }
        }
        
        cachedConfig = new FinetuneConfigEntity();
        return cachedConfig;
    }

    public void saveConfig(FinetuneConfigEntity config) {
        Path configPath = getConfigPath();
        
        try {
            Files.createDirectories(configPath.getParent());
            
            Map<String, Object> rootConfig = new HashMap<>();
            Map<String, Object> autoFinetuneConfig = objectMapper.convertValue(config, Map.class);
            rootConfig.put("auto_finetune", autoFinetuneConfig);
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), rootConfig);
            cachedConfig = config;
            
            log.info("配置已保存到: {}", configPath);
        } catch (Exception e) {
            log.error("保存配置文件失败", e);
            throw new RuntimeException("保存配置失败: " + e.getMessage());
        }
    }

    public void updatePartialConfig(Map<String, Object> updates) {
        FinetuneConfigEntity config = getConfig();
        
        if (updates.containsKey("enabled")) {
            config.setEnabled((Boolean) updates.get("enabled"));
        }
        
        if (updates.containsKey("trigger")) {
            Map<String, Object> triggerUpdates = (Map<String, Object>) updates.get("trigger");
            FinetuneConfigEntity.TriggerConfig trigger = config.getTrigger();
            if (triggerUpdates.containsKey("minNewDocuments")) {
                trigger.setMinNewDocuments(((Number) triggerUpdates.get("minNewDocuments")).intValue());
            }
            if (triggerUpdates.containsKey("minDaysSinceLastTraining")) {
                trigger.setMinDaysSinceLastTraining(((Number) triggerUpdates.get("minDaysSinceLastTraining")).intValue());
            }
            if (triggerUpdates.containsKey("scheduleCron")) {
                trigger.setScheduleCron((String) triggerUpdates.get("scheduleCron"));
            }
        }
        
        if (updates.containsKey("resourceControl")) {
            Map<String, Object> resourceUpdates = (Map<String, Object>) updates.get("resourceControl");
            FinetuneConfigEntity.ResourceControlConfig resource = config.getResourceControl();
            updateResourceConfig(resource, resourceUpdates);
        }
        
        if (updates.containsKey("trainingStrategy")) {
            Map<String, Object> trainingUpdates = (Map<String, Object>) updates.get("trainingStrategy");
            FinetuneConfigEntity.TrainingStrategyConfig training = config.getTrainingStrategy();
            updateTrainingConfig(training, trainingUpdates);
        }
        
        if (updates.containsKey("remoteServer")) {
            Map<String, Object> remoteUpdates = (Map<String, Object>) updates.get("remoteServer");
            FinetuneConfigEntity.RemoteServerConfig remote = config.getRemoteServer();
            updateRemoteConfig(remote, remoteUpdates);
        }
        
        if (updates.containsKey("notification")) {
            Map<String, Object> notificationUpdates = (Map<String, Object>) updates.get("notification");
            FinetuneConfigEntity.NotificationConfig notification = config.getNotification();
            updateNotificationConfig(notification, notificationUpdates);
        }
        
        saveConfig(config);
    }

    private void updateResourceConfig(FinetuneConfigEntity.ResourceControlConfig config, Map<String, Object> updates) {
        if (updates.containsKey("maxGpuMemoryPercent")) {
            config.setMaxGpuMemoryPercent(((Number) updates.get("maxGpuMemoryPercent")).intValue());
        }
        if (updates.containsKey("maxCpuMemoryPercent")) {
            config.setMaxCpuMemoryPercent(((Number) updates.get("maxCpuMemoryPercent")).intValue());
        }
        if (updates.containsKey("maxTrainingHours")) {
            config.setMaxTrainingHours(((Number) updates.get("maxTrainingHours")).intValue());
        }
        if (updates.containsKey("temperatureThresholdCelsius")) {
            config.setTemperatureThresholdCelsius(((Number) updates.get("temperatureThresholdCelsius")).intValue());
        }
        if (updates.containsKey("pauseOnHighTemp")) {
            config.setPauseOnHighTemp((Boolean) updates.get("pauseOnHighTemp"));
        }
    }

    private void updateTrainingConfig(FinetuneConfigEntity.TrainingStrategyConfig config, Map<String, Object> updates) {
        if (updates.containsKey("maxSamples")) {
            config.setMaxSamples(((Number) updates.get("maxSamples")).intValue());
        }
        if (updates.containsKey("epochs")) {
            config.setEpochs(((Number) updates.get("epochs")).intValue());
        }
        if (updates.containsKey("learningRate")) {
            config.setLearningRate(((Number) updates.get("learningRate")).doubleValue());
        }
        if (updates.containsKey("batchSize")) {
            config.setBatchSize(((Number) updates.get("batchSize")).intValue());
        }
        if (updates.containsKey("loraRank")) {
            config.setLoraRank(((Number) updates.get("loraRank")).intValue());
        }
        if (updates.containsKey("loraAlpha")) {
            config.setLoraAlpha(((Number) updates.get("loraAlpha")).intValue());
        }
        if (updates.containsKey("maxLength")) {
            config.setMaxLength(((Number) updates.get("maxLength")).intValue());
        }
        if (updates.containsKey("use4bit")) {
            config.setUse4bit((Boolean) updates.get("use4bit"));
        }
    }

    private void updateRemoteConfig(FinetuneConfigEntity.RemoteServerConfig config, Map<String, Object> updates) {
        if (updates.containsKey("enabled")) {
            config.setEnabled((Boolean) updates.get("enabled"));
        }
        if (updates.containsKey("host")) {
            config.setHost((String) updates.get("host"));
        }
        if (updates.containsKey("port")) {
            config.setPort(((Number) updates.get("port")).intValue());
        }
        if (updates.containsKey("username")) {
            config.setUsername((String) updates.get("username"));
        }
        if (updates.containsKey("password")) {
            config.setPassword((String) updates.get("password"));
        }
        if (updates.containsKey("privateKeyPath")) {
            config.setPrivateKeyPath((String) updates.get("privateKeyPath"));
        }
        if (updates.containsKey("privateKeyPassphrase")) {
            config.setPrivateKeyPassphrase((String) updates.get("privateKeyPassphrase"));
        }
        if (updates.containsKey("remoteModelPath")) {
            config.setRemoteModelPath((String) updates.get("remoteModelPath"));
        }
        if (updates.containsKey("remoteDataPath")) {
            config.setRemoteDataPath((String) updates.get("remoteDataPath"));
        }
        if (updates.containsKey("remotePythonPath")) {
            config.setRemotePythonPath((String) updates.get("remotePythonPath"));
        }
        if (updates.containsKey("connectionTimeout")) {
            config.setConnectionTimeout(((Number) updates.get("connectionTimeout")).intValue());
        }
    }

    private void updateNotificationConfig(FinetuneConfigEntity.NotificationConfig config, Map<String, Object> updates) {
        if (updates.containsKey("onStart")) {
            config.setOnStart((Boolean) updates.get("onStart"));
        }
        if (updates.containsKey("onComplete")) {
            config.setOnComplete((Boolean) updates.get("onComplete"));
        }
        if (updates.containsKey("onFailure")) {
            config.setOnFailure((Boolean) updates.get("onFailure"));
        }
        if (updates.containsKey("webhookUrl")) {
            config.setWebhookUrl((String) updates.get("webhookUrl"));
        }
        if (updates.containsKey("emailRecipients")) {
            config.setEmailRecipients(((java.util.List<String>) updates.get("emailRecipients")).toArray(new String[0]));
        }
    }

    public FinetuneConfigEntity.RemoteServerConfig getRemoteServerConfig() {
        return getConfig().getRemoteServer();
    }

    public void updateRemoteServerConfig(FinetuneConfigEntity.RemoteServerConfig remoteConfig) {
        FinetuneConfigEntity config = getConfig();
        config.setRemoteServer(remoteConfig);
        saveConfig(config);
    }

    private Path getConfigPath() {
        return Paths.get(finetuneBasePath, configFilePath).toAbsolutePath();
    }

    public void resetToDefault() {
        cachedConfig = new FinetuneConfigEntity();
        saveConfig(cachedConfig);
    }
}
