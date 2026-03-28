package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.calmara.api.websocket.TrainingLogWebSocketHandler;
import com.calmara.api.websocket.TrainingLogWebSocketHandler.GpuStatusMessage;
import com.jcraft.jsch.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class GpuMonitorService {

    private final RemoteServerService remoteServerService;
    private final TrainingLogWebSocketHandler webSocketHandler;
    private final FinetuneConfigService configService;
    
    private final Map<String, ScheduledFuture<?>> monitoringTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private static final double TEMPERATURE_WARNING_THRESHOLD = 80.0;
    private static final double TEMPERATURE_CRITICAL_THRESHOLD = 85.0;
    private static final double MEMORY_WARNING_THRESHOLD = 90.0;
    
    private volatile boolean pauseRequested = false;

    public GpuMonitorService(RemoteServerService remoteServerService,
                             TrainingLogWebSocketHandler webSocketHandler,
                             FinetuneConfigService configService) {
        this.remoteServerService = remoteServerService;
        this.webSocketHandler = webSocketHandler;
        this.configService = configService;
    }

    public void startMonitoring(String trainingId, FinetuneConfigEntity.RemoteServerConfig config) {
        if (monitoringTasks.containsKey(trainingId)) {
            log.warn("GPU监控已在运行: {}", trainingId);
            return;
        }
        
        pauseRequested = false;
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                GpuStatus status = queryGpuStatus(config);
                
                GpuStatusMessage message = new GpuStatusMessage(
                        trainingId,
                        status.getGpuUtilization(),
                        status.getMemoryUsed(),
                        status.getMemoryTotal(),
                        status.getTemperature(),
                        status.isOverheating()
                );
                
                webSocketHandler.broadcastGpuStatus(trainingId, message);
                
                checkThresholds(trainingId, status, config);
                
            } catch (Exception e) {
                log.error("GPU监控失败: {}", e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
        
        monitoringTasks.put(trainingId, future);
        log.info("GPU监控已启动: {}", trainingId);
    }

    public void stopMonitoring(String trainingId) {
        ScheduledFuture<?> future = monitoringTasks.remove(trainingId);
        if (future != null) {
            future.cancel(false);
            log.info("GPU监控已停止: {}", trainingId);
        }
    }

    public boolean isPauseRequested() {
        return pauseRequested;
    }

    public void resetPauseRequest() {
        this.pauseRequested = false;
    }

    private GpuStatus queryGpuStatus(FinetuneConfigEntity.RemoteServerConfig config) {
        GpuStatus status = new GpuStatus();
        
        try {
            String gpuQueryCommand = "nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu --format=csv,noheader,nounits";
            RemoteServerService.CommandResult result = remoteServerService.executeRemoteCommand(config, gpuQueryCommand);
            
            if (result.isSuccess() && result.getOutput() != null) {
                String[] parts = result.getOutput().trim().split(",");
                if (parts.length >= 4) {
                    status.setGpuUtilization(Double.parseDouble(parts[0].trim()));
                    status.setMemoryUsed(Double.parseDouble(parts[1].trim()));
                    status.setMemoryTotal(Double.parseDouble(parts[2].trim()));
                    status.setTemperature(Double.parseDouble(parts[3].trim()));
                }
            }
            
            FinetuneConfigEntity.ResourceControlConfig resourceConfig = configService.getConfig().getResourceControl();
            double tempThreshold = resourceConfig.getTemperatureThresholdCelsius();
            status.setOverheating(status.getTemperature() >= tempThreshold);
            
        } catch (Exception e) {
            log.error("查询GPU状态失败: {}", e.getMessage());
        }
        
        return status;
    }

    private void checkThresholds(String trainingId, GpuStatus status, 
                                  FinetuneConfigEntity.RemoteServerConfig config) {
        FinetuneConfigEntity.ResourceControlConfig resourceConfig = configService.getConfig().getResourceControl();
        double tempThreshold = resourceConfig.getTemperatureThresholdCelsius();
        
        if (status.getTemperature() >= tempThreshold && resourceConfig.isPauseOnHighTemp()) {
            log.warn("GPU温度过高: {}°C >= {}°C, 请求暂停训练", status.getTemperature(), tempThreshold);
            pauseRequested = true;
            
            webSocketHandler.broadcastLog(trainingId, "WARN", 
                    String.format("GPU温度过高: %.1f°C >= %.1f°C, 训练将暂停", 
                            status.getTemperature(), tempThreshold));
        }
        
        if (status.getTemperature() >= TEMPERATURE_WARNING_THRESHOLD) {
            webSocketHandler.broadcastLog(trainingId, "WARN",
                    String.format("GPU温度警告: %.1f°C", status.getTemperature()));
        }
        
        double memoryPercent = (status.getMemoryUsed() / status.getMemoryTotal()) * 100;
        if (memoryPercent >= MEMORY_WARNING_THRESHOLD) {
            webSocketHandler.broadcastLog(trainingId, "WARN",
                    String.format("GPU显存使用率过高: %.1f%%", memoryPercent));
        }
    }

    public GpuStatus getInstantGpuStatus(FinetuneConfigEntity.RemoteServerConfig config) {
        return queryGpuStatus(config);
    }

    public List<GpuInfo> getGpuList(FinetuneConfigEntity.RemoteServerConfig config) {
        List<GpuInfo> gpuList = new ArrayList<>();
        
        try {
            String gpuListCommand = "nvidia-smi --query-gpu=index,name,memory.total --format=csv,noheader";
            RemoteServerService.CommandResult result = remoteServerService.executeRemoteCommand(config, gpuListCommand);
            
            if (result.isSuccess() && result.getOutput() != null) {
                String[] lines = result.getOutput().trim().split("\n");
                for (String line : lines) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        GpuInfo info = new GpuInfo();
                        info.setIndex(Integer.parseInt(parts[0].trim()));
                        info.setName(parts[1].trim());
                        info.setMemoryTotal(parts[2].trim());
                        gpuList.add(info);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取GPU列表失败: {}", e.getMessage());
        }
        
        return gpuList;
    }

    @Data
    public static class GpuStatus {
        private double gpuUtilization;
        private double memoryUsed;
        private double memoryTotal;
        private double temperature;
        private boolean overheating;
    }

    @Data
    public static class GpuInfo {
        private int index;
        private String name;
        private String memoryTotal;
    }
}
