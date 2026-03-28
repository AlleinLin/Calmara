package com.calmara.agent.rag;

import com.calmara.model.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ResultValidator {

    private static final String VALIDATE_PROMPT = """
            请判断以下回答是否合理、相关、无幻觉且安全：

            用户问题：%s

            AI回答：%s

            参考资料：%s

            判断标准：
            1. 回答是否与问题相关
            2. 回答是否基于参考资料，而非编造
            3. 回答是否安全（不包含自伤建议、危险用药建议等）
            4. 回答语气是否温和、专业

            如果回答合理，只输出：VALID
            如果回答不合理，输出：INVALID - 具体原因
            """;

    private final ChatClient chatClient;

    public ResultValidator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ValidationResult validate(String answer, String query, String context) {
        try {
            String promptText = String.format(VALIDATE_PROMPT,
                    query,
                    answer,
                    context != null ? context : "无");
            Prompt prompt = new Prompt(List.of(new UserMessage(promptText)));

            String response = chatClient.call(prompt)
                    .getResult()
                    .getOutput()
                    .getContent()
                    .trim();

            if (response.startsWith("VALID")) {
                log.info("回答校验通过");
                return ValidationResult.valid();
            } else {
                String reason = response.replace("INVALID", "").replace("-", "").trim();
                log.warn("回答校验不通过: {}", reason);
                return ValidationResult.invalid(reason);
            }

        } catch (Exception e) {
            log.error("回答校验失败", e);
            return ValidationResult.valid();
        }
    }
}
