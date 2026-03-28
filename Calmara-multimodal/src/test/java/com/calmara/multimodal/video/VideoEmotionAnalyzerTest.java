package com.calmara.multimodal.video;

import com.calmara.model.dto.EmotionResult;
import com.calmara.model.enums.RiskLevel;
import com.calmara.multimodal.emotion.VisualEmotionCalculator;
import com.calmara.multimodal.mediapipe.FaceLandmarks;
import com.calmara.multimodal.mediapipe.MediaPipeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoEmotionAnalyzerTest {

    @Mock
    private MediaPipeClient mediaPipeClient;

    @Mock
    private VisualEmotionCalculator visualEmotionCalculator;

    @Mock
    private RestTemplate restTemplate;

    private VideoEmotionAnalyzer videoEmotionAnalyzer;

    @BeforeEach
    void setUp() {
        videoEmotionAnalyzer = new VideoEmotionAnalyzer(mediaPipeClient, visualEmotionCalculator, restTemplate);
    }

    private byte[] createValidImageBytes() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    void testAnalyzeVideo_WithImageFile_ReturnsResult() throws IOException {
        byte[] imageBytes = createValidImageBytes();
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", imageBytes);
        
        FaceLandmarks mockLandmarks = new FaceLandmarks();
        mockLandmarks.setLandmarks(new float[468][3]);
        
        EmotionResult mockEmotion = EmotionResult.builder()
                .label("正常")
                .score(0.5)
                .source("visual")
                .riskLevel(RiskLevel.LOW)
                .build();
        
        when(mediaPipeClient.analyzeFaceFromImage(any(BufferedImage.class))).thenReturn(mockLandmarks);
        when(visualEmotionCalculator.calculate(any(FaceLandmarks.class))).thenReturn(mockEmotion);
        
        EmotionResult result = videoEmotionAnalyzer.analyzeVideo(imageFile);
        
        assertNotNull(result);
        assertEquals("正常", result.getLabel());
        assertEquals("video", result.getSource());
    }

    @Test
    void testAnalyzeVideo_WithNoFace_ReturnsDefaultResult() {
        MockMultipartFile videoFile = new MockMultipartFile(
                "video", "test.mp4", "video/mp4", new byte[100]);
        
        EmotionResult result = videoEmotionAnalyzer.analyzeVideo(videoFile);
        
        assertNotNull(result);
        assertEquals("正常", result.getLabel());
        assertEquals(0.0, result.getScore());
    }

    @Test
    void testAnalyzeVideo_WithEmptyFile_ReturnsDefaultResult() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "empty", "empty.jpg", "image/jpeg", new byte[0]);
        
        EmotionResult result = videoEmotionAnalyzer.analyzeVideo(emptyFile);
        
        assertNotNull(result);
        assertEquals("正常", result.getLabel());
    }

    @Test
    void testAnalyzeVideo_WithPngImage_ReturnsResult() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        
        MockMultipartFile pngFile = new MockMultipartFile(
                "image", "test.png", "image/png", imageBytes);
        
        FaceLandmarks mockLandmarks = new FaceLandmarks();
        mockLandmarks.setLandmarks(new float[468][3]);
        
        EmotionResult mockEmotion = EmotionResult.builder()
                .label("焦虑")
                .score(2.0)
                .source("visual")
                .riskLevel(RiskLevel.MEDIUM)
                .build();
        
        when(mediaPipeClient.analyzeFaceFromImage(any(BufferedImage.class))).thenReturn(mockLandmarks);
        when(visualEmotionCalculator.calculate(any(FaceLandmarks.class))).thenReturn(mockEmotion);
        
        EmotionResult result = videoEmotionAnalyzer.analyzeVideo(pngFile);
        
        assertNotNull(result);
        assertEquals("焦虑", result.getLabel());
    }
}
