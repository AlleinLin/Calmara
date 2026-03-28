#!/usr/bin/env python3
"""
Qwen2.5-7B-Instruct 模型下载脚本
用于下载 Hugging Face 上的 Qwen2.5-7B-Instruct 模型

使用方法:
    python download_model.py

依赖:
    pip install huggingface_hub
"""

import os
from huggingface_hub import snapshot_download

MODEL_ID = "Qwen/Qwen2.5-7B-Instruct"
LOCAL_DIR = os.path.dirname(os.path.abspath(__file__))

def download_model():
    print(f"开始下载模型: {MODEL_ID}")
    print(f"目标目录: {LOCAL_DIR}")
    print("-" * 50)
    
    snapshot_download(
        repo_id=MODEL_ID,
        local_dir=LOCAL_DIR,
        local_dir_use_symlinks=False,
        resume_download=True,
    )
    
    print("-" * 50)
    print("模型下载完成!")

if __name__ == "__main__":
    download_model()
