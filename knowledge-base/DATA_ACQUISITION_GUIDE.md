
# 心理健康数据获取指南

## 已获取数据

### 1. 示例知识库 (psychology_knowledge.json)
- 位置: knowledge-base/psychology_knowledge.json
- 内容: 5个心理学专业知识文档
- 状态: ✅ 已创建

### 2. Mental Health Chatbot Dataset
- 位置: knowledge-base/external/mental_health_chatbot_train.parquet
- 来源: HuggingFace
- 内容: 172个心理健康对话样本
- 状态: ✅ 已下载

## 待获取数据

### 3. SoulChatCorpus (推荐)
- 来源: 华南理工大学
- URL: https://www.modelscope.cn/datasets/YIRONGCHEN/SoulChatCorpus
- 内容: 258,354个多轮对话，1,517,344轮
- 许可: 研究用途
- 获取方式: 访问ModelScope网站下载

### 4. 心理咨询问答语料库 (efaqa-corpus-zh)
- 来源: Chatopera
- URL: https://github.com/chatopera/efaqa-corpus-zh
- 内容: 20,000条心理咨询数据
- 许可: 春松许可证 v1.0 (需要购买证书)
- 获取方式: 购买证书后使用pip安装

### 5. PsyDTCorpus
- 来源: 华南理工大学
- URL: https://hyper.ai/cn/datasets/35465
- 内容: 5,000个高质量对话，90,365轮
- 许可: 研究用途
- 获取方式: 联系研究团队申请

## 数据整合说明

所有数据需要转换为以下格式后导入RAG知识库:

```json
{
  "id": "唯一标识",
  "title": "文档标题",
  "content": "文档内容",
  "category": "分类",
  "keywords": ["关键词1", "关键词2"]
}
```

使用KnowledgeBaseLoader.loadCustomKnowledge()方法导入。
