package com.calmara.model.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
