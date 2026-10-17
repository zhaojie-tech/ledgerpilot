"""options.model 白名单 与 user_input 限长（≤1000）。"""
from fastapi.testclient import TestClient

from app.api import routes as routes_mod
from app.api.schemas import ParseResponse
from app.config.settings import settings
from app.main import app


class _OkPipeline:
    async def execute(self, request):
        return ParseResponse(request_id="ok", original_input=request.user_input)


def test_model_not_allowed_rejected(monkeypatch):
    monkeypatch.setattr(routes_mod, "_pipeline", _OkPipeline())
    client = TestClient(app)
    resp = client.post(
        "/api/v1/parse",
        json={"user_input": "买打印机3500元", "tenant_id": 1, "options": {"model": "gpt-4"}},
    )
    assert resp.status_code == 422
    assert "白名单" in resp.json()["detail"]


def test_model_in_whitelist_passes(monkeypatch):
    monkeypatch.setattr(routes_mod, "_pipeline", _OkPipeline())
    client = TestClient(app)
    resp = client.post(
        "/api/v1/parse",
        json={"user_input": "买打印机3500元", "tenant_id": 1, "options": {"model": "deepseek-chat"}},
    )
    assert resp.status_code == 200


def test_model_alias_resolved_against_whitelist(monkeypatch):
    """别名 deepseek 解析为 deepseek-chat 后应在白名单内。"""
    monkeypatch.setattr(routes_mod, "_pipeline", _OkPipeline())
    client = TestClient(app)
    resp = client.post(
        "/api/v1/parse",
        json={"user_input": "买打印机3500元", "tenant_id": 1, "options": {"model": "deepseek"}},
    )
    assert resp.status_code == 200


def test_whitelist_can_be_disabled_explicitly(monkeypatch):
    """ALLOWED_MODELS 显式置空 = 不限制（自建 OpenAI 兼容端点场景）。"""
    monkeypatch.setattr(settings, "allowed_models", [])
    monkeypatch.setattr(routes_mod, "_pipeline", _OkPipeline())
    client = TestClient(app)
    resp = client.post(
        "/api/v1/parse",
        json={"user_input": "买打印机3500元", "tenant_id": 1, "options": {"model": "my-own-model"}},
    )
    assert resp.status_code == 200


def test_user_input_max_length_1000(monkeypatch):
    monkeypatch.setattr(routes_mod, "_pipeline", _OkPipeline())
    client = TestClient(app)
    ok = client.post("/api/v1/parse", json={"user_input": "收" * 1000, "tenant_id": 1})
    assert ok.status_code == 200
    too_long = client.post("/api/v1/parse", json={"user_input": "收" * 1001, "tenant_id": 1})
    assert too_long.status_code == 422


def test_batch_items_each_limited_to_1000():
    client = TestClient(app)
    resp = client.post(
        "/api/v1/parse/batch",
        json={"inputs": ["收" * 1001], "tenant_id": 1},
    )
    assert resp.status_code == 422
