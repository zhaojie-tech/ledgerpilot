"""
知识库导入脚本

用法：python scripts/import_knowledge.py
"""
import sys
import os

# 将项目根目录加入 path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.rag.knowledge_base import import_all, get_stats


def main():
    print("=" * 60)
    print("LedgerPilot AI 知识库导入")
    print("=" * 60)

    print("\n开始导入知识文件...")
    stats = import_all()

    print(f"\n导入完成！")
    print(f"  总计: {stats['total']} 条知识片段")
    for cat, count in stats.get('by_category', {}).items():
        print(f"  - {cat}: {count} 条")

    print("\n验证知识库统计:")
    final_stats = get_stats()
    print(f"  总计: {final_stats['total']} 条")
    for cat, count in final_stats.get('by_category', {}).items():
        print(f"  - {cat}: {count} 条")

    print("\n✅ 知识库就绪")


if __name__ == "__main__":
    main()
