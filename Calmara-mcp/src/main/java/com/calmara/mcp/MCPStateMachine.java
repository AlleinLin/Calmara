package com.calmara.mcp;

import com.calmara.model.dto.MCPCommand;
import com.calmara.model.dto.MCPResult;
import com.calmara.model.dto.PsychRecord;
import com.calmara.model.dto.RiskAlert;
import com.calmara.mcp.email.EmailMCPService;
import com.calmara.mcp.excel.ExcelMCPService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class MCPStateMachine {

    private final ExcelMCPService excelService;
    private final EmailMCPService emailService;
    private final ObjectMapper objectMapper;

    private final Map<String, MCPExecution> executions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(2);

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000;

    public MCPStateMachine(ExcelMCPService excelService,
                           EmailMCPService emailService,
                           ObjectMapper objectMapper) {
        this.excelService = excelService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    public MCPResult executeWithStateMachine(MCPCommand command) {
        String executionId = UUID.randomUUID().toString();
        MCPExecution execution = new MCPExecution(executionId, command);
        executions.put(executionId, execution);

        try {
            return processExecution(execution);
        } finally {
            executions.remove(executionId);
        }
    }

    public MCPResult executeWithStateMachineAsync(MCPCommand command,
                                                   MCPCompletionCallback callback) {
        String executionId = UUID.randomUUID().toString();
        MCPExecution execution = new MCPExecution(executionId, command);
        executions.put(executionId, execution);

        CompletableFuture.supplyAsync(() -> processExecution(execution))
                .thenAccept(result -> {
                    executions.remove(executionId);
                    if (callback != null) {
                        callback.onComplete(executionId, result);
                    }
                })
                .exceptionally(e -> {
                    executions.remove(executionId);
                    log.error("异步MCP执行失败: {}", e.getMessage());
                    return null;
                });

        return MCPResult.success("执行已提交").withData("executionId", executionId);
    }

    private MCPResult processExecution(MCPExecution execution) {
        MCPCommand command = execution.getCommand();
        String tool = command.getTool();

        execution.setState(MCPState.PROCESSING);
        log.info("MCP执行开始: id={}, tool={}", execution.getId(), tool);

        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= MAX_RETRIES) {
            try {
                MCPResult result = executeTool(tool, command);

                if (result.isSuccess()) {
                    execution.setState(MCPState.COMPLETED);
                    execution.setResult(result);
                    log.info("MCP执行成功: id={}, tool={}", execution.getId(), tool);
                    return result;
                }

                lastException = new RuntimeException(result.getMessage());

            } catch (Exception e) {
                lastException = e;
                log.warn("MCP执行失败(第{}次): id={}, error={}", retryCount + 1, execution.getId(), e.getMessage());
            }

            retryCount++;
            execution.setRetryCount(retryCount);

            if (retryCount <= MAX_RETRIES) {
                execution.setState(MCPState.RETRYING);
                log.info("等待重试: id={}, 第{}次", execution.getId(), retryCount);

                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        execution.setState(MCPState.FAILED);
        execution.setError(lastException != null ? lastException.getMessage() : "Unknown error");

        log.error("MCP执行最终失败: id={}, tool={}, retries={}",
                execution.getId(), tool, retryCount);

        return MCPResult.failure("执行失败(已重试" + retryCount + "次): " +
                (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    @SuppressWarnings("unchecked")
    private MCPResult executeTool(String tool, MCPCommand command) {
        return switch (tool) {
            case "excel_writer" -> executeExcelWrite(command);
            case "mail_alert" -> executeEmailAlert(command);
            default -> {
                log.warn("未知的MCP工具: {}", tool);
                yield MCPResult.failure("未知工具: " + tool);
            }
        };
    }

    private MCPResult executeExcelWrite(MCPCommand command) {
        try {
            Map<String, Object> params = command.getParams();

            PsychRecord record = PsychRecord.builder()
                    .userId(getStringValue(params, "userId"))
                    .content(getStringValue(params, "content"))
                    .emotion(getStringValue(params, "emotion"))
                    .emotionScore(getDoubleValue(params, "emotionScore"))
                    .riskLevel(getStringValue(params, "riskLevel"))
                    .timestamp(LocalDateTime.now())
                    .build();

            String filePath = excelService.writeRecord(record);

            log.info("Excel写入成功: {}", filePath);
            return MCPResult.success("Excel写入成功").withData("filePath", filePath);

        } catch (Exception e) {
            log.error("Excel写入失败", e);
            return MCPResult.failure("Excel写入失败: " + e.getMessage());
        }
    }

    private MCPResult executeEmailAlert(MCPCommand command) {
        try {
            Map<String, Object> params = command.getParams();

            List<String> recipients = null;
            Object recipientsObj = params.get("recipients");
            if (recipientsObj instanceof List) {
                recipients = (List<String>) recipientsObj;
            }

            RiskAlert alert = RiskAlert.builder()
                    .userId(getStringValue(params, "userId"))
                    .userName(getStringValue(params, "userName"))
                    .emotion(getStringValue(params, "emotion"))
                    .emotionScore(getDoubleValue(params, "emotionScore"))
                    .riskLevel(null)
                    .content(getStringValue(params, "content"))
                    .recipients(recipients)
                    .build();

            var result = emailService.sendAlert(alert);

            if (result.isSuccess()) {
                return MCPResult.success(result.getMessage());
            } else {
                return MCPResult.failure(result.getMessage());
            }

        } catch (Exception e) {
            log.error("邮件发送失败", e);
            return MCPResult.failure("邮件发送失败: " + e.getMessage());
        }
    }

    public Optional<MCPExecution> getExecution(String executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    public List<MCPExecution> getActiveExecutions() {
        return new ArrayList<>(executions.values());
    }

    private String getStringValue(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }

    private Double getDoubleValue(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public enum MCPState {
        PENDING,
        PROCESSING,
        RETRYING,
        COMPLETED,
        FAILED
    }

    @Data
    public static class MCPExecution {
        private final String id;
        private final MCPCommand command;
        private MCPState state = MCPState.PENDING;
        private int retryCount = 0;
        private MCPResult result;
        private String error;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime completedAt;

        public MCPExecution(String id, MCPCommand command) {
            this.id = id;
            this.command = command;
        }

        public void setState(MCPState state) {
            this.state = state;
            if (state == MCPState.COMPLETED || state == MCPState.FAILED) {
                this.completedAt = LocalDateTime.now();
            }
        }
    }

    @FunctionalInterface
    public interface MCPCompletionCallback {
        void onComplete(String executionId, MCPResult result);
    }
}
