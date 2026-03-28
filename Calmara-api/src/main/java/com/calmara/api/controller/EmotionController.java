package com.calmara.api.controller;

import com.calmara.common.Result;
import com.calmara.model.dto.EmotionResult;
import com.calmara.multimodal.MultiModalEmotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/emotion")
public class EmotionController {

    private final MultiModalEmotionService emotionService;

    public EmotionController(MultiModalEmotionService emotionService) {
        this.emotionService = emotionService;
    }

    @PostMapping("/analyze/text")
    public Result<EmotionResult> analyzeText(@RequestParam String text) {
        log.info("文本情绪分析请求: {}", text);
        EmotionResult result = emotionService.analyzeText(text);
        return Result.success(result);
    }

    @PostMapping("/analyze/audio")
    public Result<EmotionResult> analyzeAudio(@RequestParam MultipartFile audio) {
        log.info("音频情绪分析请求");
        EmotionResult result = emotionService.analyzeAudio(audio);
        return Result.success(result);
    }

    @PostMapping("/analyze/image")
    public Result<EmotionResult> analyzeImage(@RequestParam MultipartFile image) {
        log.info("图像情绪分析请求");
        EmotionResult result = emotionService.analyzeImage(image);
        return Result.success(result);
    }

    @PostMapping("/analyze/video")
    public Result<EmotionResult> analyzeVideo(@RequestParam MultipartFile video) {
        log.info("视频情绪分析请求");
        EmotionResult result = emotionService.analyzeVideo(video);
        return Result.success(result);
    }

    @PostMapping("/transcribe")
    public Result<String> transcribe(@RequestParam MultipartFile audio) {
        log.info("语音转文字请求");
        String text = emotionService.transcribeAudio(audio);
        return Result.success(text);
    }
}
