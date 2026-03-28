import json
import os
from pathlib import Path

def load_json_to_knowledge_base(json_file, output_file=None):
    """
    将JSON知识库文件转换为系统可用的格式
    """
    with open(json_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"加载了 {len(data)} 个文档")
    
    for doc in data:
        print(f"  - {doc.get('title', 'Untitled')}")
    
    if output_file:
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"\n已保存到: {output_file}")
    
    return data

def merge_knowledge_bases(*json_files, output_file):
    """
    合并多个知识库文件
    """
    merged = []
    seen_ids = set()
    
    for json_file in json_files:
        print(f"合并: {json_file}")
        with open(json_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        for doc in data:
            doc_id = doc.get('id')
            if doc_id and doc_id not in seen_ids:
                merged.append(doc)
                seen_ids.add(doc_id)
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)
    
    print(f"\n合并完成，共 {len(merged)} 个文档")
    print(f"保存到: {output_file}")
    
    return merged

def validate_knowledge_base(json_file):
    """
    验证知识库格式
    """
    with open(json_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    valid = 0
    invalid = 0
    
    for doc in data:
        if doc.get('title') and doc.get('content'):
            valid += 1
        else:
            invalid += 1
            print(f"无效文档: {doc.get('id', 'Unknown')}")
    
    print(f"\n验证结果:")
    print(f"  有效: {valid}")
    print(f"  无效: {invalid}")
    
    return valid, invalid

def main():
    base_dir = Path("e:/项目AI/calmara/knowledge-base")
    
    print("=" * 60)
    print("知识库导入工具")
    print("=" * 60)
    
    print("\n1. 验证知识库格式...")
    validate_knowledge_base(base_dir / "chinese_psychology_knowledge.json")
    validate_knowledge_base(base_dir / "soulchat_examples.json")
    validate_knowledge_base(base_dir / "psychology_knowledge.json")
    
    print("\n2. 合并所有知识库...")
    merge_knowledge_bases(
        base_dir / "chinese_psychology_knowledge.json",
        base_dir / "soulchat_examples.json",
        base_dir / "psychology_knowledge.json",
        output_file=base_dir / "merged_knowledge_base.json"
    )
    
    print("\n3. 生成导入命令...")
    print("\n使用以下命令导入知识库到系统:")
    print("\n方法1: 通过API导入")
    print(f"curl -X POST http://localhost:8080/api/admin/knowledge-base/import/json \\")
    print(f"  -H 'Content-Type: application/json' \\")
    print(f"  -d @{base_dir / 'merged_knowledge_base.json'}")
    
    print("\n方法2: 通过管理后台上传文件")
    print(f"访问: http://localhost:8080/admin/knowledge-base")
    print(f"上传文件: {base_dir / 'merged_knowledge_base.json'}")
    
    print("\n" + "=" * 60)

if __name__ == "__main__":
    main()
