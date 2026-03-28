"""
Calmara - 合并LoRA权重并导出为GGUF格式
用于Ollama部署
"""

import os
import sys
import argparse
from pathlib import Path

def merge_and_export(model_path, output_path, base_model_path):
    """合并LoRA权重并导出"""
    print("=" * 60)
    print("Calmara - 合并LoRA权重")
    print("=" * 60)

    print(f"\nLoRA模型: {model_path}")
    print(f"基础模型: {base_model_path}")

    try:
        from transformers import AutoTokenizer, AutoModelForCausalLM
        from peft import PeftModel
        import torch

        print("\n加载基础模型...")
        base_model = AutoModelForCausalLM.from_pretrained(
            base_model_path,
            torch_dtype=torch.float16,
            device_map="cpu",
            trust_remote_code=True,
        )

        print("加载LoRA权重...")
        model = PeftModel.from_pretrained(base_model, model_path)

        print("合并权重...")
        merged_model = model.merge_and_unload()

        print(f"\n保存合并后的模型到: {output_path}")
        merged_model.save_pretrained(output_path)

        tokenizer = AutoTokenizer.from_pretrained(base_model_path, trust_remote_code=True)
        tokenizer.save_pretrained(output_path)

        print("\n模型合并完成！")

        print("\n" + "=" * 60)
        print("GGUF导出说明:")
        print("=" * 60)
        print("""
由于GGUF导出需要llama.cpp工具，建议使用Ollama直接部署：

方法1: 使用Ollama魔搭社区模型
    ollama run qwen2.5:7b-chat

方法2: 下载微调后的qwen2.5模型并配置Modelfile
    1. 下载模型: huggingface.co/Qwen/Qwen2.5-7B-Instruct
    2. 创建Modelfile指向该模型
    3. ollama create calmara -f Modelfile

方法3: 使用微调的LoRA权重(需合并到基础模型)
    1. 合并LoRA权重到基础模型
    2. 使用llama.cpp转换为GGUF格式
    3. ollama create calmara -f Modelfile
        """)

        return True

    except ImportError as e:
        print(f"\n缺少依赖: {e}")
        print("请安装: pip install transformers peft torch")
        return False
    except Exception as e:
        print(f"\n合并失败: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Calmara LoRA权重合并")
    parser.add_argument("--lora_path", type=str,
                        default="e:/项目AI/Calmara/llm/output/calmara-lora-20260327-000538/final",
                        help="LoRA模型路径")
    parser.add_argument("--base_model", type=str,
                        default="e:/项目AI/Calmara/llm/models/Qwen/Qwen2___5-7B-Instruct",
                        help="基础模型路径")
    parser.add_argument("--output", type=str,
                        default="e:/项目AI/Calmara/llm/output/merged_model",
                        help="输出路径")

    args = parser.parse_args()

    success = merge_and_export(args.lora_path, args.output, args.base_model)

    if success:
        print(f"\n成功! 合并模型保存在: {args.output}")
    else:
        print("\n合并失败，请检查错误信息")

if __name__ == "__main__":
    main()
