package com.calmara.agent.intent;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.IntentType;
import com.calmara.model.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class IntentClassifier {

    private static final String INTENT_PROMPT = """
            你是一个用户意图分类器，只做意图识别，不回答问题。
            用户输入内容：%s

            请将用户意图严格分为以下三类之一，只输出标签，不要其他任何内容：
            - CHAT：日常闲聊、问候、天气、娱乐、无关内容
            - CONSULT：心理咨询、情绪倾诉、压力、焦虑、低落、失眠、亲密关系、学习压力等心理相关
            - RISK：自杀、自残、绝望、自伤、伤人、严重抑郁等高危内容

            只输出以下三个词之一：CHAT、CONSULT、RISK
            """;

    private final ChatClient chatClient;

    public IntentClassifier(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public IntentType classify(String text, EmotionResult emotionResult) {
        if (emotionResult != null && emotionResult.getRiskLevel() == RiskLevel.HIGH) {
            log.info("情绪高风险，直接标记为RISK意图");
            return IntentType.RISK;
        }

        try {
            String promptText = String.format(INTENT_PROMPT, text);
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim()
                    .toUpperCase()
                    .replace("。", "")
                    .replace("，", "");

            log.info("意图分类结果: input='{}', intent={}", text, response);

            IntentType intentType = parseIntentType(response);

            if (intentType == IntentType.CHAT &&
                    emotionResult != null &&
                    emotionResult.getRiskLevel() == RiskLevel.MEDIUM) {
                log.info("情绪中风险，将CHAT升级为CONSULT");
                return IntentType.CONSULT;
            }

            return intentType;

        } catch (Exception e) {
            log.error("意图分类失败，默认返回CONSULT", e);
            return IntentType.CONSULT;
        }
    }

    private IntentType parseIntentType(String response) {
        if (response.contains("CHAT")) {
            return IntentType.CHAT;
        } else if (response.contains("CONSULT")) {
            return IntentType.CONSULT;
        } else if (response.contains("RISK")) {
            return IntentType.RISK;
        }
        return IntentType.CONSULT;
    }
}
