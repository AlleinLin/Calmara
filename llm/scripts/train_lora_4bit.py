"""
Calmara - Qwen2.5-7B 4bit量化LoRA微调
最小内存占用的训练版本
"""

import os
import json
import argparse
from datetime import datetime
import gc

import torch

print("=" * 60)
print("Calmara - Qwen2.5-7B 4bit量化LoRA微调")
print("=" * 60)

class ConversationDataset(torch.utils.data.Dataset):
    def __init__(self, data_path, tokenizer, max_length=128):
        with open(data_path, 'r', encoding='utf-8') as f:
            self.data = json.load(f)
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        item = self.data[idx]
        convs = item.get('conversations', [])
        human = next((c['value'] for c in convs if c['from'] == 'human'), '')
        gpt = next((c['value'] for c in convs if c['from'] == 'gpt'), '')
        text = human + " " + gpt

        enc = self.tokenizer(text, truncation=True, max_length=self.max_length,
                            padding='max_length', return_tensors='pt')
        return enc['input_ids'].squeeze(), enc['attention_mask'].squeeze()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_path", type=str, required=True)
    parser.add_argument("--data_path", type=str, required=True)
    parser.add_argument("--output_dir", type=str, default="output")
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--rank", type=int, default=4)
    parser.add_argument("--alpha", type=int, default=8)
    args = parser.parse_args()

    from transformers import AutoTokenizer, AutoModelForCausalLM, BitsAndBytesConfig
    from peft import LoraConfig, get_peft_model, TaskType

    timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
    output_dir = f"{args.output_dir}/calmara-lora-{timestamp}"

    print(f"\n加载Tokenizer...")
    tok = AutoTokenizer.from_pretrained(args.model_path, trust_remote_code=True, use_fast=False)
    tok.pad_token = tok.eos_token

    print(f"\n加载4bit量化模型...")
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
        bnb_4bit_use_double_quant=True,
    )

    model = AutoModelForCausalLM.from_pretrained(
        args.model_path,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True,
    )
    model.config.use_cache = False

    print(f"应用LoRA (r={args.rank}, alpha={args.alpha})...")
    lora_cfg = LoraConfig(
        r=args.rank, lora_alpha=args.alpha,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
        lora_dropout=0.05, bias="none", task_type=TaskType.CAUSAL_LM
    )
    model = get_peft_model(model, lora_cfg)
    model.print_trainable_parameters()

    dataset = ConversationDataset(args.data_path, tok)
    loader = torch.utils.data.DataLoader(dataset, batch_size=1, shuffle=True)
    print(f"\n数据集: {len(dataset)}条")

    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-4)
    device = next(model.parameters()).device

    print(f"\n开始训练 (设备: {device})...")

    for epoch in range(args.epochs):
        model.train()
        total_loss = 0
        for step, (input_ids, attention_mask) in enumerate(loader):
            input_ids = input_ids.to(device)
            outputs = model(input_ids=input_ids, labels=input_ids)
            loss = outputs.loss
            loss.backward()
            optimizer.step()
            optimizer.zero_grad()
            total_loss += loss.item()
            if step % 10 == 0:
                print(f"Epoch {epoch+1} Step {step+1} Loss: {loss.item():.4f}")

        print(f"Epoch {epoch+1} 完成, 平均Loss: {total_loss/len(loader):.4f}")

        ckpt = f"{output_dir}/epoch-{epoch+1}"
        os.makedirs(ckpt, exist_ok=True)
        model.save_pretrained(ckpt)
        tok.save_pretrained(ckpt)
        gc.collect()
        torch.cuda.empty_cache()

    final = f"{output_dir}/final"
    os.makedirs(final, exist_ok=True)
    model.save_pretrained(final)
    tok.save_pretrained(final)
    print(f"\n训练完成! 模型: {final}")

if __name__ == "__main__":
    main()
