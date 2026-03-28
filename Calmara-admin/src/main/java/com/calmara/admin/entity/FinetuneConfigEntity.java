package com.calmara.admin.entity;

import lombok.Data;
import java.io.Serializable;

@Data
public class FinetuneConfigEntity implements Serializable {
    
    private boolean enabled = true;
    
    private TriggerConfig trigger = new TriggerConfig();
    
    private ResourceControlConfig resourceControl = new ResourceControlConfig();
    
    private TrainingStrategyConfig trainingStrategy = new TrainingStrategyConfig();
    
    private EvaluationConfig evaluation = new EvaluationConfig();
    
    private ModelManagementConfig modelManagement = new ModelManagementConfig();
    
    private NotificationConfig notification = new NotificationConfig();
    
    private RemoteServerConfig remoteServer = new RemoteServerConfig();

    @Data
    public static class TriggerConfig implements Serializable {
        private String type = "threshold";
        private int minNewDocuments = 1000;
        private int minDaysSinceLastTraining = 7;
        private String scheduleCron = "0 2 * * 0";
    }

    @Data
    public static class ResourceControlConfig implements Serializable {
        private int maxGpuMemoryPercent = 80;
        private int maxCpuMemoryPercent = 70;
        private int maxTrainingHours = 6;
        private int coolDownMinutes = 30;
        private int temperatureThresholdCelsius = 85;
        private boolean pauseOnHighTemp = true;
    }

    @Data
    public static class TrainingStrategyConfig implements Serializable {
        private String mode = "incremental";
        private int maxSamples = 50000;
        private int epochs = 3;
        private double learningRate = 1e-4;
        private int batchSize = 4;
        private int gradientAccumulationSteps = 8;
        private int loraRank = 32;
        private int loraAlpha = 64;
        private int maxLength = 512;
        private boolean use4bit = true;
        private int saveEveryNEpochs = 1;
    }

    @Data
    public static class EvaluationConfig implements Serializable {
        private boolean enabled = true;
        private int testSamples = 500;
        private String[] metrics = {"perplexity", "response_quality", "safety_score"};
        private double minImprovementPercent = 2.0;
        private boolean rollbackOnDegradation = true;
    }

    @Data
    public static class ModelManagementConfig implements Serializable {
        private boolean keepBestOnly = true;
        private int maxCheckpoints = 3;
        private boolean autoDeploy = false;
        private boolean backupBeforeUpdate = true;
        private String defaultModelName = "calmara-lora";
    }

    @Data
    public static class NotificationConfig implements Serializable {
        private boolean onStart = true;
        private boolean onComplete = true;
        private boolean onFailure = true;
        private String webhookUrl = "";
        private String[] emailRecipients = new String[0];
    }

    @Data
    public static class RemoteServerConfig implements Serializable {
        private boolean enabled = false;
        private String host = "";
        private int port = 22;
        private String username = "";
        private String password = "";
        private String privateKeyPath = "";
        private String privateKeyPassphrase = "";
        private String remoteModelPath = "";
        private String remoteDataPath = "";
        private String remotePythonPath = "python";
        private String remoteOutputPath = "";
        private boolean useSshTunnel = false;
        private int connectionTimeout = 30000;
        private int commandTimeout = 3600000;
    }
}
