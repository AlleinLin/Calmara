# Calmara - Qwen2.5 模型微调与部署指南

## 项目概述

Calmara是基于Qwen2.5-7B的智能心理关怀助手系统，支持多模态感知、情绪识别、心理辅导和危机预警。

## 目录结构

```
Calmara/
├── Calmara-common/           # 公共模块
├── Calmara-model/           # 实体模型
├── Calmara-multimodal/      # 多模态处理
├── Calmara-agent-core/      # Agent核心(Ollama集成)
├── Calmara-mcp/             # MCP服务
├── Calmara-security/         # 安全模块
├── Calmara-api/             # API接口
├── Calmara-web/             # Web前端
├── docker/                  # Docker部署
└── llm/                     # LLM微调模块
    ├── models/              # 模型存储
    ├── dataset/             # 训练数据
    ├── scripts/             # Python脚本
    ├── output/              # 输出目录
    └── config/              # 配置文件
```

## 快速开始

### 1. 检查环境

```bash
python scripts/validate_model.py
```

### 2. 下载模型

```bash
python scripts/download_model.py
```

或手动下载Qwen2.5-7B-Instruct模型到 `models/` 目录

### 3. 准备数据

```bash
python scripts/prepare_data.py
```

### 4. 开始训练

```bash
# Windows
train.bat

# Linux/macOS
chmod +x train.sh && ./train.sh
```

### 5. 导出模型

```bash
python scripts/export_gguf.py --model_path output/final
```

### 6. Ollama部署

```bash
# 启动Ollama服务
ollama serve

# 创建模型
ollama create calmara -f Modelfile

# 运行
ollama run calmara
```

## 训练参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 模型 | Qwen2.5-7B-Instruct | 基础模型 |
| LoRA Rank | 16 | 参数量级 |
| Alpha | 32 | 缩放因子 |
| Epochs | 3 | 训练轮数 |
| Batch Size | 2 | 批次大小 |
| Learning Rate | 2e-4 | 学习率 |
| Max Length | 2048 | 最大序列 |

## 硬件要求

- GPU: NVIDIA RTX 3080 Ti 16GB (已满足)
- RAM: 16GB+
- 存储: 30GB+

## 训练数据集

数据集包含24条心理对话样本，涵盖：
- 焦虑、压力问题
- 情绪低落、抑郁
- 人际关系
- 学习压力
- 危机干预

## Ollama集成

Calmara后端已集成Ollama服务：

```yaml
calmara:
  ollama:
    base-url: http://localhost:11434
    model: qwen2.5:7b-chat
```

## 下一步

1. 完成模型下载（约2小时，取决于网络）
2. 执行训练（RTX 3080 Ti上约2-4小时）
3. 导出GGUF并部署Ollama
4. 启动Calmara后端服务

## 故障排除

### CUDA out of memory
减小batch_size或使用4bit量化

### 模型下载慢
使用镜像源或代理

### 训练中断
Transformers自动保存checkpoint
