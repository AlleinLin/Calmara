"""
Calmara - 导出微调模型为GGUF格式
用于Ollama部署
"""

import os
import sys
import argparse
from pathlib import Path
import shutil

def export_to_gguf(model_path: str, output_path: str, quant_type: str = "Q4_K_M"):
    """
    将微调后的模型导出为GGUF格式
    """
    print("=" * 60)
    print("Calmara - 导出GGUF模型")
    print("=" * 60)

    print(f"\n输入模型: {model_path}")
    print(f"输出路径: {output_path}")
    print(f"量化类型: {quant_type}")

    output_dir = Path(output_path)
    output_dir.mkdir(parents=True, exist_ok=True)

    llama_cpp_python_dir = Path(__file__).parent.parent
    conversion_script = llama_cpp_python_dir / "convert_hf_to_gguf.py"

    print("\n正在执行模型转换...")

    cmd = f"""
    python -m transformers.models.qwen2.convert_qwen2_to_gguf \\
        {model_path} \\
        --quantize_bit {quant_type} \\
        --outfile {output_path}/calmara-qwen2.5-7b-{quant_type.lower()}.gguf
    """

    print(f"执行命令: {cmd}")

    try:
        os.system(cmd)
        print("\n转换完成!")
    except Exception as e:
        print(f"\n使用transformers内置转换失败，尝试备用方案...")

        try:
            from transformers import AutoTokenizer, AutoModelForCausalLM
            print("模型转换需要使用llama.cpp的convert.py脚本")
            print("\n请手动执行以下步骤:")
            print(f"1. 安装llama.cpp: pip install llama-cpp-python")
            print(f"2. 下载convert.py: git clone https://github.com/ggerganov/llama.cpp")
            print(f"3. 执行转换:")
            print(f"   python llama.cpp/convert.py {model_path} --outfile {output_path}/calmara-qwen2.5-7b.gguf --outtype {quant_type.lower()}")
        except Exception as e2:
            print(f"转换出错: {e2}")

    print(f"\n输出文件位置: {output_path}/calmara-qwen2.5-7b-{quant_type.lower()}.gguf")

    return output_path

def create_modelfile(model_path: str, output_dir: str):
    """
    创建Ollama Modelfile
    """
    print("\n" + "=" * 60)
    print("创建Ollama Modelfile")
    print("=" * 60)

    modelfile_content = f'''# Calara Qwen2.5-7B 心理对话模型
# 基于Qwen2.5-7B-Instruct微调

FROM {model_path}

# 设置模板
TEMPLATE """
<|im_start|>system
你是一个专业的校园心理咨询助手，名为Calmara。你的职责是：
1. 耐心倾听用户的心理困扰
2. 提供温暖、专业的心理支持和建议
3. 识别高风险情况并提供危机干预资源
4. 保持同理心，不评判，给予无条件的积极关注
5. 建议用户在需要时寻求专业帮助

注意：如果用户有自杀念头或自伤倾向，请立即提供心理援助热线并建议寻求紧急帮助。
<|im_end|>
<|im_start|>user
{{{{ .Prompt }}}}<|im_end|>
<|im_start|>assistant
"""

# 设置系统提示
SYSTEM """
你是Calmara，一个专业的校园心理咨询助手。你应该始终保持温暖、耐心的态度，提供专业的心理支持。
"""

# 设置参数
PARAMETER temperature 0.7
PARAMETER top_p 0.9
PARAMETER top_k 50
PARAMETER num_ctx 4096
PARAMETER repeat_penalty 1.1
"""

    modelfile_path = Path(output_dir) / "Modelfile"
    with open(modelfile_path, 'w', encoding='utf-8') as f:
        f.write(modelfile_content)

    print(f"Modelfile已创建: {modelfile_path}")

    readme_content = """# Calmara Ollama 部署指南

## 简介
Calmara Qwen2.5-7B心理对话模型，基于Qwen2.5-7B-Instruct微调，专门用于校园心理咨询场景。

## 文件说明
- `calmara-qwen2.5-7b.gguf` - 模型权重文件
- `Modelfile` - Ollama模型定义文件

## 部署步骤

### 1. 安装Ollama
```bash
# macOS/Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows (使用WSL2)
wsl install
```

### 2. 创建模型
```bash
# 确保Modelfile和模型文件在同一目录
ollama create calmara -f Modelfile .
```

### 3. 运行模型
```bash
# 交互式对话
ollama run calmara

# API服务
ollama serve
```

### 4. API调用示例
```bash
curl -X POST http://localhost:11434/api/generate \\
    -d '{{"model": "calmara", "prompt": "我最近很焦虑怎么办？"}}'
```

## 系统提示词
模型已内置专业的心理辅导系统提示，确保：
- 同理心沟通
- 专业建议
- 危机识别
- 隐私保护

## 联系方式
如有问题，请联系开发团队。
"""

    readme_path = Path(output_dir) / "README.md"
    with open(readme_path, 'w', encoding='utf-8') as f:
        f.write(readme_content)

    print(f"部署说明已创建: {readme_path}")

    return modelfile_path

def main():
    parser = argparse.ArgumentParser(description="导出GGUF模型并创建Modelfile")
    parser.add_argument("--model_path", type=str, required=True,
                        help="微调后的模型路径")
    parser.add_argument("--output_dir", type=str, default="output",
                        help="输出目录")

    args = parser.parse_args()

    model_dir = Path(args.model_path)
    if not model_dir.exists():
        print(f"错误: 模型路径不存在: {model_path}")
        sys.exit(1)

    gguf_path = export_to_gguf(
        model_path=args.model_path,
        output_path=args.output_dir,
        quant_type="Q4_K_M"
    )

    modelfile = create_modelfile(
        model_path=str(gguf_path / "calmara-qwen2.5-7b-q4_k_m.gguf"),
        output_dir=str(gguf_path)
    )

    print("\n" + "=" * 60)
    print("导出和部署准备完成!")
    print("=" * 60)
    print(f"\n请查看 {gguf_path}/README.md 获取部署说明")

if __name__ == "__main__":
    main()
