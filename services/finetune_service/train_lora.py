#!/usr/bin/env python3
"""
LoRA微调脚本 - 生产级实现
用于微调Qwen2.5-7B模型，适配心理咨询场景
支持Unsloth加速、多GPU训练、模型评估
"""
import os
import sys
import json
import argparse
import logging
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass, field
from datetime import datetime

import torch
from torch.utils.data import Dataset, DataLoader
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    Trainer,
    DataCollatorForSeq2Seq,
    EarlyStoppingCallback
)
from peft import (
    LoraConfig,
    get_peft_model,
    TaskType,
    PeftModel
)
from datasets import Dataset as HFDataset
import numpy as np
from loguru import logger

try:
    from unsloth import FastLanguageModel
    UNSLOTH_AVAILABLE = True
except ImportError:
    UNSLOTH_AVAILABLE = False
    logger.warning("Unsloth not available, using standard PEFT")

@dataclass
class TrainingConfig:
    base_model: str = "Qwen/Qwen2.5-7B-Instruct"
    output_dir: str = "./output"
    data_path: str = "./data/train.json"
    eval_data_path: Optional[str] = "./data/eval.json"
    
    lora_r: int = 16
    lora_alpha: int = 32
    lora_dropout: float = 0.05
    target_modules: List[str] = field(default_factory=lambda: [
        "q_proj", "k_proj", "v_proj", "o_proj",
        "gate_proj", "up_proj", "down_proj"
    ])
    
    num_train_epochs: int = 3
    per_device_train_batch_size: int = 4
    per_device_eval_batch_size: int = 4
    gradient_accumulation_steps: int = 4
    learning_rate: float = 2e-4
    weight_decay: float = 0.01
    warmup_ratio: float = 0.1
    max_grad_norm: float = 1.0
    
    max_seq_length: int = 2048
    packing: bool = False
    
    use_unsloth: bool = True
    use_4bit: bool = True
    use_double_quant: bool = True
    quant_type: str = "nf4"
    
    eval_steps: int = 100
    save_steps: int = 100
    logging_steps: int = 10
    save_total_limit: int = 3
    
    early_stopping_patience: int = 3
    early_stopping_threshold: float = 0.001
    
    merge_and_export: bool = True
    export_gguf: bool = True
    gguf_output_path: str = "./output/model.gguf"

class PsychDataset(Dataset):
    """
    心理咨询对话数据集
    支持多种数据格式
    """
    
    SYSTEM_PROMPT = """你是一个专业的校园心理咨询智能助手，具备以下能力：
1. 情绪识别：能够识别用户的情绪状态（正常、焦虑、低落、高风险）
2. 意图判断：判断用户意图是闲聊(CHAT)、心理咨询(CONSULT)还是高风险(RISK)
3. 专业咨询：提供温和、专业、安全的心理疏导建议
4. 危机干预：识别高风险情况并提供适当的干预建议

请根据用户的输入，提供专业、有同理心的回复。"""
    
    def __init__(
        self,
        data_path: str,
        tokenizer: Any,
        max_length: int = 2048,
        mode: str = "train"
    ):
        self.tokenizer = tokenizer
        self.max_length = max_length
        self.mode = mode
        self.data = self._load_data(data_path)
        
    def _load_data(self, path: str) -> List[Dict]:
        if not os.path.exists(path):
            logger.warning(f"Data file not found: {path}, using sample data")
            return self._get_sample_data()
        
        with open(path, 'r', encoding='utf-8') as f:
            if path.endswith('.jsonl'):
                data = [json.loads(line) for line in f if line.strip()]
            else:
                data = json.load(f)
        
        logger.info(f"Loaded {len(data)} samples from {path}")
        return data
    
    def _get_sample_data(self) -> List[Dict]:
        return [
            {
                "instruction": "我最近总是睡不着，心里很慌",
                "input": "",
                "output": "我理解你现在的困扰。失眠和焦虑常常是相互影响的。能告诉我这种情况持续多久了吗？有什么特别让你担心的事情吗？",
                "emotion": "焦虑",
                "intent": "CONSULT"
            },
            {
                "instruction": "我觉得活着没有意义",
                "input": "",
                "output": "我听到了你内心的痛苦，感谢你愿意和我分享。这种感受一定让你很难受。我想让你知道，你并不孤单。我建议你尽快和信任的人或专业心理咨询师聊聊，他们能给你更多支持。如果你愿意，我可以帮你找到合适的帮助资源。",
                "emotion": "高风险",
                "intent": "RISK"
            },
            {
                "instruction": "今天天气真好",
                "input": "",
                "output": "是的，好天气确实让人心情愉悦！有什么计划享受这个美好的天气吗？",
                "emotion": "正常",
                "intent": "CHAT"
            }
        ]
    
    def __len__(self) -> int:
        return len(self.data)
    
    def __getitem__(self, idx: int) -> Dict[str, torch.Tensor]:
        item = self.data[idx]
        
        instruction = item.get("instruction", "")
        input_text = item.get("input", "")
        output = item.get("output", "")
        
        if input_text:
            prompt = f"用户：{instruction}\n背景：{input_text}\n助手："
        else:
            prompt = f"用户：{instruction}\n助手："
        
        full_text = prompt + output
        
        encoding = self.tokenizer(
            full_text,
            max_length=self.max_length,
            padding="max_length",
            truncation=True,
            return_tensors="pt"
        )
        
        input_ids = encoding["input_ids"].squeeze()
        attention_mask = encoding["attention_mask"].squeeze()
        
        labels = input_ids.clone()
        
        prompt_ids = self.tokenizer(
            prompt,
            max_length=self.max_length,
            truncation=True,
            return_tensors="pt"
        )["input_ids"].squeeze()
        
        prompt_length = (prompt_ids != self.tokenizer.pad_token_id).sum().item()
        labels[:prompt_length] = -100
        
        return {
            "input_ids": input_ids,
            "attention_mask": attention_mask,
            "labels": labels
        }

class LoRATrainer:
    """
    LoRA微调训练器
    支持Unsloth加速和标准PEFT
    """
    
    def __init__(self, config: TrainingConfig):
        self.config = config
        self.model = None
        self.tokenizer = None
        self.peft_model = None
        
    def setup(self):
        logger.info("Setting up training environment...")
        
        if self.config.use_unsloth and UNSLOTH_AVAILABLE:
            self._setup_unsloth()
        else:
            self._setup_standard()
        
        logger.info("Setup complete")
    
    def _setup_unsloth(self):
        logger.info("Using Unsloth for accelerated training")
        
        self.model, self.tokenizer = FastLanguageModel.from_pretrained(
            model_name=self.config.base_model,
            max_seq_length=self.config.max_seq_length,
            dtype=torch.float16,
            load_in_4bit=self.config.use_4bit
        )
        
        self.model = FastLanguageModel.get_peft_model(
            self.model,
            r=self.config.lora_r,
            target_modules=self.config.target_modules,
            lora_alpha=self.config.lora_alpha,
            lora_dropout=self.config.lora_dropout,
            bias="none",
            use_gradient_checkpointing=True,
            random_state=42,
            use_rslora=True,
            loftq_config=None
        )
    
    def _setup_standard(self):
        logger.info("Using standard PEFT for training")
        
        self.tokenizer = AutoTokenizer.from_pretrained(
            self.config.base_model,
            trust_remote_code=True
        )
        
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token
        
        model_kwargs = {
            "trust_remote_code": True,
            "torch_dtype": torch.float16
        }
        
        if self.config.use_4bit:
            from transformers import BitsAndBytesConfig
            model_kwargs["quantization_config"] = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_quant_type=self.config.quant_type,
                bnb_4bit_use_double_quant=self.config.use_double_quant,
                bnb_4bit_compute_dtype=torch.float16
            )
        
        self.model = AutoModelForCausalLM.from_pretrained(
            self.config.base_model,
            **model_kwargs
        )
        
        lora_config = LoraConfig(
            task_type=TaskType.CAUSAL_LM,
            r=self.config.lora_r,
            lora_alpha=self.config.lora_alpha,
            lora_dropout=self.config.lora_dropout,
            target_modules=self.config.target_modules,
            bias="none"
        )
        
        self.model = get_peft_model(self.model, lora_config)
        self.model.print_trainable_parameters()
    
    def prepare_datasets(self) -> Tuple[Dataset, Optional[Dataset]]:
        train_dataset = PsychDataset(
            self.config.data_path,
            self.tokenizer,
            self.config.max_seq_length,
            mode="train"
        )
        
        eval_dataset = None
        if self.config.eval_data_path and os.path.exists(self.config.eval_data_path):
            eval_dataset = PsychDataset(
                self.config.eval_data_path,
                self.tokenizer,
                self.config.max_seq_length,
                mode="eval"
            )
        
        return train_dataset, eval_dataset
    
    def train(self):
        self.setup()
        
        train_dataset, eval_dataset = self.prepare_datasets()
        
        training_args = TrainingArguments(
            output_dir=self.config.output_dir,
            num_train_epochs=self.config.num_train_epochs,
            per_device_train_batch_size=self.config.per_device_train_batch_size,
            per_device_eval_batch_size=self.config.per_device_eval_batch_size,
            gradient_accumulation_steps=self.config.gradient_accumulation_steps,
            learning_rate=self.config.learning_rate,
            weight_decay=self.config.weight_decay,
            warmup_ratio=self.config.warmup_ratio,
            max_grad_norm=self.config.max_grad_norm,
            logging_steps=self.config.logging_steps,
            eval_steps=self.config.eval_steps if eval_dataset else None,
            save_steps=self.config.save_steps,
            save_total_limit=self.config.save_total_limit,
            load_best_model_at_end=True if eval_dataset else False,
            metric_for_best_model="eval_loss" if eval_dataset else None,
            greater_is_better=False,
            fp16=True,
            gradient_checkpointing=True,
            optim="adamw_torch",
            report_to="none",
            ddp_find_unused_parameters=False
        )
        
        trainer = Trainer(
            model=self.model,
            args=training_args,
            train_dataset=train_dataset,
            eval_dataset=eval_dataset,
            callbacks=[EarlyStoppingCallback(
                early_stopping_patience=self.config.early_stopping_patience,
                early_stopping_threshold=self.config.early_stopping_threshold
            )] if eval_dataset else []
        )
        
        logger.info("Starting training...")
        trainer.train()
        
        logger.info("Training complete!")
        
        self._save_model(trainer)
        
        if self.config.merge_and_export:
            self._merge_and_export()
        
        return trainer
    
    def _save_model(self, trainer: Trainer):
        output_path = os.path.join(self.config.output_dir, "lora_adapter")
        trainer.save_model(output_path)
        self.tokenizer.save_pretrained(output_path)
        logger.info(f"LoRA adapter saved to {output_path}")
    
    def _merge_and_export(self):
        logger.info("Merging LoRA weights with base model...")
        
        if self.config.use_unsloth and UNSLOTH_AVAILABLE:
            self.model.save_pretrained_merged(
                os.path.join(self.config.output_dir, "merged"),
                self.tokenizer,
                save_method="merged_16bit"
            )
        else:
            base_model = AutoModelForCausalLM.from_pretrained(
                self.config.base_model,
                torch_dtype=torch.float16,
                trust_remote_code=True
            )
            
            merged_model = PeftModel.from_pretrained(
                base_model,
                os.path.join(self.config.output_dir, "lora_adapter")
            )
            
            merged_model = merged_model.merge_and_unload()
            
            merged_path = os.path.join(self.config.output_dir, "merged")
            merged_model.save_pretrained(merged_path)
            self.tokenizer.save_pretrained(merged_path)
        
        logger.info("Model merged and exported")
        
        if self.config.export_gguf:
            self._export_gguf()
    
    def _export_gguf(self):
        logger.info("Exporting to GGUF format...")
        
        gguf_script = """
# GGUF export requires llama.cpp
# Run the following commands:
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp
make

# Convert to GGUF
python convert-hf-to-gguf.py {merged_path} --outfile {output_path} --outtype q4_k_m
"""
        logger.info(gguf_script.format(
            merged_path=os.path.join(self.config.output_dir, "merged"),
            output_path=self.config.gguf_output_path
        ))
        
        try:
            import subprocess
            merged_path = os.path.join(self.config.output_dir, "merged")
            
            result = subprocess.run(
                ["python", "-m", "llama_cpp.convert", merged_path, 
                 "--outfile", self.config.gguf_output_path,
                 "--outtype", "q4_k_m"],
                capture_output=True,
                text=True
            )
            
            if result.returncode == 0:
                logger.info(f"GGUF model exported to {self.config.gguf_output_path}")
            else:
                logger.warning(f"GGUF export failed: {result.stderr}")
        except Exception as e:
            logger.warning(f"GGUF export not available: {e}")

def evaluate_model(model_path: str, eval_data_path: str) -> Dict[str, float]:
    """
    评估微调后的模型
    """
    logger.info(f"Evaluating model: {model_path}")
    
    tokenizer = AutoTokenizer.from_pretrained(model_path, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        torch_dtype=torch.float16,
        device_map="auto",
        trust_remote_code=True
    )
    
    with open(eval_data_path, 'r', encoding='utf-8') as f:
        eval_data = json.load(f)
    
    correct_emotions = 0
    correct_intents = 0
    total = len(eval_data)
    
    for item in eval_data:
        prompt = f"用户：{item['instruction']}\n请判断用户情绪（正常/焦虑/低落/高风险）："
        
        inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
        outputs = model.generate(**inputs, max_new_tokens=20)
        response = tokenizer.decode(outputs[0], skip_special_tokens=True)
        
        expected_emotion = item.get("emotion", "")
        if expected_emotion in response:
            correct_emotions += 1
        
        expected_intent = item.get("intent", "")
        if expected_intent in response:
            correct_intents += 1
    
    emotion_accuracy = correct_emotions / total if total > 0 else 0
    intent_accuracy = correct_intents / total if total > 0 else 0
    
    logger.info(f"Emotion accuracy: {emotion_accuracy:.2%}")
    logger.info(f"Intent accuracy: {intent_accuracy:.2%}")
    
    return {
        "emotion_accuracy": emotion_accuracy,
        "intent_accuracy": intent_accuracy,
        "total_samples": total
    }

def main():
    parser = argparse.ArgumentParser(description="LoRA Fine-tuning for Qwen2.5-7B")
    parser.add_argument("--config", type=str, default=None, help="Path to config file")
    parser.add_argument("--data", type=str, default="./data/train.json", help="Training data path")
    parser.add_argument("--output", type=str, default="./output", help="Output directory")
    parser.add_argument("--epochs", type=int, default=3, help="Number of epochs")
    parser.add_argument("--lr", type=float, default=2e-4, help="Learning rate")
    parser.add_argument("--lora-r", type=int, default=16, help="LoRA rank")
    parser.add_argument("--eval", type=str, default=None, help="Evaluation data path")
    parser.add_argument("--merge", action="store_true", help="Merge and export model")
    
    args = parser.parse_args()
    
    config = TrainingConfig(
        data_path=args.data,
        output_dir=args.output,
        num_train_epochs=args.epochs,
        learning_rate=args.lr,
        lora_r=args.lora_r,
        eval_data_path=args.eval,
        merge_and_export=args.merge
    )
    
    if args.config:
        with open(args.config, 'r') as f:
            config_dict = json.load(f)
            for key, value in config_dict.items():
                if hasattr(config, key):
                    setattr(config, key, value)
    
    trainer = LoRATrainer(config)
    trainer.train()
    
    if args.eval:
        evaluate_model(
            os.path.join(args.output, "merged"),
            args.eval
        )

if __name__ == "__main__":
    main()
