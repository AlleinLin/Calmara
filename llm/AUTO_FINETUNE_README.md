# Calmara 自动化微调系统

## 概述

Calmara 自动化微调系统是一个完整的机器学习工作流程解决方案，用于在心理健康对话数据更新时自动触发模型微调，持续优化模型性能。

## 系统架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   数据导入       │────▶│   触发检测       │────▶│   微调执行       │
│ (KnowledgeBase) │     │ (AutoFinetuneSvc)│     │ (Python脚本)    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    监控 & 结果评估 & 模型更新                      │
└─────────────────────────────────────────────────────────────────┘
```

## 核心功能

### 1. 自动触发机制

| 触发条件 | 默认值 | 说明 |
|----------|--------|------|
| 新增文档数 | ≥ 1000 | 知识库新增文档达到阈值 |
| 时间间隔 | ≥ 7天 | 距上次训练超过指定天数 |
| 定时调度 | 每周日凌晨2点 | Cron表达式配置 |

### 2. 资源保护

| 保护措施 | 默认值 | 说明 |
|----------|--------|------|
| CPU使用率 | < 70% | 超过阈值推迟训练 |
| 内存使用率 | < 85% | 超过阈值推迟训练 |
| 最大训练时长 | 6小时 | 超时自动停止 |
| GPU温度监控 | < 85°C | 过热自动暂停 |

### 3. 远程服务器支持

- SSH密钥认证连接
- 自动数据同步
- 远程训练执行
- 结果自动拉取

### 4. 模型评估与更新

- 训练损失监控
- 困惑度评估
- 响应质量评分
- 安全性检测
- 自动回滚机制

## 配置说明

### application.yml 配置

```yaml
calmara:
  finetune:
    enabled: true                          # 启用自动微调
    min-new-documents: 1000                # 触发阈值：新增文档数
    min-days-since-last: 7                 # 触发阈值：距上次训练天数
    max-cpu-percent: 70                    # 资源限制：CPU使用率
    max-memory-percent: 85                 # 资源限制：内存使用率
    max-training-hours: 6                  # 最大训练时长
    training-script: llm/scripts/train_lora_enhanced.py
    config-file: llm/config/training_config.json
    state-file: llm/config/finetune_state.json
    log-file: logs/finetune.log
    remote:
      enabled: false                       # 启用远程训练
      host: gpu-server.example.com         # 远程服务器地址
      port: 22                             # SSH端口
      username: train                      # 用户名
      private-key-path: /path/to/key       # 私钥路径
      model-path: /data/models             # 远程模型路径
```

### 训练配置文件

位置: `llm/config/training_config.json`

```json
{
    "model_config": {
        "base_model": "Qwen/Qwen2.5-7B-Instruct",
        "model_path_local": "llm/models/Qwen/Qwen2.5-7B-Instruct"
    },
    "lora_config": {
        "rank": 32,
        "alpha": 64,
        "dropout": 0.1,
        "target_modules": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]
    },
    "training_config": {
        "num_train_epochs": 3,
        "per_device_train_batch_size": 4,
        "gradient_accumulation_steps": 8,
        "learning_rate": 1e-4,
        "max_length": 512
    },
    "dataset": {
        "train_path": "llm/dataset/train.json",
        "test_path": "llm/dataset/test.json",
        "max_samples": 50000
    }
}
```

## API 接口

### 训练管理

#### 获取训练状态

```http
GET /api/admin/finetune/status
```

响应示例:
```json
{
    "code": 200,
    "data": {
        "status": "training",
        "reason": "新增文档数达到阈值: 1523 >= 1000",
        "startTime": "2024-01-15T02:00:00",
        "progress": 45,
        "currentEpoch": 2,
        "currentLoss": 0.234
    }
}
```

#### 手动触发训练

```http
POST /api/admin/finetune/trigger
Content-Type: application/json

{
    "reason": "手动触发微调"
}
```

#### 停止训练

```http
POST /api/admin/finetune/stop
```

### 配置管理

#### 获取完整配置

```http
GET /api/admin/finetune/config
```

#### 更新完整配置

```http
POST /api/admin/finetune/config
Content-Type: application/json

{
    "enabled": true,
    "trigger": {...},
    "resourceControl": {...},
    "trainingStrategy": {...},
    "remoteServer": {...}
}
```

#### 部分更新配置

```http
PATCH /api/admin/finetune/config
Content-Type: application/json

{
    "enabled": true,
    "trigger": {
        "minNewDocuments": 500
    }
}
```

#### 重置为默认配置

```http
POST /api/admin/finetune/config/reset
```

### 子模块配置

#### 触发器配置

```http
GET  /api/admin/finetune/config/trigger
POST /api/admin/finetune/config/trigger
```

#### 资源控制配置

```http
GET  /api/admin/finetune/config/resource
POST /api/admin/finetune/config/resource
```

#### 训练策略配置

```http
GET  /api/admin/finetune/config/training
POST /api/admin/finetune/config/training
```

#### 评估配置

```http
GET  /api/admin/finetune/config/evaluation
POST /api/admin/finetune/config/evaluation
```

#### 模型管理配置

```http
GET  /api/admin/finetune/config/model-management
POST /api/admin/finetune/config/model-management
```

#### 通知配置

```http
GET  /api/admin/finetune/config/notification
POST /api/admin/finetune/config/notification
```

### 远程服务器管理

#### 获取远程服务器配置

```http
GET /api/admin/finetune/config/remote-server
```

#### 更新远程服务器配置

```http
POST /api/admin/finetune/config/remote-server
Content-Type: application/json

{
    "enabled": true,
    "host": "192.168.1.100",
    "port": 22,
    "username": "train",
    "password": "",
    "privateKeyPath": "/path/to/private_key",
    "privateKeyPassphrase": "",
    "remoteModelPath": "/data/models/calmara",
    "remoteDataPath": "/data/calmara/dataset",
    "remotePythonPath": "python3",
    "connectionTimeout": 30000,
    "commandTimeout": 3600000
}
```

#### 测试远程服务器连接

```http
POST /api/admin/finetune/remote/test-connection
Content-Type: application/json

{
    "host": "192.168.1.100",
    "port": 22,
    "username": "train",
    "privateKeyPath": "/path/to/key"
}
```

响应示例:
```json
{
    "code": 200,
    "data": {
        "host": "192.168.1.100",
        "port": 22,
        "connected": true,
        "message": "连接成功",
        "gpuInfo": "NVIDIA RTX 4090, 24564 MiB",
        "pythonVersion": "Python 3.10.12",
        "availableDiskSpace": "500G"
    }
}
```

#### 测试当前配置的远程服务器

```http
POST /api/admin/finetune/remote/test-connection/current
```

#### 执行远程命令

```http
POST /api/admin/finetune/remote/execute
Content-Type: application/json

{
    "command": "nvidia-smi"
}
```

#### 上传文件到远程服务器

```http
POST /api/admin/finetune/remote/upload
Content-Type: application/json

{
    "localPath": "llm/dataset/train.json",
    "remotePath": "/data/calmara/dataset/train.json"
}
```

#### 从远程服务器下载文件

```http
POST /api/admin/finetune/remote/download
Content-Type: application/json

{
    "remotePath": "/data/calmara/output/model.safetensors",
    "localPath": "llm/output/model.safetensors"
}
```

## 工作流程

### 自动触发流程

```
1. 定时检查 (每小时)
   │
   ├─▶ 检查新增文档数
   │
   ├─▶ 检查距上次训练天数
   │
   └─▶ 满足任一条件 → 进入资源检测
   
2. 资源检测
   │
   ├─▶ CPU使用率检查
   │
   ├─▶ 内存使用率检查
   │
   └─▶ 资源充足 → 开始训练
       资源不足 → 推迟训练并记录原因

3. 训练执行
   │
   ├─▶ 准备训练数据 (prepare_training_data.py)
   │
   ├─▶ 启动训练进程 (train_lora_enhanced.py)
   │
   └─▶ 监控训练进度

4. 结果评估
   │
   ├─▶ 检查训练损失
   │
   ├─▶ 评估模型质量
   │
   └─▶ 效果提升 → 保存新模型
       效果下降 → 回滚到旧模型

5. 状态更新
   │
   └─▶ 更新 finetune_state.json
```

### 远程训练流程

```
1. 连接远程服务器 (SSH)
   │
2. 同步训练数据到远程
   │
3. 执行远程训练命令
   │
4. 监控远程训练状态
   │
5. 拉取训练结果
   │
6. 更新本地模型
```

## 文件结构

```
Calmara/
├── Calmara-admin/
│   └── src/main/java/com/calmara/admin/
│       ├── controller/
│       │   └── FinetuneController.java      # REST API控制器
│       └── service/
│           └── AutoFinetuneService.java     # 核心服务
│
├── llm/
│   ├── config/
│   │   ├── training_config.json            # 训练参数配置
│   │   ├── auto_finetune_config.json       # 自动微调配置
│   │   └── finetune_state.json             # 状态持久化
│   │
│   ├── dataset/
│   │   ├── train.json                      # 训练数据
│   │   └── test.json                       # 测试数据
│   │
│   ├── scripts/
│   │   ├── prepare_training_data.py        # 数据准备脚本
│   │   ├── train_lora_enhanced.py          # 训练脚本
│   │   └── evaluate_model.py               # 评估脚本
│   │
│   └── output/                             # 训练输出目录
│       └── calmara-lora-YYYYMMDD-HHMMSS/
│           ├── epoch-1/
│           ├── epoch-2/
│           ├── best/
│           └── final/
│
└── logs/
    └── finetune.log                        # 训练日志
```

## 使用示例

### 启用自动微调

在 `application.yml` 中设置:

```yaml
calmara:
  finetune:
    enabled: true
```

### 手动触发训练

```bash
curl -X POST http://localhost:8080/api/admin/finetune/trigger \
  -H "Content-Type: application/json" \
  -d '{"reason": "导入新数据集后手动触发"}'
```

### 查看训练进度

```bash
curl http://localhost:8080/api/admin/finetune/status
```

### 配置远程GPU服务器

```yaml
calmara:
  finetune:
    remote:
      enabled: true
      host: 192.168.1.100
      port: 22
      username: calmara
      private-key-path: ~/.ssh/calmara_key
      model-path: /data/calmara/models
```

## 监控与告警

### 日志位置

- 训练日志: `logs/finetune.log`
- 应用日志: 控制台输出

### 状态文件

位置: `llm/config/finetune_state.json`

```json
{
    "lastDocumentCount": 15234,
    "lastTrainingTime": "2024-01-15T02:00:00",
    "lastSuccessfulTraining": "2024-01-15T04:30:00",
    "trainingCount": 5,
    "postponedCount": 2,
    "lastPostponedReason": "系统资源不足",
    "lastTrainingLoss": 0.234
}
```

### Webhook 通知

配置 `auto_finetune_config.json`:

```json
{
    "notification": {
        "on_start": true,
        "on_complete": true,
        "on_failure": true,
        "webhook_url": "https://your-webhook-url/notify"
    }
}
```

## 异常处理

| 异常情况 | 处理方式 |
|----------|----------|
| 资源不足 | 推迟训练，记录原因 |
| 训练超时 | 自动停止，保留检查点 |
| GPU过热 | 暂停训练，温度降低后恢复 |
| 模型效果下降 | 自动回滚到上一版本 |
| 远程连接失败 | 降级到本地训练 |

## 最佳实践

1. **资源规划**: 确保服务器有足够的GPU内存和存储空间
2. **数据质量**: 定期检查训练数据质量，过滤低质量样本
3. **监控告警**: 配置Webhook及时获取训练状态
4. **模型备份**: 启用 `backup_before_update` 保留历史版本
5. **渐进式训练**: 使用较小的学习率进行增量微调

## 故障排查

### 训练未触发

1. 检查 `finetune.enabled` 是否为 `true`
2. 检查触发条件是否满足
3. 查看日志中的资源检测结果

### 训练失败

1. 查看 `logs/finetune.log` 日志
2. 检查GPU内存是否充足
3. 验证训练数据格式是否正确

### 远程训练连接失败

1. 验证SSH密钥权限
2. 检查网络连通性
3. 确认远程服务器Python环境

## 版本历史

- v1.0.0 - 初始版本，支持自动触发和本地训练
- v1.1.0 - 添加远程服务器支持
- v1.2.0 - 添加模型评估和自动回滚
- v1.3.0 - 添加Webhook通知和完整API

## 许可证

Apache License 2.0
