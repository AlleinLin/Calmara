package com.calmara.mcp;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.model.dto.MCPCommand;
import com.calmara.model.dto.MCPResult;
import com.calmara.model.dto.PsychRecord;
import com.calmara.model.dto.RiskAlert;
import com.calmara.model.dto.SendResult;
import com.calmara.mcp.email.EmailMCPService;
import com.calmara.mcp.excel.ExcelMCPService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class SagaOrchestrator {

    private static final String SAGA_LOG_KEY = "calmara:saga:log";
    private static final String SAGA_STATE_KEY = "calmara:saga:state";
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    private final MCPStateMachine stateMachine;
    private final ExcelMCPService excelService;
    private final EmailMCPService emailService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final Map<String, SagaExecution> activeSagas = new ConcurrentHashMap<>();
    private final BlockingQueue<SagaExecution> compensationQueue = new LinkedBlockingQueue<>();

    public SagaOrchestrator(MCPStateMachine stateMachine,
                           ExcelMCPService excelService,
                           EmailMCPService emailService,
                           RedisTemplate<String, Object> redisTemplate,
                           ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.excelService = excelService;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public MCPResult executeWithSaga(MCPCommand command) {
        String sagaId = UUID.randomUUID().toString();
        SagaExecution execution = new SagaExecution(sagaId, command);
        activeSagas.put(sagaId, execution);
        
        try {
            log.info("[Saga:{}] 开始执行Saga事务", sagaId);
            
            persistSagaState(execution, SagaState.STARTED);
            
            SagaStep excelStep = new SagaStep(
                    "excel_write",
                    () -> executeExcelWrite(command),
                    () -> compensateExcelWrite(command)
            );
            
            execution.addStep(excelStep);
            MCPResult excelResult = executeStepWithRetry(excelStep, execution);
            
            if (!excelResult.isSuccess()) {
                log.error("[Saga:{}] Excel写入失败，触发补偿", sagaId);
                compensate(execution);
                return MCPResult.failure("Excel写入失败: " + excelResult.getMessage());
            }
            
            execution.setExcelResult(excelResult);
            persistSagaState(execution, SagaState.EXCEL_COMPLETED);
            
            if (shouldSendEmail(command)) {
                SagaStep emailStep = new SagaStep(
                        "email_alert",
                        () -> executeEmailAlert(command),
                        () -> compensateEmailAlert(command)
                );
                
                execution.addStep(emailStep);
                MCPResult emailResult = executeStepWithRetry(emailStep, execution);
                
                if (!emailResult.isSuccess()) {
                    log.error("[Saga:{}] 邮件发送失败，触发补偿", sagaId);
                    compensate(execution);
                    return MCPResult.failure("邮件发送失败: " + emailResult.getMessage());
                }
                
                execution.setEmailResult(emailResult);
            }
            
            persistSagaState(execution, SagaState.COMPLETED);
            log.info("[Saga:{}] Saga事务执行成功", sagaId);
            
            return MCPResult.success("Saga事务执行成功")
                    .withData("sagaId", sagaId)
                    .withData("excelPath", execution.getExcelResult() != null ? 
                            execution.getExcelResult().getData().get("path") : null);
            
        } catch (Exception e) {
            log.error("[Saga:{}] Saga执行异常", sagaId, e);
            compensate(execution);
            return MCPResult.failure("Saga执行失败: " + e.getMessage());
        } finally {
            activeSagas.remove(sagaId);
        }
    }

    public CompletableFuture<MCPResult> executeWithSagaAsync(MCPCommand command,
                                                              SagaCompletionCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            MCPResult result = executeWithSaga(command);
            if (callback != null) {
                callback.onComplete(result);
            }
            return result;
        }, executorService);
    }

    private MCPResult executeStepWithRetry(SagaStep step, SagaExecution execution) {
        AtomicInteger retryCount = new AtomicInteger(0);
        Exception lastException = null;
        
        while (retryCount.get() < MAX_RETRY) {
            try {
                log.debug("[Saga:{}] 执行步骤: {} (尝试 {})", 
                        execution.getSagaId(), step.getName(), retryCount.get() + 1);
                
                MCPResult result = step.execute();
                
                if (result.isSuccess()) {
                    step.setState(StepState.COMPLETED);
                    return result;
                }
                
                if (isRetryableError(result.getMessage())) {
                    retryCount.incrementAndGet();
                    if (retryCount.get() < MAX_RETRY) {
                        Thread.sleep(RETRY_DELAY_MS * retryCount.get());
                    }
                    continue;
                }
                
                step.setState(StepState.FAILED);
                return result;
                
            } catch (Exception e) {
                lastException = e;
                log.warn("[Saga:{}] 步骤执行异常: {} - {}", 
                        execution.getSagaId(), step.getName(), e.getMessage());
                retryCount.incrementAndGet();
                
                if (retryCount.get() < MAX_RETRY) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount.get());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        step.setState(StepState.FAILED);
        return MCPResult.failure("步骤执行失败，重试次数耗尽: " + 
                (lastException != null ? lastException.getMessage() : "未知错误"));
    }

    private void compensate(SagaExecution execution) {
        log.info("[Saga:{}] 开始执行补偿操作", execution.getSagaId());
        
        persistSagaState(execution, SagaState.COMPENSATING);
        
        List<SagaStep> completedSteps = execution.getSteps().stream()
                .filter(s -> s.getState() == StepState.COMPLETED)
                .sorted(Comparator.comparingInt(SagaStep::getOrder).reversed())
                .toList();
        
        for (SagaStep step : completedSteps) {
            try {
                log.info("[Saga:{}] 补偿步骤: {}", execution.getSagaId(), step.getName());
                step.compensate();
                step.setState(StepState.COMPENSATED);
            } catch (Exception e) {
                log.error("[Saga:{}] 补偿失败: {} - {}", 
                        execution.getSagaId(), step.getName(), e.getMessage());
                step.setState(StepState.COMPENSATION_FAILED);
                
                scheduleManualCompensation(execution, step, e);
            }
        }
        
        persistSagaState(execution, SagaState.COMPENSATED);
    }

    private void scheduleManualCompensation(SagaExecution execution, SagaStep step, Exception error) {
        CompensationTask task = new CompensationTask();
        task.setSagaId(execution.getSagaId());
        task.setStepName(step.getName());
        task.setCommand(execution.getCommand());
        task.setError(error.getMessage());
        task.setCreatedAt(LocalDateTime.now());
        task.setRetryCount(0);
        task.setStatus("PENDING");
        
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            redisTemplate.opsForList().rightPush("calmara:compensation:queue", taskJson);
            log.warn("[Saga:{}] 已加入手动补偿队列: {}", execution.getSagaId(), step.getName());
        } catch (Exception e) {
            log.error("[Saga:{}] 加入补偿队列失败", execution.getSagaId(), e);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void processCompensationQueue() {
        Object taskObj = redisTemplate.opsForList().leftPop("calmara:compensation:queue");
        
        if (taskObj == null) {
            return;
        }
        
        try {
            CompensationTask task = objectMapper.readValue(
                    taskObj.toString(), CompensationTask.class);
            
            log.info("处理补偿任务: sagaId={}, step={}", 
                    task.getSagaId(), task.getStepName());
            
            if (task.getRetryCount() >= MAX_RETRY) {
                task.setStatus("FAILED_PERMANENTLY");
                log.error("补偿任务永久失败: {}", task);
                alertAdministrators(task);
                return;
            }
            
            boolean success = retryCompensation(task);
            
            if (success) {
                task.setStatus("COMPLETED");
                log.info("补偿任务完成: sagaId={}", task.getSagaId());
            } else {
                task.setRetryCount(task.getRetryCount() + 1);
                task.setStatus("RETRYING");
                redisTemplate.opsForList().rightPush("calmara:compensation:queue", 
                        objectMapper.writeValueAsString(task));
            }
            
        } catch (Exception e) {
            log.error("处理补偿任务失败", e);
        }
    }

    private boolean retryCompensation(CompensationTask task) {
        try {
            MCPCommand command = task.getCommand();
            
            if ("excel_write".equals(task.getStepName())) {
                compensateExcelWrite(command);
                return true;
            } else if ("email_alert".equals(task.getStepName())) {
                compensateEmailAlert(command);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("重试补偿失败: {}", task.getSagaId(), e);
            return false;
        }
    }

    private void alertAdministrators(CompensationTask task) {
        log.error("需要人工介入的补偿任务: {}", task);
    }

    private MCPResult executeExcelWrite(MCPCommand command) {
        try {
            Map<String, Object> params = command.getParams();
            PsychRecord record = PsychRecord.builder()
                    .userId((String) params.get("userId"))
                    .content((String) params.get("content"))
                    .emotion((String) params.get("emotion"))
                    .emotionScore((Double) params.get("emotionScore"))
                    .riskLevel((String) params.get("riskLevel"))
                    .timestamp(LocalDateTime.now())
                    .build();
            
            String path = excelService.writeRecord(record);
            return MCPResult.success("Excel写入成功").withData("path", path);
            
        } catch (Exception e) {
            return MCPResult.failure("Excel写入失败: " + e.getMessage());
        }
    }

    private MCPResult executeEmailAlert(MCPCommand command) {
        try {
            Map<String, Object> params = command.getParams();
            
            @SuppressWarnings("unchecked")
            List<String> recipients = (List<String>) params.get("recipients");
            
            RiskAlert alert = RiskAlert.builder()
                    .userId((String) params.get("userId"))
                    .userName((String) params.get("userName"))
                    .emotion((String) params.get("emotion"))
                    .emotionScore((Double) params.get("emotionScore"))
                    .content((String) params.get("content"))
                    .recipients(recipients != null ? recipients : new ArrayList<>())
                    .build();
            
            SendResult sendResult = emailService.sendAlert(alert);
            if (sendResult.isSuccess()) {
                return MCPResult.success(sendResult.getMessage());
            } else {
                return MCPResult.failure(sendResult.getMessage());
            }
            
        } catch (Exception e) {
            return MCPResult.failure("邮件发送失败: " + e.getMessage());
        }
    }

    private void compensateExcelWrite(MCPCommand command) {
        log.info("执行Excel写入补偿: 标记记录为已撤销");
        try {
            Map<String, Object> params = command.getParams();
            String userId = (String) params.get("userId");
            LocalDateTime timestamp = (LocalDateTime) params.get("timestamp");
            
            if (userId != null) {
                LocalDateTime revokeTime = timestamp != null ? timestamp : LocalDateTime.now();
                excelService.markRecordAsRevoked(userId, revokeTime);
                log.info("Excel记录已标记撤销: userId={}, timestamp={}", userId, revokeTime);
            }
        } catch (Exception e) {
            log.error("Excel补偿操作失败", e);
            throw new RuntimeException("Excel补偿失败: " + e.getMessage(), e);
        }
    }

    private void compensateEmailAlert(MCPCommand command) {
        log.info("执行邮件预警补偿: 发送撤销通知");
        try {
            Map<String, Object> params = command.getParams();
            
            @SuppressWarnings("unchecked")
            List<String> recipients = (List<String>) params.get("recipients");
            String userId = (String) params.get("userId");
            
            if (recipients != null && !recipients.isEmpty()) {
                String subject = "【撤销通知】心理预警已处理";
                String content = String.format(
                        "此前的心理预警（用户：%s）已被系统撤销或处理完成，请知悉。\n\n" +
                        "撤销时间：%s\n" +
                        "如有疑问，请联系系统管理员。",
                        userId,
                        LocalDateTime.now().toString()
                );
                
                emailService.sendRevocationNotice(recipients, subject, content);
                log.info("补偿邮件已发送: recipients={}", recipients);
            }
        } catch (Exception e) {
            log.error("邮件补偿操作失败", e);
            throw new RuntimeException("邮件补偿失败: " + e.getMessage(), e);
        }
    }

    private boolean shouldSendEmail(MCPCommand command) {
        if (!"mail_alert".equals(command.getTool())) {
            String riskLevel = (String) command.getParams().get("riskLevel");
            return "HIGH".equals(riskLevel);
        }
        return true;
    }

    private boolean isRetryableError(String message) {
        if (message == null) return false;
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("timeout") ||
               lowerMessage.contains("connection") ||
               lowerMessage.contains("temporarily") ||
               lowerMessage.contains("rate limit");
    }

    private void persistSagaState(SagaExecution execution, SagaState state) {
        try {
            SagaStateRecord record = new SagaStateRecord();
            record.setSagaId(execution.getSagaId());
            record.setState(state.name());
            record.setCommand(execution.getCommand());
            record.setTimestamp(LocalDateTime.now());
            
            String recordJson = objectMapper.writeValueAsString(record);
            redisTemplate.opsForHash().put(SAGA_STATE_KEY, execution.getSagaId(), recordJson);
            
            SagaLogEntry logEntry = new SagaLogEntry();
            logEntry.setSagaId(execution.getSagaId());
            logEntry.setState(state.name());
            logEntry.setTimestamp(LocalDateTime.now());
            
            String logJson = objectMapper.writeValueAsString(logEntry);
            redisTemplate.opsForList().rightPush(SAGA_LOG_KEY + ":" + execution.getSagaId(), logJson);
            
        } catch (Exception e) {
            log.error("持久化Saga状态失败", e);
        }
    }

    public SagaExecution getSagaExecution(String sagaId) {
        return activeSagas.get(sagaId);
    }

    public List<SagaExecution> getActiveSagas() {
        return new ArrayList<>(activeSagas.values());
    }

    public interface SagaCompletionCallback {
        void onComplete(MCPResult result);
    }

    @Data
    public static class SagaExecution {
        private final String sagaId;
        private final MCPCommand command;
        private final List<SagaStep> steps = new ArrayList<>();
        private MCPResult excelResult;
        private MCPResult emailResult;
        private LocalDateTime startTime = LocalDateTime.now();
        private LocalDateTime endTime;
        
        public SagaExecution(String sagaId, MCPCommand command) {
            this.sagaId = sagaId;
            this.command = command;
        }
        
        public void addStep(SagaStep step) {
            step.setOrder(steps.size());
            steps.add(step);
        }
    }

    @Data
    public static class SagaStep {
        private final String name;
        private final Callable<MCPResult> action;
        private final Runnable compensation;
        private StepState state = StepState.PENDING;
        private int order;
        private LocalDateTime executedAt;
        private LocalDateTime compensatedAt;
        
        public SagaStep(String name, Callable<MCPResult> action, Runnable compensation) {
            this.name = name;
            this.action = action;
            this.compensation = compensation;
        }
        
        public MCPResult execute() throws Exception {
            this.executedAt = LocalDateTime.now();
            return action.call();
        }
        
        public void compensate() {
            this.compensatedAt = LocalDateTime.now();
            compensation.run();
        }
    }

    public enum StepState {
        PENDING, RUNNING, COMPLETED, FAILED, COMPENSATED, COMPENSATION_FAILED
    }

    public enum SagaState {
        STARTED, EXCEL_COMPLETED, EMAIL_COMPLETED, COMPLETED, 
        COMPENSATING, COMPENSATED, FAILED
    }

    @Data
    public static class SagaStateRecord {
        private String sagaId;
        private String state;
        private MCPCommand command;
        private LocalDateTime timestamp;
    }

    @Data
    public static class SagaLogEntry {
        private String sagaId;
        private String state;
        private LocalDateTime timestamp;
    }

    @Data
    public static class CompensationTask {
        private String sagaId;
        private String stepName;
        private MCPCommand command;
        private String error;
        private LocalDateTime createdAt;
        private int retryCount;
        private String status;
    }
}
