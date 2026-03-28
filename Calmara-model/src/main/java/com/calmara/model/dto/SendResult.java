package com.calmara.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendResult {
    private boolean success;
    private String message;

    public static SendResult success() {
        return new SendResult(true, "发送成功");
    }

    public static SendResult success(String message) {
        return new SendResult(true, message);
    }

    public static SendResult failure(String message) {
        return new SendResult(false, message);
    }
}
