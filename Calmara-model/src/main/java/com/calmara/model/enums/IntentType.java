package com.calmara.model.enums;

import lombok.Getter;

@Getter
public enum IntentType {
    CHAT("日常闲聊", false, false),
    CONSULT("心理咨询", true, false),
    RISK("高风险", true, true);

    private final String description;
    private final boolean needRecord;
    private final boolean needAlert;

    IntentType(String description, boolean needRecord, boolean needAlert) {
        this.description = description;
        this.needRecord = needRecord;
        this.needAlert = needAlert;
    }

    public boolean shouldRecord() {
        return needRecord;
    }

    public boolean shouldAlert() {
        return needAlert;
    }
}
