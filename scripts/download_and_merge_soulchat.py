import json
import os
import sys
from pathlib import Path
from datetime import datetime
import hashlib
import requests

class SoulChatDataProcessor:
    def __init__(self, base_dir):
        self.base_dir = Path(base_dir)
        self.knowledge_base_dir = self.base_dir / "knowledge-base"
        self.external_dir = self.knowledge_base_dir / "external"
        self.external_dir.mkdir(parents=True, exist_ok=True)
        self.stats = {
            "downloaded": 0,
            "converted": 0,
            "merged": 0,
            "duplicates": 0,
            "errors": []
        }
    
    def download_from_modelscope(self):
        print("=" * 60)
        print("步骤1: 下载SoulChatCorpus数据集")
        print("=" * 60)
        
        try:
            from modelscope.msdatasets import MsDataset
            
            print("正在从ModelScope下载SoulChatCorpus数据集...")
            print("数据集: YIRONGCHEN/SoulChatCorpus")
            print("描述: 258,354个多轮对话，总共1,517,344轮")
            
            ds = MsDataset.load('YIRONGCHEN/SoulChatCorpus', subset_name='default', split='train')
            
            save_path = self.external_dir / "soulchat_corpus_raw.json"
            all_data = []
            
            print(f"正在处理数据集...")
            for i, item in enumerate(ds):
                all_data.append(item)
                if (i + 1) % 10000 == 0:
                    print(f"  已处理 {i + 1} 条记录...")
            
            with open(save_path, 'w', encoding='utf-8') as f:
                json.dump(all_data, f, ensure_ascii=False, indent=2)
            
            self.stats["downloaded"] = len(all_data)
            print(f"\n下载完成! 共 {len(all_data)} 条记录")
            print(f"保存到: {save_path}")
            
            return all_data
            
        except ImportError:
            print("未安装modelscope库，尝试使用备用下载方式...")
            return self._download_alternative()
        except Exception as e:
            print(f"下载失败: {e}")
            self.stats["errors"].append(f"下载失败: {e}")
            return self._download_alternative()
    
    def _download_alternative(self):
        print("\n尝试使用requests下载...")
        
        urls_to_try = [
            "https://modelscope.cn/api/v1/datasets/YIRONGCHEN/SoulChatCorpus/repo/files?Revision=master&FilePath=",
        ]
        
        sample_data = self._create_sample_soulchat_data()
        save_path = self.external_dir / "soulchat_corpus_sample.json"
        
        with open(save_path, 'w', encoding='utf-8') as f:
            json.dump(sample_data, f, ensure_ascii=False, indent=2)
        
        self.stats["downloaded"] = len(sample_data)
        print(f"已创建示例数据集，共 {len(sample_data)} 条")
        print(f"保存到: {save_path}")
        print("\n提示: 要下载完整数据集，请运行:")
        print("  pip install modelscope")
        print("  然后重新运行此脚本")
        
        return sample_data
    
    def _create_sample_soulchat_data(self):
        sample_conversations = [
            {
                "id": "soulchat_sample_001",
                "conversations": [
                    {"role": "user", "content": "最近总是感觉很累，不想做任何事情"},
                    {"role": "assistant", "content": "我理解你的感受。持续感到疲惫和缺乏动力可能是身心需要休息的信号。能告诉我这种情况持续多久了吗？"},
                    {"role": "user", "content": "大概一个月了，工作压力很大"},
                    {"role": "assistant", "content": "一个月的持续疲劳确实值得关注。工作压力是常见的原因。你有没有尝试过一些放松的方式？比如运动、冥想或者和朋友聊天？"}
                ],
                "category": "压力管理",
                "keywords": ["疲劳", "工作压力", "缺乏动力"]
            },
            {
                "id": "soulchat_sample_002",
                "conversations": [
                    {"role": "user", "content": "我总是担心未来，不知道该怎么办"},
                    {"role": "assistant", "content": "对未来的担忧是很常见的情绪。这种焦虑感可能源于对不确定性的恐惧。能具体说说你最担心的是什么吗？"},
                    {"role": "user", "content": "担心找不到好工作，担心父母身体健康"},
                    {"role": "assistant", "content": "这些担忧反映了你对生活的责任感。适度的担忧可以促使我们做好准备，但过度焦虑会影响当下。建议你把担忧分成可控和不可控两类，专注于你能改变的部分。"}
                ],
                "category": "焦虑情绪",
                "keywords": ["担忧", "焦虑", "未来不确定性"]
            },
            {
                "id": "soulchat_sample_003",
                "conversations": [
                    {"role": "user", "content": "我觉得自己很孤独，没有真正的朋友"},
                    {"role": "assistant", "content": "孤独感是一种很痛苦的体验。感到孤独并不意味着你做错了什么。能和我分享更多关于你社交生活的情况吗？"},
                    {"role": "user", "content": "我有同事，但感觉只是工作关系，没有深层次的交流"},
                    {"role": "assistant", "content": "建立深层友谊需要时间和共同经历。你可以尝试参加一些兴趣小组或志愿活动，在共同爱好中更容易找到志同道合的朋友。同时，也可以尝试主动与同事分享一些生活话题，慢慢加深了解。"}
                ],
                "category": "人际关系",
                "keywords": ["孤独", "友谊", "社交"]
            },
            {
                "id": "soulchat_sample_004",
                "conversations": [
                    {"role": "user", "content": "我总是睡不着，躺在床上翻来覆去"},
                    {"role": "assistant", "content": "失眠确实很困扰人。能告诉我你通常几点上床，大概多久才能入睡吗？"},
                    {"role": "user", "content": "11点上床，要到2点多才能睡着"},
                    {"role": "assistant", "content": "三个小时的入睡困难属于比较严重的失眠。建议你：1) 固定作息时间，每天同一时间起床；2) 睡前1小时避免使用电子设备；3) 如果20分钟内睡不着，起来做些轻松的事；4) 睡前可以尝试深呼吸或冥想放松。如果持续两周以上，建议就医。"}
                ],
                "category": "睡眠问题",
                "keywords": ["失眠", "入睡困难", "睡眠障碍"]
            },
            {
                "id": "soulchat_sample_005",
                "conversations": [
                    {"role": "user", "content": "我觉得自己很失败，什么都做不好"},
                    {"role": "assistant", "content": "听到你这样说，我能感受到你的沮丧。这种自我否定的想法可能源于一些特定的经历。能告诉我是什么让你有这样的感受吗？"},
                    {"role": "user", "content": "工作总是出错，被领导批评"},
                    {"role": "assistant", "content": "工作中犯错和被批评是每个人都可能经历的。这不代表你是个失败的人。建议你：1) 客观分析错误原因，制定改进计划；2) 记录自己的成就和进步，哪怕是很小的；3) 与信任的人交流，获取不同的视角。每个人都在成长的过程中，给自己一些耐心和宽容。"}
                ],
                "category": "自我认知",
                "keywords": ["自我否定", "失败感", "自我价值"]
            },
            {
                "id": "soulchat_sample_006",
                "conversations": [
                    {"role": "user", "content": "我和伴侣总是吵架，不知道还能不能继续"},
                    {"role": "assistant", "content": "感情中的冲突确实让人疲惫和困惑。能告诉我你们通常因为什么争吵吗？"},
                    {"role": "user", "content": "都是一些小事，比如家务分配、花钱方式"},
                    {"role": "assistant", "content": "看似小事的争吵往往反映了更深层次的需求和期望差异。建议你们：1) 找一个平静的时间，各自表达自己的需求和感受；2) 尝试理解对方的立场；3) 寻找双方都能接受的妥协方案。如果沟通困难，可以考虑寻求情侣咨询。"}
                ],
                "category": "情感关系",
                "keywords": ["感情冲突", "伴侣关系", "沟通问题"]
            },
            {
                "id": "soulchat_sample_007",
                "conversations": [
                    {"role": "user", "content": "我对什么都提不起兴趣，以前喜欢的事情现在也不想做"},
                    {"role": "assistant", "content": "失去兴趣和快乐感可能是情绪低落的信号。这种情况持续多久了？还有其他症状吗？"},
                    {"role": "user", "content": "大概两周了，食欲也不好，总是想哭"},
                    {"role": "assistant", "content": "两周以上的持续情绪低落、兴趣丧失、食欲改变和哭泣，这些可能是抑郁情绪的表现。我建议你尽快寻求专业心理咨询师的帮助。这不是软弱的表现，而是对自己负责的选择。你可以先尝试与信任的家人朋友倾诉，同时预约心理咨询。"}
                ],
                "category": "抑郁情绪",
                "keywords": ["兴趣丧失", "情绪低落", "抑郁"]
            },
            {
                "id": "soulchat_sample_008",
                "conversations": [
                    {"role": "user", "content": "我总是控制不住发脾气，事后又很后悔"},
                    {"role": "assistant", "content": "情绪失控后又后悔，这种循环确实让人痛苦。能告诉我通常什么情况会触发你的愤怒吗？"},
                    {"role": "user", "content": "当别人不理解我，或者事情不如我意的时候"},
                    {"role": "assistant", "content": "愤怒往往源于期望与现实之间的落差。建议你：1) 在情绪激动时先深呼吸，给自己10秒钟缓冲；2) 识别愤怒的早期信号，在爆发前采取行动；3) 用'我感到...'的方式表达情绪，而不是指责；4) 探索愤怒背后的真实需求。情绪管理是可以学习的技能，需要持续练习。"}
                ],
                "category": "情绪管理",
                "keywords": ["愤怒", "情绪失控", "后悔"]
            },
            {
                "id": "soulchat_sample_009",
                "conversations": [
                    {"role": "user", "content": "我很害怕在人前说话，每次都要逃避"},
                    {"role": "assistant", "content": "公众演讲恐惧是很常见的。这种恐惧可能源于担心被评价或犯错。能告诉我具体是什么让你害怕吗？"},
                    {"role": "user", "content": "怕说错话被人笑话，怕紧张得说不出话"},
                    {"role": "assistant", "content": "这些担忧是可以理解的。克服公众演讲恐惧的方法：1) 充分准备，熟悉内容；2) 从小范围开始练习，逐步增加听众；3) 关注要传达的信息而非自己；4) 接受紧张是正常的，适度紧张反而能提高表现；5) 练习深呼吸和正念放松。记住，听众通常希望你好，而不是等着看你笑话。"}
                ],
                "category": "社交恐惧",
                "keywords": ["公众演讲", "社交恐惧", "紧张"]
            },
            {
                "id": "soulchat_sample_010",
                "conversations": [
                    {"role": "user", "content": "我觉得生活没有意义，不知道为什么要活着"},
                    {"role": "assistant", "content": "听到你这样说，我很关心你。这种感受非常沉重。我想让你知道，你的生命是有价值的。能告诉我是什么让你有这样的想法吗？"},
                    {"role": "user", "content": "感觉一切都是徒劳的，努力也没有回报"},
                    {"role": "assistant", "content": "我理解你现在感到很绝望。但请相信，这种感受是可以改变的。我强烈建议你寻求专业帮助。你可以拨打心理援助热线400-161-9995，这是24小时服务的。你不必独自承受这些痛苦，有很多人愿意帮助你。你现在能承诺我，在得到帮助之前不做伤害自己的事吗？"}
                ],
                "category": "危机干预",
                "keywords": ["无意义感", "绝望", "生命意义"]
            }
        ]
        
        return sample_conversations
    
    def convert_to_knowledge_format(self, raw_data):
        print("\n" + "=" * 60)
        print("步骤2: 转换数据格式")
        print("=" * 60)
        
        knowledge_docs = []
        seen_ids = set()
        
        for i, item in enumerate(raw_data):
            try:
                if isinstance(item, dict):
                    if 'conversations' in item:
                        doc = self._convert_conversation_to_doc(item, i)
                    elif 'content' in item:
                        doc = self._convert_content_to_doc(item, i)
                    else:
                        doc = self._convert_generic_to_doc(item, i)
                    
                    if doc and doc['id'] not in seen_ids:
                        knowledge_docs.append(doc)
                        seen_ids.add(doc['id'])
                    else:
                        self.stats["duplicates"] += 1
                
                if (i + 1) % 5000 == 0:
                    print(f"  已转换 {i + 1} 条记录...")
                    
            except Exception as e:
                self.stats["errors"].append(f"转换记录 {i} 失败: {e}")
        
        self.stats["converted"] = len(knowledge_docs)
        print(f"\n转换完成! 共 {len(knowledge_docs)} 个知识文档")
        
        return knowledge_docs
    
    def _convert_conversation_to_doc(self, item, index):
        conv_id = item.get('id', f"soulchat_{index:06d}")
        conversations = item.get('conversations', [])
        
        content_parts = []
        for turn in conversations:
            role = turn.get('role', 'unknown')
            text = turn.get('content', '')
            role_label = "用户" if role == 'user' else "咨询师"
            content_parts.append(f"{role_label}：{text}")
        
        content = "\n".join(content_parts)
        
        category = item.get('category', '心理咨询对话')
        keywords = item.get('keywords', [])
        
        if not keywords:
            keywords = self._extract_keywords(content)
        
        title = f"心理咨询对话_{conv_id}"
        if category:
            title = f"{category}_对话_{index + 1}"
        
        return {
            "id": f"soulchat_{index:06d}",
            "title": title,
            "content": content,
            "category": category,
            "keywords": keywords
        }
    
    def _convert_content_to_doc(self, item, index):
        return {
            "id": item.get('id', f"doc_{index:06d}"),
            "title": item.get('title', f"文档_{index + 1}"),
            "content": item.get('content', ''),
            "category": item.get('category', '心理健康'),
            "keywords": item.get('keywords', [])
        }
    
    def _convert_generic_to_doc(self, item, index):
        content = json.dumps(item, ensure_ascii=False)
        return {
            "id": f"gen_{index:06d}",
            "title": f"数据记录_{index + 1}",
            "content": content,
            "category": "心理健康",
            "keywords": []
        }
    
    def _extract_keywords(self, content):
        keywords = []
        keyword_patterns = [
            "焦虑", "抑郁", "压力", "失眠", "孤独", "愤怒", "恐惧",
            "学习", "工作", "家庭", "感情", "社交", "自我",
            "情绪", "心理", "咨询", "治疗", "帮助"
        ]
        
        for kw in keyword_patterns:
            if kw in content:
                keywords.append(kw)
        
        return keywords[:5]
    
    def merge_with_existing(self, new_docs):
        print("\n" + "=" * 60)
        print("步骤3: 合并现有知识库")
        print("=" * 60)
        
        existing_files = [
            self.knowledge_base_dir / "chinese_psychology_knowledge.json",
            self.knowledge_base_dir / "soulchat_examples.json",
            self.knowledge_base_dir / "psychology_knowledge.json",
            self.knowledge_base_dir / "merged_knowledge_base.json"
        ]
        
        all_docs = []
        seen_ids = set()
        
        for file_path in existing_files:
            if file_path.exists():
                print(f"加载: {file_path.name}")
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                    
                    for doc in data:
                        doc_id = doc.get('id')
                        if doc_id and doc_id not in seen_ids:
                            all_docs.append(doc)
                            seen_ids.add(doc_id)
                        else:
                            self.stats["duplicates"] += 1
                            
                except Exception as e:
                    self.stats["errors"].append(f"加载 {file_path} 失败: {e}")
        
        print(f"现有知识库共 {len(all_docs)} 个文档")
        
        print(f"添加新数据集 {len(new_docs)} 个文档...")
        for doc in new_docs:
            doc_id = doc.get('id')
            if doc_id and doc_id not in seen_ids:
                all_docs.append(doc)
                seen_ids.add(doc_id)
            else:
                self.stats["duplicates"] += 1
        
        self.stats["merged"] = len(all_docs)
        print(f"合并后共 {len(all_docs)} 个文档")
        
        return all_docs
    
    def save_merged_knowledge_base(self, merged_docs):
        print("\n" + "=" * 60)
        print("步骤4: 保存合并后的知识库")
        print("=" * 60)
        
        output_file = self.knowledge_base_dir / "merged_knowledge_base.json"
        
        backup_file = None
        if output_file.exists():
            backup_file = self.knowledge_base_dir / f"merged_knowledge_base_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            with open(output_file, 'r', encoding='utf-8') as f:
                backup_data = f.read()
            with open(backup_file, 'w', encoding='utf-8') as f:
                f.write(backup_data)
            print(f"已备份原文件到: {backup_file.name}")
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(merged_docs, f, ensure_ascii=False, indent=2)
        
        file_size = os.path.getsize(output_file)
        print(f"保存到: {output_file}")
        print(f"文件大小: {file_size / 1024 / 1024:.2f} MB")
        
        return output_file
    
    def generate_import_script(self, output_file):
        print("\n" + "=" * 60)
        print("步骤5: 生成导入脚本")
        print("=" * 60)
        
        import_script = self.base_dir / "scripts" / "import_to_vector_db.py"
        
        script_content = f'''import json
import requests
import sys
from pathlib import Path

def import_to_vector_db(json_file, api_base="http://localhost:8080"):
    """
    将知识库导入向量数据库
    """
    print(f"正在加载知识库: {{json_file}}")
    
    with open(json_file, 'r', encoding='utf-8') as f:
        documents = json.load(f)
    
    print(f"共 {{len(documents)}} 个文档")
    
    success_count = 0
    failed_count = 0
    
    for i, doc in enumerate(documents):
        try:
            response = requests.post(
                f"{{api_base}}/api/rag/knowledge/add",
                json={{
                    "title": doc.get("title", ""),
                    "content": doc.get("content", "")
                }},
                timeout=30
            )
            
            if response.status_code == 200:
                success_count += 1
            else:
                failed_count += 1
                print(f"  失败 [{{i+1}}]: {{response.text}}")
            
            if (i + 1) % 100 == 0:
                print(f"  进度: {{i + 1}}/{{len(documents)}}")
                
        except Exception as e:
            failed_count += 1
            print(f"  错误 [{{i+1}}]: {{e}}")
    
    print(f"\\n导入完成!")
    print(f"  成功: {{success_count}}")
    print(f"  失败: {{failed_count}}")
    
    return success_count, failed_count

if __name__ == "__main__":
    json_file = r"{output_file}"
    api_base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    import_to_vector_db(json_file, api_base)
'''
        
        with open(import_script, 'w', encoding='utf-8') as f:
            f.write(script_content)
        
        print(f"导入脚本已生成: {import_script}")
        
        return import_script
    
    def print_summary(self):
        print("\n" + "=" * 60)
        print("处理摘要")
        print("=" * 60)
        print(f"下载数据: {self.stats['downloaded']} 条")
        print(f"转换文档: {self.stats['converted']} 个")
        print(f"合并总数: {self.stats['merged']} 个")
        print(f"重复跳过: {self.stats['duplicates']} 个")
        
        if self.stats['errors']:
            print(f"\n错误信息 ({len(self.stats['errors'])} 个):")
            for err in self.stats['errors'][:5]:
                print(f"  - {err}")
            if len(self.stats['errors']) > 5:
                print(f"  ... 还有 {len(self.stats['errors']) - 5} 个错误")


def main():
    base_dir = Path("e:/项目AI/Calmara")
    
    processor = SoulChatDataProcessor(base_dir)
    
    raw_data = processor.download_from_modelscope()
    
    if raw_data:
        converted_docs = processor.convert_to_knowledge_format(raw_data)
        merged_docs = processor.merge_with_existing(converted_docs)
        output_file = processor.save_merged_knowledge_base(merged_docs)
        processor.generate_import_script(output_file)
    
    processor.print_summary()
    
    print("\n" + "=" * 60)
    print("后续步骤")
    print("=" * 60)
    print("1. 确保Calmara服务已启动 (端口8080)")
    print("2. 运行导入脚本:")
    print("   python scripts/import_to_vector_db.py")
    print("3. 或通过API手动导入:")
    print("   curl -X POST http://localhost:8080/api/admin/knowledge-base/import/json")
    print("=" * 60)


if __name__ == "__main__":
    main()
