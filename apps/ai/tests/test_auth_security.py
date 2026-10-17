"""static token 鉴权（默认开启、本地豁免可配）与 CORS 白名单。"""
import httpx
from fastapi.testclient import TestClient

from app.config.settings import settings
from app.main import app, create_app


def _patch_get_stats(monkeypatch):
    monkeypatch.setattr("app.rag.knowledge_base.get_stats", lambda: {"total": 0, "by_category": {}})


def test_ping_exempt_from_auth(monkeypatch):
    monkeypatch.setattr(settings, "api_token", "secret-token")
    client = TestClient(app)
    assert client.get("/api/v1/ping").status_code == 200
    assert client.get("/api/v1/health").status_code == 200


def test_token_required_for_business_endpoints(monkeypatch):
    monkeypatch.setattr(settings, "api_token", "secret-token")
    _patch_get_stats(monkeypatch)
    client = TestClient(app)
    # 无 token / 错 token 一律 401（/knowledge/* 属业务端点，受保护）
    assert client.get("/api/v1/knowledge/stats").status_code == 401
    assert (
        client.get("/api/v1/knowledge/stats", headers={"X-API-Token": "wrong"}).status_code == 401
    )
    assert (
        client.get("/api/v1/knowledge/stats", headers={"X-API-Token": "secret-token"}).status_code
        == 200
    )


def test_open_without_token(monkeypatch):
    monkeypatch.setattr(settings, "api_token", "")
    _patch_get_stats(monkeypatch)
    client = TestClient(app)
    assert client.get("/api/v1/knowledge/stats").status_code == 200


async def test_local_exemption_when_enabled(monkeypatch):
    """auth_allow_local=true 时 127.0.0.1 请求免 token（httpx ASGITransport 来源即本机回环）。"""
    monkeypatch.setattr(settings, "api_token", "secret-token")
    monkeypatch.setattr(settings, "auth_allow_local", True)
    _patch_get_stats(monkeypatch)
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as ac:
        resp = await ac.get("/api/v1/knowledge/stats")
    assert resp.status_code == 200


async def test_remote_still_requires_token_when_local_exemption_on(monkeypatch):
    """本地豁免不得扩大到非回环来源：TestClient 来源 host=testclient，视作远程。"""
    monkeypatch.setattr(settings, "api_token", "secret-token")
    monkeypatch.setattr(settings, "auth_allow_local", True)
    client = TestClient(app)
    assert client.get("/api/v1/knowledge/stats").status_code == 401


def test_cors_whitelist_echoes_origin(monkeypatch):
    monkeypatch.setattr(settings, "cors_origins", ["https://app.example.com"])
    client = TestClient(create_app())
    resp = client.options(
        "/api/v1/parse",
        headers={
            "Origin": "https://app.example.com",
            "Access-Control-Request-Method": "POST",
        },
    )
    assert resp.headers.get("access-control-allow-origin") == "https://app.example.com"


def test_cors_default_no_headers():
    """CORS_ORIGINS 留空 = 不注册 CORS（服务端调用方不受影响）。"""
    client = TestClient(app)
    resp = client.get(
        "/api/v1/ping", headers={"Origin": "https://evil.example.com"}
    )
    assert "access-control-allow-origin" not in resp.headers


def test_cors_wildcard_forces_no_credentials(monkeypatch):
    """禁止 `*` + credentials 组合：来源含 * 时强制关闭 allow_credentials。"""
    monkeypatch.setattr(settings, "cors_origins", ["*"])
    monkeypatch.setattr(settings, "cors_allow_credentials", True)
    client = TestClient(create_app())
    resp = client.options(
        "/api/v1/parse",
        headers={"Origin": "https://anything.example.com", "Access-Control-Request-Method": "POST"},
    )
    assert resp.headers.get("access-control-allow-origin") == "*"
    assert resp.headers.get("access-control-allow-credentials") != "true"
