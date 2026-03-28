#!/usr/bin/env python3
"""
训练数据准备脚本
用于处理心理对话数据，转换为微调格式
"""
import os
import json
import argparse
import re
from pathlib import Path
from typing import List, Dict, Any
from datetime import datetime
import random

from loguru import logger

class DataProcessor:
    """
    数据处理器
    支持多种数据格式的转换和清洗
    """
    
    EMOTION_LABELS = ["正常", "焦虑", "低落", "高风险"]
    INTENT_LABELS = ["CHAT", "CONSULT", "RISK"]
    
    def __init__(self, output_dir: str = "./data"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
    def process_psychqa(self, input_path: str) -> List[Dict]:
        """
        处理PsychQA数据集格式
        """
        logger.info(f"Processing PsychQA data from {input_path}")
        
        with open(input_path, 'r', encoding='utf-8') as f:
            raw_data = json.load(f)
        
        processed = []
        
        for item in raw_data:
            processed_item = {
                "instruction": item.get("question", item.get("input", "")),
                "input": item.get("context", ""),
                "output": item.get("answer", item.get("response", "")),
                "emotion": self._infer_emotion(item),
                "intent": self._infer_intent(item)
            }
            
            if processed_item["instruction"] and processed_item["output"]:
                processed.append(processed_item)
        
        logger.info(f"Processed {len(processed)} items from PsychQA")
        return processed
    
    def process_dialogue(self, input_path: str) -> List[Dict]:
        """
        处理对话格式数据
        """
        logger.info(f"Processing dialogue data from {input_path}")
        
        with open(input_path, 'r', encoding='utf-8') as f:
            raw_data = json.load(f)
        
        processed = []
        
        for dialogue in raw_data:
            if isinstance(dialogue, dict):
                turns = dialogue.get("turns", dialogue.get("conversations", []))
                
                for i in range(0, len(turns) - 1, 2):
                    if i + 1 < len(turns):
                        user_turn = turns[i]
                        assistant_turn = turns[i + 1]
                        
                        user_content = user_turn.get("content", user_turn.get("text", ""))
                        assistant_content = assistant_turn.get("content", assistant_turn.get("text", ""))
                        
                        if user_content and assistant_content:
                            processed.append({
                                "instruction": user_content,
                                "input": "",
                                "output": assistant_content,
                                "emotion": self._infer_emotion_from_text(user_content),
                                "intent": self._infer_intent_from_text(user_content)
                            })
        
        logger.info(f"Processed {len(processed)} dialogue turns")
        return processed
    
    def process_excel_export(self, input_path: str) -> List[Dict]:
        """
        处理从系统导出的Excel数据
        """
        import csv
        
        logger.info(f"Processing Excel export from {input_path}")
        
        processed = []
        
        with open(input_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            
            for row in reader:
                processed.append({
                    "instruction": row.get("content", row.get("对话内容", "")),
                    "input": "",
                    "output": row.get("response", row.get("AI回复", "")),
                    "emotion": row.get("emotion", row.get("情绪标签", "正常")),
                    "intent": row.get("intent", "CONSULT")
                })
        
        logger.info(f"Processed {len(processed)} items from Excel export")
        return processed
    
    def _infer_emotion(self, item: Dict) -> str:
        """
        从数据项推断情绪标签
        """
        if "emotion" in item:
            return item["emotion"]
        
        text = item.get("question", item.get("input", "")) + " " + item.get("answer", "")
        return self._infer_emotion_from_text(text)
    
    def _infer_intent(self, item: Dict) -> str:
        """
        从数据项推断意图标签
        """
        if "intent" in item:
            return item["intent"]
        
        text = item.get("question", item.get("input", ""))
        return self._infer_intent_from_text(text)
    
    def _infer_emotion_from_text(self, text: str) -> str:
        """
        从文本推断情绪
        """
        text = text.lower()
        
        high_risk_keywords = ["自杀", "自残", "不想活", "活着没意义", "绝望", "死"]
        for kw in high_risk_keywords:
            if kw in text:
                return "高风险"
        
        anxious_keywords = ["焦虑", "紧张", "担心", "睡不着", "心慌", "害怕", "恐慌"]
        for kw in anxious_keywords:
            if kw in text:
                return "焦虑"
        
        depressed_keywords = ["难过", "悲伤", "抑郁", "没意思", "空虚", "孤独", "绝望"]
        for kw in depressed_keywords:
            if kw in text:
                return "低落"
        
        return "正常"
    
    def _infer_intent_from_text(self, text: str) -> str:
        """
        从文本推断意图
        """
        text = text.lower()
        
        risk_keywords = ["自杀", "自残", "不想活", "活着没意义"]
        for kw in risk_keywords:
            if kw in text:
                return "RISK"
        
        consult_keywords = ["怎么办", "怎么处理", "帮我", "咨询", "心理", "情绪", "压力", "焦虑"]
        for kw in consult_keywords:
            if kw in text:
                return "CONSULT"
        
        return "CHAT"
    
    def clean_data(self, data: List[Dict]) -> List[Dict]:
        """
        数据清洗
        """
        cleaned = []
        
        for item in data:
            instruction = item.get("instruction", "").strip()
            output = item.get("output", "").strip()
            
            if not instruction or not output:
                continue
            
            if len(instruction) < 5 or len(output) < 10:
                continue
            
            instruction = self._remove_pii(instruction)
            output = self._remove_pii(output)
            
            item["instruction"] = instruction
            item["output"] = output
            cleaned.append(item)
        
        logger.info(f"Cleaned data: {len(data)} -> {len(cleaned)}")
        return cleaned
    
    def _remove_pii(self, text: str) -> str:
        """
        移除个人身份信息
        """
        text = re.sub(r'\b\d{11}\b', '[PHONE]', text)
        text = re.sub(r'\b[\w\.-]+@[\w\.-]+\.\w+\b', '[EMAIL]', text)
        text = re.sub(r'\b\d{17,18}[Xx]?\b', '[ID]', text)
        text = re.sub(r'学号[：:]\s*\d+', '学号：[STUDENT_ID]', text)
        
        return text
    
    def augment_data(self, data: List[Dict], factor: int = 2) -> List[Dict]:
        """
        数据增强
        """
        augmented = list(data)
        
        for item in data:
            for _ in range(factor - 1):
                new_item = item.copy()
                
                new_item["instruction"] = self._paraphrase(item["instruction"])
                
                augmented.append(new_item)
        
        logger.info(f"Augmented data: {len(data)} -> {len(augmented)}")
        return augmented
    
    def _paraphrase(self, text: str) -> str:
        """
        简单的改写增强
        """
        paraphrases = {
            "我": ["我", "本人", "自己"],
            "觉得": ["觉得", "感觉", "认为"],
            "很": ["很", "非常", "特别"],
            "怎么办": ["怎么办", "如何是好", "该怎么处理"],
            "帮我": ["帮我", "帮助我", "协助我"]
        }
        
        for original, replacements in paraphrases.items():
            if original in text:
                replacement = random.choice(replacements)
                text = text.replace(original, replacement, 1)
                break
        
        return text
    
    def split_data(self, data: List[Dict], train_ratio: float = 0.8) -> tuple:
        """
        划分训练集和验证集
        """
        random.shuffle(data)
        
        split_idx = int(len(data) * train_ratio)
        train_data = data[:split_idx]
        eval_data = data[split_idx:]
        
        return train_data, eval_data
    
    def save_data(self, data: List[Dict], filename: str):
        """
        保存数据
        """
        output_path = self.output_dir / filename
        
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        
        logger.info(f"Saved {len(data)} items to {output_path}")
    
    def generate_sample_data(self) -> List[Dict]:
        """
        生成示例数据
        """
        sample_data = [
            {
                "instruction": "我最近总是睡不着，心里很慌，学习压力很大",
                "input": "",
                "output": "我理解你现在的困扰。失眠和焦虑常常是相互影响的。能告诉我这种情况持续多久了吗？有什么特别让你担心的事情吗？我们可以一起探讨一些改善睡眠的方法。",
                "emotion": "焦虑",
                "intent": "CONSULT"
            },
            {
                "instruction": "我觉得活着没有意义，想结束这一切",
                "input": "",
                "output": "我听到了你内心的痛苦，感谢你愿意和我分享。这种感受一定让你很难受。我想让你知道，你并不孤单，你的生命很重要。我强烈建议你尽快和信任的人或专业心理咨询师聊聊。如果你愿意，我可以帮你找到合适的帮助资源。请记住，困难是暂时的，你值得被帮助。",
                "emotion": "高风险",
                "intent": "RISK"
            },
            {
                "instruction": "今天天气真好，心情也不错",
                "input": "",
                "output": "是的，好天气确实让人心情愉悦！有什么计划享受这个美好的天气吗？保持积极的心态对心理健康很有帮助。",
                "emotion": "正常",
                "intent": "CHAT"
            },
            {
                "instruction": "最近总是感到很疲惫，什么都不想做",
                "input": "",
                "output": "我理解你的感受。持续的疲惫感可能和很多因素有关，比如睡眠质量、压力水平或者情绪状态。能告诉我这种情况持续多久了吗？有没有什么特别的事情让你感到疲惫？",
                "emotion": "低落",
                "intent": "CONSULT"
            },
            {
                "instruction": "考试压力太大了，我怕考不好",
                "input": "",
                "output": "考试焦虑是很常见的情况。适度的紧张其实有助于发挥，但过度的焦虑会影响表现。可以试试深呼吸放松法，或者把大目标分解成小目标逐步完成。你平时有什么缓解压力的方法吗？",
                "emotion": "焦虑",
                "intent": "CONSULT"
            },
            {
                "instruction": "我和室友的关系很紧张，不知道怎么处理",
                "input": "",
                "output": "人际关系问题确实会带来很大压力。能具体说说是什么情况吗？通常沟通是解决问题的关键，但沟通的方式很重要。我们可以一起探讨一些有效的沟通策略。",
                "emotion": "焦虑",
                "intent": "CONSULT"
            },
            {
                "instruction": "我想找人聊聊，但又不知道该说什么",
                "input": "",
                "output": "能迈出这一步已经很勇敢了。有时候我们不需要准备什么特别的话题，就从当下的感受开始聊起就好。你现在感觉怎么样？有什么想说的都可以告诉我。",
                "emotion": "低落",
                "intent": "CONSULT"
            },
            {
                "instruction": "我害怕别人知道我的心理问题",
                "input": "",
                "output": "这种担心是可以理解的。心理健康问题和身体健康问题一样，都是需要关注和处理的。我们的对话是完全保密的，你可以放心地表达自己的想法。寻求帮助是勇敢和明智的选择。",
                "emotion": "焦虑",
                "intent": "CONSULT"
            }
        ]
        
        return sample_data

def main():
    parser = argparse.ArgumentParser(description="Prepare training data for LoRA fine-tuning")
    parser.add_argument("--input", type=str, help="Input data path")
    parser.add_argument("--output", type=str, default="./data", help="Output directory")
    parser.add_argument("--format", type=str, default="auto", 
                       choices=["auto", "psychqa", "dialogue", "excel"],
                       help="Input data format")
    parser.add_argument("--augment", type=int, default=1, help="Data augmentation factor")
    parser.add_argument("--train-ratio", type=float, default=0.8, help="Training data ratio")
    parser.add_argument("--generate-sample", action="store_true", help="Generate sample data")
    
    args = parser.parse_args()
    
    processor = DataProcessor(output_dir=args.output)
    
    if args.generate_sample or not args.input:
        logger.info("Generating sample data...")
        data = processor.generate_sample_data()
    else:
        if args.format == "auto":
            if "psychqa" in args.input.lower():
                data = processor.process_psychqa(args.input)
            elif "dialogue" in args.input.lower():
                data = processor.process_dialogue(args.input)
            elif args.input.endswith(".csv"):
                data = processor.process_excel_export(args.input)
            else:
                data = processor.process_psychqa(args.input)
        elif args.format == "psychqa":
            data = processor.process_psychqa(args.input)
        elif args.format == "dialogue":
            data = processor.process_dialogue(args.input)
        elif args.format == "excel":
            data = processor.process_excel_export(args.input)
    
    data = processor.clean_data(data)
    
    if args.augment > 1:
        data = processor.augment_data(data, args.augment)
    
    train_data, eval_data = processor.split_data(data, args.train_ratio)
    
    processor.save_data(train_data, "train.json")
    processor.save_data(eval_data, "eval.json")
    
    logger.info(f"Data preparation complete: {len(train_data)} train, {len(eval_data)} eval")

if __name__ == "__main__":
    main()
