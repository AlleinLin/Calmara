package com.calmara.mcp;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MCPCoordinator {

    private final MCPStateMachine stateMachine;
    private final ExcelMCPService excelService;
    private final EmailMCPService emailService;
    private final ObjectMapper objectMapper;

    public MCPCoordinator(MCPStateMachine stateMachine,
                          ExcelMCPService excelService,
                          EmailMCPService emailService,
                          ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.excelService = excelService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    public MCPResult execute(String commandJson) {
        try {
            MCPCommand command = objectMapper.readValue(commandJson, MCPCommand.class);
            log.info("执行MCP命令: tool={}", command.getTool());
            return stateMachine.executeWithStateMachine(command);
        } catch (Exception e) {
            log.error("MCP命令执行失败", e);
            return MCPResult.failure("MCP命令执行失败: " + e.getMessage());
        }
    }

    public MCPResult executeAsync(String commandJson, MCPStateMachine.MCPCompletionCallback callback) {
        try {
            MCPCommand command = objectMapper.readValue(commandJson, MCPCommand.class);
            log.info("异步执行MCP命令: tool={}", command.getTool());
            return stateMachine.executeWithStateMachineAsync(command, callback);
        } catch (Exception e) {
            log.error("MCP命令异步执行失败", e);
            return MCPResult.failure("MCP命令执行失败: " + e.getMessage());
        }
    }

    public MCPResult executeExcelWrite(PsychRecord record) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", record.getUserId());
            params.put("content", record.getContent());
            params.put("emotion", record.getEmotion());
            params.put("emotionScore", record.getEmotionScore());
            params.put("riskLevel", record.getRiskLevel());

            MCPCommand command = MCPCommand.builder()
                    .tool("excel_writer")
                    .params(params)
                    .build();

            return stateMachine.executeWithStateMachine(command);
        } catch (Exception e) {
            log.error("Excel写入失败", e);
            return MCPResult.failure("Excel写入失败: " + e.getMessage());
        }
    }

    public MCPResult executeEmailAlert(RiskAlert alert) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", alert.getUserId());
            params.put("userName", alert.getUserName());
            params.put("emotion", alert.getEmotion());
            params.put("emotionScore", alert.getEmotionScore());
            params.put("content", alert.getContent());
            params.put("recipients", alert.getRecipients());

            MCPCommand command = MCPCommand.builder()
                    .tool("mail_alert")
                    .params(params)
                    .build();

            return stateMachine.executeWithStateMachine(command);
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            return MCPResult.failure("邮件发送失败: " + e.getMessage());
        }
    }

    public List<MCPStateMachine.MCPExecution> getActiveExecutions() {
        return stateMachine.getActiveExecutions();
    }

    @Data
    public static class MCPParams {
        private String userId;
        private String content;
        private String emotion;
        private Double emotionScore;
        private String riskLevel;
        private List<String> recipients;
    }
}
