package com.calmara.model.dto;

import com.calmara.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAlert {
    private String userId;
    private String userName;
    private String emotion;
    private Double emotionScore;
    private RiskLevel riskLevel;
    private String content;
    private List<String> recipients;
}
