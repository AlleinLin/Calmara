import json
import requests
import sys
import time
from pathlib import Path
from datetime import datetime
import ijson

class VectorDBImporter:
    def __init__(self, api_base="http://localhost:8080"):
        self.api_base = api_base
        self.stats = {
            "total": 0,
            "success": 0,
            "failed": 0,
            "start_time": None,
            "end_time": None
        }
    
    def check_service(self):
        try:
            response = requests.get(f"{self.api_base}/api/admin/knowledge-base/stats", timeout=5)
            if response.status_code == 200:
                print(f"服务连接成功: {self.api_base}")
                return True
        except Exception as e:
            print(f"服务连接失败: {e}")
            return False
        return False
    
    def import_from_json_file(self, json_file, batch_size=100):
        print("=" * 60)
        print("向量数据库导入工具")
        print("=" * 60)
        print(f"数据文件: {json_file}")
        print(f"API地址: {self.api_base}")
        print(f"批处理大小: {batch_size}")
        print()
        
        if not self.check_service():
            print("错误: 无法连接到Calmara服务，请确保服务已启动")
            return False
        
        self.stats["start_time"] = datetime.now()
        
        file_size = Path(json_file).stat().st_size / 1024 / 1024
        print(f"文件大小: {file_size:.2f} MB")
        print()
        
        print("开始导入数据...")
        
        batch = []
        total_processed = 0
        
        with open(json_file, 'r', encoding='utf-8') as f:
            for doc in ijson.items(f, 'item'):
                total_processed += 1
                self.stats["total"] = total_processed
                
                batch.append({
                    "title": doc.get("title", ""),
                    "content": doc.get("content", "")
                })
                
                if len(batch) >= batch_size:
                    self._import_batch(batch)
                    batch = []
                    
                    if total_processed % 1000 == 0:
                        elapsed = (datetime.now() - self.stats["start_time"]).total_seconds()
                        rate = total_processed / elapsed if elapsed > 0 else 0
                        print(f"  进度: {total_processed:,} 条, 成功: {self.stats['success']:,}, "
                              f"失败: {self.stats['failed']}, 速率: {rate:.1f} 条/秒")
        
        if batch:
            self._import_batch(batch)
        
        self.stats["end_time"] = datetime.now()
        
        self._print_summary()
        
        return True
    
    def _import_batch(self, batch):
        for doc in batch:
            try:
                response = requests.post(
                    f"{self.api_base}/api/rag/knowledge/add",
                    json=doc,
                    timeout=30
                )
                
                if response.status_code == 200:
                    self.stats["success"] += 1
                else:
                    self.stats["failed"] += 1
                    
            except Exception as e:
                self.stats["failed"] += 1
    
    def import_via_admin_api(self, json_file):
        print("=" * 60)
        print("使用管理API批量导入")
        print("=" * 60)
        
        if not self.check_service():
            print("错误: 无法连接到Calmara服务")
            return False
        
        print("读取数据文件...")
        
        with open(json_file, 'r', encoding='utf-8') as f:
            data = f.read()
        
        print(f"数据大小: {len(data) / 1024 / 1024:.2f} MB")
        print("正在导入...")
        
        try:
            response = requests.post(
                f"{self.api_base}/api/admin/knowledge-base/import/json",
                data=data,
                headers={"Content-Type": "application/json"},
                timeout=300
            )
            
            if response.status_code == 200:
                result = response.json()
                count = result.get("data", 0)
                print(f"导入成功! 共导入 {count} 条记录")
                return True
            else:
                print(f"导入失败: {response.status_code}")
                print(response.text)
                return False
                
        except Exception as e:
            print(f"导入出错: {e}")
            return False
    
    def _print_summary(self):
        elapsed = (self.stats["end_time"] - self.stats["start_time"]).total_seconds()
        
        print("\n" + "=" * 60)
        print("导入完成")
        print("=" * 60)
        print(f"总记录数: {self.stats['total']:,}")
        print(f"成功导入: {self.stats['success']:,}")
        print(f"失败数量: {self.stats['failed']:,}")
        print(f"耗时: {elapsed:.1f} 秒")
        
        if elapsed > 0:
            rate = self.stats["success"] / elapsed
            print(f"平均速率: {rate:.1f} 条/秒")
        
        print("=" * 60)


def main():
    api_base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    json_file = sys.argv[2] if len(sys.argv) > 2 else "e:/项目AI/Calmara/knowledge-base/merged_knowledge_base.json"
    
    importer = VectorDBImporter(api_base)
    
    if Path(json_file).exists():
        importer.import_from_json_file(json_file)
    else:
        print(f"错误: 文件不存在 - {json_file}")


if __name__ == "__main__":
    main()
