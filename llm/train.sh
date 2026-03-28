#!/bin/bash

set -e

echo "============================================================"
echo "Calmara - Qwen2.5-7B LoRA微调训练"
echo "============================================================"
echo ""

# 检查Python
echo "[1/7] 检查Python环境..."
python3 --version

# 安装依赖
echo ""
echo "[2/7] 安装Python依赖..."
pip3 install -q torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip3 install -q transformers>=4.36.0 peft>=0.7.0 datasets>=2.14.0 accelerate>=0.25.0 bitsandbytes>=0.41.0

# 创建数据目录
echo ""
echo "[3/7] 准备训练数据..."
cd "$(dirname "$0")"
python3 scripts/prepare_data.py

# 下载模型
echo ""
echo "[4/7] 检查Qwen2.5模型..."
echo "请确保已安装modelscope并下载Qwen2.5-7B-Instruct模型"
echo "模型路径: $(pwd)/models/Qwen2.5-7B-Instruct"

# 设置环境变量
echo ""
echo "[5/7] 配置训练环境..."
export PYTHONPATH="$(pwd):$PYTHONPATH"
export TRANSFORMERS_CACHE="$(pwd)/models/huggingface"
export HF_HOME="$(pwd)/models/huggingface"

# 开始训练
echo ""
echo "[6/7] 开始LoRA微调训练..."
echo ""
echo "训练参数:"
echo "  - 模型: Qwen2.5-7B-Instruct"
echo "  - LoRA Rank: 16"
echo "  - 训练轮数: 3"
echo "  - 批次大小: 2"
echo "  - 学习率: 2e-4"
echo ""

MODEL_PATH="${1:-models/Qwen2.5-7B-Instruct}"

python3 scripts/train_lora.py \
    --model_path "$MODEL_PATH" \
    --data_path dataset/train.json \
    --output_dir output \
    --epochs 3 \
    --batch_size 2 \
    --learning_rate 2e-4 \
    --rank 16 \
    --alpha 32

# 验证模型
echo ""
echo "[7/7] 验证微调模型..."
python3 scripts/validate_model.py --model_path output

echo ""
echo "============================================================"
echo "训练完成！"
echo "============================================================"
echo ""
echo "下一步:"
echo "1. 查看 output 目录下的训练结果"
echo "2. 使用 export_gguf.py 导出为GGUF格式"
echo "3. 使用Ollama部署微调模型"
echo ""
