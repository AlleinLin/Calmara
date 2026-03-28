package com.calmara.common;

public class Constants {

    public static final class Emotion {
        public static final String NORMAL = "正常";
        public static final String ANXIOUS = "焦虑";
        public static final String DEPRESSED = "低落";
        public static final String HIGH_RISK = "高风险";

        public static final double NORMAL_SCORE = 0.0;
        public static final double ANXIOUS_SCORE = 2.0;
        public static final double DEPRESSED_SCORE = 3.0;
        public static final double HIGH_RISK_SCORE = 4.0;
    }

    public static final class RiskLevel {
        public static final String LOW = "LOW";
        public static final String MEDIUM = "MEDIUM";
        public static final String HIGH = "HIGH";

        public static final double LOW_THRESHOLD = 1.0;
        public static final double HIGH_THRESHOLD = 2.0;
    }

    public static final class Weights {
        public static final double TEXT_WEIGHT = 0.1;
        public static final double AUDIO_WEIGHT = 0.4;
        public static final double VISUAL_WEIGHT = 0.5;
    }

    public static final class Intent {
        public static final String CHAT = "CHAT";
        public static final String CONSULT = "CONSULT";
        public static final String RISK = "RISK";
    }

    public static final class MCPTool {
        public static final String EXCEL_WRITER = "excel_writer";
        public static final String MAIL_ALERT = "mail_alert";
    }

    public static final class Role {
        public static final String USER = "USER";
        public static final String ADMIN = "ADMIN";
    }

    public static final class Header {
        public static final String AUTHORIZATION = "Authorization";
        public static final String BEARER_PREFIX = "Bearer ";
    }
}
