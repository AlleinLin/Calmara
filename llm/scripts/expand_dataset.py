"""
Calmara - 扩展心理对话数据集
1. 扩展原有40条数据到500+条
2. 包含更多场景和对话轮次
3. 覆盖8大心理问题类别
"""

import json
import os
from datetime import datetime

PSYCH_CATEGORIES = {
    "anxiety": {
        "name": "焦虑情绪",
        "scenarios": [
            "考试焦虑",
            "社交焦虑",
            "工作压力焦虑",
            "健康焦虑",
            "睡眠焦虑",
            "演讲焦虑",
            "分离焦虑",
            "控制焦虑",
            "财务焦虑",
            "未来不确定性焦虑",
        ],
        "expressions": [
            "最近总是莫名紧张",
            "心里总是不踏实",
            "担心会发生不好的事",
            "坐立不安",
            "心跳加速",
            "手心出汗",
            "脑子停不下来",
            "难以控制担忧",
            "害怕失败",
            "恐惧社交场合",
        ],
        "responses": [
            "深呼吸可以有效缓解焦虑。试着吸气4秒，屏住4秒，呼气4秒，重复几次。",
            "焦虑是对未来的担忧。尝试把担忧写下来，区分哪些是你能控制的，哪些是不能控制的。",
            "身体和心理是相连的。适度运动如散步、瑜伽，可以帮助缓解焦虑情绪。",
            "正念冥想能帮助你回到当下。试着闭上眼睛，专注呼吸，当杂念出现时温和地把它放下。",
            "给自己建立一个'焦虑时间'，比如每天下午5点专门处理担忧，其他时间尽量不去想。",
            "倾诉很重要。和你信任的人分享你的担忧，说出来会感觉好很多。",
            "睡眠质量影响情绪。尝试睡前1小时不看手机，做些放松活动。",
            "咖啡因会加重焦虑反应。试着减少咖啡、浓茶等摄入。",
        ],
    },
    "depression": {
        "name": "抑郁情绪",
        "scenarios": [
            "长期情绪低落",
            "兴趣减退",
            "自我否定",
            "睡眠问题",
            "食欲改变",
            "疲劳感",
            "注意力下降",
            "无价值感",
            "绝望感",
            "自杀念头",
        ],
        "expressions": [
            "对什么都没兴趣",
            "觉得自己一无是处",
            "活着没意思",
            "不想见人",
            "吃不下饭",
            "浑身没劲",
            "脑子反应慢",
            "对不起家人",
            "看不到希望",
            "想解脱",
        ],
        "responses": [
            "你现在的感受是真的，但请相信痛苦是可以度过的。抑郁像乌云，会来也会走。",
            "不要对自己太苛刻。抑郁是一种疾病，不是你的错，也不是性格弱点。",
            "从小事开始，每天设定一个极简单的目标，比如起床、刷牙、吃饭。",
            "保持规律作息很重要。即使不想吃，也要尽量按时吃饭。",
            "适度活动可以帮助缓解抑郁。即使只是出门散步，也有帮助。",
            "你的生命很宝贵。如果你现在有伤害自己的想法，请拨打400-161-9995心理援助热线。",
            "和专业心理咨询师聊聊可以获得更多支持。他们能帮助你度过难关。",
            "你不需要独自承受。和信任的人分享你的感受，让他们陪伴你。",
        ],
    },
    "relationship": {
        "name": "人际关系",
        "scenarios": [
            "家庭冲突",
            "室友矛盾",
            "朋友疏远",
            "恋爱困扰",
            "社交恐惧",
            "讨好型人格",
            "边界不清",
            "沟通障碍",
            "信任问题",
            "孤独感",
        ],
        "expressions": [
            "和父母总吵架",
            "室友生活习惯不同",
            "觉得朋友越来越少",
            "不知道怎么处理感情",
            "总是不自觉讨好别人",
            "不知道怎么拒绝",
            "别人不理解我",
            "觉得孤单",
            "害怕被抛弃",
            "不知道怎么和人相处",
        ],
        "responses": [
            "沟通是关系的桥梁。试着平静地表达你的感受和需求，同时也倾听对方的想法。",
            "建立健康的边界很重要。学会说'不'，同时尊重别人的边界。",
            "真正的朋友会接纳真实的你。不必为了讨好别人而失去自己。",
            "给彼此一些空间。亲密关系也需要独处的时间和空间。",
            "主动联系老朋友或尝试认识新朋友。友谊需要经营和维护。",
            "倾听是很好的沟通方式。试着先理解对方，再表达自己。",
            "冲突是正常的，关键是建设性地处理。避免攻击性语言，专注于问题本身。",
            "如果社交让你疲惫，允许自己暂时休息。自我照顾不是自私。",
        ],
    },
    "academic": {
        "name": "学业压力",
        "scenarios": [
            "考试压力",
            "成绩不理想",
            "学习困难",
            "专业迷茫",
            "考研还是工作",
            "拖延症",
            "注意力不集中",
            "学习效率低",
            "竞争压力",
            "学业倦怠",
        ],
        "expressions": [
            "考试没考好",
            "学不进去",
            "不知道选什么专业",
            "上课听不懂",
            "总是拖延",
            "注意力不集中",
            "竞争太激烈",
            "努力了还是不行",
            "对学习失去动力",
            "讨厌上学",
        ],
        "responses": [
            "一次成绩不能定义你的全部。分析原因，调整学习方法，下次会更好的。",
            "拖延往往是因为任务太难或缺乏动力。试着把它分解成小步骤。",
            "每个人学习节奏不同。不要总是和别人比较，专注于自己的进步。",
            "兴趣是可以培养的。试着发现学习中的乐趣，或者寻找实际应用场景。",
            "番茄工作法可以帮助提高专注力：专注25分钟，休息5分钟。",
            "如果学习困难，可以寻求帮助：老师、同学、辅导班都是资源。",
            "劳逸结合很重要。保证睡眠和休息，才能更好地学习。",
            "记得你的价值不只是成绩。学业只是人生的一个方面。",
        ],
    },
    "self_growth": {
        "name": "自我成长",
        "scenarios": [
            "自我认知",
            "性格困惑",
            "人生方向",
            "自信心",
            "习惯养成",
            "时间管理",
            "情绪管理",
            "价值观探索",
            "目标设定",
            "自律提升",
        ],
        "expressions": [
            "不了解自己",
            "觉得性格有缺陷",
            "不知道想要什么",
            "没有自信",
            "总是半途而废",
            "时间总是不够用",
            "控制不住情绪",
            "不知道什么是对的",
            "没有目标",
            "管不住自己",
        ],
        "responses": [
            "自我探索是一生的过程。不必急于给自己贴标签，允许自己慢慢了解。",
            "每种性格都有优缺点。接纳自己的特点，发挥优势，改善可以改善的。",
            "自信心来自于一次次成功经验的积累。从小事开始，设定并完成目标。",
            "习惯养成需要时间。试着一次只改变一个习惯，坚持21天以上。",
            "情绪没有好坏对错。学会觉察和理解自己的情绪，而不是压抑或逃避。",
            "时间管理实际上是选择管理。把重要的事情优先安排，而不是被紧急的事情牵着走。",
            "人生方向不是想出来的，是走出来的。多尝试，多体验，方向会渐渐清晰。",
            "自律不是自我惩罚。找到内在动机，让好习惯变得可持续。",
        ],
    },
    "emotion": {
        "name": "情绪管理",
        "scenarios": [
            "情绪波动",
            "愤怒控制",
            "情绪压抑",
            "情绪表达",
            "情绪识别",
            "情绪调节",
            "挫折应对",
            "失落悲伤",
            "恐惧害怕",
            "羞耻感",
        ],
        "expressions": [
            "情绪不稳定",
            "容易发脾气",
            "把情绪闷在心里",
            "不知道怎么表达",
            "分不清自己什么情绪",
            "总是心情不好",
            "遇到挫折就崩溃",
            "失去后很难过",
            "总是害怕",
            "觉得自己很丢脸",
        ],
        "responses": [
            "情绪波动可能由多种因素引起：睡眠、压力、激素等。先照顾好自己的基本需求。",
            "愤怒是一种保护情绪。试着理解愤怒背后真正的感觉是什么——是受伤、恐惧还是失望？",
            "情绪需要被表达，而不是被压抑。找到健康的方式：写日记、画画、运动、和人倾诉。",
            "情绪识别是情绪管理的第一步。试着给情绪命名：你现在感受到的是什么？",
            "当你情绪激动时，暂停一下。深呼吸，离开现场，等冷静下来再处理。",
            "悲伤是爱与连接的自然反应。允许自己悲伤，给自己时间和空间疗愈。",
            "恐惧往往来自于想象。问问自己：最坏的情况是什么？发生的可能性有多大？",
            "羞耻感往往是因为我们把错误等同于自己的价值。犯错是学习的一部分。",
        ],
    },
    "crisis": {
        "name": "危机干预",
        "scenarios": [
            "自杀念头",
            "自伤行为",
            "极度绝望",
            "急性应激",
            "创伤后应激",
            "解离状态",
            "精神病性症状",
            "严重抑郁发作",
            "物质滥用",
            "暴食或厌食",
        ],
        "expressions": [
            "不想活了",
            "活着太累了",
            "想解脱",
            "活着没意思",
            "伤害自己",
            "觉得死了更好",
            "对生活完全绝望",
            "觉得没人能帮助我",
            "我太差劲了",
            "我想消失",
        ],
        "responses": [
            "我听到了你的痛苦。你现在的感受是真的，但请相信痛苦是有尽头的。",
            "你的生命非常宝贵。请拨打心理援助热线400-161-9995，那里有专业人员可以帮助你。",
            "如果你有具体的自杀计划，请立即告诉你身边信任的人，或者拨打120急救。",
            "你不需要独自面对这些。有很多人愿意帮助你，包括我。",
            "现在最微小的一步是联系帮助。请拨打400-161-9995，或者告诉身边信任的人你的感受。",
            "危机往往是暂时的。现在感觉最绝望的时刻，不代表永远是这样。",
            "专业的帮助可以带来改变。心理咨询师和精神科医生都能提供支持。",
            "在全国心理援助热线之外，你也可以直接去医院的心理科或精神科寻求帮助。",
        ],
    },
    "daily": {
        "name": "日常对话",
        "scenarios": [
            "初次问候",
            "一般聊天",
            "结束对话",
            "自我介绍",
            "寻求建议",
            "表达感谢",
            "倾诉陪伴",
            "轻松话题",
            "情绪分享",
            "感谢告别",
        ],
        "expressions": [
            "你好",
            "今天心情不错",
            "谢谢你",
            "你是谁",
            "你能帮我吗",
            "想找人聊聊",
            "最近怎么样",
            "天气真好",
            "我有一个问题",
            "再见",
        ],
        "responses": [
            "你好！我是Calmara，一个专业的校园心理咨询助手。很高兴认识你，有什么我可以帮助你的吗？",
            "我在这里倾听你。如果有任何想聊的，随时告诉我。",
            "感谢你的信任。我会尽我所能帮助你。",
            "能帮到你我也很开心。记住，你不是一个人，我会一直在这里支持你。",
            "再见！希望今天的对话对你有帮助。记得我随时都在，随时可以回来聊聊。保重！",
            "天气好的时候，心情也会好一些。希望你每天都有一份好心情。",
            "谢谢你的分享。记住，无论什么时候，我都在这里陪伴你。",
            "和你聊天我很开心。希望我的回答对你有帮助。如果还有其他问题，随时来找我。",
        ],
    },
}


def generate_enhanced_dataset(target_size=500):
    """生成扩展版心理对话数据集"""
    data = []
    data_id = 0

    categories = list(PSYCH_CATEGORIES.keys())
    samples_per_category = target_size // len(categories)

    for category_key, category_info in PSYCH_CATEGORIES.items():
        for i in range(samples_per_category):
            scenario = category_info["scenarios"][i % len(category_info["scenarios"])]
            expression = category_info["expressions"][i % len(category_info["expressions"])]
            response_template = category_info["responses"][i % len(category_info["responses"])]

            if category_key == "crisis":
                human_value = expression
                gpt_value = response_template
            elif category_key == "daily":
                human_value = expression
                gpt_value = response_template
            else:
                if i % 3 == 0:
                    human_value = f"我{scenario}，{expression}"
                elif i % 3 == 1:
                    human_value = f"{expression}，尤其是{scenario}"
                else:
                    human_value = f"最近{scenario}的问题困扰着我，{expression}"

                if i % 2 == 0:
                    gpt_value = response_template
                else:
                    gpt_value = f"感谢你分享这些。{response_template}有什么具体想聊的吗？"

            data.append(
                {
                    "id": f"psych_{data_id:04d}",
                    "conversations": [
                        {"from": "human", "value": human_value},
                        {"from": "gpt", "value": gpt_value},
                    ],
                    "category": category_key,
                }
            )
            data_id += 1

            if i % 3 == 0 and category_key != "crisis" and category_key != "daily":
                multi_turn_sample = {
                    "id": f"psych_{data_id:04d}",
                    "conversations": [
                        {"from": "human", "value": f"我{scenario}，{expression}"},
                        {"from": "gpt", "value": f"{response_template}你想先聊聊哪方面呢？"},
                        {"from": "human", "value": "具体说说..."},
                        {
                            "from": "gpt",
                            "value": f"明白了，{scenario}确实很常见。我建议你：1. 先尝试记录下来什么时候最严重；2. 找信任的人聊聊；3. 如果影响生活，考虑寻求专业帮助。记住，改变需要时间，对自己要有耐心。",
                        },
                    ],
                    "category": category_key,
                }
                data.append(multi_turn_sample)
                data_id += 1

    while len(data) < target_size:
        cat_key = categories[len(data) % len(categories)]
        cat = PSYCH_CATEGORIES[cat_key]
        idx = len(data) % len(cat["expressions"])

        sample = {
            "id": f"psych_{data_id:04d}",
            "conversations": [
                {"from": "human", "value": f"{cat['expressions'][idx]}，而且{cat['scenarios'][idx]}"},
                {"from": "gpt", "value": f"{cat['responses'][idx]}"},
            ],
            "category": cat_key,
        }
        data.append(sample)
        data_id += 1

    return data


def generate_test_dataset(target_size=50):
    """生成测试数据集（不包含在训练中）"""
    test_data = []
    data_id = 0

    test_scenarios = [
        ("anxiety", "我明天有个重要面试，紧张得睡不着"),
        ("depression", "不知道为什么，最近总是开心不起来"),
        ("relationship", "我和最好的朋友吵架了，不知道该不该先道歉"),
        ("academic", "考研成绩出来了，没考上理想的学校"),
        ("self_growth", "新的一年，我不知道该定什么目标"),
        ("emotion", "今天莫名其妙发脾气了，事后又后悔"),
        ("crisis", "我觉得活着没什么意义"),
        ("daily", "你好，请问你叫什么名字？"),
        ("anxiety", "每次打电话给父母要钱都很紧张"),
        ("depression", "毕业后找不到工作，感觉自己很没用"),
        ("relationship", "男朋友说需要空间，我该怎么办"),
        ("academic", "室友每天熬夜学习，给我很大压力"),
        ("self_growth", "我想改变自己，但不知道从哪开始"),
        ("emotion", "控制不住对孩子发火"),
    ]

    for i, (cat, prompt) in enumerate(list(test_scenarios * (target_size // len(test_scenarios) + 1))[:target_size]):
        test_data.append(
            {
                "id": f"test_{data_id:04d}",
                "conversations": [
                    {"from": "human", "value": prompt},
                    {"from": "gpt", "value": ""},
                ],
                "category": cat,
            }
        )
        data_id += 1

    return test_data


def main():
    print("=" * 60)
    print("Calmara - 扩展心理对话数据集生成器")
    print("=" * 60)

    target_train = 500
    target_test = 50

    print(f"\n生成训练数据集: {target_train}条...")
    train_data = generate_enhanced_dataset(target_train)

    output_dir = "e:/项目AI/Calmara/llm/scripts/dataset"
    os.makedirs(output_dir, exist_ok=True)

    train_path = os.path.join(output_dir, "train.json")
    with open(train_path, "w", encoding="utf-8") as f:
        json.dump(train_data, f, ensure_ascii=False, indent=2)

    print(f"✓ 训练数据已保存: {train_path}")

    print(f"\n生成测试数据集: {target_test}条...")
    test_data = generate_test_dataset(target_test)

    test_path = os.path.join(output_dir, "test.json")
    with open(test_path, "w", encoding="utf-8") as f:
        json.dump(test_data, f, ensure_ascii=False, indent=2)

    print(f"✓ 测试数据已保存: {test_path}")

    print("\n数据集统计:")
    print("-" * 40)
    categories = {}
    for item in train_data:
        cat = item["category"]
        categories[cat] = categories.get(cat, 0) + 1

    for cat, count in sorted(categories.items()):
        print(f"  {cat}: {count}条")

    print(f"\n总计: {len(train_data)}条训练数据, {len(test_data)}条测试数据")
    print("\n数据集分类:")
    for cat, info in PSYCH_CATEGORIES.items():
        print(f"  - {cat}: {info['name']}")


if __name__ == "__main__":
    main()