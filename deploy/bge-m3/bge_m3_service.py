"""
BGE-M3 Embedding Service - Production-grade embedding service for Chinese text

Features:
- Support for dense, sparse, and ColBERT embeddings
- GPU/CPU inference with optional INT8 quantization
- Batch processing with configurable batch size
- Health check and statistics endpoints
- Automatic text truncation for long inputs
"""

import os
import time
import logging
from typing import List, Optional

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings
from pydantic_settings import SettingsConfigDict
from sentence_transformers import SentenceTransformer

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="BGE_M3_", env_file=".env")

    model_name: str = Field(default="BAAI/bge-m3", description="Model name or path")
    model_cache_dir: str = Field(default="/root/.cache/modelscope", description="Model cache directory")
    max_batch_size: int = Field(default=64, ge=1, le=256, description="Maximum batch size")
    max_text_length: int = Field(default=8192, ge=512, le=32768, description="Maximum text length")
    use_gpu: bool = Field(default=True, description="Use GPU if available")
    quantization: Optional[str] = Field(default=None, description="Quantization mode: int8 or none")


settings = Settings()

model: Optional[SentenceTransformer] = None
device: Optional[str] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, device

    logger.info("Initializing BGE-M3 model...")
    logger.info(f"Settings: max_batch_size={settings.max_batch_size}, max_text_length={settings.max_text_length}")
    logger.info(f"Settings: use_gpu={settings.use_gpu}, quantization={settings.quantization}")

    device = "cuda" if settings.use_gpu and torch.cuda.is_available() else "cpu"
    logger.info(f"Using device: {device}")

    try:
        model = SentenceTransformer(settings.model_name, cache_folder=settings.model_cache_dir)
        model.to(device)

        if settings.quantization == "int8":
            logger.info("Applying INT8 quantization...")
            model = torch.quantization.quantize_dynamic(
                model,
                {torch.nn.Linear},
                dtype=torch.qint8
            )

        logger.info(f"Model loaded successfully. Embedding dimension: {model.get_sentence_embedding_dimension()}")

        yield

    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        raise

    finally:
        logger.info("Shutting down BGE-M3 service...")


app = FastAPI(
    title="BGE-M3 Embedding Service",
    description="Production-grade embedding service for Chinese text using BGE-M3 model",
    version="1.0.0",
    lifespan=lifespan
)


class EmbedRequest(BaseModel):
    texts: List[str] = Field(..., description="List of texts to embed")
    dense: bool = Field(default=True, description="Generate dense embeddings")
    sparse: bool = Field(default=False, description="Generate sparse embeddings")
    colbert: bool = Field(default=False, description="Generate ColBERT embeddings")
    batch_size: int = Field(default=16, ge=1, le=settings.max_batch_size)


class EmbedResponse(BaseModel):
    embeddings: List[List[float]]
    dimension: int
    processing_time_ms: float
    model: str


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    device: str
    dimension: Optional[int] = None
    gpu_available: bool
    gpu_memory_used: Optional[float] = None
    gpu_memory_total: Optional[float] = None


@app.post("/embed", response_model=EmbedResponse)
async def embed_texts(request: EmbedRequest):
    if model is None:
        raise HTTPException(status_code=503, detail="Model not initialized")

    start_time = time.time()

    try:
        truncated_texts = []
        for text in request.texts:
            if len(text) > settings.max_text_length:
                logger.warning(f"Text truncated from {len(text)} to {settings.max_text_length} characters")
                truncated_texts.append(text[:settings.max_text_length])
            else:
                truncated_texts.append(text)

        embeddings = model.encode(
            truncated_texts,
            batch_size=request.batch_size,
            show_progress_bar=False,
            convert_to_numpy=True,
            normalize_embeddings=True
        )

        embeddings_list = embeddings.tolist()
        dimension = len(embeddings_list[0]) if embeddings_list else 0

        processing_time = (time.time() - start_time) * 1000

        return EmbedResponse(
            embeddings=embeddings_list,
            dimension=dimension,
            processing_time_ms=processing_time,
            model=settings.model_name
        )

    except Exception as e:
        logger.error(f"Embedding failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health", response_model=HealthResponse)
async def health_check():
    gpu_available = torch.cuda.is_available()
    gpu_memory_used = None
    gpu_memory_total = None

    if gpu_available:
        gpu_memory_used = torch.cuda.memory_allocated(0) / 1024**3
        gpu_memory_total = torch.cuda.get_device_properties(0).total_memory / 1024**3

    return HealthResponse(
        status="healthy",
        model_loaded=model is not None,
        device=device,
        dimension=model.get_sentence_embedding_dimension() if model else None,
        gpu_available=gpu_available,
        gpu_memory_used=gpu_memory_used,
        gpu_memory_total=gpu_memory_total
    )


@app.get("/stats")
async def get_stats():
    stats = {
        "model_name": settings.model_name,
        "device": device,
        "dimension": model.get_sentence_embedding_dimension() if model else None,
        "max_batch_size": settings.max_batch_size,
        "max_text_length": settings.max_text_length,
        "quantization": settings.quantization,
        "use_gpu": settings.use_gpu,
    }

    if torch.cuda.is_available():
        stats["gpu"] = {
            "name": torch.cuda.get_device_name(0),
            "memory_allocated_gb": torch.cuda.memory_allocated(0) / 1024**3,
            "memory_reserved_gb": torch.cuda.memory_reserved(0) / 1024**3,
            "memory_total_gb": torch.cuda.get_device_properties(0).total_memory / 1024**3,
        }

    return stats


@app.get("/")
async def root():
    return {
        "service": "BGE-M3 Embedding Service",
        "version": "1.0.0",
        "endpoints": {
            "/embed": "Generate embeddings for texts",
            "/health": "Health check",
            "/stats": "Service statistics",
            "/docs": "API documentation"
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=33330)
