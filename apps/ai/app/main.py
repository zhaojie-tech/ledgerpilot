"""
LedgerPilot AI Service - FastAPI 应用入口
"""
import hmac
import logging
import sys
from contextlib import asynccontextmanager

try:
    import pysqlite3  # noqa: F401 可选依赖：系统 sqlite3 < 3.35 时由 pysqlite3-binary 提供
    sys.modules["sqlite3"] = sys.modules.pop("pysqlite3")
except ImportError:
    pass  # 系统自带 sqlite3 满足 chromadb 要求时直接用标准库

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse

from app.api.health import router as health_router
from app.api.routes import router as api_router
from app.config.providers import validate_llm_config
from app.config.settings import settings

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

# 鉴权豁免路径（连通性/健康/文档）。业务端点（含 /knowledge/*）一律受 token 保护
_AUTH_EXEMPT_PATHS = {
    "/ping", "/health", "/docs", "/redoc", "/openapi.json",
    "/api/v1/ping", "/api/v1/health",
}


@asynccontextmanager
async def lifespan(app: FastAPI):
    # L1：配置校验在 lifespan 内执行（不在 import 期）——显式 enabled 缺 key、
    # LLM_REQUIRED 零可用、embedding 显式缺 key → 拒绝启动（列明项）
    validate_llm_config(settings)
    logger.info("LedgerPilot AI Service v%s 启动（lifespan 配置校验通过）", settings.service_version)
    yield
    logger.info("LedgerPilot AI Service 关闭")


def create_app() -> FastAPI:
    """构建应用（独立成工厂便于按不同配置组装与测试）"""
    app = FastAPI(
        lifespan=lifespan,
        title="LedgerPilot AI Service",
        description="自然语言 → 结构化会计凭证 JSON 的解析服务。输出为草稿凭证，须经会计复核后使用。",
        version=settings.service_version,
        docs_url="/docs",
        redoc_url="/redoc",
    )
    app.include_router(health_router)

    @app.get("/", include_in_schema=False)
    async def root_index():
        return HTMLResponse("""<!doctype html>
<html lang="zh"><head><meta charset="utf-8"><title>LedgerPilot AI Service</title>
<style>body{font-family:system-ui;max-width:640px;margin:48px auto;color:#222}
a{color:#0a66c2}code{background:#f4f4f4;padding:2px 6px;border-radius:4px}
li{margin:6px 0}</style></head><body>
<h1>LedgerPilot AI Service <small>v""" + settings.service_version + """</small></h1>
<p>自然语言 → 结构化会计凭证 JSON 的解析服务（草稿凭证，须经会计复核后使用）。</p>
<ul>
  <li>📘 <a href="/docs">Swagger UI（/docs）</a> — 全部端点在线调试</li>
  <li>📗 <a href="/redoc">ReDoc（/redoc）</a></li>
  <li>💓 <a href="/api/v1/healthz">/api/v1/healthz</a>（存活）· <a href="/api/v1/readyz">/api/v1/readyz</a>（就绪）</li>
  <li> pong：<code>curl http://host:8000/api/v1/ping</code></li>
</ul>
<p><b>鉴权</b>：业务端点需请求头 <code>X-API-Token</code>（值为部署方设置的
<code>LEDGERPILOT_API_TOKEN</code>）；/ping、/healthz、/docs 豁免。</p>
<p>配套服务：apps/backend（Java 凭证引擎，本仓库 apps/backend）</p>
</body></html>""")

    # CORS：白名单模式。CORS_ORIGINS 留空 = 不注册（服务端调用方不受影响）。
    # 浏览器安全约束：来源含 * 时强制关闭 allow_credentials，禁止 `*` + credentials 组合。
    if settings.cors_origins:
        allow_all = "*" in settings.cors_origins
        allow_credentials = settings.cors_allow_credentials and not allow_all
        if settings.cors_allow_credentials and allow_all:
            logger.warning("CORS_ORIGINS 含 * ，已强制 allow_credentials=False")
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origins,
            allow_credentials=allow_credentials,
            allow_methods=["GET", "POST"],
            allow_headers=["Authorization", "Content-Type", "X-API-Token"],
        )

    # Static token 鉴权：LEDGERPILOT_API_TOKEN 非空即对所有非豁免路径强制校验
    @app.middleware("http")
    async def static_token_auth(request: Request, call_next):
        if settings.api_token:
            path = request.url.path.rstrip("/") or "/"
            exempt = path in _AUTH_EXEMPT_PATHS
            if not exempt and settings.auth_allow_local:
                client_host = request.client.host if request.client else ""
                exempt = client_host in ("127.0.0.1", "::1")
            if not exempt:
                provided = request.headers.get("X-API-Token", "")
                if not provided or not hmac.compare_digest(provided, settings.api_token):
                    return JSONResponse(
                        status_code=401,
                        content={"detail": "missing or invalid API token"},
                    )
        return await call_next(request)

    # 注册路由
    app.include_router(api_router, prefix="/api/v1")

    return app


app = create_app()
