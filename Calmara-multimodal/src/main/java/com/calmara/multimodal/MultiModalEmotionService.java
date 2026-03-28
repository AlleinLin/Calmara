package com.calmara.multimodal;

import com.calmara.model.dto.EmotionResult;
import com.calmara.multimodal.emotion.TextEmotionAnalyzer;
import com.calmara.multimodal.emotion.VisualEmotionCalculator;
import com.calmara.multimodal.fusion.MultiModalFusionEngine;
import com.calmara.multimodal.mediapipe.FaceLandmarks;
import com.calmara.multimodal.mediapipe.MediaPipeClient;
import com.calmara.multimodal.video.VideoEmotionAnalyzer;
import com.calmara.multimodal.whisper.WhisperClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MultiModalEmotionService {

    private final WhisperClient whisperClient;
    private final MediaPipeClient mediaPipeClient;
    private final VisualEmotionCalculator visualEmotionCalculator;
    private final TextEmotionAnalyzer textEmotionAnalyzer;
    private final MultiModalFusionEngine fusionEngine;
    private final VideoEmotionAnalyzer videoEmotionAnalyzer;

    private final Map<String, List<EmotionResult>> sessionEmotionHistory = new ConcurrentHashMap<>();

    public MultiModalEmotionService(WhisperClient whisperClient,
                                     MediaPipeClient mediaPipeClient,
                                     VisualEmotionCalculator visualEmotionCalculator,
                                     TextEmotionAnalyzer textEmotionAnalyzer,
                                     MultiModalFusionEngine fusionEngine,
                                     VideoEmotionAnalyzer videoEmotionAnalyzer) {
        this.whisperClient = whisperClient;
        this.mediaPipeClient = mediaPipeClient;
        this.visualEmotionCalculator = visualEmotionCalculator;
        this.textEmotionAnalyzer = textEmotionAnalyzer;
        this.fusionEngine = fusionEngine;
        this.videoEmotionAnalyzer = videoEmotionAnalyzer;
    }

    public String transcribeAudio(MultipartFile audioFile) {
        return whisperClient.transcribe(audioFile);
    }

    public EmotionResult analyzeText(String text) {
        return textEmotionAnalyzer.analyze(text);
    }

    public EmotionResult analyzeImage(MultipartFile imageFile) {
        FaceLandmarks landmarks = mediaPipeClient.analyzeFace(imageFile);
        if (landmarks == null) {
            return EmotionResult.builder()
                    .label("正常")
                    .score(0.0)
                    .source("visual")
                    .build();
        }
        return visualEmotionCalculator.calculate(landmarks);
    }

    public EmotionResult analyzeVideo(MultipartFile videoFile) {
        log.info("使用VideoEmotionAnalyzer分析视频: {}", videoFile.getOriginalFilename());
        return videoEmotionAnalyzer.analyzeVideo(videoFile);
    }

    public EmotionResult analyzeAudio(MultipartFile audioFile) {
        String text = whisperClient.transcribe(audioFile);
        return textEmotionAnalyzer.analyze(text);
    }

    public EmotionResult fuseEmotions(List<EmotionResult> emotionResults) {
        return fusionEngine.fuse(emotionResults);
    }

    public EmotionResult analyzeAll(String text, MultipartFile audio,
                                    MultipartFile image, MultipartFile video) {
        List<CompletableFuture<EmotionResult>> futures = new ArrayList<>();
        List<EmotionResult> results = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            futures.add(CompletableFuture.supplyAsync(() -> textEmotionAnalyzer.analyze(text)));
        }

        if (audio != null && !audio.isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                String transcribed = whisperClient.transcribe(audio);
                return textEmotionAnalyzer.analyze(transcribed);
            }));
        }

        if (image != null && !image.isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                FaceLandmarks landmarks = mediaPipeClient.analyzeFace(image);
                if (landmarks != null) {
                    return visualEmotionCalculator.calculate(landmarks);
                }
                return null;
            }));
        }

        if (video != null && !video.isEmpty()) {
            futures.add(CompletableFuture.supplyAsync(() -> 
                    videoEmotionAnalyzer.analyzeVideo(video)));
        }

        for (CompletableFuture<EmotionResult> future : futures) {
            try {
                EmotionResult result = future.join();
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.error("情绪分析异常", e);
            }
        }

        return fusionEngine.fuse(results);
    }

    public EmotionResult analyzeWithContext(String sessionId, String text, 
                                            MultipartFile audio, MultipartFile image, 
                                            MultipartFile video) {
        EmotionResult currentEmotion = analyzeAll(text, audio, image, video);
        
        List<EmotionResult> history = sessionEmotionHistory.computeIfAbsent(
                sessionId, k -> new ArrayList<>());
        history.add(currentEmotion);
        
        if (history.size() > 10) {
            history.remove(0);
        }
        
        if (history.size() >= 3) {
            EmotionResult trendAdjusted = adjustForTrend(currentEmotion, history);
            return trendAdjusted;
        }
        
        return currentEmotion;
    }

    private EmotionResult adjustForTrend(EmotionResult current, List<EmotionResult> history) {
        double avgScore = history.stream()
                .mapToDouble(EmotionResult::getScore)
                .average()
                .orElse(current.getScore());
        
        double trendFactor = 0.0;
        if (history.size() >= 3) {
            int recent = Math.min(3, history.size());
            double recentAvg = history.subList(history.size() - recent, history.size())
                    .stream()
                    .mapToDouble(EmotionResult::getScore)
                    .average()
                    .orElse(current.getScore());
            
            if (recentAvg > avgScore + 0.5) {
                trendFactor = 0.3;
            } else if (recentAvg < avgScore - 0.5) {
                trendFactor = -0.2;
            }
        }
        
        double adjustedScore = Math.max(0, Math.min(4, current.getScore() + trendFactor));
        
        return EmotionResult.builder()
                .label(current.getLabel())
                .score(adjustedScore)
                .source(current.getSource())
                .riskLevel(current.getRiskLevel())
                .build();
    }

    public List<EmotionResult> getEmotionHistory(String sessionId) {
        return sessionEmotionHistory.getOrDefault(sessionId, new ArrayList<>());
    }

    public void clearSessionHistory(String sessionId) {
        sessionEmotionHistory.remove(sessionId);
    }

    public void clearAllHistory() {
        sessionEmotionHistory.clear();
    }
}
