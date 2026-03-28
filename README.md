<div align="center">

# 🧠 Calmara

**多模态智心 Agent 智能体系统**

面向校园心理健康场景的 AI 智能体系统，结合多模态感知、大语言模型和 Agentic RAG

[!\[Java\](https://img.shields.io/badge/Java-17-orange.svg null)](https://openjdk.org/)
[!\[Spring Boot\](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg null)](https://spring.io/projects/spring-boot)
[!\[License\](https://img.shields.io/badge/License-Internal-blue.svg null)]()

[功能特性](#-功能特性) • [系统架构](#-系统架构) • [快速开始](#-快速开始) • [API文档](#-api-文档) • [模型训练](#-模型训练) • [部署指南](#-部署指南)

</div>

***

## 📋 目录

- [功能特性](#-功能特性)
- [系统架构](#-系统架构)
- [技术栈](#-技术栈)
- [项目结构](#-项目结构)
- [快速开始](#-快速开始)
- [API 文档](#-api-文档)
- [知识库系统](#-知识库系统)
- [模型训练](#-模型训练)
- [部署指南](#-部署指南)
- [配置说明](#-配置说明)

***

## ✨ 功能特性

### 🎯 核心能力

| 特性              | 描述                                 |
| --------------- | ---------------------------------- |
| **多模态输入**       | 支持文本、语音、图像、视频四种输入方式                |
| **情绪融合**        | 视觉(50%) + 听觉(40%) + 文本(10%) 加权情绪计算 |
| **意图分类**        | CHAT / CONSULT / RISK 三路智能分类       |
| **Agentic RAG** | 智能知识检索，多步推理，幻觉检测                   |
| **MCP 集成**      | Excel 记录写入 + 邮件预警通知                |
| **SSE 流式**      | 实时打字机效果响应                          |
| **角色安全**        | User/Admin 角色隔离，JWT 认证             |

### 🔄 三种 RAG 模式

```
┌─────────────────────────────────────────────────────────────────┐
│  Simple RAG        Router RAG         Agentic RAG              │
│  ───────────       ──────────         ─────────────            │
│  Query → 检索      Query → 路由       Query → 规划              │
│     ↓                ↓                   ↓                      │
│  生成回答         闲聊/检索           多步检索                   │
│                                          ↓                      │
│                                     校验 → 生成                  │
│                                          ↓                      │
│                                     不合理? → 重试               │
└─────────────────────────────────────────────────────────────────┘
```

| 模式          | 特点        | 适用场景  | API 端点                  |
| ----------- | --------- | ----- | ----------------------- |
| Simple RAG  | 简单直接，每次检索 | 简单查询  | `POST /api/rag/simple`  |
| Router RAG  | 智能路由，动态分流 | 效率优先  | `POST /api/rag/router`  |
| Agentic RAG | 多步推理，循环校验 | 准确性优先 | `POST /api/rag/agentic` |

***

## 🏗️ 系统架构

### 多模态情绪融合

```
用户输入 (表情/语气/文本)
        ↓
┌───────────────────────────────┐
│   多模态情绪融合 (初筛)        │
│   ┌─────────────────────┐    │
│   │ 视觉情绪 (50%)      │    │
│   │ 听觉情绪 (40%)      │    │
│   │ 文本情绪 (10%)      │    │
│   └─────────────────────┘    │
└───────────────────────────────┘
        ↓
   加权分数 >= 2.0 → 标记风险预兆
        ↓
   输出: EmotionResult
```

> ⚠️ **重要**: 多模态情绪融合只是初筛，不会直接触发预警，避免误报。

### Agentic RAG 决策流程

```
用户输入 + 多模态情绪分数
        ↓
┌─────────────────────────────────────────────────────────┐
│                 MultiAgentCoordinator                   │
└─────────────────────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────────────────────┐
│  Step 1: QueryAnalysisAgent                             │
│  • 语义理解、情绪关键词提取                              │
│  • 意图初步判断、上下文关联                              │
└─────────────────────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────────────────────┐
│  Step 2: RiskAssessmentAgent                            │
│  • 结合情绪分数 + 对话内容 + 上下文                      │
│  • 判定: HIGH / MEDIUM / LOW                            │
└─────────────────────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────────────────────┐
│  Step 3: ConsultationAgent (非 HIGH 时触发)             │
│  • 基于知识库提供专业心理支持                           │
│  • 共情优先、非指导性引导                               │
└─────────────────────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────────────────────┐
│  Step 4: ResponseGenerationAgent                        │
│  • 整合所有 Agent 结果                                  │
│  • 最终意图判定: CHAT / CONSULT / RISK                  │
│  • RISK → 触发邮件预警 + Excel 记录                     │
└─────────────────────────────────────────────────────────┘
```

### 风险预警流程

```
多模态情绪融合 (初筛)
        ↓
   分数 >= 2.0 → 标记风险预兆 (不预警)
        ↓
Agentic RAG (决策层)
        ↓
   ┌─────────────────────────────────┐
   │  RISK 判定?                      │
   │  ├── 是 → 邮件预警 + Excel 记录  │
   │  └── 否 → 正常咨询回复           │
   └─────────────────────────────────┘
```

***

## 💻 技术栈

<table>
<tr>
<td width="50%">

### 后端

- **Java 17** + **Spring Boot 3.2**
- **Spring Security 6** - 认证授权
- **Spring AI** - AI 集成框架
- **MyBatis Plus** - ORM 框架

</td>
<td width="50%">

### AI/ML

- **Qwen2.5-7B** - 大语言模型 (微调版)
- **Ollama** - 本地 LLM 推理
- **Whisper** - 语音识别
- **MediaPipe** - 情绪识别

</td>
</tr>
<tr>
<td width="50%">

### 数据存储

- **PostgreSQL 16** - 主数据库
- **Redis 7** - 缓存/会话
- **ChromaDB** - 向量数据库

</td>
<td width="50%">

### 工具库

- **Lombok** - 代码简化
- **Hutool** - 工具集
- **Jackson** - JSON 处理
- **Apache POI** - Excel 操作

</td>
</tr>
</table>

***

## 📁 项目结构

```
Calmara/
├── 📂 Calmara-common/          # 公共工具类
│   ├── session/                # Redis 会话管理
│   └── utils/                  # 工具类
│
├── 📂 Calmara-model/           # 数据模型层
│   ├── dto/                    # 数据传输对象
│   ├── entity/                 # 实体类
│   ├── enums/                  # 枚举定义
│   └── mapper/                 # MyBatis Mapper
│
├── 📂 Calmara-multimodal/      # 多模态处理
│   ├── emotion/                # 情绪分析
│   ├── fusion/                 # 多模态融合
│   ├── mediapipe/              # 视觉情绪
│   ├── video/                  # 视频处理
│   └── whisper/                # 语音识别
│
├── 📂 Calmara-agent-core/      # Agent 核心模块
│   ├── Agent.java              # Agent 接口
│   ├── QueryAnalysisAgent.java # 查询分析
│   ├── RiskAssessmentAgent.java# 风险评估
│   ├── ConsultationAgent.java  # 心理咨询
│   ├── ResponseGenerationAgent.java
│   ├── MultiAgentCoordinator.java
│   └── rag/                    # RAG 实现
│       ├── SimpleRAGService.java
│       ├── RouterRAGService.java
│       ├── AgenticRAGService.java
│       ├── VectorStore.java
│       └── embedding/          # Embedding 提供者
│
├── 📂 Calmara-mcp/             # MCP 服务集成
│   ├── email/                  # 邮件服务
│   ├── excel/                  # Excel 服务
│   └── SagaOrchestrator.java   # Saga 事务
│
├── 📂 Calmara-security/        # 安全认证
│   ├── filter/                 # JWT 过滤器
│   ├── service/                # 认证服务
│   └── utils/                  # JWT 工具
│
├── 📂 Calmara-api/             # API 接口层
│   ├── controller/             # REST 控制器
│   └── websocket/              # WebSocket
│
├── 📂 Calmara-admin/           # 管理后台
│   ├── controller/             # 管理接口
│   └── service/                # 管理服务
│
├── 📂 Calmara-web/             # Web 应用入口
│   └── CalmaraApplication.java
│
├── 📂 llm/                     # 模型训练
│   ├── models/                 # 模型文件
│   │   └── download_model.py   # 模型下载脚本
│   ├── scripts/                # 训练脚本
│   ├── dataset/                # 训练数据
│   └── output/                 # 训练输出
│
├── 📂 knowledge-base/          # 知识库
│   ├── chinese_psychology_knowledge.json
│   ├── soulchat_examples.json
│   └── psychology_knowledge.json
│
├── 📂 deploy/                  # 部署配置
│   └── bge-m3/                 # BGE-M3 服务
│
├── 📄 docker-compose.yml       # Docker 编排
├── 📄 .env.example             # 环境变量示例
└── 📄 pom.xml                  # Maven 配置
```

***

## 🚀 快速开始

### 前置要求

| 依赖         | 版本   | 说明                     |
| ---------- | ---- | ---------------------- |
| JDK        | 17   | ⚠️ JDK 25 与 Lombok 不兼容 |
| Maven      | 3.8+ | 构建工具                   |
| Docker     | 最新   | 容器化部署                  |
| PostgreSQL | 16   | 数据库                    |
| Redis      | 7    | 缓存                     |

### 1. 克隆项目

```bash
git clone 
cd Calmara
```

### 2. 下载模型

```bash
cd llm/models

# 安装依赖
pip install huggingface_hub

# 运行下载脚本
python download_model.py
```

### 3. 启动依赖服务

```bash
# 使用 Docker 启动 PostgreSQL 和 Redis
docker run -d --name calmara-postgres \
  -e POSTGRES_DB=calmara \
  -e POSTGRES_USER=calmara \
  -e POSTGRES_PASSWORD=calmara_password \
  -p 5432:5432 \
  postgres:16-alpine

docker run -d --name calmara-redis \
  -p 6379:6379 \
  redis:7-alpine
```

### 4. 构建项目

```powershell
# Windows PowerShell
$env:JAVA_HOME = "JDK17"
mvn clean package -DskipTests
```

```bash
# Linux/macOS
export JAVA_HOME=/path/to/jdk17
mvn clean package -DskipTests
```

### 5. 运行应用

```bash
java -jar Calmara-web/target/Calmara-web-1.0.0.jar
```

访问: <http://localhost:8080>

***

## 📖 API 文档

### 认证接口

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

### 聊天接口 (SSE 流式)

```http
POST /api/chat/stream
Content-Type: multipart/form-data

text: "我最近很焦虑"
sessionId: (可选)
audio: (可选)
image: (可选)
```

### RAG 接口

```http
# Simple RAG
POST /api/rag/simple
Content-Type: application/json
{
  "query": "如何缓解考试焦虑?",
  "emotionResult": { "label": "焦虑", "score": 2.0 }
}

# Router RAG
POST /api/rag/router

# Agentic RAG
POST /api/rag/agentic
```

### 情绪分析接口

```http
POST /api/emotion/analyze/text
POST /api/emotion/analyze/audio
POST /api/emotion/analyze/image
```

### 知识库管理接口

| 接口                                      | 方法     | 功能      |
| --------------------------------------- | ------ | ------- |
| `/api/admin/knowledge-base/import/json` | POST   | JSON 导入 |
| `/api/admin/knowledge-base/import/file` | POST   | 文件导入    |
| `/api/admin/knowledge-base/batch`       | POST   | 批量导入    |
| `/api/admin/knowledge-base/add`         | POST   | 添加文档    |
| `/api/admin/knowledge-base/update/{id}` | PUT    | 更新文档    |
| `/api/admin/knowledge-base/delete/{id}` | DELETE | 删除文档    |
| `/api/admin/knowledge-base/list`        | GET    | 查询列表    |
| `/api/admin/knowledge-base/stats`       | GET    | 统计信息    |

***

## 📚 知识库系统

### 内置知识库

| 知识库           | 文件                                  | 内容        |
| ------------- | ----------------------------------- | --------- |
| 中文心理学知识       | `chinese_psychology_knowledge.json` | 15 个专业分类  |
| SoulChat 对话示例 | `soulchat_examples.json`            | 5 个真实咨询对话 |
| 基础心理学知识       | `psychology_knowledge.json`         | 焦虑、抑郁、失眠等 |

### 外部数据源

| 数据集             | 来源        | 规模      | 许可         |
| --------------- | --------- | ------- | ---------- |
| SoulChatCorpus  | 华南理工大学    | 258K 对话 | Apache 2.0 |
| efaqa-corpus-zh | Chatopera | 20K 条   | 商业许可       |
| PsyDTCorpus     | 华南理工大学    | 5K 对话   | 研究用途       |

### 数据格式

```json
{
  "id": "unique-id",
  "title": "文档标题",
  "content": "文档内容",
  "category": "分类",
  "keywords": ["关键词1", "关键词2"]
}
```

***

## 🎓 模型训练

### 环境准备

```bash
cd llm

# 安装 Python 依赖
pip install -r requirements.txt

# 安装 PyTorch (CUDA 支持)
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
```

### 数据准备

```bash
python scripts/prepare_data.py
```

### 开始训练

**Windows:**

```cmd
train.bat
```

**Linux/macOS:**

```bash
chmod +x train.sh
./train.sh
```

**手动执行:**

```bash
python scripts/train_lora.py \
    --model_path models/Qwen2.5-7B-Instruct \
    --data_path dataset/train.json \
    --output_dir output \
    --epochs 3 \
    --batch_size 2 \
    --learning_rate 2e-4 \
    --rank 16 \
    --alpha 32
```

### 训练参数

| 参数              | 默认值                      | 说明         |
| --------------- | ------------------------ | ---------- |
| `model_path`    | Qwen/Qwen2.5-7B-Instruct | 模型路径       |
| `data_path`     | dataset/train.json       | 训练数据       |
| `output_dir`    | output                   | 输出目录       |
| `epochs`        | 3                        | 训练轮数       |
| `batch_size`    | 2                        | 批次大小       |
| `learning_rate` | 2e-4                     | 学习率        |
| `rank`          | 16                       | LoRA rank  |
| `alpha`         | 32                       | LoRA alpha |

### 硬件要求

| 配置  | 最低要求     | 推荐配置             |
| --- | -------- | ---------------- |
| GPU | 8GB VRAM | RTX 3080 Ti 16GB |
| RAM | 16GB     | 32GB             |
| 存储  | 30GB     | 50GB SSD         |

### 模型验证

```bash
python scripts/validate_model.py --model_path output/qwen2.5-7b-lora-xxxxx/final
```

### 导出 GGUF

```bash
python scripts/export_gguf.py \
    --model_path output/qwen2.5-7b-lora-xxxxx/final \
    --output_dir output/gguf
```

### Ollama 部署

```bash
cd output/gguf
ollama create calmara -f Modelfile .
ollama run calmara
```

***

## 🐳 部署指南

### Docker Compose (推荐)

```bash
# 复制配置文件
cp .env.example .env

# 编辑配置
vim .env

# 启动所有服务
docker-compose up -d
```

### 服务端口

| 服务          | 端口    | 说明           |
| ----------- | ----- | ------------ |
| Calmara API | 8080  | 主应用          |
| PostgreSQL  | 5432  | 数据库          |
| Redis       | 6379  | 缓存           |
| ChromaDB    | 8000  | 向量数据库        |
| Ollama      | 11434 | LLM 服务       |
| BGE-M3      | 33330 | Embedding 服务 |

### 健康检查

```bash
# 检查服务状态
curl http://localhost:8080/actuator/health

# 检查 ChromaDB
curl http://localhost:8000/api/v1/heartbeat

# 检查 Ollama
curl http://localhost:11434/api/tags
```

***

## ⚙️ 配置说明

### 核心配置

```yaml
# application.yml
calmara:
  ollama:
    base-url: http://localhost:11434
    model: qwen2.5:7b-chat
    finetuned-model: qwen2.5-calmara:latest
  
  embedding:
    provider: ollama
    dimension: 1024
  
  chroma:
    url: http://localhost:8000
    collection-name: psychological_knowledge
    enabled: true
  
  knowledge:
    base-path: ./knowledge-base
    auto-load: true
  
  risk:
    thresholds:
      low: 1.0
      high: 2.0
```

### 风险阈值

| 等级     | 分数范围      | 预警         |
| ------ | --------- | ---------- |
| LOW    | < 1.0     | 否          |
| MEDIUM | 1.0 - 2.0 | 否          |
| HIGH   | >= 2.0    | 邮件 + Excel |

### 默认账户

| 角色  | 用户名   | 密码       |
| --- | ----- | -------- |
| 管理员 | admin | admin123 |
| 用户  | user  | user123  |

***

## 📊 监控指标

访问 Prometheus 指标: `http://localhost:8080/actuator/prometheus`

关键指标:

- `embedding_requests_total` - Embedding 请求总数
- `embedding_latency` - Embedding 延迟
- `vector_store_search_total` - 向量搜索总数
- `vector_store_search_duration` - 搜索耗时

***

## 📄 许可证

MIT

***

## 🙏 致谢

- [华南理工大学未来技术学院](https://futuretech.scut.edu.cn/) - 广东省数字孪生人重点实验室 (SoulChatCorpus)
- [Chatopera](https://github.com/chatopera) - efaqa-corpus-zh
- [ModelScope](https://modelscope.cn/) - 模型与数据平台
- [Qwen Team](https://github.com/QwenLM) - Qwen2.5 大语言模型

***

<div align="center">

**[⬆ 返回顶部](#-calmara)**

Made with ❤️ by Allein

</div>
