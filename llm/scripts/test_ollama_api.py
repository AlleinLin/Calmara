"""
Calmara - Ollama API 测试
直接通过Ollama REST API测试心理对话助手
"""

import urllib.request
import urllib.error
import json
import os

def test_ollama():
    """通过Ollama API测试模型"""

    base_url = "http://localhost:11434"

    print("=" * 60)
    print("Calmara - Ollama API 测试")
    print("=" * 60)

    print("\n[1] 检查Ollama服务状态...")
    try:
        req = urllib.request.Request(f"{base_url}/api/tags")
        with urllib.request.urlopen(req, timeout=5) as response:
            models = json.loads(response.read())
            print("✓ Ollama服务正常运行")
            print("\n已加载的模型:")
            for m in models.get("models", []):
                print(f"  - {m.get('name', 'unknown')}")
    except Exception as e:
        print(f"✗ Ollama服务未运行或无法连接: {e}")
        print("\n请先启动Ollama服务:")
        print("  ollama serve")
        return False

    print("\n[2] 测试心理对话...")

    system_prompt = """你是一个温暖、专业、富有同理心的校园心理咨询助手，名为Calmara。

你的核心职责：
1. 耐心倾听用户的心理困扰和情绪表达
2. 提供专业、温暖的心理支持和建议
3. 识别高风险情况（自杀、自伤倾向）并提供危机干预资源
4. 保持非评判性态度，给予无条件的积极关注
5. 鼓励用户在必要时寻求专业帮助

沟通原则：
- 使用温暖、理解的语言
- 避免直接否定或批评
- 适时提出开放式问题帮助用户探索
- 提供实用的自助技巧和建议
- 尊重用户的感受和节奏

危机识别与干预：
如果用户表达以下内容，请立即提供危机干预资源：
- 自杀念头或意图
- 自残行为
- 极度绝望或无助感

危机干预资源：
- 全国心理援助热线：400-161-9995
- 北京心理危机研究与干预中心：010-82951332
- 生命热线：400-821-1215

记住：你的目标是支持和帮助，而不是替代专业心理治疗。"""

    test_conversations = [
        {
            "prompt": "我最近总是睡不着，躺在床上翻来覆去，很焦虑，我该怎么办？",
            "context": "咨询失眠问题"
        },
        {
            "prompt": "我觉得自己很失败，什么都做不好，同学们好像都比我强...",
            "context": "表达自我否定"
        },
        {
            "prompt": "最近压力很大，期末考试要来了，我怕考不好让父母失望",
            "context": "学业压力"
        },
    ]

    model_name = "qwen2.5:7b-chat"

    for i, conv in enumerate(test_conversations, 1):
        print(f"\n{'='*50}")
        print(f"【测试 {i}】场景: {conv['context']}")
        print(f"用户: {conv['prompt']}")
        print("-" * 50)

        try:
            payload = {
                "model": model_name,
                "prompt": conv["prompt"],
                "system": system_prompt,
                "stream": False,
                "options": {
                    "temperature": 0.7,
                    "top_p": 0.9,
                    "num_predict": 300,
                }
            }

            data = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                f"{base_url}/api/generate",
                data=data,
                headers={"Content-Type": "application/json"},
            )

            with urllib.request.urlopen(req, timeout=120) as response:
                result = json.loads(response.read())
                reply = result.get("response", "无回复")
                print(f"\nCalmara回复:\n{reply}")

        except Exception as e:
            print(f"请求失败: {e}")

    print("\n" + "=" * 60)
    print("测试完成!")
    print("=" * 60)

    print("\n[3] LoRA权重状态:")
    lora_path = "e:/项目AI/Calmara/llm/output/calmara-lora-20260327-000538/final"
    if os.path.exists(lora_path):
        size = os.path.getsize(os.path.join(lora_path, "adapter_model.safetensors"))
        print(f"✓ LoRA权重已保存: {lora_path}")
        print(f"  文件大小: {size / (1024*1024):.2f} MB")
        print("  (可在更大显存环境合并到基础模型)")

    return True

if __name__ == "__main__":
    test_ollama()