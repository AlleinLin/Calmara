"""
Calmara - Qwen2.5-7B 优化LoRA微调训练脚本
解决内存问题的优化版本
"""

import os
import sys
import json
import argparse
from pathlib import Path
from datetime import datetime
import gc

import torch
from torch.utils.data import Dataset, DataLoader

print("=" * 60)
print("Calmara - Qwen2.5-7B LoRA Fine-tuning (优化版)")
print("=" * 60)

class ConversationDataset(Dataset):
    """心理对话数据集"""

    def __init__(self, data_path, tokenizer, max_length=256):
        with open(data_path, 'r', encoding='utf-8') as f:
            self.data = json.load(f)

        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        item = self.data[idx]

        if 'conversations' in item:
            human_msg = ""
            gpt_msg = ""

            for conv in item['conversations']:
                if conv['from'] == 'human':
                    human_msg = conv['value']
                elif conv['from'] == 'gpt':
                    gpt_msg = conv['value']

            text = human_msg + "\n" + gpt_msg
        else:
            text = item.get('instruction', '') + "\n" + item.get('response', '')

        encoding = self.tokenizer(
            text,
            truncation=True,
            max_length=self.max_length,
            padding='max_length',
            return_tensors='pt'
        )

        input_ids = encoding['input_ids'].squeeze()
        labels = input_ids.clone()

        return {
            'input_ids': input_ids,
            'labels': labels
        }

def main():
    parser = argparse.ArgumentParser(description="Calmara Qwen2.5 LoRA Fine-tuning")
    parser.add_argument("--model_path", type=str, required=True, help="模型路径")
    parser.add_argument("--data_path", type=str, required=True, help="训练数据路径")
    parser.add_argument("--output_dir", type=str, default="output", help="输出目录")
    parser.add_argument("--epochs", type=int, default=3, help="训练轮数")
    parser.add_argument("--batch_size", type=int, default=1, help="批次大小")
    parser.add_argument("--learning_rate", type=float, default=2e-4, help="学习率")
    parser.add_argument("--max_length", type=int, default=256, help="最大序列长度")
    parser.add_argument("--rank", type=int, default=8, help="LoRA rank")
    parser.add_argument("--alpha", type=int, default=16, help="LoRA alpha")
    parser.add_argument("--gradient_steps", type=int, default=8, help="梯度累积步数")

    args = parser.parse_args()

    from transformers import AutoTokenizer, AutoModelForCausalLM
    from peft import LoraConfig, get_peft_model, TaskType, prepare_model_for_kbit_training

    timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
    output_dir = f"{args.output_dir}/qwen2.5-7b-lora-{timestamp}"

    print(f"\n输出目录: {output_dir}")
    print(f"训练参数: epochs={args.epochs}, batch_size={args.batch_size}, gradient_steps={args.gradient_steps}")

    print(f"\n正在加载Tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(
        args.model_path,
        trust_remote_code=True,
        use_fast=False
    )
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    print("Tokenizer加载完成")

    print(f"\n正在加载模型 ({args.max_length}x{args.batch_size})...")
    model_kwargs = {
        "trust_remote_code": True,
        "torch_dtype": torch.float16,
        "device_map": "auto",
        "low_cpu_mem_usage": True,
        "max_memory": {0: "8GiB", "cpu": "32GiB"},
    }

    try:
        model = AutoModelForCausalLM.from_pretrained(args.model_path, **model_kwargs)
    except Exception as e:
        print(f"加载失败，尝试备选方案: {e}")
        model_kwargs.pop("max_memory")
        model = AutoModelForCausalLM.from_pretrained(args.model_path, **model_kwargs)

    model.config.use_cache = False
    model = prepare_model_for_kbit_training(model)
    print(f"模型加载完成，设备: {next(model.parameters()).device}")

    lora_config = LoraConfig(
        task_type=TaskType.CAUSAL_LM,
        r=args.rank,
        lora_alpha=args.alpha,
        lora_dropout=0.05,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
        bias="none",
        inference_mode=False,
    )
    print(f"LoRA配置: r={args.rank}, alpha={args.alpha}")
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    dataset = ConversationDataset(args.data_path, tokenizer, args.max_length)
    dataloader = DataLoader(dataset, batch_size=args.batch_size, shuffle=True)
    print(f"\n数据集: {len(dataset)} 条对话")

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate)

    print(f"\n训练设备: {device}")
    print("开始训练...\n")

    global_step = 0
    for epoch in range(args.epochs):
        model.train()
        total_loss = 0
        optimizer.zero_grad()

        for step, batch in enumerate(dataloader):
            input_ids = batch['input_ids'].to(device)
            labels = batch['labels'].to(device)

            outputs = model(input_ids=input_ids, labels=labels)
            loss = outputs.loss / args.gradient_steps
            loss.backward()

            total_loss += outputs.loss.item()

            if (step + 1) % args.gradient_steps == 0:
                optimizer.step()
                optimizer.zero_grad()
                global_step += 1

                if step % 10 == 0:
                    print(f"Epoch {epoch+1} Step {step+1}/{len(dataloader)} Loss: {loss.item()*args.gradient_steps:.4f}")

        avg_loss = total_loss / len(dataloader)
        print(f"\nEpoch {epoch+1} 完成，平均损失: {avg_loss:.4f}")

        checkpoint_dir = f"{output_dir}/checkpoint-epoch-{epoch+1}"
        os.makedirs(checkpoint_dir, exist_ok=True)
        model.save_pretrained(checkpoint_dir)
        tokenizer.save_pretrained(checkpoint_dir)
        print(f"检查点已保存: {checkpoint_dir}")

        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

    print("\n训练完成！")

    final_dir = f"{output_dir}/final"
    os.makedirs(final_dir, exist_ok=True)
    model.save_pretrained(final_dir)
    tokenizer.save_pretrained(final_dir)

    print(f"\n最终模型: {final_dir}")
    print("\n" + "=" * 60)
    print("下一步: python scripts/export_gguf.py --model_path " + final_dir)
    print("=" * 60)

if __name__ == "__main__":
    main()
