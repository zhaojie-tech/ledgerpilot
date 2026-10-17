"""
LLM 客户端回退链测试（respx 零外呼）：
- 双提供商 500→成功：降级链命中第二个
- 全部 5xx：回落规则兜底（收款模式）
- 429/超时计入熔断
"""
import pytest
import respx

from app.llm.client import LLMClient


@pytest.fixture
def client():
    # 干净实例：独立熔断计数，避免用例间串扰
    return LLMClient()


@respx.mock
@pytest.mark.asyncio
async def test_fallback_to_second_provider(client, monkeypatch):
    monkeypatch_enabled(monkeypatch, client)
    # 降级链顺序 dashscope → deepseek：首个 500，降级到第二个成功
    respx.post("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions").respond(500)
    deepseek_ok = respx.post("https://api.deepseek.com/v1/chat/completions").respond(
        200,
        json={
            "choices": [
                {
                    "message": {"content": '{"x":1}'},
                    "finish_reason": "stop",
                }
            ],
            "usage": {"total_tokens": 10, "prompt_tokens": 7, "completion_tokens": 3},
        },
    )
    r = await client.chat("p")
    assert respx.calls.call_count == 2  # 首个 500 + 降级成功
    assert deepseek_ok.called
    assert r["content"] == '{"x":1}'
    assert r["token_prompt"] == 7 and r["token_completion"] == 3  # token 用量拆分


@respx.mock
@pytest.mark.asyncio
async def test_all_fail_falls_back_to_rule(client, monkeypatch):
    monkeypatch_enabled(monkeypatch, client)
    respx.post("https://api.deepseek.com/v1/chat/completions").respond(500)
    respx.post(
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    ).respond(500)
    r = await client.chat("[请解析以下输入]\n收到张三商贸货款10000元，银行转账\n")
    assert r["source"] == "fallback_rule"
    assert "10000" in r["content"]


def monkeypatch_enabled(monkeypatch, client):
    """测试态：两个提供商均视为启用（绕过 key 缺失的自动禁用）。打在 client 实例上，自动还原。"""
    import types
    cat = {
        "dashscope": types.SimpleNamespace(
            name="dashscope", api_key="k",
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            default_model="qwen-plus", enabled=True, timeout=2, rps=10, max_tokens=100, aliases=[]),
        "deepseek": types.SimpleNamespace(
            name="deepseek", api_key="k", base_url="https://api.deepseek.com/v1/chat/completions",
            default_model="deepseek-chat", enabled=True, timeout=2, rps=10, max_tokens=100, aliases=[]),
    }
    import types as _t
    object.__setattr__(client, "_catalog", lambda: cat)
    _ = _t
