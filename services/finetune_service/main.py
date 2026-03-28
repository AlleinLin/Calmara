"""
LoRA微调服务API
提供训练、评估、导出的HTTP接口
"""
import os
import json
import asyncio
import subprocess
from pathlib import Path
from typing import Optional, Dict, Any
from datetime import datetime

import uvicorn
from fastapi import FastAPI, HTTPException, BackgroundTasks, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings
from loguru import logger

class Settings(BaseSettings):
    host: str = Field(default="0.0.0.0", env="HOST")
    port: int = Field(default=8004, env="PORT")
    base_model: str = Field(default="Qwen/Qwen2.5-7B-Instruct", env="BASE_MODEL")
    output_dir: str = Field(default="/output", env="OUTPUT_DIR")
    data_dir: str = Field(default="/data", env="DATA_DIR")
    
    class Config:
        env_file = ".env"

settings = Settings()

app = FastAPI(
    title="LoRA Fine-tuning Service",
    description="Production-grade LoRA fine-tuning service for Qwen models",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class TrainingRequest(BaseModel):
    data_path: Optional[str] = None
    eval_data_path: Optional[str] = None
    epochs: int = 3
    learning_rate: float = 2e-4
    lora_r: int = 16
    lora_alpha: int = 32
    batch_size: int = 4
    max_seq_length: int = 2048
    use_unsloth: bool = True
    merge_and_export: bool = True

class TrainingStatus(BaseModel):
    job_id: str
    status: str
    progress: float
    current_epoch: int
    total_epochs: int
    loss: Optional[float] = None
    start_time: str
    end_time: Optional[str] = None
    error: Optional[str] = None
    output_path: Optional[str] = None

training_jobs: Dict[str, TrainingStatus] = {}

@app.on_event("startup")
async def startup_event():
    Path(settings.output_dir).mkdir(parents=True, exist_ok=True)
    Path(settings.data_dir).mkdir(parents=True, exist_ok=True)
    logger.info("LoRA Fine-tuning service started")

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "finetune"}

@app.get("/status/{job_id}", response_model=TrainingStatus)
async def get_training_status(job_id: str):
    if job_id not in training_jobs:
        raise HTTPException(status_code=404, detail="Job not found")
    return training_jobs[job_id]

@app.get("/jobs")
async def list_jobs():
    return {"jobs": list(training_jobs.values())}

@app.post("/train", response_model=TrainingStatus)
async def start_training(
    request: TrainingRequest,
    background_tasks: BackgroundTasks
):
    import uuid
    job_id = str(uuid.uuid4())[:8]
    
    status = TrainingStatus(
        job_id=job_id,
        status="pending",
        progress=0.0,
        current_epoch=0,
        total_epochs=request.epochs,
        start_time=datetime.now().isoformat()
    )
    training_jobs[job_id] = status
    
    background_tasks.add_task(
        run_training,
        job_id,
        request
    )
    
    return status

def run_training(job_id: str, request: TrainingRequest):
    status = training_jobs[job_id]
    status.status = "running"
    
    try:
        data_path = request.data_path or f"{settings.data_dir}/train.json"
        
        cmd = [
            "python", "train_lora.py",
            "--data", data_path,
            "--output", f"{settings.output_dir}/{job_id}",
            "--epochs", str(request.epochs),
            "--lr", str(request.learning_rate),
            "--lora-r", str(request.lora_r),
        ]
        
        if request.eval_data_path:
            cmd.extend(["--eval", request.eval_data_path])
        
        if request.merge_and_export:
            cmd.append("--merge")
        
        logger.info(f"Starting training: {' '.join(cmd)}")
        
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True
        )
        
        for line in process.stdout:
            logger.info(f"[{job_id}] {line.strip()}")
            
            if "epoch" in line.lower() and "/" in line:
                try:
                    parts = line.split()
                    for i, part in enumerate(parts):
                        if "/" in part and part.replace("/", "").replace(".", "").isdigit():
                            current, total = part.split("/")
                            status.current_epoch = int(float(current))
                            status.progress = status.current_epoch / status.total_epochs * 100
                except:
                    pass
            
            if "loss" in line.lower():
                try:
                    loss_str = line.split("loss")[-1].split()[0]
                    status.loss = float(loss_str.replace(":", "").strip())
                except:
                    pass
        
        process.wait()
        
        if process.returncode == 0:
            status.status = "completed"
            status.progress = 100.0
            status.end_time = datetime.now().isoformat()
            status.output_path = f"{settings.output_dir}/{job_id}"
        else:
            status.status = "failed"
            status.error = "Training process failed"
            status.end_time = datetime.now().isoformat()
            
    except Exception as e:
        status.status = "failed"
        status.error = str(e)
        status.end_time = datetime.now().isoformat()
        logger.error(f"Training failed: {e}")

@app.post("/prepare-data")
async def prepare_training_data(
    file: UploadFile = File(...),
    format: str = "auto",
    augment: int = 1
):
    """
    上传并准备训练数据
    """
    import uuid
    data_id = str(uuid.uuid4())[:8]
    
    output_path = f"{settings.data_dir}/{data_id}"
    Path(output_path).mkdir(parents=True, exist_ok=True)
    
    input_path = f"{output_path}/input.json"
    with open(input_path, "wb") as f:
        content = await file.read()
        f.write(content)
    
    try:
        cmd = [
            "python", "prepare_data.py",
            "--input", input_path,
            "--output", output_path,
            "--format", format,
            "--augment", str(augment)
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        if result.returncode != 0:
            raise HTTPException(
                status_code=500, 
                detail=f"Data preparation failed: {result.stderr}"
            )
        
        return {
            "data_id": data_id,
            "train_path": f"{output_path}/train.json",
            "eval_path": f"{output_path}/eval.json",
            "message": "Data prepared successfully"
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/evaluate/{job_id}")
async def evaluate_model(job_id: str, eval_data_path: str):
    """
    评估微调后的模型
    """
    status = training_jobs.get(job_id)
    
    if not status or status.status != "completed":
        raise HTTPException(status_code=400, detail="Model not ready for evaluation")
    
    model_path = status.output_path
    if not model_path:
        raise HTTPException(status_code=400, detail="Model path not found")
    
    try:
        cmd = [
            "python", "train_lora.py",
            "--eval", eval_data_path,
            "--model", f"{model_path}/merged"
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        return {
            "job_id": job_id,
            "evaluation_output": result.stdout,
            "success": result.returncode == 0
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/jobs/{job_id}")
async def cancel_job(job_id: str):
    if job_id not in training_jobs:
        raise HTTPException(status_code=404, detail="Job not found")
    
    status = training_jobs[job_id]
    
    if status.status == "running":
        status.status = "cancelled"
        status.end_time = datetime.now().isoformat()
    
    return {"message": f"Job {job_id} cancelled"}

if __name__ == "__main__":
    uvicorn.run(app, host=settings.host, port=settings.port)
