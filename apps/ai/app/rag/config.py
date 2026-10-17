"""
RAG 配置
"""
import os

from dotenv import load_dotenv

load_dotenv()

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# ChromaDB 持久化目录（默认仓库内 data/，已被 .gitignore 排除；可用 CHROMA_PERSIST_DIR 覆盖）
CHROMA_PERSIST_DIR = os.getenv("CHROMA_PERSIST_DIR", os.path.join(_REPO_ROOT, "data", "chroma_db"))

# 检索参数
TOP_K = int(os.getenv("RAG_TOP_K", "5"))        # 检索返回条数
RELEVANCE_THRESHOLD = float(os.getenv("RAG_RELEVANCE_THRESHOLD", "0.3"))  # 相关性阈值

# 知识分类
CATEGORIES = [
    "standard",    # 会计准则
    "tax",         # 税法法规
    "subject",     # 科目说明
    "template",    # 分录模板
    "tax_rate",    # 税率表
    "history",     # 企业历史凭证（Phase 2）
]

# 知识文件目录（导入白名单）：只读本目录及其分类子目录，
# 导入接口不受理任何请求传入的路径——KNOWLEDGE_DIR 可将知识源指到仓库之外。
KNOWLEDGE_BASE_DIR = os.getenv("KNOWLEDGE_DIR", os.path.join(_REPO_ROOT, "knowledge"))
