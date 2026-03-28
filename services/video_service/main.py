"""
视频处理服务 - 生产级实现
支持视频帧提取、关键帧分析、情绪时序分析
"""
import os
import time
import logging
import tempfile
from typing import Optional, Dict, Any, List
from pathlib import Path
from dataclasses import dataclass

import uvicorn
import cv2
import numpy as np
from fastapi import FastAPI, UploadFile, File, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings
from loguru import logger
import httpx

class Settings(BaseSettings):
    host: str = Field(default="0.0.0.0", env="HOST")
    port: int = Field(default=8003, env="PORT")
    mediapipe_service_url: str = Field(default="http://localhost:8002", env="MEDIAPIPE_SERVICE_URL")
    api_key: str = Field(default="", env="API_KEY")
    max_file_size: int = Field(default=200 * 1024 * 1024, env="MAX_FILE_SIZE")
    max_frames: int = Field(default=30, env="MAX_FRAMES")
    
    class Config:
        env_file = ".env"

settings = Settings()

app = FastAPI(
    title="Video Processing Service",
    description="Production-grade video analysis service",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class FrameAnalysisResult(BaseModel):
    frame_index: int
    timestamp: float
    emotion: Optional[Dict[str, Any]] = None
    has_face: bool
    confidence: float

class VideoAnalysisResult(BaseModel):
    duration: float
    fps: float
    total_frames: int
    analyzed_frames: int
    frames: List[FrameAnalysisResult]
    emotion_summary: Dict[str, Any]
    risk_indicators: List[str]

def verify_api_key(x_api_key: str = Header(None)):
    if settings.api_key and x_api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")
    return True

class VideoProcessor:
    """
    视频处理器
    支持智能帧采样和关键帧检测
    """
    
    def __init__(self, mediapipe_url: str):
        self.mediapipe_url = mediapipe_url
        self.http_client = httpx.AsyncClient(timeout=30.0)
    
    async def analyze_video(
        self, 
        video_path: str, 
        sample_strategy: str = "smart",
        max_frames: int = 30
    ) -> VideoAnalysisResult:
        """
        分析视频，提取情绪信息
        """
        cap = cv2.VideoCapture(video_path)
        
        if not cap.isOpened():
            raise ValueError(f"Cannot open video: {video_path}")
        
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        fps = cap.get(cv2.CAP_PROP_FPS)
        duration = total_frames / fps if fps > 0 else 0
        
        frame_indices = self._select_frames(
            cap, total_frames, sample_strategy, max_frames
        )
        
        frames_results = []
        emotion_scores = []
        risk_indicators = []
        
        for idx in frame_indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            
            if not ret:
                continue
            
            timestamp = idx / fps if fps > 0 else 0
            
            frame_result = await self._analyze_frame(frame, idx, timestamp)
            frames_results.append(frame_result)
            
            if frame_result.emotion:
                emotion_scores.append(frame_result.emotion.get("score", 0))
                
                if frame_result.emotion.get("score", 0) >= 3.0:
                    risk_indicators.append(
                        f"Frame {idx} ({timestamp:.1f}s): High risk emotion detected"
                    )
        
        cap.release()
        
        emotion_summary = self._summarize_emotions(frames_results)
        
        return VideoAnalysisResult(
            duration=duration,
            fps=fps,
            total_frames=total_frames,
            analyzed_frames=len(frames_results),
            frames=frames_results,
            emotion_summary=emotion_summary,
            risk_indicators=risk_indicators
        )
    
    def _select_frames(
        self, 
        cap: cv2.VideoCapture, 
        total_frames: int,
        strategy: str,
        max_frames: int
    ) -> List[int]:
        """
        智能选择要分析的帧
        """
        if strategy == "uniform":
            if total_frames <= max_frames:
                return list(range(total_frames))
            return list(np.linspace(0, total_frames - 1, max_frames, dtype=int))
        
        elif strategy == "smart":
            return self._smart_frame_selection(cap, total_frames, max_frames)
        
        elif strategy == "keyframes":
            return self._detect_keyframes(cap, total_frames, max_frames)
        
        else:
            return list(np.linspace(0, total_frames - 1, max_frames, dtype=int))
    
    def _smart_frame_selection(
        self, 
        cap: cv2.VideoCapture, 
        total_frames: int,
        max_frames: int
    ) -> List[int]:
        """
        智能帧选择：结合均匀采样和场景变化检测
        """
        uniform_frames = list(np.linspace(0, total_frames - 1, max_frames // 2, dtype=int))
        
        scene_change_frames = self._detect_scene_changes(cap, total_frames, max_frames // 2)
        
        all_frames = sorted(set(uniform_frames + scene_change_frames))
        
        return all_frames[:max_frames]
    
    def _detect_scene_changes(
        self, 
        cap: cv2.VideoCapture, 
        total_frames: int,
        max_changes: int
    ) -> List[int]:
        """
        检测场景变化帧
        """
        scene_frames = []
        prev_gray = None
        threshold = 0.3
        
        sample_step = max(1, total_frames // 1000)
        
        for idx in range(0, total_frames, sample_step):
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            
            if not ret:
                continue
            
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            gray = cv2.resize(gray, (100, 100))
            
            if prev_gray is not None:
                diff = cv2.absdiff(gray, prev_gray)
                change_ratio = np.sum(diff > 30) / diff.size
                
                if change_ratio > threshold:
                    scene_frames.append(idx)
            
            prev_gray = gray
            
            if len(scene_frames) >= max_changes:
                break
        
        return scene_frames
    
    def _detect_keyframes(
        self, 
        cap: cv2.VideoCapture, 
        total_frames: int,
        max_frames: int
    ) -> List[int]:
        """
        检测关键帧（基于人脸检测）
        """
        face_cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
        )
        
        keyframes = []
        sample_step = max(1, total_frames // 500)
        
        for idx in range(0, total_frames, sample_step):
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            
            if not ret:
                continue
            
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, 1.1, 4)
            
            if len(faces) > 0:
                face_size = max(w * h for (x, y, w, h) in faces)
                keyframes.append((idx, face_size))
        
        keyframes.sort(key=lambda x: x[1], reverse=True)
        
        return [idx for idx, _ in keyframes[:max_frames]]
    
    async def _analyze_frame(
        self, 
        frame: np.ndarray, 
        frame_index: int,
        timestamp: float
    ) -> FrameAnalysisResult:
        """
        分析单个帧
        """
        try:
            _, img_encoded = cv2.imencode('.jpg', frame)
            img_bytes = img_encoded.tobytes()
            
            response = await self.http_client.post(
                f"{self.mediapipe_url}/analyze",
                files={"file": ("frame.jpg", img_bytes, "image/jpeg")},
                timeout=10.0
            )
            
            if response.status_code == 200:
                emotion_data = response.json()
                return FrameAnalysisResult(
                    frame_index=frame_index,
                    timestamp=timestamp,
                    emotion=emotion_data,
                    has_face=emotion_data.get("landmarks_count", 0) > 0,
                    confidence=emotion_data.get("confidence", 0.0)
                )
            else:
                return FrameAnalysisResult(
                    frame_index=frame_index,
                    timestamp=timestamp,
                    emotion=None,
                    has_face=False,
                    confidence=0.0
                )
                
        except Exception as e:
            logger.warning(f"Frame analysis failed: {e}")
            return FrameAnalysisResult(
                frame_index=frame_index,
                timestamp=timestamp,
                emotion=None,
                has_face=False,
                confidence=0.0
            )
    
    def _summarize_emotions(
        self, 
        frame_results: List[FrameAnalysisResult]
    ) -> Dict[str, Any]:
        """
        汇总情绪分析结果
        """
        if not frame_results:
            return {
                "average_score": 0.0,
                "max_score": 0.0,
                "dominant_emotion": "正常",
                "emotion_distribution": {},
                "stability": 1.0
            }
        
        scores = []
        emotions = []
        
        for result in frame_results:
            if result.emotion:
                scores.append(result.emotion.get("score", 0))
                emotions.append(result.emotion.get("label", "正常"))
        
        if not scores:
            return {
                "average_score": 0.0,
                "max_score": 0.0,
                "dominant_emotion": "正常",
                "emotion_distribution": {},
                "stability": 1.0
            }
        
        emotion_counts = {}
        for e in emotions:
            emotion_counts[e] = emotion_counts.get(e, 0) + 1
        
        dominant = max(emotion_counts, key=emotion_counts.get)
        
        if len(scores) > 1:
            variance = np.var(scores)
            stability = max(0, 1 - variance)
        else:
            stability = 1.0
        
        return {
            "average_score": np.mean(scores),
            "max_score": max(scores),
            "min_score": min(scores),
            "dominant_emotion": dominant,
            "emotion_distribution": emotion_counts,
            "stability": stability,
            "score_trend": self._compute_trend(scores)
        }
    
    def _compute_trend(self, scores: List[float]) -> str:
        """
        计算情绪趋势
        """
        if len(scores) < 3:
            return "insufficient_data"
        
        first_half = np.mean(scores[:len(scores)//2])
        second_half = np.mean(scores[len(scores)//2:])
        
        diff = second_half - first_half
        
        if diff > 0.5:
            return "worsening"
        elif diff < -0.5:
            return "improving"
        else:
            return "stable"

processor: Optional[VideoProcessor] = None

def get_processor():
    global processor
    if processor is None:
        processor = VideoProcessor(settings.mediapipe_service_url)
    return processor

@app.on_event("startup")
async def startup_event():
    get_processor()
    logger.info("Video processing service started")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("Video processing service shutting down")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "video-processing"}

@app.post("/analyze", response_model=VideoAnalysisResult)
async def analyze_video(
    file: UploadFile = File(...),
    sample_strategy: str = "smart",
    max_frames: int = 30,
    _: bool = Depends(verify_api_key)
):
    """
    分析视频情绪
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
        processor = get_processor()
        result = await processor.analyze_video(
            tmp_path, 
            sample_strategy, 
            max_frames
        )
        return result
    finally:
        os.unlink(tmp_path)

@app.post("/extract-frames")
async def extract_frames(
    file: UploadFile = File(...),
    frame_indices: str = "",
    _: bool = Depends(verify_api_key)
):
    """
    提取指定帧
    """
    import base64
    
    file_ext = Path(file.filename).suffix.lower() if file.filename else ""
    with tempfile.NamedTemporaryFile(delete=False, suffix=file_ext) as tmp_file:
        content = await file.read()
        tmp_file.write(content)
        tmp_path = tmp_file.name
    
    try:
        cap = cv2.VideoCapture(tmp_path)
        
        if frame_indices:
            indices = [int(x) for x in frame_indices.split(",")]
        else:
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            indices = list(np.linspace(0, total_frames - 1, 10, dtype=int))
        
        frames = []
        for idx in indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            
            if ret:
                _, buffer = cv2.imencode('.jpg', frame)
                frames.append({
                    "index": idx,
                    "base64": base64.b64encode(buffer).decode('utf-8')
                })
        
        cap.release()
        
        return {"frames": frames}
        
    finally:
        os.unlink(tmp_path)

if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port)
