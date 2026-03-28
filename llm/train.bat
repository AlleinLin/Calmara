@echo off
chcp 65001 >nul
echo ============================================================
echo Calmara - Qwen2.5-7B LoRA微调训练
echo ============================================================
echo.

REM 检查Python
echo [1/7] 检查Python环境...
python --version
if errorlevel 1 (
    echo 错误: 未找到Python，请先安装Python 3.8+
    pause
    exit /b 1
)

REM 安装依赖
echo.
echo [2/7] 安装Python依赖...
pip install -q torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip install -q transformers>=4.36.0 peft>=0.7.0 datasets>=2.14.0 accelerate>=0.25.0 bitsandbytes>=0.41.0
if errorlevel 1 (
    echo 警告: 部分依赖安装失败，尝试继续...
)

REM 创建数据目录
echo.
echo [3/7] 准备训练数据...
cd /d "%~dp0"
python scripts\prepare_data.py
if errorlevel 1 (
    echo 错误: 数据准备失败
    pause
    exit /b 1
)

REM 下载模型
echo.
echo [4/7] 检查Qwen2.5模型...
echo 请确保已安装modelscope并下载Qwen2.5-7B-Instruct模型
echo 模型路径: %CD%\models\Qwen2.5-7B-Instruct

REM 设置环境变量
echo.
echo [5/7] 配置训练环境...
set PYTHONPATH=%CD%;%PYTHONPATH%
set TRANSFORMERS_CACHE=%CD%\models\huggingface
set HF_HOME=%CD%\models\huggingface

REM 开始训练
echo.
echo [6/7] 开始LoRA微调训练...
echo.
echo 训练参数:
echo   - 模型: Qwen2.5-7B-Instruct
echo   - LoRA Rank: 16
echo   - 训练轮数: 3
echo   - 批次大小: 2
echo   - 学习率: 2e-4
echo.

set /p MODEL_PATH="请输入模型路径 (直接回车使用默认: models\Qwen2.5-7B-Instruct): "
if "%MODEL_PATH%"=="" set MODEL_PATH=models\Qwen2.5-7B-Instruct

python scripts\train_lora.py ^
    --model_path "%MODEL_PATH%" ^
    --data_path dataset\train.json ^
    --output_dir output ^
    --epochs 3 ^
    --batch_size 2 ^
    --learning_rate 2e-4 ^
    --rank 16 ^
    --alpha 32

if errorlevel 1 (
    echo 错误: 训练失败
    pause
    exit /b 1
)

REM 验证模型
echo.
echo [7/7] 验证微调模型...
python scripts\validate_model.py --model_path output

echo.
echo ============================================================
echo 训练完成！
echo ============================================================
echo.
echo 下一步:
echo 1. 查看 output 目录下的训练结果
echo 2. 使用 export_gguf.py 导出为GGUF格式
echo 3. 使用Ollama部署微调模型
echo.

pause
