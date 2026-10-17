"""批处理部分失败——单条异常不拖垮整批，失败项为 index+error{code,message}。"""
from fastapi.testclient import TestClient

from app.api import routes as routes_mod
from app.api.schemas import ParseResponse
from app.main import app


class _FakePipeline:
    """按输入内容决定成败的假管线。"""

    def __init__(self, fail_keywords=("失败",)):
        self.fail_keywords = fail_keywords

    async def execute(self, request):
        for kw in self.fail_keywords:
            if kw in request.user_input:
                raise RuntimeError(f"模拟单条崩溃: {request.user_input}")
        return ParseResponse(request_id="ok", original_input=request.user_input)


def _post_batch(client, inputs, options=None):
    payload = {"inputs": inputs, "tenant_id": 1}
    if options:
        payload["options"] = options
    return client.post("/api/v1/parse/batch", json=payload)


def test_batch_partial_failure_returns_per_item_errors(monkeypatch):
    monkeypatch.setattr(routes_mod, "_pipeline", _FakePipeline())
    client = TestClient(app)

    resp = _post_batch(client, ["收到货款10000元", "这条会失败", "再买打印机3500元"])
    assert resp.status_code == 200  # 不再整批 500

    data = resp.json()["results"]
    assert len(data) == 3
    # 成功项原样返回
    assert data[0]["request_id"] == "ok"
    assert data[0]["original_input"] == "收到货款10000元"
    # 失败项 = index + error{code,message}，原因不再丢失
    assert data[1]["index"] == 1
    assert data[1]["error"]["code"] == "PARSE_FAILED"
    assert "模拟单条崩溃" in data[1]["error"]["message"]
    # 其余条目继续完成
    assert data[2]["request_id"] == "ok"


def test_batch_all_success_keeps_parse_responses(monkeypatch):
    monkeypatch.setattr(routes_mod, "_pipeline", _FakePipeline())
    client = TestClient(app)
    resp = _post_batch(client, ["收到货款10000元", "收到货款2000元"])
    assert resp.status_code == 200
    data = resp.json()["results"]
    assert [r["request_id"] for r in data] == ["ok", "ok"]
    assert all("index" not in r and "error" not in r for r in data)


def test_batch_model_not_allowed_fails_fast(monkeypatch):
    monkeypatch.setattr(routes_mod, "_pipeline", _FakePipeline())
    client = TestClient(app)
    resp = _post_batch(client, ["收到货款10000元"], options={"model": "gpt-4-turbo"})
    assert resp.status_code == 422
    assert "白名单" in resp.json()["detail"]


def test_no_legacy_pydantic_v1_api_in_routes():
    """回归锁：旧实现 `__fields__[...].type_`（Pydantic v1 API）在 v2 下直接崩溃。"""
    import inspect

    from app.api import routes
    src = inspect.getsource(routes)
    assert "__fields__" not in src
    assert ".type_" not in src
