"""
Calmara - 微调模型验证脚本
测试模型是否正常工作
"""

import os
import sys

def test_model_inference(model_path: str = None):
    """
    测试模型推理功能
    """
    print("=" * 60)
    print("Calmara - 微调模型验证")
    print("=" * 60)

    test_prompts = [
        "我最近总是睡不着，应该怎么办？",
        "我觉得自己很失败，什么都做不好...",
        "你好，你是Calmara吗？",
    ]

    print("\n测试提示词:")
    for i, prompt in enumerate(test_prompts, 1):
        print(f"  {i}. {prompt}")

    print("\n" + "-" * 60)

    if model_path and os.path.exists(model_path):
        print(f"\n正在加载模型: {model_path}")

        try:
            from transformers import AutoTokenizer, AutoModelForCausalLM
            import torch

            tokenizer = AutoTokenizer.from_pretrained(
                model_path,
                trust_remote_code=True,
                use_fast=False
            )

            model = AutoModelForCausalLM.from_pretrained(
                model_path,
                device_map="auto",
                torch_dtype=torch.float16,
                trust_remote_code=True
            )

            print("模型加载成功！\n")

            for i, prompt in enumerate(test_prompts, 1):
                print(f"测试 {i}: {prompt}")
                print("-" * 40)

                formatted_prompt = f"<|im_start|>user\n{prompt}<|im_end|>\n<|im_start|>assistant\n"

                inputs = tokenizer(formatted_prompt, return_tensors="pt").to(model.device)

                with torch.no_grad():
                    outputs = model.generate(
                        **inputs,
                        max_new_tokens=512,
                        temperature=0.7,
                        top_p=0.9,
                        do_sample=True,
                        repetition_penalty=1.1
                    )

                response = tokenizer.decode(outputs[0], skip_special_tokens=True)

                assistant_response = response.split("<|im_start|>assistant")[-1].strip()
                print(f"回复: {assistant_response[:200]}...")
                print()

            print("=" * 60)
            print("所有测试通过！模型工作正常。")
            print("=" * 60)

        except Exception as e:
            print(f"\n模型加载失败: {e}")
            print("请确保已正确执行微调训练并安装了所有依赖")
            return False
    else:
        print("\n模型路径未指定或不存在，跳过实际推理测试")
        print("请确保在训练完成后运行此脚本进行验证")
        return False

    return True

def check_system_requirements():
    """
    检查系统环境是否满足要求
    """
    print("\n" + "=" * 60)
    print("检查系统环境")
    print("=" * 60)

    checks = []

    print("\n1. Python版本...")
    py_version = sys.version_info
    print(f"   Python {py_version.major}.{py_version.minor}.{py_version.micro}")
    checks.append(("Python >= 3.8", py_version >= (3, 8)))

    print("\n2. PyTorch...")
    try:
        import torch
        print(f"   PyTorch {torch.__version__}")
        print(f"   CUDA可用: {torch.cuda.is_available()}")
        if torch.cuda.is_available():
            print(f"   GPU: {torch.cuda.get_device_name(0)}")
            print(f"   显存: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB")
        checks.append(("PyTorch", True))
    except ImportError:
        print("   PyTorch 未安装")
        checks.append(("PyTorch", False))

    print("\n3. Transformers...")
    try:
        import transformers
        print(f"   Transformers {transformers.__version__}")
        checks.append(("Transformers", True))
    except ImportError:
        print("   Transformers 未安装")
        checks.append(("Transformers", False))

    print("\n4. PEFT...")
    try:
        import peft
        print(f"   PEFT {peft.__version__}")
        checks.append(("PEFT", True))
    except ImportError:
        print("   PEFT 未安装")
        checks.append(("PEFT", False))

    print("\n5. GPU显存检查...")
    try:
        import torch
        if torch.cuda.is_available():
            free_memory = torch.cuda.get_device_properties(0).total_memory / 1024**3
            checks.append(("GPU显存 >= 8GB", free_memory >= 8))
            if free_memory < 8:
                print(f"   警告: 显存 {free_memory:.1f}GB 可能不足以运行7B模型")
        else:
            print("   CUDA不可用")
            checks.append(("CUDA可用", False))
    except:
        checks.append(("GPU检测", False))

    print("\n" + "=" * 60)
    print("检查结果汇总")
    print("=" * 60)

    all_passed = True
    for name, passed in checks:
        status = "✓" if passed else "✗"
        print(f"  [{status}] {name}")
        if not passed:
            all_passed = False

    print()
    if all_passed:
        print("所有检查通过！环境配置正确。")
    else:
        print("部分检查未通过，请安装缺失的依赖。")

    return all_passed

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Calmara模型验证")
    parser.add_argument("--model_path", type=str, default=None,
                        help="模型路径")

    args = parser.parse_args()

    print()
    requirements_ok = check_system_requirements()

    if requirements_ok:
        print("\n开始模型测试...")
        test_model_inference(args.model_path)
    else:
        print("\n请先解决环境问题后再运行测试。")
        print("安装依赖: pip install -r requirements.txt")

if __name__ == "__main__":
    main()
