"""
Calmara - Qwen2.5-7B 模型下载脚本
使用ModelScope下载模型
"""

import os
import sys
from pathlib import Path

def download_with_modelscope():
    """
    使用ModelScope下载Qwen2.5-7B-Instruct模型
    """
    print("=" * 60)
    print("正在从ModelScope下载Qwen2.5-7B-Instruct模型...")
    print("=" * 60)

    try:
        from modelscope import snapshot_download

        model_dir = snapshot_download(
            'Qwen/Qwen2.5-7B-Instruct',
            cache_dir=str(Path(__file__).parent.parent / 'models')
        )

        print(f"\n模型下载完成！")
        print(f"模型路径: {model_dir}")
        return model_dir

    except ImportError:
        print("\nModelScope未安装，正在安装...")
        os.system("pip install modelscope")
        return download_with_modelscope()

    except Exception as e:
        print(f"\n下载失败: {e}")
        return None

def download_with_huggingface():
    """
    使用HuggingFace下载模型
    """
    print("=" * 60)
    print("正在从HuggingFace下载Qwen2.5-7B-Instruct模型...")
    print("=" * 60)

    try:
        from huggingface_hub import snapshot_download

        model_dir = snapshot_download(
            repo_id="Qwen/Qwen2.5-7B-Instruct",
            cache_dir=str(Path(__file__).parent.parent / 'models')
        )

        print(f"\n模型下载完成！")
        print(f"模型路径: {model_dir}")
        return model_dir

    except ImportError:
        print("\n huggingface_hub未安装，正在安装...")
        os.system("pip install huggingface_hub")
        return download_with_huggingface()

    except Exception as e:
        print(f"\n下载失败: {e}")
        return None

def main():
    import argparse

    parser = argparse.ArgumentParser(description="下载Qwen2.5-7B-Instruct模型")
    parser.add_argument("--source", type=str, default="modelscope",
                        choices=["modelscope", "huggingface"],
                        help="下载源")
    parser.add_argument("--model_id", type=str, default="Qwen/Qwen2.5-7B-Instruct",
                        help="模型ID")

    args = parser.parse_args()

    print("\n" + "=" * 60)
    print("Qwen2.5-7B-Instruct 模型下载")
    print("=" * 60)
    print(f"\n下载源: {args.source}")
    print(f"模型ID: {args.model_id}")
    print(f"预计大小: ~14GB")
    print(f"下载时间: 取决于网络速度 (可能需要30分钟到数小时)")
    print()

    confirm = input("是否继续下载? (y/n): ").strip().lower()
    if confirm != 'y':
        print("下载已取消")
        return

    if args.source == "modelscope":
        model_dir = download_with_modelscope()
    else:
        model_dir = download_with_huggingface()

    if model_dir:
        print("\n" + "=" * 60)
        print("下载成功！")
        print("=" * 60)
        print(f"\n模型路径: {model_dir}")
        print("\n下一步:")
        print("1. 运行训练: python scripts/train_lora.py")
        print("2. 或使用train.bat (Windows) / train.sh (Linux)")
    else:
        print("\n下载失败，请检查网络连接后重试")

if __name__ == "__main__":
    main()
