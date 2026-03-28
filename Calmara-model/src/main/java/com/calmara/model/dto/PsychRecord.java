package com.calmara.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PsychRecord {
    private String userId;
    private String content;
    private String emotion;
    private Double emotionScore;
    private String riskLevel;
    private LocalDateTime timestamp;
}
