"""
Calmara - Qwen2.5-7B 增强版LoRA微调训练脚本
支持4bit量化、梯度检查点、混合精度训练
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
print("Calmara - Qwen2.5-7B 增强版LoRA微调")
print("=" * 60)


class ConversationDataset(Dataset):
    """心理对话数据集"""

    def __init__(self, data_path, tokenizer, max_length=512):
        with open(data_path, 'r', encoding='utf-8') as f:
            self.data = json.load(f)

        self.tokenizer = tokenizer
        self.max_length = max_length
        print(f"加载数据集: {len(self.data)} 条对话")

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        item = self.data[idx]
        
        conversations = item.get('conversations', [])
        
        if conversations:
            human_msgs = []
            gpt_msgs = []
            
            for conv in conversations:
                if conv.get('from') == 'human':
                    human_msgs.append(conv.get('value', ''))
                elif conv.get('from') == 'gpt':
                    gpt_msgs.append(conv.get('value', ''))
            
            text_parts = []
            for h, g in zip(human_msgs, gpt_msgs):
                text_parts.append(f"<|im_start|>user\n{h}<|im_end|>\n<|im_start|>assistant\n{g}<|im_end|>")
            
            text = "\n".join(text_parts)
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
        
        labels[labels == self.tokenizer.pad_token_id] = -100

        return {
            'input_ids': input_ids,
            'labels': labels,
            'attention_mask': encoding['attention_mask'].squeeze()
        }


def load_config(config_path):
    """加载训练配置"""
    with open(config_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser(description="Calmara Qwen2.5 增强版LoRA微调")
    parser.add_argument("--config", type=str, default="llm/config/training_config.json", help="配置文件路径")
    parser.add_argument("--model_path", type=str, default=None, help="模型路径（覆盖配置）")
    parser.add_argument("--data_path", type=str, default=None, help="训练数据路径（覆盖配置）")
    parser.add_argument("--output_dir", type=str, default=None, help="输出目录（覆盖配置）")
    parser.add_argument("--epochs", type=int, default=None, help="训练轮数")
    parser.add_argument("--batch_size", type=int, default=None, help="批次大小")
    parser.add_argument("--learning_rate", type=float, default=None, help="学习率")
    parser.add_argument("--max_length", type=int, default=None, help="最大序列长度")
    parser.add_argument("--rank", type=int, default=None, help="LoRA rank")
    parser.add_argument("--alpha", type=int, default=None, help="LoRA alpha")
    parser.add_argument("--gradient_steps", type=int, default=None, help="梯度累积步数")
    parser.add_argument("--use_4bit", action="store_true", help="使用4bit量化")
    parser.add_argument("--max_samples", type=int, default=None, help="最大样本数")

    args = parser.parse_args()

    config = {}
    if Path(args.config).exists():
        config = load_config(args.config)
        print(f"加载配置: {args.config}")

    model_config = config.get('model_config', {})
    lora_config = config.get('lora_config', {})
    training_config = config.get('training_config', {})
    quant_config = config.get('quantization', {})
    dataset_config = config.get('dataset', {})

    model_path = args.model_path or model_config.get('model_path_local', 'Qwen/Qwen2.5-7B-Instruct')
    data_path = args.data_path or dataset_config.get('train_path', 'llm/dataset/train.json')
    output_base = args.output_dir or training_config.get('output_dir', 'llm/output')
    
    epochs = args.epochs or training_config.get('num_train_epochs', 3)
    batch_size = args.batch_size or training_config.get('per_device_train_batch_size', 4)
    learning_rate = args.learning_rate or training_config.get('learning_rate', 1e-4)
    max_length = args.max_length or training_config.get('max_length', 512)
    rank = args.rank or lora_config.get('rank', 32)
    alpha = args.alpha or lora_config.get('alpha', 64)
    gradient_steps = args.gradient_steps or training_config.get('gradient_accumulation_steps', 8)
    use_4bit = args.use_4bit or quant_config.get('load_in_4bit', True)

    timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
    output_dir = f"{output_base}/calmara-lora-{timestamp}"

    print(f"\n{'='*60}")
    print("训练配置")
    print(f"{'='*60}")
    print(f"模型路径: {model_path}")
    print(f"数据路径: {data_path}")
    print(f"输出目录: {output_dir}")
    print(f"训练轮数: {epochs}")
    print(f"批次大小: {batch_size}")
    print(f"梯度累积: {gradient_steps}")
    print(f"学习率: {learning_rate}")
    print(f"最大长度: {max_length}")
    print(f"LoRA rank: {rank}, alpha: {alpha}")
    print(f"4bit量化: {use_4bit}")
    print(f"{'='*60}\n")

    from transformers import AutoTokenizer, AutoModelForCausalLM, BitsAndBytesConfig
    from peft import LoraConfig, get_peft_model, TaskType, prepare_model_for_kbit_training

    print("正在加载Tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(
        model_path,
        trust_remote_code=True,
        use_fast=False
    )
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    print("Tokenizer加载完成")

    print(f"\n正在加载模型...")
    
    if use_4bit:
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type=quant_config.get('bnb_4bit_quant_type', 'nf4'),
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_use_double_quant=quant_config.get('bnb_4bit_use_double_quant', True)
        )
        model_kwargs = {
            "quantization_config": bnb_config,
            "device_map": "auto",
            "trust_remote_code": True,
        }
    else:
        model_kwargs = {
            "torch_dtype": torch.float16,
            "device_map": "auto",
            "trust_remote_code": True,
            "low_cpu_mem_usage": True,
        }

    try:
        model = AutoModelForCausalLM.from_pretrained(model_path, **model_kwargs)
    except Exception as e:
        print(f"加载失败，尝试备选方案: {e}")
        model_kwargs.pop("quantization_config", None)
        model_kwargs["torch_dtype"] = torch.float16
        model = AutoModelForCausalLM.from_pretrained(model_path, **model_kwargs)

    model.config.use_cache = False
    
    if use_4bit:
        model = prepare_model_for_kbit_training(model)
    
    model.gradient_checkpointing_enable()
    print(f"模型加载完成")

    target_modules = lora_config.get('target_modules', ["q_proj", "k_proj", "v_proj", "o_proj"])
    
    peft_config = LoraConfig(
        task_type=TaskType.CAUSAL_LM,
        r=rank,
        lora_alpha=alpha,
        lora_dropout=lora_config.get('dropout', 0.1),
        target_modules=target_modules,
        bias=lora_config.get('bias', 'none'),
        inference_mode=False,
    )
    
    print(f"\nLoRA配置: rank={rank}, alpha={alpha}, target_modules={target_modules}")
    model = get_peft_model(model, peft_config)
    model.print_trainable_parameters()

    dataset = ConversationDataset(data_path, tokenizer, max_length)
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    
    optimizer = torch.optim.AdamW(
        model.parameters(), 
        lr=learning_rate,
        weight_decay=training_config.get('weight_decay', 0.01)
    )

    total_steps = len(dataloader) * epochs // gradient_steps
    warmup_steps = min(training_config.get('warmup_steps', 50), total_steps // 10)
    
    from torch.optim.lr_scheduler import CosineAnnealingLR
    scheduler = CosineAnnealingLR(optimizer, T_max=total_steps - warmup_steps, eta_min=learning_rate * 0.1)

    print(f"\n训练设备: {device}")
    print(f"总步数: {total_steps}, 预热步数: {warmup_steps}")
    print("开始训练...\n")

    global_step = 0
    best_loss = float('inf')
    
    for epoch in range(epochs):
        model.train()
        total_loss = 0
        optimizer.zero_grad()

        for step, batch in enumerate(dataloader):
            input_ids = batch['input_ids'].to(device)
            labels = batch['labels'].to(device)
            attention_mask = batch['attention_mask'].to(device)

            outputs = model(
                input_ids=input_ids, 
                labels=labels,
                attention_mask=attention_mask
            )
            loss = outputs.loss / gradient_steps
            loss.backward()

            total_loss += outputs.loss.item()

            if (step + 1) % gradient_steps == 0:
                torch.nn.utils.clip_grad_norm_(model.parameters(), training_config.get('max_grad_norm', 1.0))
                optimizer.step()
                scheduler.step()
                optimizer.zero_grad()
                global_step += 1

                if global_step % 10 == 0:
                    current_loss = outputs.loss.item()
                    lr = scheduler.get_last_lr()[0]
                    print(f"Epoch {epoch+1} Step {global_step} Loss: {current_loss:.4f} LR: {lr:.2e}")

        avg_loss = total_loss / len(dataloader)
        print(f"\nEpoch {epoch+1} 完成，平均损失: {avg_loss:.4f}")

        checkpoint_dir = f"{output_dir}/epoch-{epoch+1}"
        os.makedirs(checkpoint_dir, exist_ok=True)
        model.save_pretrained(checkpoint_dir)
        tokenizer.save_pretrained(checkpoint_dir)
        print(f"检查点已保存: {checkpoint_dir}")

        if avg_loss < best_loss:
            best_loss = avg_loss
            best_dir = f"{output_dir}/best"
            os.makedirs(best_dir, exist_ok=True)
            model.save_pretrained(best_dir)
            tokenizer.save_pretrained(best_dir)
            print(f"最佳模型已保存: {best_dir}")

        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

    print("\n训练完成！")

    final_dir = f"{output_dir}/final"
    os.makedirs(final_dir, exist_ok=True)
    model.save_pretrained(final_dir)
    tokenizer.save_pretrained(final_dir)

    print(f"\n最终模型: {final_dir}")
    print(f"最佳损失: {best_loss:.4f}")
    print("\n" + "=" * 60)
    print("下一步:")
    print("1. 测试模型: python llm/scripts/test_model.py --model_path " + final_dir)
    print("2. 导出GGUF: python llm/scripts/export_gguf.py --model_path " + final_dir)
    print("=" * 60)


if __name__ == "__main__":
    main()
