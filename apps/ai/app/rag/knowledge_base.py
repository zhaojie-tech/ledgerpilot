"""
知识库管理：导入文档到 ChromaDB
"""
import hashlib
import logging
import os
import uuid

from app.rag.chunker import chunk_by_line, chunk_markdown
from app.rag.config import CATEGORIES, KNOWLEDGE_BASE_DIR
from app.rag.service import get_collection

logger = logging.getLogger(__name__)


def import_file(filepath: str, category: str, chunk_fn=None) -> int:
    """
    导入单个知识文件到 ChromaDB

    Args:
        filepath: 文件路径
        category: 知识分类（standard/tax/subject/template/tax_rate）
        chunk_fn: 分块函数（默认按文件扩展名自动选择）

    Returns:
        导入的文档数量
    """
    if not os.path.exists(filepath):
        logger.error(f"文件不存在: {filepath}")
        return 0

    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    if not content.strip():
        logger.warning(f"文件内容为空: {filepath}")
        return 0

    # 自动选择分块策略
    if chunk_fn is None:
        ext = os.path.splitext(filepath)[1].lower()
        if ext in (".md",):
            chunk_fn = chunk_markdown
        else:
            chunk_fn = chunk_by_line

    chunks = chunk_fn(content)

    if not chunks:
        logger.warning(f"分块结果为空: {filepath}")
        return 0

    # 构建导入数据
    collection = get_collection()
    source_name = os.path.basename(filepath)
    revision = uuid.uuid4().hex[:8]  # 每次导入生成新片段 id，支撑 add-then-swap

    ids = []
    documents = []
    metadatas = []

    for i, chunk in enumerate(chunks):
        chunk_id = hashlib.md5(
            f"{source_name}:{revision}:{i}:{chunk['content'][:100]}".encode()
        ).hexdigest()[:16]

        ids.append(chunk_id)
        documents.append(chunk["content"])
        metadatas.append({
            "source": source_name,
            "category": category,
            "heading": chunk.get("heading", ""),
            "chunk_index": i,
        })

    # add-then-swap：先写入新片段，成功后再删除旧片段。
    # 导入失败（如嵌入外呼报错）时旧数据原样保留；仅当新片段落库后清理旧片段落空。
    old_ids = collection.get(where={"source": source_name}, include=[])["ids"]

    try:
        collection.add(ids=ids, documents=documents, metadatas=metadatas)
    except Exception as e:
        logger.error(f"导入失败（旧数据已保留）: {source_name}, 错误: {e}")
        return 0

    if old_ids:
        try:
            collection.delete(ids=old_ids)
        except Exception as e:
            logger.error(f"旧片段清理失败（同源将出现重复，重试导入可自愈）: {source_name}, 错误: {e}")

    logger.info(f"导入知识文件: {source_name}, 片段数={len(chunks)}, 分类={category}")
    return len(chunks)


def import_all() -> dict:
    """
    导入 knowledge/ 目录下所有知识文件

    Returns:
        {"total": int, "by_category": {...}}
    """
    stats = {"total": 0, "by_category": {}}

    if not os.path.exists(KNOWLEDGE_BASE_DIR):
        logger.warning(f"知识目录不存在: {KNOWLEDGE_BASE_DIR}")
        return stats

    # 目录名 → 分类映射
    dir_category_map = {
        "accounting_standards": "standard",
        "tax_regulations": "tax",
        "subject_templates": "subject",
        "tax_rates": "tax_rate",
    }

    # 独立文件映射（文件名包含关键词 → 分类）
    file_category_rules = {
        "分录模板": "template",
        "科目": "subject",
        "科目表": "subject",
        "税率": "tax_rate",
        "增值税": "tax",
        "所得税": "tax",
        "准则": "standard",
    }

    for category_dir, category in dir_category_map.items():
        dir_path = os.path.join(KNOWLEDGE_BASE_DIR, category_dir)
        if not os.path.isdir(dir_path):
            continue

        count = 0
        for filename in sorted(os.listdir(dir_path)):
            if not filename.endswith((".md", ".txt", ".csv")):
                continue

            filepath = os.path.join(dir_path, filename)
            n = import_file(filepath, category)
            count += n

        stats["by_category"][category] = count
        stats["total"] += count
        logger.info(f"导入目录 {category_dir}: {count} 条")

    # 扫描 knowledge/ 根目录下的独立文件
    for filename in sorted(os.listdir(KNOWLEDGE_BASE_DIR)):
        if filename.startswith(".") or os.path.isdir(os.path.join(KNOWLEDGE_BASE_DIR, filename)):
            continue
        if not filename.endswith((".md", ".txt")):
            continue

        # 根据文件名判断分类
        category = "template"  # 默认
        for keyword, cat in file_category_rules.items():
            if keyword in filename:
                category = cat
                break

        filepath = os.path.join(KNOWLEDGE_BASE_DIR, filename)
        n = import_file(filepath, category)
        stats["by_category"][category] = stats["by_category"].get(category, 0) + n
        stats["total"] += n

    logger.info(f"知识库导入完成: 总计 {stats['total']} 条, 分类统计: {stats['by_category']}")
    return stats


def get_stats() -> dict:
    """获取知识库统计"""
    collection = get_collection()
    total = collection.count()

    # 按分类统计
    category_stats = {}
    for cat in CATEGORIES:
        try:
            result = collection.get(where={"category": cat}, include=[])
            category_stats[cat] = len(result["ids"])
        except Exception:
            category_stats[cat] = 0

    return {"total": total, "by_category": category_stats}
