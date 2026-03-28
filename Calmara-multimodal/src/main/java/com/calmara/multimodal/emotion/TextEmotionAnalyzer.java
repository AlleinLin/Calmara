package com.calmara.multimodal.emotion;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.EmotionLabel;
import com.calmara.model.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TextEmotionAnalyzer {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的心理情绪分析专家，专门分析中文文本中的情绪状态。
            你需要根据用户的文本内容，判断其情绪状态。
            """;

    private static final String EMOTION_PROMPT = """
            请分析以下用户文本的情绪状态。
            
            用户输入：%s
            
            请严格按照以下JSON格式输出，不要输出其他内容：
            {
                "emotion": "正常|焦虑|低落|高风险",
                "confidence": 0.0-1.0,
                "reasoning": "简短的分析理由",
                "risk_indicators": ["风险指标1", "风险指标2"]
            }
            
            情绪标签定义：
            - 正常：情绪平稳，无明显负面情绪，日常交流
            - 焦虑：紧张、担忧、睡不着、焦虑不安、压力大
            - 低落：悲伤、沮丧、失落、无助、缺乏动力
            - 高风险：自杀念头、自残倾向、绝望、严重抑郁、极端表述
            
            注意：
            1. 对于高风险情绪，必须在risk_indicators中列出具体的风险指标
            2. confidence表示你对判断的置信度
            3. 只输出JSON，不要输出其他内容
            """;

    private final ChatClient chatClient;
    
    @Value("${calmara.emotion.use-finetuned-model:true}")
    private boolean useFinetunedModel;
    
    @Value("${calmara.ollama.finetuned-model:qwen2.5-calmara:latest}")
    private String finetunedModel;

    public TextEmotionAnalyzer(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public EmotionResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return EmotionResult.builder()
                    .label("正常")
                    .score(0.0)
                    .confidence(1.0)
                    .source("text")
                    .riskLevel(RiskLevel.LOW)
                    .build();
        }

        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SYSTEM_PROMPT));
            messages.add(new UserMessage(String.format(EMOTION_PROMPT, text)));
            
            Prompt prompt = new Prompt(messages);

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent();

            EmotionResult result = parseEmotionResponse(response, text);
            
            log.info("文本情绪分析完成: input='{}', emotion={}, score={}, confidence={}", 
                    text.length() > 50 ? text.substring(0, 50) + "..." : text,
                    result.getLabel(), result.getScore(), result.getConfidence());

            return result;

        } catch (Exception e) {
            log.error("文本情绪分析失败", e);
            return fallbackAnalysis(text);
        }
    }

    private EmotionResult parseEmotionResponse(String response, String originalText) {
        String emotionLabel = "正常";
        double confidence = 0.5;
        String reasoning = "";
        
        try {
            String jsonStr = extractJson(response);
            
            if (jsonStr != null) {
                emotionLabel = extractJsonValue(jsonStr, "emotion");
                String confidenceStr = extractJsonValue(jsonStr, "confidence");
                if (confidenceStr != null) {
                    confidence = Double.parseDouble(confidenceStr);
                }
                reasoning = extractJsonValue(jsonStr, "reasoning");
            }
        } catch (Exception e) {
            log.warn("解析情绪响应失败，使用简单解析: {}", e.getMessage());
            emotionLabel = simpleParse(response);
        }
        
        EmotionLabel emotion = EmotionLabel.fromLabel(emotionLabel);
        
        return EmotionResult.builder()
                .label(emotion.getLabel())
                .score(emotion.getScore())
                .confidence(confidence)
                .source("text")
                .riskLevel(RiskLevel.fromScore(emotion.getScore()))
                .reasoning(reasoning)
                .build();
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^,\"\\}]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim().replace("\"", "");
        }
        return null;
    }

    private String simpleParse(String response) {
        String lowerResponse = response.toLowerCase();
        
        if (lowerResponse.contains("高风险") || lowerResponse.contains("自杀") || 
            lowerResponse.contains("自残") || lowerResponse.contains("绝望")) {
            return "高风险";
        }
        if (lowerResponse.contains("低落") || lowerResponse.contains("悲伤") || 
            lowerResponse.contains("沮丧") || lowerResponse.contains("抑郁")) {
            return "低落";
        }
        if (lowerResponse.contains("焦虑") || lowerResponse.contains("紧张") || 
            lowerResponse.contains("担忧") || lowerResponse.contains("压力")) {
            return "焦虑";
        }
        return "正常";
    }

    private EmotionResult fallbackAnalysis(String text) {
        String emotionLabel = simpleParse(text);
        EmotionLabel emotion = EmotionLabel.fromLabel(emotionLabel);
        
        return EmotionResult.builder()
                .label(emotion.getLabel())
                .score(emotion.getScore())
                .confidence(0.3)
                .source("text")
                .riskLevel(RiskLevel.fromScore(emotion.getScore()))
                .reasoning("基于关键词的简单分析")
                .build();
    }
}
