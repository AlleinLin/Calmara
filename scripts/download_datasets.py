import requests
import json
import os
from pathlib import Path
from datetime import datetime
import hashlib

class DataAcquisitionMonitor:
    def __init__(self, log_file="data_acquisition.log"):
        self.log_file = log_file
        self.results = {
            "success": [],
            "failed": [],
            "timestamp": datetime.now().isoformat()
        }
    
    def log_success(self, source, file_path, size):
        record = {
            "source": source,
            "file": file_path,
            "size": size,
            "timestamp": datetime.now().isoformat(),
            "status": "SUCCESS"
        }
        self.results["success"].append(record)
        self._write_log(f"[SUCCESS] {source} -> {file_path} ({size} bytes)")
    
    def log_failure(self, source, error):
        record = {
            "source": source,
            "error": str(error),
            "timestamp": datetime.now().isoformat(),
            "status": "FAILED"
        }
        self.results["failed"].append(record)
        self._write_log(f"[FAILED] {source}: {error}")
    
    def _write_log(self, message):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with open(self.log_file, "a", encoding="utf-8") as f:
            f.write(f"[{timestamp}] {message}\n")
        print(message)
    
    def save_report(self, report_file="data_acquisition_report.json"):
        with open(report_file, "w", encoding="utf-8") as f:
            json.dump(self.results, f, ensure_ascii=False, indent=2)
        print(f"\n报告已保存到: {report_file}")
        print(f"成功: {len(self.results['success'])} 个")
        print(f"失败: {len(self.results['failed'])} 个")

def download_file(url, save_path, monitor, source_name):
    try:
        print(f"正在下载: {source_name}")
        print(f"URL: {url}")
        
        response = requests.get(url, stream=True, timeout=30)
        response.raise_for_status()
        
        os.makedirs(os.path.dirname(save_path), exist_ok=True)
        
        with open(save_path, "wb") as f:
            for chunk in response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
        
        file_size = os.path.getsize(save_path)
        monitor.log_success(source_name, save_path, file_size)
        
        return True
    except Exception as e:
        monitor.log_failure(source_name, e)
        return False

def create_sample_knowledge_base():
    knowledge_base = [
        {
            "id": "kb_001",
            "title": "抑郁症识别与应对",
            "content": "抑郁症是一种严重的心理疾病，主要症状包括：持续的情绪低落、兴趣丧失、精力减退、自我评价过低、睡眠障碍、食欲改变、注意力下降、反复出现死亡念头。干预措施：1. 心理治疗：认知行为疗法（CBT）、人际心理治疗；2. 药物治疗：抗抑郁药物需在医生指导下使用；3. 生活方式调整：规律作息、适度运动、社交活动；4. 危机干预：如有自杀念头，立即拨打心理援助热线400-161-9995；5. 家庭支持：家人理解和陪伴非常重要。",
            "category": "depression",
            "keywords": ["抑郁", "低落", "绝望", "无望", "抑郁症"]
        },
        {
            "id": "kb_002",
            "title": "焦虑症缓解方法",
            "content": "焦虑是一种常见的情绪反应，可以通过以下方法缓解：1. 深呼吸练习：缓慢吸气4秒，屏住呼吸4秒，缓慢呼气4秒；2. 正念冥想：专注于当下，观察自己的呼吸和身体感受；3. 身体放松：渐进式肌肉放松，从头到脚逐个部位放松；4. 规律运动：每周3-5次有氧运动，如散步、跑步、游泳；5. 充足睡眠：保持7-9小时的睡眠时间。",
            "category": "anxiety",
            "keywords": ["焦虑", "紧张", "担忧", "坐立不安", "焦虑症"]
        },
        {
            "id": "kb_003",
            "title": "失眠改善方法",
            "content": "失眠是指难以入睡、睡眠质量差或早醒。以下方法可能有助于改善睡眠：1. 保持规律作息：每天在同一时间睡觉和起床；2. 创造良好的睡眠环境：安静、黑暗、适宜的温度；3. 睡前避免刺激：减少咖啡因、酒精摄入，避免使用电子设备；4. 放松技巧：温水泡脚、听轻音乐、阅读纸质书；5. 如果持续失眠，建议寻求专业帮助。",
            "category": "insomnia",
            "keywords": ["失眠", "睡眠", "睡不着", "早醒", "睡眠障碍"]
        },
        {
            "id": "kb_004",
            "title": "压力管理技巧",
            "content": "压力是现代生活中常见的问题，以下方法可以帮助管理压力：1. 时间管理：合理安排时间，设定优先级；2. 学会说'不'：不要承担超出自己能力范围的任务；3. 保持健康生活方式：均衡饮食、规律运动、充足睡眠；4. 寻求支持：与朋友、家人或专业人士交流；5. 放松练习：瑜伽、冥想、深呼吸等。",
            "category": "stress",
            "keywords": ["压力", "紧张", "忙碌", "焦虑", "压力管理"]
        },
        {
            "id": "kb_005",
            "title": "危机干预资源",
            "content": "如果您或您认识的人有自杀想法，请立即寻求帮助：全国心理援助热线：400-161-9995；北京心理危机研究与干预中心：010-82951332；生命热线：400-821-1215；青少年心理咨询热线：12355。请记住：您的生命非常宝贵，无论现在感到多么绝望，总有可以帮助您的人。请联系身边的亲人朋友，或直接拨打上述热线。",
            "category": "crisis",
            "keywords": ["自杀", "自残", "自伤", "危机", "自杀念头"]
        }
    ]
    
    return knowledge_base

def main():
    monitor = DataAcquisitionMonitor()
    
    base_dir = Path("e:/项目AI/calmara/knowledge-base")
    base_dir.mkdir(parents=True, exist_ok=True)
    
    print("=" * 60)
    print("开始数据获取与系统集成任务")
    print("=" * 60)
    
    print("\n步骤1: 创建示例知识库...")
    sample_kb = create_sample_knowledge_base()
    sample_file = base_dir / "psychology_knowledge.json"
    with open(sample_file, "w", encoding="utf-8") as f:
        json.dump(sample_kb, f, ensure_ascii=False, indent=2)
    monitor.log_success("示例知识库", str(sample_file), os.path.getsize(sample_file))
    
    print("\n步骤2: 下载英文心理健康对话数据集...")
    urls = [
        {
            "name": "Mental Health Chatbot Dataset (HuggingFace)",
            "url": "https://huggingface.co/datasets/heliosbrahma/mental_health_chatbot_dataset/resolve/main/data/train-00000-of-00001.parquet",
            "file": base_dir / "external" / "mental_health_chatbot_train.parquet"
        }
    ]
    
    for item in urls:
        download_file(item["url"], item["file"], monitor, item["name"])
    
    print("\n步骤3: 创建数据源配置...")
    data_sources = {
        "soulchat_corpus": {
            "name": "SoulChatCorpus 心理健康对话数据集",
            "source": "华南理工大学",
            "url": "https://www.modelscope.cn/datasets/YIRONGCHEN/SoulChatCorpus",
            "description": "258,354个多轮对话，总共1,517,344轮",
            "format": "JSON",
            "license": "研究用途",
            "status": "需要手动下载"
        },
        "efaqa_corpus": {
            "name": "心理咨询问答语料库",
            "source": "Chatopera",
            "url": "https://github.com/chatopera/efaqa-corpus-zh",
            "description": "20,000条心理咨询数据",
            "format": "JSON",
            "license": "春松许可证 v1.0",
            "status": "需要购买证书"
        },
        "psydt_corpus": {
            "name": "PsyDTCorpus 心理咨询师数字孪生数据集",
            "source": "华南理工大学",
            "url": "https://hyper.ai/cn/datasets/35465",
            "description": "5,000个高质量对话，90,365轮",
            "format": "JSON",
            "license": "研究用途",
            "status": "需要申请"
        },
        "mental_health_chatbot": {
            "name": "Mental Health Chatbot Dataset",
            "source": "HuggingFace",
            "url": "https://huggingface.co/datasets/heliosbrahma/mental_health_chatbot_dataset",
            "description": "172个心理健康对话样本",
            "format": "Parquet",
            "license": "开源",
            "status": "已下载"
        }
    }
    
    config_file = base_dir / "data_sources.json"
    with open(config_file, "w", encoding="utf-8") as f:
        json.dump(data_sources, f, ensure_ascii=False, indent=2)
    monitor.log_success("数据源配置", str(config_file), os.path.getsize(config_file))
    
    print("\n步骤4: 创建数据获取指南...")
    guide = """
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
"""
    
    guide_file = base_dir / "DATA_ACQUISITION_GUIDE.md"
    with open(guide_file, "w", encoding="utf-8") as f:
        f.write(guide)
    monitor.log_success("数据获取指南", str(guide_file), os.path.getsize(guide_file))
    
    print("\n" + "=" * 60)
    monitor.save_report()
    print("=" * 60)

if __name__ == "__main__":
    main()
