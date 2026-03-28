"""
Calmara - Qwen2.5-7B LoRA Fine-tuning Dataset Preparation
从SoulChatCorpus数据集生成训练数据
"""

import json
import os
import sys
import random
from pathlib import Path
from datetime import datetime
import ijson

def convert_soulchat_to_training_format(source_file, output_dir, max_samples=50000, train_test_split=0.95):
    """
    将SoulChatCorpus数据集转换为训练格式
    """
    print("=" * 60)
    print("Calmara - 训练数据准备")
    print("=" * 60)
    print(f"源文件: {source_file}")
    print(f"输出目录: {output_dir}")
    print(f"最大样本数: {max_samples}")
    print(f"训练/测试分割: {train_test_split}")
    print()
    
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    train_data = []
    test_data = []
    total_count = 0
    
    print("开始处理数据...")
    
    with open(source_file, 'r', encoding='utf-8') as f:
        for item in ijson.items(f, 'item'):
            if total_count >= max_samples:
                break
            
            try:
                conv_id = item.get('id', total_count)
                topic = item.get('topic', '心理咨询')
                messages = item.get('messages', [])
                
                if not messages or len(messages) < 2:
                    continue
                
                conversation = {
                    "id": f"soulchat_{conv_id:06d}",
                    "conversations": [],
                    "category": topic
                }
                
                for msg in messages:
                    role = msg.get('role', '')
                    content = msg.get('content', '')
                    
                    if role == 'user':
                        conversation["conversations"].append({
                            "from": "human",
                            "value": content
                        })
                    elif role == 'assistant':
                        conversation["conversations"].append({
                            "from": "gpt",
                            "value": content
                        })
                
                if len(conversation["conversations"]) >= 2:
                    if random.random() < train_test_split:
                        train_data.append(conversation)
                    else:
                        test_data.append(conversation)
                    
                    total_count += 1
                    
                    if total_count % 5000 == 0:
                        print(f"  已处理 {total_count} 条对话...")
                        
            except Exception as e:
                print(f"  跳过错误记录: {e}")
                continue
    
    print(f"\n处理完成!")
    print(f"训练集: {len(train_data)} 条")
    print(f"测试集: {len(test_data)} 条")
    
    train_file = Path(output_dir) / "train.json"
    test_file = Path(output_dir) / "test.json"
    
    print(f"\n保存训练数据到: {train_file}")
    with open(train_file, 'w', encoding='utf-8') as f:
        json.dump(train_data, f, ensure_ascii=False, indent=2)
    
    print(f"保存测试数据到: {test_file}")
    with open(test_file, 'w', encoding='utf-8') as f:
        json.dump(test_data, f, ensure_ascii=False, indent=2)
    
    print(f"\n数据统计:")
    categories = {}
    for item in train_data + test_data:
        cat = item.get('category', '未知')
        categories[cat] = categories.get(cat, 0) + 1
    
    print("分类分布:")
    for cat, count in sorted(categories.items(), key=lambda x: -x[1])[:10]:
        print(f"  {cat}: {count}")
    
    return train_file, test_file


def create_enhanced_sample_dataset(output_dir):
    """
    创建增强版示例数据集（当源文件不存在时使用）
    """
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    conversations = []
    
    qa_pairs = [
        {
            "instruction": "我最近总是睡不着，感觉很焦虑，应该怎么办？",
            "response": "睡不着确实很让人困扰。建议你尝试以下方法：1. 保持规律的作息时间，每天固定时间睡觉和起床；2. 睡前避免使用电子设备；3. 可以尝试深呼吸或冥想来放松身心；4. 白天适度运动，但不要在睡前剧烈运动。如果持续失眠超过两周，建议咨询专业心理医生。"
        },
        {
            "instruction": "我觉得自己很失败，什么都做不好...",
            "response": "听到你这么说，我很心疼。首先，请不要用'失败'来定义自己。每个人都有自己的优点和不足。试着回顾一下过去，你一定有过成功的经历，哪怕是小小的进步。如果持续感到低落超过两周，建议和信任的人聊聊，或者寻求专业心理咨询师的帮助。"
        },
        {
            "instruction": "最近学习压力好大，感觉喘不过气来",
            "response": "学习压力确实会让人感到紧张和疲惫。试试这些方法：1. 把大任务分解成小目标，一步一步完成；2. 适当休息，做些放松活动；3. 和朋友或家人倾诉；4. 保持充足的睡眠和营养。记住，你的价值不是只由成绩来决定的。必要时可以寻求学校心理咨询中心的帮助。"
        },
        {
            "instruction": "我和父母吵架了，不知道该怎么办",
            "response": "和父母发生分歧是很常见的事。建议你先冷静下来，等双方情绪平复后再沟通。试着理解父母的立场，也表达自己的想法和感受。沟通时要保持尊重和耐心，使用'我'语言表达感受而非指责。如果实在无法解决，可以找其他信任的长辈或心理咨询师帮忙调解。"
        },
        {
            "instruction": "我总是担心很多事情，控制不住地想东想西",
            "response": "过度担忧会影响生活质量。试试'STOP'技巧：S-停下来，T-深呼吸，O-观察你的想法，P-继续做该做的事。也可以记录让你担忧的事情，然后区分哪些是真正需要解决的，哪些是无法控制的。同时，培养一些爱好，保持适度运动，都有助于缓解焦虑。"
        },
        {
            "instruction": "我对未来感到很迷茫，不知道该做什么",
            "response": "对未来感到迷茫是成长中的正常阶段。建议你：1. 尝试了解自己的兴趣和优势；2. 多尝试不同的事物，发现自己的热情所在；3. 和信任的人聊聊你的想法；4. 不必急于确定一生方向，可以先设定短期目标。记住，迷茫期也是自我探索的好时机。"
        },
        {
            "instruction": "我经常感到孤独，没有人理解我",
            "response": "孤独感确实很让人难受。首先，请记住你并不孤单，很多人都会有这样的感受。试着主动和他人建立联系：加入兴趣小组、参与社区活动、或者养宠物。也可以尝试帮助他人，这会让你感受到价值和连接。如果持续感到孤独影响生活，建议寻求心理咨询。"
        },
        {
            "instruction": "我最近情绪很低落，对什么都没兴趣",
            "response": "对事物失去兴趣可能是情绪低落的信号。请照顾好自己：1. 保持规律作息和健康饮食；2. 尝试做些轻度运动；3. 和信任的人倾诉；4. 记录自己的情绪变化。如果这种状态持续两周以上，且影响日常生活，建议寻求专业帮助。全国心理援助热线：400-161-9995。"
        },
        {
            "instruction": "我在人际交往中总是很紧张，怎么办？",
            "response": "社交紧张是很多人都会遇到的问题。可以尝试：1. 提前准备话题；2. 从小的社交场合开始练习；3. 专注于倾听而非表现自己；4. 接受不完美，社交技能需要时间培养。记住，大多数人也都关心自己的表现，不会过分关注你。逐步练习，会越来越好的。"
        },
        {
            "instruction": "我觉得自己不够好，总是要讨好别人",
            "response": "总是讨好别人会很累。试着学会爱自己、接纳自己。你的价值不是由别人的认可决定的。建立健康的边界很重要：学会说'不'，这不是自私，而是尊重自己。真正的朋友会尊重真实的你。试着从小事开始，表达真实的感受和想法。"
        },
        {
            "instruction": "我失恋了，感觉天都塌了",
            "response": "失恋的痛苦是真实的，请允许自己难过。但请记住，这段感情的结束不代表你的价值降低了。给自己时间疗愈，可以：1. 倾诉和表达情绪；2. 保持正常生活节奏；3. 避免过度沉溺或联系前任；4. 尝试新事物。渐渐地，你会发现自己重新找回力量。"
        },
        {
            "instruction": "我控制不住对孩子发脾气，事后又后悔",
            "response": "父母也是人，会有情绪波动是正常的。重要的是事后修复：找个平静的时机向孩子道歉，解释你的感受。学会识别自己的情绪触发点，提前预防。也可以找其他方式来释放压力。记住，成为好父母不意味着不能犯错，而是在犯错后愿意学习和成长。"
        },
        {
            "instruction": "我最近总是莫名其妙地心情不好",
            "response": "情绪波动有时没有明显原因，这也是正常的。试着关注自己的身体状态：睡眠、饮食、运动都会影响情绪。记录情绪日记可能帮助你发现规律。如果持续时间长或严重影响生活，建议找专业人士聊聊，有时候生理或心理因素需要专业干预。"
        },
        {
            "instruction": "我觉得活着没什么意思",
            "response": "我很在意你现在的感受。请记住，无论现在多么痛苦，总有办法可以得到帮助。请拨打心理援助热线：400-161-9995（24小时）。如果你有具体的自我伤害想法，请立即告诉身边信任的人，或者直接拨打120/110寻求紧急帮助。你的生命很宝贵，有人关心你。"
        },
        {
            "instruction": "工作压力让我每天都很疲惫",
            "response": "工作倦怠是现代人常见的问题。建议：1. 明确工作边界，避免过度加班；2. 培养工作以外的兴趣爱好；3. 适度运动放松身心；4. 和上司沟通工作负担。如果长期无法缓解，可能需要考虑工作调整。记住，没有一份工作值得牺牲你的身心健康。"
        },
        {
            "instruction": "我总是和别人比较，觉得自己很差",
            "response": "和别人比较往往会让我们忽视自己的独特之处。试着专注于自己的成长和进步，而不是与他人竞争。每个人的起点和处境都不同。列一个清单，写下自己的优点和过去取得的成就。学会欣赏自己的独特价值，你值得被善待。"
        },
        {
            "instruction": "我无法集中注意力，学习效率很低",
            "response": "注意力不集中可能由多种因素引起：压力、睡眠、营养、甚至心理健康问题。试试番茄工作法：专注25分钟，休息5分钟。减少干扰，创造安静的学习环境。保证充足睡眠和营养。如果持续存在且严重影响生活，建议咨询专业人士排除ADHD等可能性。"
        },
        {
            "instruction": "我觉得没有人能真正理解我",
            "response": "感到不被理解是很孤独的感受。试着更清晰地表达自己的想法和感受，有时候他人不是不想理解，而是不知道如何支持你。也可以寻找有相似经历的人群，归属感会减轻孤独。写作、艺术创作也是表达内心的好方式。"
        },
        {
            "instruction": "我最近暴饮暴食，情绪很不稳定",
            "response": "情绪性进食通常与情绪调节有关。建议：1. 记录饮食和情绪，了解触发因素；2. 学习健康的情绪调节方式，如运动、倾诉；3. 规律饮食，避免过度饥饿；4. 对自己温柔一点，改变需要时间。如果情况严重，建议寻求专业帮助。"
        },
        {
            "instruction": "我总是拖延，怎么改掉这个毛病？",
            "response": "拖延往往是因为任务太困难或缺乏动力。试试：1. 将大任务分解成小步骤；2. 使用计时器增加紧迫感；3. 营造无干扰环境；4. 给自己设立奖励机制。理解拖延背后的原因也很重要——是对失败的恐惧，还是对任务不感兴趣？找到原因才能对症下药。"
        },
        {
            "instruction": "我感到压力很大，但不知道怎么说出来",
            "response": "表达情绪需要练习。可以从写日记开始，把感受写下来。也可以通过画画、音乐等方式表达。找一个信任的人，从小话题开始练习表达。记住，表达脆弱不是软弱，而是建立真实连接的方式。如果实在难以开口，心理咨询师可以提供安全的空间。"
        },
        {
            "instruction": "你好，今天天气真不错",
            "response": "你好！是的，最近天气确实很好。希望你也有一个愉快的心情。如果你有任何心事想要倾诉，我在这里愿意倾听。或者如果你只是想聊聊天，我也很乐意陪伴你。"
        },
        {
            "instruction": "你叫什么名字？",
            "response": "你好！我是Calmara，一个专业的校园心理咨询助手。我在这里帮助你缓解压力、疏导情绪、解决心理困扰。如果你有任何心理困扰想要聊聊，或者只是想找人陪伴，都可以告诉我。"
        },
        {
            "instruction": "谢谢你的帮助",
            "response": "不客气！能帮助你我也很开心。如果以后有任何困扰，随时可以找我聊聊。记住，你不是一个人，我会一直在这里支持你。保重！"
        },
        {
            "instruction": "考试前总是很紧张，怎么办？",
            "response": "考前紧张是正常的，适度的紧张反而能提高注意力。试试这些方法：1. 充分准备，制定复习计划；2. 考前做深呼吸放松；3. 积极自我暗示，如'我已经准备好了'；4. 保证考前充足睡眠；5. 考试时先做会的题目。如果紧张严重影响发挥，可以寻求学校心理老师的帮助。"
        },
        {
            "instruction": "室友生活习惯和我不同，经常有矛盾",
            "response": "宿舍生活确实需要磨合。建议：1. 开学初一起制定宿舍公约，明确作息、卫生等规则；2. 有问题及时沟通，用'我'语言表达感受；3. 相互尊重，求同存异；4. 保持自己的独立空间和爱好。如果矛盾无法解决，可以寻求辅导员或心理咨询师的帮助。"
        },
        {
            "instruction": "我想考研，但不知道从哪里开始准备",
            "response": "考研准备需要系统规划：1. 确定目标院校和专业，了解考试科目；2. 制定长期和短期学习计划；3. 收集资料，包括教材、真题、网课等；4. 找研友互相监督；5. 保持身心健康，适度运动。记住，考研是一场持久战，节奏和心态很重要。"
        },
        {
            "instruction": "我觉得自己有社交恐惧症",
            "response": "社交恐惧症是可以改善的。建议：1. 从小范围社交开始练习；2. 学习放松技巧，如深呼吸；3. 挑战负面想法，如'大家都在看我'；4. 关注对话内容而非自己；5. 接受不完美。如果严重影响生活，建议寻求专业心理咨询，认知行为疗法对社交恐惧很有效。"
        },
        {
            "instruction": "父母总是拿我和别人比较，我很烦",
            "response": "被比较的感觉确实不好受。建议：1. 理解父母可能出于关心，但方式不当；2. 找合适时机表达你的感受，如'我希望你们能看到我的进步'；3. 展示你的优点和成就；4. 建立自己的评价标准，不过度依赖他人认可。记住，你是独一无二的。"
        },
        {
            "instruction": "我总是做噩梦，睡眠质量很差",
            "response": "噩梦可能与压力、焦虑或创伤有关。建议：1. 睡前避免刺激性内容；2. 建立放松的睡前仪式，如听轻音乐；3. 记录梦境，寻找可能的触发因素；4. 白天处理压力源；5. 保持规律作息。如果噩梦频繁且影响生活，建议寻求专业心理咨询。"
        },
        {
            "instruction": "我觉得自己可能有抑郁症",
            "response": "如果你持续两周以上感到情绪低落、兴趣丧失、精力减退、睡眠或食欲改变，建议尽快寻求专业帮助。可以：1. 去学校心理咨询中心；2. 到医院精神科就诊；3. 拨打心理援助热线400-161-9995。抑郁症是可以治疗的，寻求帮助是勇敢的表现。"
        }
    ]
    
    for i, qa in enumerate(qa_pairs):
        conversation = {
            "id": f"sample_{i:04d}",
            "conversations": [
                {"from": "human", "value": qa["instruction"]},
                {"from": "gpt", "value": qa["response"]}
            ],
            "category": "psychology_counseling"
        }
        conversations.append(conversation)
    
    random.shuffle(conversations)
    split_idx = int(len(conversations) * 0.9)
    
    train_data = conversations[:split_idx]
    test_data = conversations[split_idx:]
    
    train_file = Path(output_dir) / "train.json"
    test_file = Path(output_dir) / "test.json"
    
    with open(train_file, 'w', encoding='utf-8') as f:
        json.dump(train_data, f, ensure_ascii=False, indent=2)
    
    with open(test_file, 'w', encoding='utf-8') as f:
        json.dump(test_data, f, ensure_ascii=False, indent=2)
    
    print(f"示例数据集创建完成")
    print(f"训练集: {len(train_data)} 条")
    print(f"测试集: {len(test_data)} 条")
    
    return train_file, test_file


def main():
    base_dir = Path("e:/项目AI/Calmara")
    source_file = base_dir / "knowledge-base" / "external" / "SoulChatCorpus" / "SoulChatCorpus-sft-multi-Turn.json"
    output_dir = base_dir / "llm" / "dataset"
    
    config_file = base_dir / "llm" / "config" / "training_config.json"
    max_samples = 50000
    
    if config_file.exists():
        try:
            with open(config_file, 'r', encoding='utf-8') as f:
                config = json.load(f)
                max_samples = config.get('dataset', {}).get('max_samples', 50000)
        except:
            pass
    
    print("=" * 60)
    print("Calmara - 训练数据准备工具")
    print("=" * 60)
    
    if source_file.exists():
        print(f"\n发现SoulChatCorpus数据集")
        print(f"文件大小: {source_file.stat().st_size / 1024 / 1024:.2f} MB")
        train_file, test_file = convert_soulchat_to_training_format(
            str(source_file), 
            str(output_dir),
            max_samples=max_samples
        )
    else:
        print(f"\n未找到SoulChatCorpus数据集，创建示例数据...")
        train_file, test_file = create_enhanced_sample_dataset(str(output_dir))
    
    print("\n" + "=" * 60)
    print("数据准备完成！")
    print("=" * 60)
    print(f"训练数据: {train_file}")
    print(f"测试数据: {test_file}")
    print("\n下一步: 运行训练脚本")
    print("  python llm/scripts/train_lora.py --model_path <模型路径> --data_path <训练数据路径>")


if __name__ == "__main__":
    main()
