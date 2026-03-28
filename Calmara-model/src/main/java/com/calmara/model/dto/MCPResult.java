package com.calmara.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPResult {
    private boolean success;
    private String message;
    private Map<String, Object> data;

    public static MCPResult success(String message) {
        return new MCPResult(true, message, new HashMap<>());
    }

    public static MCPResult success(String message, Map<String, Object> data) {
        return new MCPResult(true, message, data != null ? data : new HashMap<>());
    }

    public static MCPResult failure(String message) {
        return new MCPResult(false, message, null);
    }

    public MCPResult withData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    public Object getData(String key) {
        return this.data != null ? this.data.get(key) : null;
    }
}
