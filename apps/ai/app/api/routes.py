"""
LedgerPilot AI Service - FastAPI 路由

鉴权说明：本路由全部挂载于 /api/v1 之下，应用级 static token 中间件（见 app/main.py）
对所有业务端点（含 /knowledge/*）强制校验 X-API-Token；/ping、/health 豁免。
"""
import asyncio
import logging
import sys
from datetime import datetime

from fastapi import APIRouter, HTTPException

from app.api.schemas import (
    BatchErrorDetail,
    BatchParseErrorItem,
    BatchParseRequest,
    BatchParseResponse,
    HealthResponse,
    ParseRequest,
    ParseResponse,
    PingResponse,
)
from app.config.settings import settings
from app.core.pipeline import NLPParsePipeline

logger = logging.getLogger(__name__)
router = APIRouter()

# 全局 pipeline 实例（懒加载；依赖注入点改造列入后续里程碑）
_pipeline: NLPParsePipeline | None = None

# /knowledge/import 串行锁：导入涉及分钟级嵌入计算，禁止并发导入互相踩踏
_knowledge_import_lock = asyncio.Lock()


def get_pipeline() -> NLPParsePipeline:
    global _pipeline
    if _pipeline is None:
        _pipeline = NLPParsePipeline()
    return _pipeline


def set_pipeline(pipeline: NLPParsePipeline) -> None:
    """DI 注入点（测试/嵌入场景用假管线替换真实管线）"""
    global _pipeline
    _pipeline = pipeline


def _ensure_model_allowed(model: str | None) -> None:
    """options.model 白名单校验：防止调用方指定任意模型烧 key。

    白名单来自 ALLOWED_MODELS（默认仅两个提供商的默认模型，显式置空 = 不限制）；
    请求传入提供商别名（如 deepseek）按别名解析后的目标模型判断。
    """
    if not model:
        return
    from app.llm.client import LLMClient
    resolved = LLMClient.MODEL_ALIASES.get(model, model)
    if settings.allowed_models and resolved not in settings.allowed_models:
        raise HTTPException(
            status_code=422,
            detail=f"model '{model}' 不在 ALLOWED_MODELS 白名单内: {settings.allowed_models}",
        )


@router.get("/ping", response_model=PingResponse, summary="连通性测试")
async def ping():
    """简单的连通性测试，验证 Python 服务可达"""
    return PingResponse(
        message="pong from LedgerPilot AI Service",
        timestamp=datetime.now().isoformat(),
    )


@router.post("/parse", response_model=ParseResponse, summary="单条 NLP 解析")
async def parse(request: ParseRequest):
    """
    将自然语言描述解析为结构化的会计凭证数据

    - **user_input**: 用户的自然语言输入（≤1000 字符）
    - **tenant_id**: 租户 ID
    - **context**: 租户科目上下文
    - **options.model**: 指定 LLM 模型（受 ALLOWED_MODELS 白名单约束）
    """
    _ensure_model_allowed(request.options.model)
    try:
        pipeline = get_pipeline()
        result = await pipeline.execute(request)
        logger.info(
            f"解析完成: request_id={result.request_id}, "
            f"confidence={result.confidence}, "
            f"level={result.confidence_level}, "
            f"items={len(result.items)}, "
            f"duration={result.metadata.duration_ms}ms"
        )
        return result
    except Exception as e:
        # 内部异常细节只进日志，对外仅错误码
        logger.error("解析异常: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail="PARSE_INTERNAL_ERROR")


@router.post("/parse/batch", response_model=BatchParseResponse, summary="批量 NLP 解析")
async def batch_parse(request: BatchParseRequest):
    """
    批量解析多条自然语言输入（并行处理）

    - **inputs**: 多条自然语言（最多 20 条，每条 ≤1000 字符）

    部分失败语义：单条异常不影响其他条目，失败项以
    `{"index": <下标>, "error": {"code", "message"}}` 占位返回（HTTP 仍为 200）。
    """
    # 批内共享同一 options，模型白名单在发起任何 LLM 调用前统一前置校验
    _ensure_model_allowed(request.options.model)
    pipeline = get_pipeline()

    async def parse_one(user_input: str) -> ParseResponse:
        req = ParseRequest(
            user_input=user_input,
            tenant_id=request.tenant_id,
            context=request.context,
            options=request.options,
        )
        return await pipeline.execute(req)

    tasks = [parse_one(inp) for inp in request.inputs]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    parsed_results = []
    for i, r in enumerate(results):
        if isinstance(r, Exception):
            logger.warning("批处理第 %d 条失败: %s: %s", i, type(r).__name__, r)
            parsed_results.append(BatchParseErrorItem(
                index=i,
                error=BatchErrorDetail(
                    code="PARSE_FAILED",
                    message=f"{type(r).__name__}: {r}",
                ),
            ))
        else:
            parsed_results.append(r)

    return BatchParseResponse(results=parsed_results)


@router.get("/knowledge/stats", summary="知识库统计")
async def knowledge_stats():
    """查看知识库状态"""
    try:
        from app.rag.knowledge_base import get_stats
        # chroma 统计含本地 IO，放线程执行避免阻塞事件循环
        return await asyncio.to_thread(get_stats)
    except Exception as e:
        # 内部细节只进日志，对外仅状态码
        logger.warning("知识库统计失败: %s", e)
        return {"total": 0, "error": "KNOWLEDGE_UNAVAILABLE", "by_category": {}}


@router.post("/knowledge/import", summary="导入知识库")
async def knowledge_import():
    """从配置的 KNOWLEDGE_DIR 导入所有知识文件（仅读该白名单目录，不受理任意路径）。

    导入在专用线程执行（嵌入调用分钟级，避免阻塞事件循环），
    并以进程内锁串行化，防止并发导入互踩。
    """
    from app.rag.knowledge_base import import_all
    async with _knowledge_import_lock:
        stats = await asyncio.to_thread(import_all)
    return stats


@router.post("/knowledge/search", summary="测试知识检索")
async def knowledge_search(body: dict = None):
    """测试 RAG 检索，输入查询语句，返回命中的知识片段"""
    try:
        from app.rag.service import search
        query = body.get("query", "") if body else ""
        if not query:
            return {"results": [], "message": "query is required"}
        # 检索内部含 embedding 外呼（最长 30s），放线程执行避免阻塞事件循环
        results = await asyncio.to_thread(search, query, 5)
        return {
            "query": query,
            "count": len(results),
            "results": results,
            "message": "ok"
        }
    except Exception as e:
        logger.warning("知识检索失败: %s", e)
        return {"results": [], "error": "SEARCH_FAILED"}


@router.get("/health", response_model=HealthResponse, summary="健康检查（已废弃别名，请用 /healthz 与 /readyz）")
async def health():
    """deprecated 别名：仅进程级状态（liveness 语义）。

    依赖探活已拆分至 /readyz。既有调用方不受影响。
    """
    return HealthResponse(
        status="healthy",
        version=settings.service_version,
        python_version=sys.version.split()[0],
        llm_providers={},
    )
