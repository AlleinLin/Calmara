package com.calmara.multimodal.emotion;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.EmotionLabel;
import com.calmara.model.enums.RiskLevel;
import com.calmara.multimodal.mediapipe.FaceLandmarks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VisualEmotionCalculator {

    private static final double BROW_THRESHOLD = 0.02;
    private static final double MOUTH_THRESHOLD = -0.01;
    private static final double EYE_THRESHOLD = 0.02;
    private static final double CHEEK_THRESHOLD = 0.03;

    public EmotionResult calculate(FaceLandmarks landmarks) {
        if (landmarks == null || landmarks.getLandmarks() == null) {
            return EmotionResult.builder()
                    .label("正常")
                    .score(0.0)
                    .source("visual")
                    .riskLevel(RiskLevel.LOW)
                    .build();
        }

        double totalScore = 0.0;

        double browScore = calculateBrowScore(landmarks);
        log.debug("眉毛得分: {}", browScore);
        if (browScore > BROW_THRESHOLD) {
            totalScore += 1.5;
        }

        double mouthScore = calculateMouthScore(landmarks);
        log.debug("嘴角得分: {}", mouthScore);
        if (mouthScore < MOUTH_THRESHOLD) {
            totalScore += 1.0;
        }

        double eyeScore = calculateEyeOpenness(landmarks);
        log.debug("眼睛开合度: {}", eyeScore);
        if (eyeScore < EYE_THRESHOLD) {
            totalScore += 1.0;
        }

        double cheekScore = calculateCheekTension(landmarks);
        log.debug("脸颊紧绷度: {}", cheekScore);
        if (cheekScore > CHEEK_THRESHOLD) {
            totalScore += 1.5;
        }

        String label = determineLabel(totalScore);
        RiskLevel riskLevel = determineRiskLevel(totalScore);

        log.info("视觉情绪计算完成: label={}, score={}, riskLevel={}", label, totalScore, riskLevel);

        return EmotionResult.builder()
                .label(label)
                .score(totalScore)
                .source("visual")
                .riskLevel(riskLevel)
                .build();
    }

    private double calculateBrowScore(FaceLandmarks landmarks) {
        float[] leftBrowInner = landmarks.getLandmark(FaceLandmarks.LEFT_BROW_INNER);
        float[] leftBrowOuter = landmarks.getLandmark(FaceLandmarks.LEFT_BROW_OUTER);
        float[] rightBrowInner = landmarks.getLandmark(FaceLandmarks.RIGHT_BROW_INNER);
        float[] rightBrowOuter = landmarks.getLandmark(FaceLandmarks.RIGHT_BROW_OUTER);

        if (leftBrowInner == null || leftBrowOuter == null ||
                rightBrowInner == null || rightBrowOuter == null) {
            return 0.0;
        }

        double leftDiff = leftBrowInner[1] - leftBrowOuter[1];
        double rightDiff = rightBrowInner[1] - rightBrowOuter[1];

        return (leftDiff + rightDiff) / 2.0;
    }

    private double calculateMouthScore(FaceLandmarks landmarks) {
        float[] mouthLeft = landmarks.getLandmark(FaceLandmarks.MOUTH_LEFT);
        float[] mouthRight = landmarks.getLandmark(FaceLandmarks.MOUTH_RIGHT);

        if (mouthLeft == null || mouthRight == null) {
            return 0.0;
        }

        return (mouthLeft[1] + mouthRight[1]) / 2.0 - 0.5;
    }

    private double calculateEyeOpenness(FaceLandmarks landmarks) {
        float[] leftTop = landmarks.getLandmark(FaceLandmarks.LEFT_EYE_TOP);
        float[] leftBottom = landmarks.getLandmark(FaceLandmarks.LEFT_EYE_BOTTOM);
        float[] rightTop = landmarks.getLandmark(FaceLandmarks.RIGHT_EYE_TOP);
        float[] rightBottom = landmarks.getLandmark(FaceLandmarks.RIGHT_EYE_BOTTOM);

        if (leftTop == null || leftBottom == null ||
                rightTop == null || rightBottom == null) {
            return 0.1;
        }

        double leftOpenness = Math.abs(leftTop[1] - leftBottom[1]);
        double rightOpenness = Math.abs(rightTop[1] - rightBottom[1]);

        return (leftOpenness + rightOpenness) / 2.0;
    }

    private double calculateCheekTension(FaceLandmarks landmarks) {
        int[] cheekIndices = {123, 352, 234, 454, 227, 447};

        double sumVariance = 0.0;
        int count = 0;

        for (int idx : cheekIndices) {
            float[] point = landmarks.getLandmark(idx);
            if (point != null) {
                sumVariance += Math.abs(point[2]);
                count++;
            }
        }

        return count > 0 ? sumVariance / count : 0.0;
    }

    private String determineLabel(double score) {
        if (score >= 4.0) {
            return EmotionLabel.HIGH_RISK.getLabel();
        } else if (score >= 3.0) {
            return EmotionLabel.DEPRESSED.getLabel();
        } else if (score >= 2.0) {
            return EmotionLabel.ANXIOUS.getLabel();
        } else {
            return EmotionLabel.NORMAL.getLabel();
        }
    }

    private RiskLevel determineRiskLevel(double score) {
        if (score >= 4.0) {
            return RiskLevel.HIGH;
        } else if (score >= 2.0) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }
}
