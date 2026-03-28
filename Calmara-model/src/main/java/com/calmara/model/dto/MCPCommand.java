package com.calmara.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPCommand {
    private String tool;
    private Map<String, Object> params;

    public boolean isExcelWriter() {
        return "excel_writer".equals(tool);
    }

    public boolean isMailAlert() {
        return "mail_alert".equals(tool);
    }
}
