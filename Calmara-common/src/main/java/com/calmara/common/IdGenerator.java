package com.calmara.common;

import java.util.UUID;

public class IdGenerator {

    public static String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String generateUserId() {
        return "U" + System.currentTimeMillis();
    }

    public static String generateMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String generateAlertId() {
        return "alert_" + System.currentTimeMillis();
    }
}
