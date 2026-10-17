"""
RAG 检索服务 - 智谱 Embedding API + ChromaDB
"""
import logging
import os

import httpx

from app.llm.circuit import CircuitBreaker
from app.rag.config import CHROMA_PERSIST_DIR, RELEVANCE_THRESHOLD, TOP_K

logger = logging.getLogger(__name__)

# embedding 外呼同受熔断（独立实例，不与 LLM 共享计数）
_embedding_breaker = CircuitBreaker("zhipu-embedding")

# 智谱 Embedding API
ZHIPU_API_KEY = os.environ.get("ZHIPU_API_KEY", "")
ZHIPU_EMBEDDING_URL = "https://open.bigmodel.cn/api/paas/v4/embeddings"
ZHIPU_EMBEDDING_MODEL = "embedding-3"

_chroma_client = None
_collection = None
_http_client = None


def _get_http_client() -> httpx.Client:
    global _http_client
    if _http_client is None:
        _http_client = httpx.Client(timeout=30.0)
    return _http_client


def _zhipu_embed(texts: list[str]) -> list[list[float]]:
    """调用智谱 Embedding API（受独立熔断实例保护——连续超时/5xx/429 开路后
    直接短路，RAG 静默降级，不影响主解析链路）。"""
    if not _embedding_breaker.allow_request():
        logger.warning("[zhipu-embedding] 熔断开路，跳过 embedding（RAG 降级）")
        raise RuntimeError("embedding circuit open")
    client = _get_http_client()
    all_embeddings = []
    try:
        for i in range(0, len(texts), 10):
            batch = texts[i:i + 10]
            resp = client.post(
                ZHIPU_EMBEDDING_URL,
                headers={"Authorization": f"Bearer {ZHIPU_API_KEY}", "Content-Type": "application/json"},
                json={"model": ZHIPU_EMBEDDING_MODEL, "input": batch},
            )
            resp.raise_for_status()
            data = resp.json()
            items = sorted(data["data"], key=lambda x: x["index"])
            all_embeddings.extend([item["embedding"] for item in items])
    except (httpx.TimeoutException, httpx.HTTPStatusError) as e:
        _embedding_breaker.record_failure(str(e))
        raise
    _embedding_breaker.record_success()
    return all_embeddings


# === ChromaDB EmbeddingFunction 接口（类方式，满足签名校验） ===

class ZhipuEmbeddingFunction:
    """ChromaDB 兼容的智谱 Embedding 函数"""

    def name(self) -> str:
        return "zhipu-embedding-3"

    def embed_documents(self, input: list[str]) -> list[list[float]]:
        if not input:
            return []
        return _zhipu_embed(input)

    def embed_query(self, input: list[str]) -> list[list[float]]:
        if not input:
            return []
        return _zhipu_embed(input)

    def __call__(self, input: list[str]) -> list[list[float]]:
        return self.embed_documents(input)


_zhipu_ef = ZhipuEmbeddingFunction()


def get_collection():
    global _chroma_client, _collection

    if _collection is not None:
        return _collection

    import chromadb

    os.makedirs(CHROMA_PERSIST_DIR, exist_ok=True)
    _chroma_client = chromadb.PersistentClient(path=CHROMA_PERSIST_DIR)
    _collection = _chroma_client.get_or_create_collection(
        name="ledgerpilot_knowledge",
        embedding_function=_zhipu_ef,
        metadata={"hnsw:space": "cosine"},
    )

    count = _collection.count()
    logger.info(f"ChromaDB 就绪: docs={count}, embedding=zhipu-embedding-3")
    return _collection


def search(user_input: str, top_k: int = TOP_K, categories: list[str] | None = None) -> list[dict]:
    """检索与用户输入相关的知识片段"""
    collection = get_collection()

    if collection.count() == 0:
        return []

    where_filter = None
    if categories:
        where_filter = {"category": {"$in": categories}}

    try:
        results = collection.query(
            query_texts=[user_input],
            n_results=top_k,
            where=where_filter,
        )

        if not results or not results["documents"] or not results["documents"][0]:
            return []

        items = []
        for i, doc in enumerate(results["documents"][0]):
            distance = results["distances"][0][i] if results["distances"] else 0
            relevance = 1 - distance

            if relevance < RELEVANCE_THRESHOLD:
                continue

            metadata = results["metadatas"][0][i] if results["metadatas"] else {}
            items.append({
                "content": doc,
                "source": metadata.get("source", "未知"),
                "category": metadata.get("category", ""),
                "heading": metadata.get("heading", ""),
                "relevance": round(relevance, 4),
            })

        logger.info(f"RAG 检索: input='{user_input[:30]}...', 命中={len(items)}/{top_k}")
        return items

    except Exception as e:
        logger.error(f"RAG 检索失败: {e}")
        return []


def build_rag_context(user_input: str) -> str:
    """构建注入到 Prompt 中的知识上下文"""
    results = search(user_input, top_k=3)

    if not results:
        return ""

    context_parts = ["## 参考知识（以下规定具有最高优先级，请据此生成凭证）\n"]

    for i, item in enumerate(results, 1):
        source_label = item.get("heading") or item.get("source", "未知来源")
        category_label = {
            "standard": "会计准则", "tax": "税法规定",
            "subject": "科目说明", "template": "分录模板",
            "tax_rate": "税率信息",
        }.get(item.get("category", ""), "参考资料")

        context_parts.append(
            f"### 知识片段 {i}（{category_label} - {source_label}）\n"
            f"{item['content']}\n"
        )

    context_parts.append(
        "### 重要提示\n"
        "- 请优先依据上面提供的参考知识生成凭证\n"
        "- 如果参考知识与示例有冲突，以参考知识为准\n"
        "- 如果参考知识不涉及当前场景，按通用会计规则处理\n"
    )

    return "\n".join(context_parts)
