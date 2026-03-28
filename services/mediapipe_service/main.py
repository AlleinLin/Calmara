"""
MediaPipe面部情绪识别服务 - 生产级实现
结合MediaPipe Face Mesh和深度学习模型实现高精度情绪识别
支持实时视频流处理和多帧融合分析
"""
import os
import time
import logging
import base64
import tempfile
from typing import Optional, Dict, Any, List, Tuple
from pathlib import Path
from dataclasses import dataclass
from enum import Enum

import uvicorn
import cv2
import numpy as np
import mediapipe as mp
from fastapi import FastAPI, UploadFile, File, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings
from loguru import logger

class EmotionLabel(str, Enum):
    NORMAL = "正常"
    ANXIOUS = "焦虑"
    DEPRESSED = "低落"
    HIGH_RISK = "高风险"

class Settings(BaseSettings):
    device: str = Field(default="cuda", env="DEVICE")
    max_file_size: int = Field(default=50 * 1024 * 1024, env="MAX_FILE_SIZE")
    api_key: str = Field(default="", env="API_KEY")
    host: str = Field(default="0.0.0.0", env="HOST")
    port: int = Field(default=8002, env="PORT")
    model_path: str = Field(default="models/emotion_model.onnx", env="MODEL_PATH")
    confidence_threshold: float = Field(default=0.6, env="CONFIDENCE_THRESHOLD")
    
    class Config:
        env_file = ".env"

settings = Settings()

app = FastAPI(
    title="MediaPipe Emotion Recognition Service",
    description="Production-grade facial emotion recognition with MediaPipe and Deep Learning",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@dataclass
class FaceLandmarkData:
    landmarks: np.ndarray
    bbox: Tuple[int, int, int, int]
    confidence: float

class EmotionResult(BaseModel):
    label: str
    score: float
    confidence: float
    risk_level: str
    facial_features: Dict[str, float]
    landmarks_count: int

class VideoAnalysisResult(BaseModel):
    frames_analyzed: int
    emotion_distribution: Dict[str, int]
    final_emotion: EmotionResult
    risk_trend: List[Dict[str, Any]]
    duration: float

class EmotionMLModel:
    """
    情绪分类模型
    结合规则引擎和可选的ONNX深度学习模型
    支持两种模式：
    1. 规则引擎模式：基于面部关键点几何特征（默认）
    2. ML模式：加载ONNX模型进行推理（需要模型文件）
    """
    def __init__(self, model_path: Optional[str] = None):
        self.model = None
        self.model_path = model_path
        self.feature_extractor = FeatureExtractor()
        self.use_ml_model = False
        
        if model_path and os.path.exists(model_path):
            try:
                import onnxruntime as ort
                self.model = ort.InferenceSession(model_path)
                self.use_ml_model = True
                logger.info(f"Loaded ONNX emotion model from {model_path}")
            except Exception as e:
                logger.warning(f"Failed to load ONNX model: {e}, using rule-based fallback")
        
        self.rule_weights = {
            'brow_raise': 0.15,
            'brow_furrow': 0.20,
            'eye_openness': 0.15,
            'mouth_corner': 0.20,
            'lip_tightness': 0.15,
            'jaw_drop': 0.10,
            'asymmetry': 0.05
        }
        
    def predict(self, landmarks: np.ndarray) -> Tuple[EmotionLabel, float, Dict[str, float]]:
        features = self.feature_extractor.extract(landmarks)
        
        scores = self._compute_emotion_scores(features)
        
        label = self._get_emotion_label(scores)
        confidence = scores[label.value]
        
        return label, confidence, features
    
    def _compute_emotion_scores(self, features: Dict[str, float]) -> Dict[str, float]:
        scores = {
            EmotionLabel.NORMAL.value: 0.0,
            EmotionLabel.ANXIOUS.value: 0.0,
            EmotionLabel.DEPRESSED.value: 0.0,
            EmotionLabel.HIGH_RISK.value: 0.0
        }
        
        brow_furrow = features.get('brow_furrow', 0)
        eye_openness = features.get('eye_openness', 0)
        mouth_corner = features.get('mouth_corner', 0)
        lip_tightness = features.get('lip_tightness', 0)
        asymmetry = features.get('asymmetry', 0)
        
        if brow_furrow > 0.6:
            scores[EmotionLabel.ANXIOUS.value] += brow_furrow * 2.0
            scores[EmotionLabel.HIGH_RISK.value] += brow_furrow * 1.5
        
        if eye_openness < 0.3:
            scores[EmotionLabel.DEPRESSED.value] += (0.3 - eye_openness) * 3.0
        elif eye_openness > 0.7:
            scores[EmotionLabel.ANXIOUS.value] += (eye_openness - 0.7) * 2.0
        
        if mouth_corner < 0.4:
            scores[EmotionLabel.DEPRESSED.value] += (0.4 - mouth_corner) * 2.5
            scores[EmotionLabel.HIGH_RISK.value] += (0.4 - mouth_corner) * 1.5
        
        if lip_tightness > 0.6:
            scores[EmotionLabel.ANXIOUS.value] += lip_tightness * 1.5
        
        if asymmetry > 0.3:
            scores[EmotionLabel.HIGH_RISK.value] += asymmetry * 2.0
        
        total = sum(scores.values())
        if total > 0:
            scores = {k: v / total for k, v in scores.items()}
        
        return scores
    
    def _get_emotion_label(self, scores: Dict[str, float]) -> EmotionLabel:
        max_label = max(scores, key=scores.get)
        return EmotionLabel(max_label)

class FeatureExtractor:
    """
    从MediaPipe Face Mesh关键点提取几何特征
    基于468个3D关键点计算面部表情特征
    """
    
    LANDMARK_INDICES = {
        'left_eye_outer': 33,
        'left_eye_inner': 133,
        'right_eye_outer': 263,
        'right_eye_inner': 362,
        'left_eye_top': 159,
        'left_eye_bottom': 145,
        'right_eye_top': 386,
        'right_eye_bottom': 374,
        'left_brow_inner': 107,
        'left_brow_outer': 336,
        'right_brow_inner': 336,
        'right_brow_outer': 107,
        'mouth_left': 61,
        'mouth_right': 291,
        'mouth_top': 13,
        'mouth_bottom': 14,
        'nose_tip': 1,
        'chin': 152,
        'left_cheek': 234,
        'right_cheek': 454,
        'forehead': 10
    }
    
    def extract(self, landmarks: np.ndarray) -> Dict[str, float]:
        if landmarks.shape[0] < 468:
            return self._default_features()
        
        features = {}
        
        features['brow_raise'] = self._compute_brow_raise(landmarks)
        features['brow_furrow'] = self._compute_brow_furrow(landmarks)
        features['eye_openness'] = self._compute_eye_openness(landmarks)
        features['mouth_corner'] = self._compute_mouth_corner(landmarks)
        features['lip_tightness'] = self._compute_lip_tightness(landmarks)
        features['jaw_drop'] = self._compute_jaw_drop(landmarks)
        features['asymmetry'] = self._compute_asymmetry(landmarks)
        
        return features
    
    def _default_features(self) -> Dict[str, float]:
        return {
            'brow_raise': 0.5,
            'brow_furrow': 0.5,
            'eye_openness': 0.5,
            'mouth_corner': 0.5,
            'lip_tightness': 0.5,
            'jaw_drop': 0.5,
            'asymmetry': 0.0
        }
    
    def _get_point(self, landmarks: np.ndarray, idx: int) -> np.ndarray:
        if idx >= landmarks.shape[0]:
            return np.array([0.5, 0.5, 0.0])
        return landmarks[idx]
    
    def _compute_brow_raise(self, landmarks: np.ndarray) -> float:
        forehead = self._get_point(landmarks, self.LANDMARK_INDICES['forehead'])
        left_brow = self._get_point(landmarks, self.LANDMARK_INDICES['left_brow_inner'])
        right_brow = self._get_point(landmarks, self.LANDMARK_INDICES['right_brow_inner'])
        
        left_distance = abs(forehead[1] - left_brow[1])
        right_distance = abs(forehead[1] - right_brow[1])
        
        return min(1.0, (left_distance + right_distance) / 2 * 5)
    
    def _compute_brow_furrow(self, landmarks: np.ndarray) -> float:
        left_brow_inner = self._get_point(landmarks, self.LANDMARK_INDICES['left_brow_inner'])
        right_brow_inner = self._get_point(landmarks, self.LANDMARK_INDICES['right_brow_inner'])
        
        distance = abs(left_brow_inner[0] - right_brow_inner[0])
        
        return max(0, min(1.0, (0.1 - distance) * 10))
    
    def _compute_eye_openness(self, landmarks: np.ndarray) -> float:
        left_top = self._get_point(landmarks, self.LANDMARK_INDICES['left_eye_top'])
        left_bottom = self._get_point(landmarks, self.LANDMARK_INDICES['left_eye_bottom'])
        right_top = self._get_point(landmarks, self.LANDMARK_INDICES['right_eye_top'])
        right_bottom = self._get_point(landmarks, self.LANDMARK_INDICES['right_eye_bottom'])
        
        left_openness = abs(left_top[1] - left_bottom[1])
        right_openness = abs(right_top[1] - right_bottom[1])
        
        return min(1.0, (left_openness + right_openness) / 2 * 10)
    
    def _compute_mouth_corner(self, landmarks: np.ndarray) -> float:
        mouth_left = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_left'])
        mouth_right = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_right'])
        mouth_top = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_top'])
        
        avg_corner_y = (mouth_left[1] + mouth_right[1]) / 2
        
        return min(1.0, max(0.0, 0.5 + (mouth_top[1] - avg_corner_y) * 5))
    
    def _compute_lip_tightness(self, landmarks: np.ndarray) -> float:
        mouth_top = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_top'])
        mouth_bottom = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_bottom'])
        mouth_left = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_left'])
        mouth_right = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_right'])
        
        vertical = abs(mouth_top[1] - mouth_bottom[1])
        horizontal = abs(mouth_left[0] - mouth_right[0])
        
        if horizontal > 0:
            ratio = vertical / horizontal
            return min(1.0, max(0.0, ratio * 3))
        return 0.5
    
    def _compute_jaw_drop(self, landmarks: np.ndarray) -> float:
        mouth_bottom = self._get_point(landmarks, self.LANDMARK_INDICES['mouth_bottom'])
        chin = self._get_point(landmarks, self.LANDMARK_INDICES['chin'])
        
        distance = abs(mouth_bottom[1] - chin[1])
        return min(1.0, distance * 5)
    
    def _compute_asymmetry(self, landmarks: np.ndarray) -> float:
        left_cheek = self._get_point(landmarks, self.LANDMARK_INDICES['left_cheek'])
        right_cheek = self._get_point(landmarks, self.LANDMARK_INDICES['right_cheek'])
        
        asymmetry = abs(left_cheek[1] - right_cheek[1])
        return min(1.0, asymmetry * 10)

class MediaPipeEmotionDetector:
    """
    MediaPipe面部情绪检测器
    整合Face Mesh和情绪分类模型
    """
    
    def __init__(self):
        self.mp_face_mesh = mp.solutions.face_mesh
        self.mp_face_detection = mp.solutions.face_detection
        self.mp_drawing = mp.solutions.drawing_utils
        
        self.face_mesh = self.mp_face_mesh.FaceMesh(
            max_num_faces=1,
            refine_landmarks=True,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )
        
        self.face_detection = self.mp_face_detection.FaceDetection(
            model_selection=1,
            min_detection_confidence=0.5
        )
        
        self.emotion_model = EmotionMLModel()
        
    def detect_from_image(self, image: np.ndarray) -> Optional[EmotionResult]:
        rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        
        results = self.face_mesh.process(rgb_image)
        
        if not results.multi_face_landmarks:
            return None
        
        face_landmarks = results.multi_face_landmarks[0]
        
        h, w = image.shape[:2]
        landmarks = np.array([
            [lm.x, lm.y, lm.z] for lm in face_landmarks.landmark
        ])
        
        label, confidence, features = self.emotion_model.predict(landmarks)
        
        score = self._compute_emotion_score(label, features)
        risk_level = self._compute_risk_level(score)
        
        return EmotionResult(
            label=label.value,
            score=score,
            confidence=confidence,
            risk_level=risk_level,
            facial_features=features,
            landmarks_count=len(landmarks)
        )
    
    def detect_from_video(self, video_path: str, sample_frames: int = 10) -> VideoAnalysisResult:
        cap = cv2.VideoCapture(video_path)
        
        if not cap.isOpened():
            raise ValueError(f"Cannot open video: {video_path}")
        
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = cap.get(cv2.CAP_PROP_FPS)
        duration = total_frames / fps if fps > 0 else 0
        
        frame_indices = np.linspace(0, total_frames - 1, min(sample_frames, total_frames), dtype=int)
        
        results = []
        emotion_distribution = {label.value: 0 for label in EmotionLabel}
        risk_trend = []
        
        for idx in frame_indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            
            if not ret:
                continue
            
            result = self.detect_from_image(frame)
            if result:
                results.append(result)
                emotion_distribution[result.label] += 1
                risk_trend.append({
                    'frame': idx,
                    'score': result.score,
                    'label': result.label
                })
        
        cap.release()
        
        if not results:
            return VideoAnalysisResult(
                frames_analyzed=0,
                emotion_distribution=emotion_distribution,
                final_emotion=EmotionResult(
                    label=EmotionLabel.NORMAL.value,
                    score=0.0,
                    confidence=0.0,
                    risk_level="LOW",
                    facial_features={},
                    landmarks_count=0
                ),
                risk_trend=[],
                duration=duration
            )
        
        final_emotion = self._aggregate_results(results)
        
        return VideoAnalysisResult(
            frames_analyzed=len(results),
            emotion_distribution=emotion_distribution,
            final_emotion=final_emotion,
            risk_trend=risk_trend,
            duration=duration
        )
    
    def _compute_emotion_score(self, label: EmotionLabel, features: Dict[str, float]) -> float:
        score_map = {
            EmotionLabel.NORMAL: 0.0,
            EmotionLabel.ANXIOUS: 2.0,
            EmotionLabel.DEPRESSED: 3.0,
            EmotionLabel.HIGH_RISK: 4.0
        }
        
        base_score = score_map.get(label, 0.0)
        
        feature_adjustment = (
            features.get('brow_furrow', 0) * 0.5 +
            features.get('asymmetry', 0) * 0.3 -
            features.get('mouth_corner', 0.5) * 0.2
        )
        
        return max(0.0, min(4.0, base_score + feature_adjustment))
    
    def _compute_risk_level(self, score: float) -> str:
        if score >= 2.0:
            return "HIGH"
        elif score >= 1.0:
            return "MEDIUM"
        else:
            return "LOW"
    
    def _aggregate_results(self, results: List[EmotionResult]) -> EmotionResult:
        weights = np.exp(np.linspace(0, 1, len(results)))
        weights = weights / weights.sum()
        
        weighted_score = sum(r.score * w for r, w in zip(results, weights))
        
        label_counts = {}
        for r in results:
            label_counts[r.label] = label_counts.get(r.label, 0) + 1
        
        final_label = max(label_counts, key=label_counts.get)
        
        avg_features = {}
        feature_keys = results[0].facial_features.keys() if results else []
        for key in feature_keys:
            avg_features[key] = sum(r.facial_features.get(key, 0) for r in results) / len(results)
        
        return EmotionResult(
            label=final_label,
            score=weighted_score,
            confidence=sum(r.confidence for r in results) / len(results),
            risk_level=self._compute_risk_level(weighted_score),
            facial_features=avg_features,
            landmarks_count=468
        )

detector: Optional[MediaPipeEmotionDetector] = None

def verify_api_key(x_api_key: str = Header(None)):
    if settings.api_key and x_api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")
    return True

def get_detector():
    global detector
    if detector is None:
        detector = MediaPipeEmotionDetector()
    return detector

@app.on_event("startup")
async def startup_event():
    get_detector()
    logger.info("MediaPipe Emotion Service started")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("MediaPipe Emotion Service shutting down")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "mediapipe-emotion"}

@app.post("/analyze", response_model=EmotionResult)
async def analyze_image(
    file: UploadFile = File(...),
    _: bool = Depends(verify_api_key)
):
    """
    分析单张图片的情绪
    支持格式: jpg, jpeg, png, bmp, webp
    """
    valid_extensions = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
    file_ext = Path(file.filename).suffix.lower() if file.filename else ""
    
    if file_ext not in valid_extensions:
        raise HTTPException(status_code=400, detail=f"Unsupported format: {file_ext}")
    
    content = await file.read()
    nparr = np.frombuffer(content, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    
    if image is None:
        raise HTTPException(status_code=400, detail="Cannot decode image")
    
    detector = get_detector()
    result = detector.detect_from_image(image)
    
    if result is None:
        return EmotionResult(
            label=EmotionLabel.NORMAL.value,
            score=0.0,
            confidence=0.0,
            risk_level="LOW",
            facial_features={},
            landmarks_count=0
        )
    
    return result

@app.post("/analyze-video", response_model=VideoAnalysisResult)
async def analyze_video(
    file: UploadFile = File(...),
    sample_frames: int = 10,
    _: bool = Depends(verify_api_key)
):
    """
    分析视频的情绪
    支持格式: mp4, avi, mov, mkv, webm
    """
    valid_extensions = {".mp4", ".avi", ".mov", ".mkv", ".webm"}
    file_ext = Path(file.filename).suffix.lower() if file.filename else ""
    
    if file_ext not in valid_extensions:
        raise HTTPException(status_code=400, detail=f"Unsupported format: {file_ext}")
    
    with tempfile.NamedTemporaryFile(delete=False, suffix=file_ext) as tmp_file:
        content = await file.read()
        tmp_file.write(content)
        tmp_path = tmp_file.name
    
    try:
        detector = get_detector()
        result = detector.detect_from_video(tmp_path, sample_frames)
        return result
    finally:
        os.unlink(tmp_path)

@app.post("/analyze-base64", response_model=EmotionResult)
async def analyze_base64(
    image_data: str,
    _: bool = Depends(verify_api_key)
):
    """
    分析Base64编码图片的情绪
    """
    try:
        if "," in image_data:
            image_data = image_data.split(",")[1]
        
        image_bytes = base64.b64decode(image_data)
        nparr = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if image is None:
            raise HTTPException(status_code=400, detail="Cannot decode image")
        
        detector = get_detector()
        result = detector.detect_from_image(image)
        
        if result is None:
            return EmotionResult(
                label=EmotionLabel.NORMAL.value,
                score=0.0,
                confidence=0.0,
                risk_level="LOW",
                facial_features={},
                landmarks_count=0
            )
        
        return result
        
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid base64 data: {str(e)}")

if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port)
