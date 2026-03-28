package com.calmara.api.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TrainingLogWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Boolean> activeSessions = new ConcurrentHashMap<>();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TrainingLogWebSocketHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastLog(String trainingId, String level, String message) {
        LogMessage logMessage = new LogMessage(
                trainingId,
                level,
                message,
                LocalDateTime.now().format(TIME_FORMATTER)
        );
        
        messagingTemplate.convertAndSend("/topic/training/" + trainingId + "/logs", logMessage);
        log.debug("[{}] {} - {}", trainingId, level, message);
    }

    public void broadcastProgress(String trainingId, int progress, String stage, 
                                   int currentEpoch, int totalEpochs, double currentLoss) {
        ProgressMessage progressMessage = new ProgressMessage(
                trainingId,
                progress,
                stage,
                currentEpoch,
                totalEpochs,
                currentLoss,
                LocalDateTime.now().format(TIME_FORMATTER)
        );
        
        messagingTemplate.convertAndSend("/topic/training/" + trainingId + "/progress", progressMessage);
    }

    public void broadcastGpuStatus(String trainingId, GpuStatusMessage gpuStatus) {
        gpuStatus.setTimestamp(LocalDateTime.now().format(TIME_FORMATTER));
        messagingTemplate.convertAndSend("/topic/training/" + trainingId + "/gpu", gpuStatus);
    }

    public void broadcastStatusChange(String trainingId, String oldStatus, String newStatus, String reason) {
        StatusChangeMessage message = new StatusChangeMessage(
                trainingId,
                oldStatus,
                newStatus,
                reason,
                LocalDateTime.now().format(TIME_FORMATTER)
        );
        
        messagingTemplate.convertAndSend("/topic/training/" + trainingId + "/status", message);
    }

    public void broadcastCompletion(String trainingId, boolean success, String message, 
                                     String modelPath, Map<String, Object> metrics) {
        CompletionMessage completionMessage = new CompletionMessage(
                trainingId,
                success,
                message,
                modelPath,
                metrics,
                LocalDateTime.now().format(TIME_FORMATTER)
        );
        
        messagingTemplate.convertAndSend("/topic/training/" + trainingId + "/completion", completionMessage);
    }

    public void broadcastError(String trainingId, String error, String stackTrace) {
        ErrorMessage errorMessage = new ErrorMessage(
                trainingId,
                error,
                stackTrace,
                LocalDateTime.now().format(TIME_FORMATTER)
        );
        
        messagingTemplate.convertAndSend("/topic/training/" + trainingId + "/error", errorMessage);
    }

    public void registerSession(String trainingId) {
        activeSessions.put(trainingId, true);
        broadcastStatusChange(trainingId, null, "INITIALIZING", "训练任务已创建");
    }

    public void unregisterSession(String trainingId) {
        activeSessions.remove(trainingId);
    }

    public boolean isSessionActive(String trainingId) {
        return activeSessions.getOrDefault(trainingId, false);
    }

    public static class LogMessage {
        private String trainingId;
        private String level;
        private String message;
        private String timestamp;

        public LogMessage(String trainingId, String level, String message, String timestamp) {
            this.trainingId = trainingId;
            this.level = level;
            this.message = message;
            this.timestamp = timestamp;
        }

        public String getTrainingId() { return trainingId; }
        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public String getTimestamp() { return timestamp; }
    }

    public static class ProgressMessage {
        private String trainingId;
        private int progress;
        private String stage;
        private int currentEpoch;
        private int totalEpochs;
        private double currentLoss;
        private String timestamp;

        public ProgressMessage(String trainingId, int progress, String stage, 
                               int currentEpoch, int totalEpochs, double currentLoss, String timestamp) {
            this.trainingId = trainingId;
            this.progress = progress;
            this.stage = stage;
            this.currentEpoch = currentEpoch;
            this.totalEpochs = totalEpochs;
            this.currentLoss = currentLoss;
            this.timestamp = timestamp;
        }

        public String getTrainingId() { return trainingId; }
        public int getProgress() { return progress; }
        public String getStage() { return stage; }
        public int getCurrentEpoch() { return currentEpoch; }
        public int getTotalEpochs() { return totalEpochs; }
        public double getCurrentLoss() { return currentLoss; }
        public String getTimestamp() { return timestamp; }
    }

    public static class GpuStatusMessage {
        private String trainingId;
        private double gpuUtilization;
        private double memoryUsed;
        private double memoryTotal;
        private double temperature;
        private boolean overheating;
        private String timestamp;

        public GpuStatusMessage(String trainingId, double gpuUtilization, double memoryUsed, 
                                double memoryTotal, double temperature, boolean overheating) {
            this.trainingId = trainingId;
            this.gpuUtilization = gpuUtilization;
            this.memoryUsed = memoryUsed;
            this.memoryTotal = memoryTotal;
            this.temperature = temperature;
            this.overheating = overheating;
        }

        public String getTrainingId() { return trainingId; }
        public double getGpuUtilization() { return gpuUtilization; }
        public double getMemoryUsed() { return memoryUsed; }
        public double getMemoryTotal() { return memoryTotal; }
        public double getTemperature() { return temperature; }
        public boolean isOverheating() { return overheating; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    public static class StatusChangeMessage {
        private String trainingId;
        private String oldStatus;
        private String newStatus;
        private String reason;
        private String timestamp;

        public StatusChangeMessage(String trainingId, String oldStatus, String newStatus, 
                                   String reason, String timestamp) {
            this.trainingId = trainingId;
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public String getTrainingId() { return trainingId; }
        public String getOldStatus() { return oldStatus; }
        public String getNewStatus() { return newStatus; }
        public String getReason() { return reason; }
        public String getTimestamp() { return timestamp; }
    }

    public static class CompletionMessage {
        private String trainingId;
        private boolean success;
        private String message;
        private String modelPath;
        private Map<String, Object> metrics;
        private String timestamp;

        public CompletionMessage(String trainingId, boolean success, String message, 
                                 String modelPath, Map<String, Object> metrics, String timestamp) {
            this.trainingId = trainingId;
            this.success = success;
            this.message = message;
            this.modelPath = modelPath;
            this.metrics = metrics;
            this.timestamp = timestamp;
        }

        public String getTrainingId() { return trainingId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getModelPath() { return modelPath; }
        public Map<String, Object> getMetrics() { return metrics; }
        public String getTimestamp() { return timestamp; }
    }

    public static class ErrorMessage {
        private String trainingId;
        private String error;
        private String stackTrace;
        private String timestamp;

        public ErrorMessage(String trainingId, String error, String stackTrace, String timestamp) {
            this.trainingId = trainingId;
            this.error = error;
            this.stackTrace = stackTrace;
            this.timestamp = timestamp;
        }

        public String getTrainingId() { return trainingId; }
        public String getError() { return error; }
        public String getStackTrace() { return stackTrace; }
        public String getTimestamp() { return timestamp; }
    }
}
