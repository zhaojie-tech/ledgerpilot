"""
健康检查拆分：
- /healthz：进程存活（liveness）——永远 200（进程活着即可），不做任何外呼；
- /readyz：依赖就绪（readiness）——LLM 提供商真实探活（/models 端点、3s 超时、
  非 2xx 一律 down），httpbin 兜底与「401 判 ok」已废除；探测结果不用于熔断。
"""
import logging

from fastapi import APIRouter

from app.config.settings import settings

logger = logging.getLogger(__name__)
router = APIRouter()


@router.get("/api/v1/healthz", summary="进程存活（liveness，无外呼）")
async def healthz():
    return {"status": "alive", "version": settings.service_version}


@router.get("/api/v1/readyz", summary="依赖就绪（readiness，真实探活）")
async def readyz():
    from app.llm.client import LLMClient
    llm_client = LLMClient()
    llm_status = await llm_client.health_check()
    ready = all(v in ("ok", "disabled") for v in llm_status.values())
    return {
        "ready": ready,
        "version": settings.service_version,
        "llm_providers": llm_status,
    }
