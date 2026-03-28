package com.calmara.multimodal.fusion;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.EmotionLabel;
import com.calmara.model.enums.RiskLevel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class MultiModalFusionEngine {

    private static final double TEXT_WEIGHT = 0.1;
    private static final double AUDIO_WEIGHT = 0.4;
    private static final double VISUAL_WEIGHT = 0.5;
    private static final double VIDEO_WEIGHT = 0.5;

    private static final double LOW_THRESHOLD = 1.0;
    private static final double HIGH_THRESHOLD = 2.0;

    public EmotionResult fuse(EmotionResult textEmotion,
                               EmotionResult audioEmotion,
                               EmotionResult visualEmotion) {
        return fuseWithVideo(textEmotion, audioEmotion, visualEmotion, null);
    }

    public EmotionResult fuseWithVideo(EmotionResult textEmotion,
                                        EmotionResult audioEmotion,
                                        EmotionResult visualEmotion,
                                        EmotionResult videoEmotion) {
        List<WeightedEmotion> validEmotions = new ArrayList<>();

        if (textEmotion != null) {
            validEmotions.add(new WeightedEmotion(textEmotion, TEXT_WEIGHT));
        }
        if (audioEmotion != null) {
            validEmotions.add(new WeightedEmotion(audioEmotion, AUDIO_WEIGHT));
        }
        if (visualEmotion != null) {
            validEmotions.add(new WeightedEmotion(visualEmotion, VISUAL_WEIGHT));
        }
        if (videoEmotion != null) {
            validEmotions.add(new WeightedEmotion(videoEmotion, VIDEO_WEIGHT));
        }

        if (validEmotions.isEmpty()) {
            return EmotionResult.builder()
                    .label("正常")
                    .score(0.0)
                    .source("fusion")
                    .riskLevel(RiskLevel.LOW)
                    .build();
        }

        double totalWeight = validEmotions.stream()
                .mapToDouble(WeightedEmotion::getWeight)
                .sum();

        double weightedScore = validEmotions.stream()
                .mapToDouble(we -> labelToScore(we.getEmotion().getLabel()) * we.getWeight() / totalWeight)
                .sum();

        RiskLevel riskLevel = determineRiskLevel(weightedScore);

        String dominantLabel = determineDominantLabel(validEmotions);

        log.info("情绪融合完成: label={}, score={}, riskLevel={}, sources={}",
                dominantLabel, weightedScore, riskLevel,
                validEmotions.stream().map(e -> e.getEmotion().getSource()).toList());

        return EmotionResult.builder()
                .label(dominantLabel)
                .score(weightedScore)
                .source("fusion")
                .riskLevel(riskLevel)
                .build();
    }

    public EmotionResult fuse(List<EmotionResult> emotions) {
        EmotionResult text = null, audio = null, visual = null, video = null;

        for (EmotionResult emotion : emotions) {
            if (emotion == null) continue;

            String source = emotion.getSource();
            if ("text".equals(source)) {
                text = emotion;
            } else if ("audio".equals(source)) {
                audio = emotion;
            } else if ("visual".equals(source)) {
                visual = emotion;
            } else if ("video".equals(source)) {
                video = emotion;
            }
        }

        return fuseWithVideo(text, audio, visual, video);
    }

    private double labelToScore(String label) {
        return switch (label) {
            case "正常" -> 0.0;
            case "焦虑" -> 2.0;
            case "低落" -> 3.0;
            case "高风险" -> 4.0;
            default -> 0.0;
        };
    }

    private RiskLevel determineRiskLevel(double score) {
        if (score >= HIGH_THRESHOLD) {
            return RiskLevel.HIGH;
        } else if (score >= LOW_THRESHOLD) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

    private String determineDominantLabel(List<WeightedEmotion> emotions) {
        return emotions.stream()
                .max(Comparator.comparingDouble(we -> labelToScore(we.getEmotion().getLabel())))
                .map(we -> we.getEmotion().getLabel())
                .orElse("正常");
    }

    @Data
    private static class WeightedEmotion {
        private final EmotionResult emotion;
        private final double weight;

        public double getWeight() {
            return weight;
        }

        public EmotionResult getEmotion() {
            return emotion;
        }
    }
}
