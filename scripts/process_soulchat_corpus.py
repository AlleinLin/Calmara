import json
import os
import sys
from pathlib import Path
from datetime import datetime
import ijson

class SoulChatCorpusProcessor:
    def __init__(self, base_dir):
        self.base_dir = Path(base_dir)
        self.knowledge_base_dir = self.base_dir / "knowledge-base"
        self.source_file = self.knowledge_base_dir / "external" / "SoulChatCorpus" / "SoulChatCorpus-sft-multi-Turn.json"
        self.stats = {
            "total_conversations": 0,
            "converted": 0,
            "merged": 0,
            "duplicates": 0,
            "errors": []
        }
    
    def process_and_merge(self):
        print("=" * 60)
        print("SoulChatCorpus 数据处理与合并工具")
        print("=" * 60)
        print(f"源文件: {self.source_file}")
        print(f"文件大小: {os.path.getsize(self.source_file) / 1024 / 1024:.2f} MB")
        print()
        
        existing_docs = self._load_existing_knowledge()
        seen_ids = {doc.get('id') for doc in existing_docs if doc.get('id')}
        print(f"现有知识库: {len(existing_docs)} 个文档")
        
        output_file = self.knowledge_base_dir / "merged_knowledge_base.json"
        
        if output_file.exists():
            backup_file = self.knowledge_base_dir / f"merged_knowledge_base_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            print(f"备份现有文件到: {backup_file.name}")
            import shutil
            shutil.copy(output_file, backup_file)
        
        print("\n开始处理SoulChatCorpus数据集...")
        print("使用流式处理，避免内存溢出...")
        
        batch_size = 10000
        batch = []
        total_added = 0
        
        with open(self.source_file, 'r', encoding='utf-8') as f:
            for i, item in enumerate(ijson.items(f, 'item')):
                try:
                    self.stats["total_conversations"] += 1
                    
                    doc = self._convert_to_knowledge_doc(item, i)
                    
                    if doc and doc['id'] not in seen_ids:
                        existing_docs.append(doc)
                        seen_ids.add(doc['id'])
                        batch.append(doc)
                        total_added += 1
                        self.stats["converted"] += 1
                    else:
                        self.stats["duplicates"] += 1
                    
                    if (i + 1) % batch_size == 0:
                        print(f"  已处理 {i + 1} 条对话, 新增 {total_added} 条...")
                        self._save_progress(existing_docs, output_file)
                        
                except Exception as e:
                    self.stats["errors"].append(f"处理记录 {i} 失败: {e}")
                    if len(self.stats["errors"]) <= 10:
                        print(f"  错误: {e}")
        
        self.stats["merged"] = len(existing_docs)
        
        print(f"\n保存最终合并结果...")
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(existing_docs, f, ensure_ascii=False, indent=2)
        
        file_size = os.path.getsize(output_file) / 1024 / 1024
        print(f"保存完成: {output_file}")
        print(f"文件大小: {file_size:.2f} MB")
        
        self._print_summary()
        
        return output_file
    
    def _load_existing_knowledge(self):
        existing_docs = []
        
        existing_files = [
            self.knowledge_base_dir / "chinese_psychology_knowledge.json",
            self.knowledge_base_dir / "soulchat_examples.json",
            self.knowledge_base_dir / "psychology_knowledge.json",
        ]
        
        for file_path in existing_files:
            if file_path.exists():
                print(f"加载: {file_path.name}")
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                    existing_docs.extend(data)
                except Exception as e:
                    print(f"  加载失败: {e}")
        
        return existing_docs
    
    def _convert_to_knowledge_doc(self, item, index):
        conv_id = item.get('id', index)
        topic = item.get('topic', '心理咨询')
        messages = item.get('messages', [])
        
        if not messages:
            return None
        
        content_parts = []
        for msg in messages:
            role = msg.get('role', 'unknown')
            text = msg.get('content', '')
            role_label = "用户" if role == 'user' else "咨询师"
            content_parts.append(f"{role_label}：{text}")
        
        content = "\n".join(content_parts)
        
        keywords = self._extract_keywords(content, topic)
        
        return {
            "id": f"soulchat_{conv_id:06d}",
            "title": f"{topic}_心理咨询对话_{index + 1}",
            "content": content,
            "category": topic,
            "keywords": keywords
        }
    
    def _extract_keywords(self, content, topic):
        keywords = [topic] if topic else []
        
        keyword_patterns = [
            "焦虑", "抑郁", "压力", "失眠", "孤独", "愤怒", "恐惧",
            "学习", "工作", "家庭", "感情", "社交", "自我",
            "情绪", "心理", "咨询", "治疗", "帮助", "成长",
            "人际关系", "恋爱", "分手", "失恋", "考试", "就业"
        ]
        
        for kw in keyword_patterns:
            if kw in content and kw not in keywords:
                keywords.append(kw)
        
        return keywords[:8]
    
    def _save_progress(self, docs, output_file):
        temp_file = output_file.with_suffix('.tmp')
        with open(temp_file, 'w', encoding='utf-8') as f:
            json.dump(docs, f, ensure_ascii=False)
        temp_file.replace(output_file)
    
    def _print_summary(self):
        print("\n" + "=" * 60)
        print("处理摘要")
        print("=" * 60)
        print(f"总对话数: {self.stats['total_conversations']:,}")
        print(f"转换成功: {self.stats['converted']:,}")
        print(f"合并总数: {self.stats['merged']:,}")
        print(f"重复跳过: {self.stats['duplicates']:,}")
        
        if self.stats['errors']:
            print(f"\n错误数: {len(self.stats['errors'])}")


def main():
    base_dir = Path("e:/项目AI/Calmara")
    
    processor = SoulChatCorpusProcessor(base_dir)
    
    output_file = processor.process_and_merge()
    
    print("\n" + "=" * 60)
    print("后续步骤")
    print("=" * 60)
    print("1. 确保Calmara服务已启动 (端口8080)")
    print("2. 运行导入脚本将数据导入向量数据库:")
    print("   python scripts/import_to_vector_db.py")
    print("=" * 60)


if __name__ == "__main__":
    main()
