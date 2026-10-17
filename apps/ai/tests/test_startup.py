"""净克隆可启动——pysqlite3 缺失兜底、应用可导入、/ping 与 /health 可用。"""
from fastapi.testclient import TestClient

from app.main import app, create_app


def test_app_importable_and_ping_ok():
    """应用导入本身即验证 pysqlite3 兜底逻辑；/ping 不依赖任何密钥。"""
    client = TestClient(app)
    resp = client.get("/api/v1/ping")
    assert resp.status_code == 200
    assert "pong" in resp.json()["message"]


def test_health_liveness_stub():
    """/health 为 deprecated liveness 别名。"""
    client = TestClient(app)
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "healthy"


def test_healthz_liveness_no_providers():
    """/healthz 永远 200、不回显提供商细节。"""
    client = TestClient(app)
    resp = client.get("/api/v1/healthz")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "alive"
    assert "llm_providers" not in data


def test_health_reports_service_version():
    client = TestClient(app)
    resp = client.get("/api/v1/health")
    assert resp.json()["version"] == "0.1.0"


def test_create_app_factory_isolated():
    """工厂可反复构建独立应用（测试与嵌入场景依赖）。"""
    a, b = create_app(), create_app()
    assert TestClient(a).get("/api/v1/ping").status_code == 200
    assert TestClient(b).get("/api/v1/ping").status_code == 200
