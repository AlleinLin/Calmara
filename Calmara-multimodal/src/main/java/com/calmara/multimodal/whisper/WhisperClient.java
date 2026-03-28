package com.calmara.multimodal.whisper;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class WhisperClient {

    private final RestTemplate restTemplate;

    @Value("${calmara.external.whisper.url:http://localhost:8001/transcribe}")
    private String whisperUrl;

    public WhisperClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String transcribe(MultipartFile audioFile) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio_file", audioFile.getResource());
            body.add("task", "transcribe");
            body.add("language", "zh");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body);

            ResponseEntity<WhisperResponse> response = restTemplate.exchange(
                    whisperUrl,
                    HttpMethod.POST,
                    requestEntity,
                    WhisperResponse.class
            );

            if (response.getBody() != null && response.getBody().getText() != null) {
                log.info("语音转写成功: {}", response.getBody().getText());
                return response.getBody().getText();
            }

            throw new BusinessException(ErrorCode.WHISPER_ERROR, "语音识别返回为空");

        } catch (RestClientException e) {
            log.error("Whisper服务调用失败", e);
            throw new BusinessException(ErrorCode.WHISPER_ERROR, "语音识别服务不可用: " + e.getMessage());
        } catch (Exception e) {
            log.error("语音处理失败", e);
            throw new BusinessException(ErrorCode.WHISPER_ERROR, "音频处理失败: " + e.getMessage());
        }
    }

    @Data
    public static class WhisperResponse {
        private String text;
        private String language;
        private Double duration;
    }
}
