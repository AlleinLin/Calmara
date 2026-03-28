package com.calmara.model.enums;

import lombok.Getter;

@Getter
public enum AlertStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    EMAIL_SENT("邮件已发送"),
    EMAIL_FAILED("邮件发送失败"),
    EXCEL_WRITTEN("Excel已写入"),
    RESOLVED("已解决"),
    FAILED("处理失败");

    private final String description;

    AlertStatus(String description) {
        this.description = description;
    }
}
