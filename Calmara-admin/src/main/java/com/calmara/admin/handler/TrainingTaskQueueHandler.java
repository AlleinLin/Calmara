package com.calmara.admin.handler;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
public class TrainingTaskQueueHandler {

    private final BlockingQueue<TrainingTask> taskQueue = new LinkedBlockingQueue<>();
    private final Map<String, TrainingTask> runningTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean trainingInProgress = new AtomicBoolean(false);
    private final AtomicReference<String> currentTaskId = new AtomicReference<>(null);
    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
    private final int maxConcurrentTasks = 1;

    @Data
    public static class TrainingTask {
        private String taskId;
        private String reason;
        private String modelPath;
        private String datasetPath;
        private String outputPath;
        private int epochs;
        private int batchSize;
        private double learningRate;
        private String status;
        private int progress;
        private String errorMessage;
        private long createdAt;
        private long startedAt;
        private long completedAt;
        private Consumer<TaskParams> callback;
    }

    @Data
    public static class TaskParams {
        private String taskId;
        private String reason;
        private TrainingTask task;
    }

    public boolean isTrainingInProgress() {
        return trainingInProgress.get();
    }

    public TrainingTask submitTask(String reason, Consumer<TaskParams> callback) {
        TrainingTask task = new TrainingTask();
        task.setTaskId(java.util.UUID.randomUUID().toString());
        task.setReason(reason);
        task.setStatus("PENDING");
        task.setCreatedAt(System.currentTimeMillis());
        task.setCallback(callback);

        if (trainingInProgress.get()) {
            taskQueue.offer(task);
            log.info("Training task queued: {} (queue size: {})", task.getTaskId(), taskQueue.size());
        } else {
            executeTask(task);
        }

        return task;
    }

    private void executeTask(TrainingTask task) {
        trainingInProgress.set(true);
        currentTaskId.set(task.getTaskId());
        task.setStatus("RUNNING");
        task.setStartedAt(System.currentTimeMillis());
        runningTasks.put(task.getTaskId(), task);

        log.info("Executing training task: {}", task.getTaskId());

        taskExecutor.submit(() -> {
            try {
                TaskParams params = new TaskParams();
                params.setTaskId(task.getTaskId());
                params.setReason(task.getReason());
                params.setTask(task);

                task.getCallback().accept(params);

                task.setStatus("COMPLETED");
                task.setCompletedAt(System.currentTimeMillis());
                log.info("Training task completed: {}", task.getTaskId());

            } catch (Exception e) {
                task.setStatus("FAILED");
                task.setErrorMessage(e.getMessage());
                task.setCompletedAt(System.currentTimeMillis());
                log.error("Training task failed: {}", task.getTaskId(), e);

            } finally {
                runningTasks.remove(task.getTaskId());
                trainingInProgress.set(false);
                currentTaskId.set(null);

                TrainingTask nextTask = taskQueue.poll();
                if (nextTask != null) {
                    log.info("Starting next queued task: {}", nextTask.getTaskId());
                    executeTask(nextTask);
                }
            }
        });
    }

    public TrainingTask getNextTask() throws InterruptedException {
        return taskQueue.take();
    }

    public void markRunning(TrainingTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(System.currentTimeMillis());
        runningTasks.put(task.getTaskId(), task);
    }

    public void markCompleted(TrainingTask task) {
        task.setStatus("COMPLETED");
        task.setCompletedAt(System.currentTimeMillis());
        runningTasks.remove(task.getTaskId());
        trainingInProgress.set(false);
        currentTaskId.set(null);
    }

    public void markFailed(TrainingTask task, String error) {
        task.setStatus("FAILED");
        task.setErrorMessage(error);
        task.setCompletedAt(System.currentTimeMillis());
        runningTasks.remove(task.getTaskId());
        trainingInProgress.set(false);
        currentTaskId.set(null);
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    public int getRunningCount() {
        return runningTasks.size();
    }

    public boolean canAcceptNewTask() {
        return runningTasks.size() < maxConcurrentTasks;
    }

    public String getCurrentTaskId() {
        return currentTaskId.get();
    }

    public TrainingTask getRunningTask(String taskId) {
        return runningTasks.get(taskId);
    }

    public void cancelTask(String taskId) {
        TrainingTask task = runningTasks.get(taskId);
        if (task != null) {
            task.setStatus("CANCELLED");
            task.setCompletedAt(System.currentTimeMillis());
            runningTasks.remove(taskId);
            trainingInProgress.set(false);
            currentTaskId.set(null);
            log.info("Training task cancelled: {}", taskId);
        }
    }

    public void shutdown() {
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
