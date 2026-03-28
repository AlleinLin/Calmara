package com.calmara.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private Long emotionRecordId;

    private String riskLevel;

    private String content;

    private String status;

    private Long handlerId;

    private String handlerName;

    private String handleNote;

    private LocalDateTime handledAt;

    private Boolean emailSent;

    private LocalDateTime emailSentAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
