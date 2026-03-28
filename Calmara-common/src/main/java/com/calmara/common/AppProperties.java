package com.calmara.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "calmara")
public class AppProperties {

    private OllamaProperties ollama = new OllamaProperties();
    private OpenAIProperties openai = new OpenAIProperties();
    private ChromaProperties chroma = new ChromaProperties();
    private ExternalProperties external = new ExternalProperties();
    private MCPProperties mcp = new MCPProperties();
    private RiskProperties risk = new RiskProperties();

    @Data
    public static class OllamaProperties {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:7b-chat";
        private int timeout = 60000;
    }

    @Data
    public static class OpenAIProperties {
        private String apiKey;
        private String embeddingModel = "text-embedding-3-small";
    }

    @Data
    public static class ChromaProperties {
        private String url = "http://localhost:8000";
        private String collectionName = "psychological_knowledge";
    }

    @Data
    public static class ExternalProperties {
        private WhisperProperties whisper = new WhisperProperties();
        private MediaPipeProperties mediapipe = new MediaPipeProperties();

        @Data
        public static class WhisperProperties {
            private String url = "http://localhost:9000/asr";
            private int timeout = 30000;
        }

        @Data
        public static class MediaPipeProperties {
            private String url = "http://localhost:8001/analyze";
            private int timeout = 10000;
        }
    }

    @Data
    public static class MCPProperties {
        private EmailProperties email = new EmailProperties();
        private ExcelProperties excel = new ExcelProperties();

        @Data
        public static class EmailProperties {
            private String host;
            private int port = 587;
            private String username;
            private String password;
            private String from;
            private boolean enabled = true;
        }

        @Data
        public static class ExcelProperties {
            private String outputDir = "/data/reports";
            private String templatePath = "classpath:templates/record-template.xlsx";
        }
    }

    @Data
    public static class RiskProperties {
        private Thresholds thresholds = new Thresholds();
        private Weights weights = new Weights();

        @Data
        public static class Thresholds {
            private double low = 1.0;
            private double high = 2.0;
        }

        @Data
        public static class Weights {
            private double text = 0.1;
            private double audio = 0.4;
            private double visual = 0.5;
        }
    }
}
