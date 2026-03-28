"""
Whisper语音识别服务 - 生产级实现
使用Faster-Whisper实现高效语音转文字
支持多语言、情绪特征提取
"""
import os
import time
import logging
import tempfile
from typing import Optional, Dict, Any, List
from pathlib import Path

import uvicorn
from fastapi import FastAPI, UploadFile, File, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings
from faster_whisper import WhisperModel
from loguru import logger

class Settings(BaseSettings):
    model_size: str = Field(default="large-v3", env="WHISPER_MODEL_SIZE")
    device: str = Field(default="cuda", env="WHISPER_DEVICE")
    compute_type: str = Field(default="float16", env="WHISPER_COMPUTE_TYPE")
    max_file_size: int = Field(default=50 * 1024 * 1024, env="MAX_FILE_SIZE")
    api_key: str = Field(default="", env="API_KEY")
    host: str = Field(default="0.0.0.0", env="HOST")
    port: int = Field(default=8001, env="PORT")
    
    class Config:
        env_file = ".env"

settings = Settings()

app = FastAPI(
    title="Whisper Speech Recognition Service",
    description="Production-grade speech-to-text service with emotion analysis",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

model: Optional[WhisperModel] = None

class TranscriptionResult(BaseModel):
    text: str
    language: str
    language_probability: float
    duration: float
    segments: List[Dict[str, Any]]
    emotion_features: Optional[Dict[str, Any]] = None

class EmotionFeatures(BaseModel):
    speaking_rate: float
    pause_ratio: float
    avg_segment_duration: float
    confidence_variance: float
    emotional_indicators: Dict[str, float]

def verify_api_key(x_api_key: str = Header(None)):
    if settings.api_key and x_api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")
    return True

def load_model():
    global model
    if model is None:
        logger.info(f"Loading Whisper model: {settings.model_size} on {settings.device}")
        model = WhisperModel(
            settings.model_size,
            device=settings.device,
            compute_type=settings.compute_type
        )
        logger.info("Whisper model loaded successfully")
    return model

def extract_emotion_features(segments: List[Dict], total_duration: float) -> EmotionFeatures:
    """
    从转录片段中提取情绪相关特征
    基于语音节奏、停顿、语速等特征推断情绪状态
    """
    if not segments:
        return EmotionFeatures(
            speaking_rate=0.0,
            pause_ratio=0.0,
            avg_segment_duration=0.0,
            confidence_variance=0.0,
            emotional_indicators={}
        )
    
    total_words = sum(len(seg.get("text", "").split()) for seg in segments)
    speaking_rate = total_words / max(total_duration, 0.1) if total_duration > 0 else 0
    
    total_speech_duration = sum(seg.get("end", 0) - seg.get("start", 0) for seg in segments)
    pause_duration = total_duration - total_speech_duration
    pause_ratio = pause_duration / max(total_duration, 0.1) if total_duration > 0 else 0
    
    segment_durations = [seg.get("end", 0) - seg.get("start", 0) for seg in segments]
    avg_segment_duration = sum(segment_durations) / len(segment_durations) if segment_durations else 0
    
    confidences = [seg.get("avg_logprob", 0) for seg in segments if "avg_logprob" in seg]
    confidence_variance = sum((c - sum(confidences)/len(confidences))**2 for c in confidences) / len(confidences) if confidences else 0
    
    emotional_indicators = {}
    
    if speaking_rate > 3.5:
        emotional_indicators["anxiety"] = min(1.0, (speaking_rate - 3.5) / 2)
    elif speaking_rate < 1.5:
        emotional_indicators["depression"] = min(1.0, (1.5 - speaking_rate) / 1.5)
    
    if pause_ratio > 0.4:
        emotional_indicators["hesitation"] = min(1.0, (pause_ratio - 0.4) / 0.3)
    
    if confidence_variance > 0.5:
        emotional_indicators["uncertainty"] = min(1.0, confidence_variance)
    
    return EmotionFeatures(
        speaking_rate=speaking_rate,
        pause_ratio=pause_ratio,
        avg_segment_duration=avg_segment_duration,
        confidence_variance=confidence_variance,
        emotional_indicators=emotional_indicators
    )

@app.on_event("startup")
async def startup_event():
    load_model()
    logger.info("Whisper service started")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("Whisper service shutting down")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "model": settings.model_size}

@app.post("/transcribe", response_model=TranscriptionResult)
async def transcribe_audio(
    file: UploadFile = File(...),
    language: Optional[str] = None,
    task: str = "transcribe",
    beam_size: int = 5,
    _: bool = Depends(verify_api_key)
):
    """
    转录音频文件
    支持格式: mp3, wav, m4a, flac, ogg
    """
    if file.size and file.size > settings.max_file_size:
        raise HTTPException(status_code=413, detail="File too large")
    
    valid_extensions = {".mp3", ".wav", ".m4a", ".flac", ".ogg", ".webm"}
    file_ext = Path(file.filename).suffix.lower() if file.filename else ""
    if file_ext not in valid_extensions:
        raise HTTPException(status_code=400, detail=f"Unsupported file format: {file_ext}")
    
    model = load_model()
    
    with tempfile.NamedTemporaryFile(delete=False, suffix=file_ext) as tmp_file:
        content = await file.read()
        tmp_file.write(content)
        tmp_path = tmp_file.name
    
    try:
        start_time = time.time()
        
        segments_gen, info = model.transcribe(
            tmp_path,
            language=language,
            task=task,
            beam_size=beam_size,
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=500)
        )
        
        segments = []
        full_text = []
        
        for seg in segments_gen:
            segment_dict = {
                "start": seg.start,
                "end": seg.end,
                "text": seg.text.strip(),
                "avg_logprob": getattr(seg, 'avg_logprob', 0),
                "no_speech_prob": getattr(seg, 'no_speech_prob', 0)
            }
            segments.append(segment_dict)
            full_text.append(seg.text.strip())
        
        transcription_time = time.time() - start_time
        
        emotion_features = extract_emotion_features(segments, info.duration)
        
        logger.info(f"Transcription completed: {info.duration:.2f}s audio in {transcription_time:.2f}s")
        
        return TranscriptionResult(
            text=" ".join(full_text),
            language=info.language,
            language_probability=info.language_probability,
            duration=info.duration,
            segments=segments,
            emotion_features=emotion_features.model_dump()
        )
        
    except Exception as e:
        logger.error(f"Transcription error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Transcription failed: {str(e)}")
    finally:
        os.unlink(tmp_path)

@app.post("/transcribe/stream")
async def transcribe_stream(
    file: UploadFile = File(...),
    _: bool = Depends(verify_api_key)
):
    """
    流式转录 - 用于实时语音处理
    """
    from fastapi.responses import StreamingResponse
    import json
    
    model = load_model()
    
    with tempfile.NamedTemporaryFile(delete=False) as tmp_file:
        content = await file.read()
        tmp_file.write(content)
        tmp_path = tmp_file.name
    
    async def generate():
        try:
            segments_gen, info = model.transcribe(tmp_path, vad_filter=True)
            
            yield json.dumps({"type": "info", "language": info.language, "duration": info.duration}) + "\n"
            
            for seg in segments_gen:
                yield json.dumps({
                    "type": "segment",
                    "start": seg.start,
                    "end": seg.end,
                    "text": seg.text.strip()
                }) + "\n"
            
            yield json.dumps({"type": "done"}) + "\n"
            
        except Exception as e:
            yield json.dumps({"type": "error", "message": str(e)}) + "\n"
        finally:
            os.unlink(tmp_path)
    
    return StreamingResponse(generate(), media_type="application/x-ndjson")

if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port)
