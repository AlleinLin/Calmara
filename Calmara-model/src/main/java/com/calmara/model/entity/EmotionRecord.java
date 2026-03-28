package com.calmara.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("emotion_record")
public class EmotionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String textEmotion;

    private Double textScore;

    private String audioEmotion;

    private Double audioScore;

    private String visualEmotion;

    private Double visualScore;

    private String fusionEmotion;

    private Double fusionScore;

    private String riskLevel;

    private String inputTypes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
