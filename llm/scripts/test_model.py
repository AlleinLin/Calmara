"""
Calmara - 微调模型推理测试
验证模型是否正常工作
"""

import os
import sys

print("=" * 60)
print("Calmara - 微调模型推理测试")
print("=" * 60)

def test_model():
    """测试模型推理"""
    try:
        import torch
        from transformers import AutoTokenizer, AutoModelForCausalLM

        model_path = "e:/项目AI/Calmara/llm/models/Qwen/Qwen2___5-7B-Instruct"

        print(f"\n加载模型: {model_path}")

        print("加载Tokenizer...")
        tokenizer = AutoTokenizer.from_pretrained(
            model_path,
            trust_remote_code=True,
            use_fast=False
        )
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token

        print("加载模型...")
        model = AutoModelForCausalLM.from_pretrained(
            model_path,
            device_map="auto",
            torch_dtype=torch.float16,
            trust_remote_code=True,
        )

        print(f"模型设备: {next(model.parameters()).device}")

        test_prompts = [
            "我最近总是睡不着，应该怎么办？",
            "我觉得自己很失败，什么都做不好...",
            "你好，你是Calmara吗？",
        ]

        print("\n" + "=" * 60)
        print("推理测试")
        print("=" * 60)

        for i, prompt in enumerate(test_prompts, 1):
            print(f"\n【测试 {i}】输入: {prompt}")
            print("-" * 40)

            formatted = f"<|im_start|>user\n{prompt}<|im_end|>\n<|im_start|>assistant\n"

            inputs = tokenizer(formatted, return_tensors="pt").to(model.device)

            with torch.no_grad():
                outputs = model.generate(
                    **inputs,
                    max_new_tokens=200,
                    temperature=0.7,
                    top_p=0.9,
                    do_sample=True,
                    repetition_penalty=1.1
                )

            response = tokenizer.decode(outputs[0], skip_special_tokens=True)
            answer = response.split("<|im_start|>assistant")[-1].strip()

            print(f"回复: {answer[:200]}...")

        print("\n" + "=" * 60)
        print("测试完成！")
        print("=" * 60)

        return True

    except Exception as e:
        print(f"\n测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def check_environment():
    """检查环境"""
    print("\n环境检查:")
    print("-" * 40)

    try:
        import torch
        print(f"✓ PyTorch: {torch.__version__}")
        print(f"✓ CUDA可用: {torch.cuda.is_available()}")
        if torch.cuda.is_available():
            print(f"✓ GPU: {torch.cuda.get_device_name(0)}")
    except:
        print("✗ PyTorch 未安装")

    try:
        import transformers
        print(f"✓ Transformers: {transformers.__version__}")
    except:
        print("✗ Transformers 未安装")

    try:
        import peft
        print(f"✓ PEFT: {peft.__version__}")
    except:
        print("✗ PEFT 未安装")

if __name__ == "__main__":
    check_environment()
    print()
    test_model()
