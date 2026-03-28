package com.calmara.admin.service;

import com.calmara.admin.entity.FinetuneConfigEntity;
import com.calmara.api.websocket.TrainingLogWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class TrainingTaskQueueService {

    private final PriorityBlockingQueue<TrainingTask> taskQueue;
    private final Map<String, TrainingTask> taskRegistry;
    private final Map<String, TrainingTask> runningTasks;
    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService scheduler;
    
    private final AutoFinetuneService autoFinetuneService;
    private final FinetuneConfigService configService;
    private final TrainingLogWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);
    private volatile boolean isProcessing = false;
    private final int maxConcurrentTasks = 1;
    
    private static final long TASK_TIMEOUT_HOURS = 6;
    private static final long CLEANUP_INTERVAL_MINUTES = 30;

    public TrainingTaskQueueService(AutoFinetuneService autoFinetuneService,
                                     FinetuneConfigService configService,
                                     TrainingLogWebSocketHandler webSocketHandler,
                                     ObjectMapper objectMapper) {
        this.autoFinetuneService = autoFinetuneService;
        this.configService = configService;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
        
        this.taskQueue = new PriorityBlockingQueue<>(11, 
                Comparator.comparingInt(TrainingTask::getPriority).reversed()
                        .thenComparing(TrainingTask::getCreatedAt));
        
        this.taskRegistry = new ConcurrentHashMap<>();
        this.runningTasks = new ConcurrentHashMap<>();
        
        this.taskExecutor = Executors.newFixedThreadPool(2);
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        startTaskProcessor();
        startTimeoutMonitor();
        startCleanupScheduler();
        
        log.info("训练任务队列服务已启动");
    }

    public String submitTask(String reason, TaskPriority priority, Map<String, Object> params) {
        String taskId = generateTaskId();
        
        TrainingTask task = new TrainingTask();
        task.setTaskId(taskId);
        task.setReason(reason);
        task.setPriority(priority.getValue());
        task.setParams(params != null ? params : new HashMap<>());
        task.setStatus(TaskStatus.QUEUED);
        task.setCreatedAt(LocalDateTime.now());
        
        taskQueue.put(task);
        taskRegistry.put(taskId, task);
        
        log.info("训练任务已提交: taskId={}, reason={}, priority={}, queueSize={}", 
                taskId, reason, priority, taskQueue.size());
        
        webSocketHandler.broadcastLog(taskId, "INFO", 
                String.format("任务已加入队列，当前队列位置: %d", taskQueue.size()));
        
        return taskId;
    }

    public String submitHighPriorityTask(String reason, Map<String, Object> params) {
        return submitTask(reason, TaskPriority.HIGH, params);
    }

    public String submitNormalTask(String reason, Map<String, Object> params) {
        return submitTask(reason, TaskPriority.NORMAL, params);
    }

    public String submitLowPriorityTask(String reason, Map<String, Object> params) {
        return submitTask(reason, TaskPriority.LOW, params);
    }

    public Optional<TrainingTask> getTask(String taskId) {
        return Optional.ofNullable(taskRegistry.get(taskId));
    }

    public List<TrainingTask> getQueuedTasks() {
        return new ArrayList<>(taskQueue);
    }

    public List<TrainingTask> getRunningTasks() {
        return new ArrayList<>(runningTasks.values());
    }

    public List<TrainingTask> getAllTasks() {
        return new ArrayList<>(taskRegistry.values());
    }

    public boolean cancelTask(String taskId) {
        TrainingTask task = taskRegistry.get(taskId);
        if (task == null) {
            return false;
        }
        
        if (task.getStatus() == TaskStatus.RUNNING) {
            log.warn("无法取消正在运行的任务: taskId={}", taskId);
            return false;
        }
        
        if (taskQueue.remove(task)) {
            task.setStatus(TaskStatus.CANCELLED);
            task.setCompletedAt(LocalDateTime.now());
            log.info("任务已取消: taskId={}", taskId);
            return true;
        }
        
        return false;
    }

    public void pauseTask(String taskId) {
        TrainingTask task = taskRegistry.get(taskId);
        if (task != null && task.getStatus() == TaskStatus.RUNNING) {
            task.setStatus(TaskStatus.PAUSED);
            autoFinetuneService.stopTraining();
            log.info("任务已暂停: taskId={}", taskId);
        }
    }

    public void resumeTask(String taskId) {
        TrainingTask task = taskRegistry.get(taskId);
        if (task != null && task.getStatus() == TaskStatus.PAUSED) {
            task.setStatus(TaskStatus.QUEUED);
            taskQueue.put(task);
            log.info("任务已恢复: taskId={}", taskId);
        }
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    public int getRunningTaskCount() {
        return runningTaskCount.get();
    }

    public QueueStats getStats() {
        return new QueueStats(
                taskQueue.size(),
                runningTaskCount.get(),
                taskRegistry.size(),
                countByStatus(TaskStatus.QUEUED),
                countByStatus(TaskStatus.RUNNING),
                countByStatus(TaskStatus.COMPLETED),
                countByStatus(TaskStatus.FAILED)
        );
    }

    private void startTaskProcessor() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                processNextTask();
            } catch (Exception e) {
                log.error("任务处理失败: {}", e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void processNextTask() {
        if (runningTaskCount.get() >= maxConcurrentTasks) {
            return;
        }
        
        if (taskQueue.isEmpty()) {
            return;
        }
        
        if (autoFinetuneService.isTrainingInProgress()) {
            return;
        }
        
        TrainingTask task = taskQueue.poll();
        if (task == null) {
            return;
        }
        
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        
        runningTaskCount.incrementAndGet();
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        runningTasks.put(task.getTaskId(), task);
        
        log.info("开始执行训练任务: taskId={}, reason={}", task.getTaskId(), task.getReason());
        
        webSocketHandler.broadcastStatusChange(task.getTaskId(), 
                TaskStatus.QUEUED.name(), TaskStatus.RUNNING.name(), "任务开始执行");
        
        taskExecutor.submit(() -> executeTask(task));
    }

    private void executeTask(TrainingTask task) {
        String taskId = task.getTaskId();
        
        try {
            webSocketHandler.broadcastLog(taskId, "INFO", "开始执行训练任务: " + task.getReason());
            
            autoFinetuneService.triggerFinetune(task.getReason());
            
            while (autoFinetuneService.isTrainingInProgress()) {
                Thread.sleep(5000);
                
                Map<String, Object> status = autoFinetuneService.getTrainingStatus();
                int progress = (int) status.getOrDefault("progress", 0);
                String stage = (String) status.getOrDefault("status", "unknown");
                
                webSocketHandler.broadcastProgress(taskId, progress, stage, 
                        0, 0, 0.0);
            }
            
            Map<String, Object> finalStatus = autoFinetuneService.getTrainingStatus();
            String status = (String) finalStatus.getOrDefault("status", "unknown");
            
            if ("completed".equals(status)) {
                task.setStatus(TaskStatus.COMPLETED);
                webSocketHandler.broadcastCompletion(taskId, true, "训练完成", 
                        (String) finalStatus.get("modelPath"), finalStatus);
            } else if ("failed".equals(status) || "error".equals(status)) {
                task.setStatus(TaskStatus.FAILED);
                task.setError((String) finalStatus.get("error"));
                webSocketHandler.broadcastError(taskId, 
                        (String) finalStatus.get("error"), null);
            } else {
                task.setStatus(TaskStatus.COMPLETED);
            }
            
        } catch (Exception e) {
            log.error("训练任务执行失败: taskId={}, error={}", taskId, e.getMessage());
            task.setStatus(TaskStatus.FAILED);
            task.setError(e.getMessage());
            webSocketHandler.broadcastError(taskId, e.getMessage(), 
                    Arrays.toString(e.getStackTrace()));
            
        } finally {
            task.setCompletedAt(LocalDateTime.now());
            runningTasks.remove(taskId);
            runningTaskCount.decrementAndGet();
            
            log.info("训练任务执行结束: taskId={}, status={}", taskId, task.getStatus());
        }
    }

    private void startTimeoutMonitor() {
        scheduler.scheduleWithFixedDelay(() -> {
            LocalDateTime timeout = LocalDateTime.now().minusHours(TASK_TIMEOUT_HOURS);
            
            runningTasks.forEach((taskId, task) -> {
                if (task.getStartedAt() != null && task.getStartedAt().isBefore(timeout)) {
                    log.warn("任务超时: taskId={}, startedAt={}", taskId, task.getStartedAt());
                    autoFinetuneService.stopTraining();
                    task.setStatus(TaskStatus.TIMEOUT);
                    task.setError("任务执行超时");
                    webSocketHandler.broadcastError(taskId, "任务执行超时", null);
                }
            });
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void startCleanupScheduler() {
        scheduler.scheduleWithFixedDelay(() -> {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            
            taskRegistry.entrySet().removeIf(entry -> {
                TrainingTask task = entry.getValue();
                return task.getCompletedAt() != null && 
                       task.getCompletedAt().isBefore(cutoff) &&
                       (task.getStatus() == TaskStatus.COMPLETED || 
                        task.getStatus() == TaskStatus.FAILED ||
                        task.getStatus() == TaskStatus.CANCELLED);
            });
            
            log.debug("清理完成的历史任务，当前任务数: {}", taskRegistry.size());
        }, CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private String generateTaskId() {
        return "train_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }

    private long countByStatus(TaskStatus status) {
        return taskRegistry.values().stream()
                .filter(t -> t.getStatus() == status)
                .count();
    }

    public void shutdown() {
        taskExecutor.shutdown();
        scheduler.shutdown();
        
        try {
            if (!taskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("训练任务队列服务已关闭");
    }

    @Data
    public static class TrainingTask {
        private String taskId;
        private String reason;
        private int priority;
        private Map<String, Object> params;
        private TaskStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String error;
        private Map<String, Object> result;
    }

    public enum TaskStatus {
        QUEUED,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    public enum TaskPriority {
        LOW(1),
        NORMAL(5),
        HIGH(10);
        
        private final int value;
        
        TaskPriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }

    @Data
    public static class QueueStats {
        private final int queueSize;
        private final int runningCount;
        private final int totalTasks;
        private final long queuedCount;
        private final long runningCount2;
        private final long completedCount;
        private final long failedCount;
    }
}
