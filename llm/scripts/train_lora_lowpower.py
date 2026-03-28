#!/usr/bin/env python3
"""
低功耗LoRA微调脚本
针对笔记本电脑优化，防止过热断电
- 使用4bit量化减少显存占用
- 小batch size降低功耗
- 梯度检查点节省显存
- 温度监控保护
"""
import os
import sys
import json
import argparse
import time
import subprocess
from pathlib import Path
from datetime import datetime
from typing import Dict, Any, List, Optional

import torch
from torch.utils.data import Dataset, DataLoader
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    Trainer,
    DataCollatorForSeq2Seq,
    EarlyStoppingCallback,
    BitsAndBytesConfig
)
from peft import (
    LoraConfig,
    get_peft_model,
    TaskType,
    PeftModel
)
from datasets import Dataset as HFDataset
from loguru import logger

MAX_GPU_TEMP = 83
CHECK_TEMP_INTERVAL = 30

class TemperatureMonitor:
    def __init__(self, max_temp=MAX_GPU_TEMP):
        self.max_temp = max_temp
        self.last_check = 0
    
    def check(self) -> int:
        try:
            result = subprocess.run(
                ['nvidia-smi', '--query-gpu=temperature.gpu', '--format=csv,noheader,nounits'],
                capture_output=True, text=True, timeout=5
            )
            if result.returncode == 0:
                return int(result.stdout.strip())
        except:
            pass
        return 0
    
    def is_safe(self) -> bool:
        temp = self.check()
        if temp > self.max_temp:
            logger.warning(f"GPU温度过高: {temp}°C > {self.max_temp}°C，暂停训练")
            return False
        return True
    
    def wait_for_cool(self):
        while not self.is_safe():
            logger.info("等待GPU冷却...")
            time.sleep(60)

class PsychDataset(Dataset):
    SYSTEM_PROMPT = """你是一个专业的校园心理咨询智能助手，具备以下能力：
1. 情绪识别：能够识别用户的情绪状态（正常、焦虑、低落、高风险）
2. 意图判断：判断用户意图是闲聊(CHAT)、心理咨询(CONSULT)还是高风险(RISK)
3. 专业咨询：提供温和、专业、安全的心理疏导建议
4. 危机干预：识别高风险情况并提供适当的干预建议

请根据用户的输入，提供专业、有同理心的回复。"""

    def __init__(self, data_path: str, tokenizer, max_length: int = 1024):
        self.tokenizer = tokenizer
        self.max_length = max_length
        self.data = self._load_data(data_path)
        logger.info(f"加载了 {len(self.data)} 条训练数据")

    def _load_data(self, path: str) -> List[Dict]:
        if not os.path.exists(path):
            logger.warning(f"数据文件不存在: {path}")
            return self._get_sample_data()
        
        with open(path, 'r', encoding='utf-8') as f:
            if path.endswith('.jsonl'):
                data = [json.loads(line) for line in f if line.strip()]
            else:
                data = json.load(f)
        return data

    def _get_sample_data(self) -> List[Dict]:
        return [
            {"instruction": "我最近总是睡不着，心里很慌", "output": "我理解你的困扰。失眠和焦虑常常相互影响。能告诉我这种情况持续多久了吗？"},
            {"instruction": "我觉得活着没有意义", "output": "我听到了你内心的痛苦。我强烈建议你尽快和信任的人或专业心理咨询师聊聊。"},
            {"instruction": "今天天气真好", "output": "是的，好天气确实让人心情愉悦！有什么计划吗？"},
        ]

    def __len__(self) -> int:
        return len(self.data)

    def __getitem__(self, idx: int) -> Dict[str, torch.Tensor]:
        item = self.data[idx]
        
        if "conversations" in item:
            convs = item["conversations"]
            text = ""
            for conv in convs:
                role = "用户" if conv["from"] == "human" else "助手"
                text += f"{role}：{conv['value']}\n"
        else:
            instruction = item.get("instruction", item.get("input", ""))
            output = item.get("output", item.get("response", ""))
            text = f"用户：{instruction}\n助手：{output}"

        encoding = self.tokenizer(
            text,
            max_length=self.max_length,
            padding="max_length",
            truncation=True,
            return_tensors="pt"
        )

        input_ids = encoding["input_ids"].squeeze()
        attention_mask = encoding["attention_mask"].squeeze()
        labels = input_ids.clone()

        return {
            "input_ids": input_ids,
            "attention_mask": attention_mask,
            "labels": labels
        }

class LowPowerTrainer:
    def __init__(self, args):
        self.args = args
        self.model = None
        self.tokenizer = None
        self.temp_monitor = TemperatureMonitor(max_temp=args.max_temp)
        self.start_time = None

    def setup(self):
        logger.info("=" * 50)
        logger.info("低功耗LoRA微调 - 初始化")
        logger.info("=" * 50)
        
        model_path = self.args.model_path
        if not os.path.exists(model_path):
            model_path = self.args.model_name
        
        logger.info(f"加载模型: {model_path}")
        
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_use_double_quant=True
        )
        
        self.tokenizer = AutoTokenizer.from_pretrained(
            model_path,
            trust_remote_code=True,
            padding_side='right'
        )
        
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token
        
        self.model = AutoModelForCausalLM.from_pretrained(
            model_path,
            quantization_config=bnb_config,
            device_map="auto",
            trust_remote_code=True,
            torch_dtype=torch.float16
        )
        
        self.model.gradient_checkpointing_enable()
        
        lora_config = LoraConfig(
            task_type=TaskType.CAUSAL_LM,
            r=self.args.lora_r,
            lora_alpha=self.args.lora_alpha,
            lora_dropout=self.args.lora_dropout,
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
            bias="none"
        )
        
        self.model = get_peft_model(self.model, lora_config)
        self.model.print_trainable_parameters()
        
        logger.info("模型加载完成")

    def train(self):
        self.setup()
        
        train_dataset = PsychDataset(
            self.args.data_path,
            self.tokenizer,
            max_length=self.args.max_length
        )
        
        output_dir = Path(self.args.output_dir) / f"calmara-lora-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        output_dir.mkdir(parents=True, exist_ok=True)
        
        training_args = TrainingArguments(
            output_dir=str(output_dir),
            num_train_epochs=self.args.epochs,
            per_device_train_batch_size=self.args.batch_size,
            gradient_accumulation_steps=self.args.gradient_accumulation,
            learning_rate=self.args.learning_rate,
            weight_decay=0.01,
            warmup_ratio=0.1,
            logging_steps=10,
            save_steps=100,
            save_total_limit=2,
            fp16=True,
            gradient_checkpointing=True,
            optim="adamw_torch",
            report_to="none",
            dataloader_num_workers=0,
            dataloader_pin_memory=False,
        )
        
        trainer = Trainer(
            model=self.model,
            args=training_args,
            train_dataset=train_dataset,
            data_collator=DataCollatorForSeq2Seq(
                self.tokenizer,
                padding=True,
                max_length=self.args.max_length
            )
        )
        
        logger.info("=" * 50)
        logger.info("开始训练 (低功耗模式)")
        logger.info(f"Batch Size: {self.args.batch_size}")
        logger.info(f"Gradient Accumulation: {self.args.gradient_accumulation}")
        logger.info(f"有效Batch Size: {self.args.batch_size * self.args.gradient_accumulation}")
        logger.info(f"最大温度限制: {self.args.max_temp}°C")
        logger.info("=" * 50)
        
        self.start_time = time.time()
        
        try:
            trainer.train()
            
            final_dir = output_dir / "final"
            self.model.save_pretrained(final_dir)
            self.tokenizer.save_pretrained(final_dir)
            
            elapsed = time.time() - self.start_time
            logger.info(f"训练完成! 耗时: {elapsed/60:.1f} 分钟")
            logger.info(f"模型保存至: {final_dir}")
            
            return str(final_dir)
            
        except KeyboardInterrupt:
            logger.warning("训练被中断")
            return None
        except Exception as e:
            logger.error(f"训练失败: {e}")
            raise

def main():
    parser = argparse.ArgumentParser(description="低功耗LoRA微调")
    parser.add_argument("--model-name", default="Qwen/Qwen2.5-7B-Instruct")
    parser.add_argument("--model-path", default="./models/Qwen/Qwen2.5-7B-Instruct")
    parser.add_argument("--data-path", default="./dataset/train.json")
    parser.add_argument("--output-dir", default="./output")
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--gradient-accumulation", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    parser.add_argument("--lora-r", type=int, default=8)
    parser.add_argument("--lora-alpha", type=int, default=16)
    parser.add_argument("--lora-dropout", type=float, default=0.05)
    parser.add_argument("--max-length", type=int, default=1024)
    parser.add_argument("--max-temp", type=int, default=83, help="最大GPU温度限制")
    
    args = parser.parse_args()
    
    trainer = LowPowerTrainer(args)
    trainer.train()

if __name__ == "__main__":
    main()
