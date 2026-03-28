package com.calmara.mcp;

import com.calmara.mcp.email.EmailMCPService;
import com.calmara.mcp.excel.ExcelMCPService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MCPCoordinatorTest {

    @Mock
    private ExcelMCPService excelService;

    @Mock
    private EmailMCPService emailService;

    @Mock
    private MCPStateMachine stateMachine;

    private MCPCoordinator coordinator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        coordinator = new MCPCoordinator(stateMachine, excelService, emailService, objectMapper);
    }

    @Test
    void testExecute_ExcelWrite_Success() throws Exception {
        when(excelService.writeRecord(any())).thenReturn("/tmp/test.xlsx");

        String command = "{\"tool\":\"excel_writer\",\"params\":{\"userId\":\"test\",\"content\":\"test content\",\"emotion\":\"焦虑\",\"riskLevel\":\"MEDIUM\"}}";

        var result = coordinator.execute(command);

        assertTrue(result.isSuccess());
        verify(excelService, times(1)).writeRecord(any());
    }

    @Test
    void testExecute_EmailAlert_Success() throws Exception {
        when(emailService.sendAlert(any())).thenReturn(com.calmara.model.dto.SendResult.success("发送成功"));

        String command = "{\"tool\":\"mail_alert\",\"params\":{\"userId\":\"test\",\"emotion\":\"高风险\",\"recipients\":[\"test@example.com\"]}}";

        var result = coordinator.execute(command);

        assertTrue(result.isSuccess());
        verify(emailService, times(1)).sendAlert(any());
    }

    @Test
    void testExecute_UnknownTool_ReturnsFailure() throws Exception {
        String command = "{\"tool\":\"unknown_tool\",\"params\":{}}";

        var result = coordinator.execute(command);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未知工具"));
    }

    @Test
    void testExecute_InvalidJson_ReturnsFailure() {
        String invalidCommand = "invalid json";

        var result = coordinator.execute(invalidCommand);

        assertFalse(result.isSuccess());
    }

    @Test
    void testExecute_ExcelWrite_Exception() throws Exception {
        when(excelService.writeRecord(any())).thenThrow(new RuntimeException("Excel error"));

        String command = "{\"tool\":\"excel_writer\",\"params\":{\"userId\":\"test\"}}";

        var result = coordinator.execute(command);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Excel写入失败"));
    }
}
