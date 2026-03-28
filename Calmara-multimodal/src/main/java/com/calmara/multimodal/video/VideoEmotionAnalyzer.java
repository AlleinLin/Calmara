package com.calmara.multimodal.video;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.RiskLevel;
import com.calmara.multimodal.emotion.VisualEmotionCalculator;
import com.calmara.multimodal.mediapipe.FaceLandmarks;
import com.calmara.multimodal.mediapipe.MediaPipeClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

@Slf4j
@Component
public class VideoEmotionAnalyzer {

    private final MediaPipeClient mediaPipeClient;
    private final VisualEmotionCalculator visualEmotionCalculator;
    private final RestTemplate restTemplate;

    @Value("${calmara.video.frame-sample-count:10}")
    private int frameSampleCount;

    @Value("${calmara.video.max-frames:30}")
    private int maxFrames;

    @Value("${calmara.video.time-weight-decay:0.1}")
    private double timeWeightDecay;

    @Value("${calmara.external.video-processor.url:http://localhost:8002}")
    private String videoProcessorUrl;

    @Value("${calmara.video.use-external-processor:true}")
    private boolean useExternalProcessor;

    public VideoEmotionAnalyzer(MediaPipeClient mediaPipeClient,
                                 VisualEmotionCalculator visualEmotionCalculator,
                                 RestTemplate restTemplate) {
        this.mediaPipeClient = mediaPipeClient;
        this.visualEmotionCalculator = visualEmotionCalculator;
        this.restTemplate = restTemplate;
    }

    public EmotionResult analyzeVideo(MultipartFile videoFile) {
        log.info("开始视频情绪分析: filename={}, size={}bytes",
                videoFile.getOriginalFilename(), videoFile.getSize());

        try {
            List<BufferedImage> frames = extractFrames(videoFile);

            if (frames.isEmpty()) {
                log.warn("无法从视频中提取帧，返回默认结果");
                return createDefaultResult();
            }

            log.info("成功提取{}帧，开始分析", frames.size());

            List<FrameEmotionResult> frameResults = new ArrayList<>();

            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                try {
                    FaceLandmarks landmarks = mediaPipeClient.analyzeFaceFromImage(frame);
                    if (landmarks != null) {
                        EmotionResult emotion = visualEmotionCalculator.calculate(landmarks);
                        frameResults.add(new FrameEmotionResult(i, emotion));
                        log.debug("帧{}分析完成: label={}, score={}",
                                i, emotion.getLabel(), emotion.getScore());
                    }
                } catch (Exception e) {
                    log.warn("帧{}分析失败: {}", i, e.getMessage());
                }
            }

            if (frameResults.isEmpty()) {
                log.warn("所有帧分析失败，返回默认结果");
                return createDefaultResult();
            }

            EmotionResult fusedResult = fuseTimeSeriesResults(frameResults);

            log.info("视频情绪分析完成: label={}, score={}, riskLevel={}, 有效帧数={}",
                    fusedResult.getLabel(), fusedResult.getScore(),
                    fusedResult.getRiskLevel(), frameResults.size());

            return fusedResult;

        } catch (Exception e) {
            log.error("视频分析失败", e);
            return createDefaultResult();
        }
    }

    private List<BufferedImage> extractFrames(MultipartFile videoFile) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();

        String contentType = videoFile.getContentType();
        String filename = videoFile.getOriginalFilename();

        if (contentType != null && contentType.startsWith("image/")) {
            log.info("检测到图像文件，作为单帧处理");
            try (InputStream is = new ByteArrayInputStream(videoFile.getBytes())) {
                BufferedImage image = ImageIO.read(is);
                if (image != null) {
                    frames.add(image);
                }
            }
            return frames;
        }

        if (filename != null && isImageFile(filename)) {
            log.info("文件扩展名表明是图像，作为单帧处理");
            try (InputStream is = new ByteArrayInputStream(videoFile.getBytes())) {
                BufferedImage image = ImageIO.read(is);
                if (image != null) {
                    frames.add(image);
                }
            }
            return frames;
        }

        if (useExternalProcessor) {
            log.info("使用外部视频处理器提取帧");
            List<BufferedImage> externalFrames = extractFramesViaExternalService(videoFile);
            if (!externalFrames.isEmpty()) {
                return externalFrames;
            }
        }

        log.warn("无法提取视频帧，视频处理需要外部服务支持");
        return frames;
    }

    private List<BufferedImage> extractFramesViaExternalService(MultipartFile videoFile) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("video", new ByteArrayResource(videoFile.getBytes()) {
                @Override
                public String getFilename() {
                    return videoFile.getOriginalFilename();
                }
            });
            body.add("sample_count", String.valueOf(frameSampleCount));
            body.add("max_frames", String.valueOf(maxFrames));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<FrameExtractionResponse> response = restTemplate.exchange(
                    videoProcessorUrl + "/extract-frames",
                    HttpMethod.POST,
                    requestEntity,
                    FrameExtractionResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                FrameExtractionResponse extraction = response.getBody();
                if (extraction.getFrames() != null && !extraction.getFrames().isEmpty()) {
                    List<BufferedImage> frames = new ArrayList<>();
                    for (String frameBase64 : extraction.getFrames()) {
                        try {
                            byte[] imageBytes = Base64.getDecoder().decode(frameBase64);
                            BufferedImage frame = ImageIO.read(new ByteArrayInputStream(imageBytes));
                            if (frame != null) {
                                frames.add(frame);
                            }
                        } catch (Exception e) {
                            log.debug("解码帧失败: {}", e.getMessage());
                        }
                    }
                    log.info("从外部服务获取到{}帧", frames.size());
                    return frames;
                }
            }
        } catch (Exception e) {
            log.warn("外部视频处理服务调用失败: {}", e.getMessage());
        }
        return List.of();
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private EmotionResult fuseTimeSeriesResults(List<FrameEmotionResult> frameResults) {
        if (frameResults.isEmpty()) {
            return createDefaultResult();
        }

        if (frameResults.size() == 1) {
            EmotionResult single = frameResults.get(0).getEmotion();
            return EmotionResult.builder()
                    .label(single.getLabel())
                    .score(single.getScore())
                    .source("video")
                    .riskLevel(single.getRiskLevel())
                    .build();
        }

        frameResults.sort(Comparator.comparingInt(FrameEmotionResult::getFrameIndex));

        double totalWeight = 0.0;
        double weightedScore = 0.0;
        int highRiskCount = 0;
        int mediumRiskCount = 0;
        Map<String, Integer> labelCounts = new HashMap<>();

        for (int i = 0; i < frameResults.size(); i++) {
            FrameEmotionResult fr = frameResults.get(i);
            EmotionResult emotion = fr.getEmotion();

            double weight = Math.exp(timeWeightDecay * i);

            weightedScore += emotion.getScore() * weight;
            totalWeight += weight;

            if (emotion.getRiskLevel() == RiskLevel.HIGH) {
                highRiskCount++;
            } else if (emotion.getRiskLevel() == RiskLevel.MEDIUM) {
                mediumRiskCount++;
            }

            labelCounts.merge(emotion.getLabel(), 1, Integer::sum);
        }

        double finalScore = totalWeight > 0 ? weightedScore / totalWeight : 0.0;

        RiskLevel finalRiskLevel;
        if (highRiskCount >= frameResults.size() * 0.3) {
            finalRiskLevel = RiskLevel.HIGH;
            finalScore = Math.max(finalScore, 2.5);
        } else if (highRiskCount > 0 || mediumRiskCount >= frameResults.size() * 0.5) {
            finalRiskLevel = RiskLevel.MEDIUM;
            finalScore = Math.max(finalScore, 1.5);
        } else {
            finalRiskLevel = RiskLevel.fromScore(finalScore);
        }

        String dominantLabel = labelCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("正常");

        if (highRiskCount >= frameResults.size() * 0.3) {
            dominantLabel = "高风险";
        }

        return EmotionResult.builder()
                .label(dominantLabel)
                .score(finalScore)
                .source("video")
                .riskLevel(finalRiskLevel)
                .metadata(buildMetadata(frameResults, highRiskCount, mediumRiskCount))
                .build();
    }

    private Map<String, Object> buildMetadata(List<FrameEmotionResult> results,
                                               int highRiskCount, int mediumRiskCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("totalFrames", results.size());
        metadata.put("highRiskFrames", highRiskCount);
        metadata.put("mediumRiskFrames", mediumRiskCount);
        metadata.put("analysisMethod", "time_series_fusion");
        return metadata;
    }

    private EmotionResult createDefaultResult() {
        return EmotionResult.builder()
                .label("正常")
                .score(0.0)
                .source("video")
                .riskLevel(RiskLevel.LOW)
                .build();
    }

    @Data
    private static class FrameEmotionResult {
        private final int frameIndex;
        private final EmotionResult emotion;
    }

    @Data
    private static class FrameExtractionResponse {
        private List<String> frames;
        private Integer totalFrames;
        private Double duration;
    }
}
