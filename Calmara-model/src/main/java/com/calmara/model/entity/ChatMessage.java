package com.calmara.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String role;

    private String content;

    private String emotionLabel;

    private String intentType;

    private String modelUsed;

    private Integer tokensUsed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public boolean isUserMessage() {
        return "user".equals(this.role);
    }

    public boolean isAssistantMessage() {
        return "assistant".equals(this.role);
    }
}
