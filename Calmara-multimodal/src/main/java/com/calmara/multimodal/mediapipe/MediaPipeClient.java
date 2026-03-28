package com.calmara.multimodal.mediapipe;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MediaPipeClient {

    private final RestTemplate restTemplate;

    @Value("${calmara.external.mediapipe.url:http://localhost:8001/analyze}")
    private String mediaPipeUrl;

    @Value("${calmara.external.mediapipe.timeout:10000}")
    private int timeout;

    public MediaPipeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public FaceLandmarks analyzeFace(MultipartFile imageFile) {
        try {
            log.debug("开始分析图像: filename={}, size={}", 
                    imageFile.getOriginalFilename(), imageFile.getSize());

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", imageFile.getResource());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body);

            ResponseEntity<MediaPipeResponse> response = restTemplate.exchange(
                    mediaPipeUrl,
                    HttpMethod.POST,
                    requestEntity,
                    MediaPipeResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                MediaPipeResponse body2 = response.getBody();
                if (body2.getLandmarks() != null && !body2.getLandmarks().isEmpty()) {
                    log.info("人脸检测成功，关键点数量: {}", body2.getLandmarkCount());
                    return convertToFaceLandmarks(body2.getLandmarks());
                }
            }

            log.warn("未检测到人脸");
            return null;

        } catch (RestClientException e) {
            log.error("MediaPipe服务调用失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("MediaPipe处理异常", e);
            return null;
        }
    }

    public FaceLandmarks analyzeFaceFromImage(BufferedImage image) {
        try {
            log.debug("开始分析BufferedImage: width={}, height={}", image.getWidth(), image.getHeight());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String formatName = "png";
            ImageIO.write(image, formatName, baos);
            byte[] imageBytes = baos.toByteArray();

            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "frame.png";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<MediaPipeResponse> response = restTemplate.exchange(
                    mediaPipeUrl,
                    HttpMethod.POST,
                    requestEntity,
                    MediaPipeResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                MediaPipeResponse responseBody = response.getBody();
                if (responseBody.getLandmarks() != null && !responseBody.getLandmarks().isEmpty()) {
                    log.debug("BufferedImage人脸检测成功，关键点数量: {}", responseBody.getLandmarkCount());
                    return convertToFaceLandmarks(responseBody.getLandmarks());
                }
            }

            log.debug("BufferedImage未检测到人脸");
            return null;

        } catch (IOException e) {
            log.error("BufferedImage转换失败", e);
            return null;
        } catch (RestClientException e) {
            log.error("MediaPipe服务调用失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("MediaPipe处理异常", e);
            return null;
        }
    }

    public FaceLandmarks analyzeVideo(MultipartFile videoFile) {
        log.info("视频文件分析请求: filename={}, size={}", 
                videoFile.getOriginalFilename(), videoFile.getSize());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("video", videoFile.getResource());
            body.add("mode", "aggregate");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String videoAnalysisUrl = mediaPipeUrl.replace("/analyze", "/analyze-video");
            
            ResponseEntity<MediaPipeResponse> response = restTemplate.exchange(
                    videoAnalysisUrl,
                    HttpMethod.POST,
                    requestEntity,
                    MediaPipeResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                MediaPipeResponse responseBody = response.getBody();
                if (responseBody.getLandmarks() != null && !responseBody.getLandmarks().isEmpty()) {
                    log.info("视频分析成功，关键点数量: {}", responseBody.getLandmarkCount());
                    return convertToFaceLandmarks(responseBody.getLandmarks());
                }
            }

            log.warn("视频分析未检测到人脸");
            return null;

        } catch (Exception e) {
            log.error("视频分析服务调用失败: {}", e.getMessage());
            return null;
        }
    }

    private FaceLandmarks convertToFaceLandmarks(List<Map<String, Double>> landmarks) {
        FaceLandmarks result = new FaceLandmarks();
        float[][] points = new float[landmarks.size()][3];

        for (int i = 0; i < landmarks.size(); i++) {
            Map<String, Double> point = landmarks.get(i);
            points[i][0] = point.get("x").floatValue();
            points[i][1] = point.get("y").floatValue();
            points[i][2] = point.get("z").floatValue();
        }

        result.setLandmarks(points);
        return result;
    }

    public boolean isAvailable() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    mediaPipeUrl.replace("/analyze", "/health"), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("MediaPipe服务不可用: {}", e.getMessage());
            return false;
        }
    }

    @Data
    public static class MediaPipeResponse {
        private List<Map<String, Double>> landmarks;
        private Integer landmarkCount;
        private String error;
        private Double confidence;
    }
}
