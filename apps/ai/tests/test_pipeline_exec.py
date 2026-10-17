"""
管线 execute 集成测试：
- 通过构造注入假 LLM/PromptBuilder，零外呼跑通全管线；
- 覆盖：happy path、LLM 异常降级、规则层无 key 路径、RAG 静默关闭、证据链填充。
"""
import json

import pytest

from app.api.schemas import ParseRequest
from app.core.pipeline import NLPParsePipeline


class FakeLLM:
    """可编程假 LLM：按序返回 chat 结果或抛异常"""

    def __init__(self, results):
        self.results = list(results)
        self.calls = []

    async def chat(self, prompt, model=None):
        self.calls.append({"model": model, "prompt_len": len(prompt)})
        r = self.results.pop(0)
        if isinstance(r, Exception):
            raise r
        return r


class FakePrompts:
    def build(self, **kwargs):
        return "PROMPT|" + kwargs.get("user_input", "")


def make_pipeline(llm):
    return NLPParsePipeline(llm_client=llm, prompt_builder=FakePrompts())


def make_request(text, subject_list=None):
    req = {"user_input": text, "tenant_id": 1, "request_id": "test-rid"}
    if subject_list is not None:
        req["context"] = {"subject_list": subject_list}
    return ParseRequest(**req)


VALID_LLM = {"content": json.dumps({
    "voucher_date": "2026-09-15",
    "items": [{
        "biz_type": "PAY", "summary": "购设备",
        "entries": [
            {"direction": "DEBIT", "subject_code": "1601", "subject_name": "固定资产",
             "amount": 25000, "summary": "购设备"},
            {"direction": "CREDIT", "subject_code": "1002", "subject_name": "银行存款",
             "amount": 25000, "summary": "购设备"},
        ],
    }],
}, ensure_ascii=False), "model": "deepseek-chat", "token_used": 100,
    "token_prompt": 80, "token_completion": 20, "source": "llm"}


@pytest.fixture
def no_rag(monkeypatch):
    """RAG 静默关闭（零外呼）"""
    monkeypatch.setattr("app.rag.service.build_rag_context", lambda *a, **k: "")


@pytest.fixture
def no_rate(monkeypatch):
    """费率注入关闭"""
    monkeypatch.setenv("RATE_FILE", "")


@pytest.mark.asyncio
async def test_happy_path(monkeypatch, no_rag, no_rate):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.delenv("DASHSCOPE_API_KEY", raising=False)
    pipe = make_pipeline(FakeLLM([VALID_LLM]))
    resp = await pipe.execute(make_request("购入一台设备支出25000元",
                                           subject_list=["1601 固定资产", "1002 银行存款"]))
    assert resp.schema_version == 2
    assert resp.validation.valid is True
    assert resp.evidence.origin == "llm"
    assert [str(e.amount) for it in resp.items for e in it.entries] == ["25000.00", "25000.00"]
    assert [e.subject_code for it in resp.items for e in it.entries] == ["1601", "1002"]
    # request_id 贯穿
    assert resp.request_id == "test-rid"


@pytest.mark.asyncio
async def test_llm_exception_returns_fail_not_500(monkeypatch, no_rag, no_rate):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.delenv("DASHSCOPE_API_KEY", raising=False)

    class Boom:
        async def chat(self, prompt, model=None):
            raise RuntimeError("all providers down")

    pipe = make_pipeline(Boom())
    resp = await pipe.execute(make_request("购入无形资产100元"))
    assert resp.confidence_level == "FAIL"
    assert resp.validation.valid is False
    assert resp.metadata.source == "error"
    assert resp.items == []


@pytest.mark.asyncio
async def test_f1_rule_layer_no_key(monkeypatch, no_rag, no_rate):
    """无可用提供商 → 规则层接管（报销场景），origin=fallback_rule"""
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.delenv("DASHSCOPE_API_KEY", raising=False)
    pipe = make_pipeline(FakeLLM([]))  # 不应被调用
    resp = await pipe.execute(make_request("报销差旅费350元"))
    assert resp.evidence.origin == "fallback_rule"
    assert resp.validation.valid is True
    assert [e.subject_code for it in resp.items for e in it.entries] == ["6603", "1001"]


@pytest.mark.asyncio
async def test_whitelist_violation_fatal(monkeypatch, no_rag, no_rate):
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    monkeypatch.delenv("DASHSCOPE_API_KEY", raising=False)
    bad = json.dumps({"voucher_date": None, "items": [{
        "biz_type": "OTHER", "summary": "x",
        "entries": [
            {"direction": "DEBIT", "subject_code": "9999", "subject_name": "幽灵", "amount": 100, "summary": "x"},
            {"direction": "CREDIT", "subject_code": "6001", "subject_name": "主营", "amount": 100, "summary": "x"},
        ]}]}, ensure_ascii=False)
    pipe = make_pipeline(FakeLLM([{"content": bad, "model": "m", "token_used": 0, "source": "llm"}]))
    resp = await pipe.execute(make_request("幽灵科目账", subject_list=["1002 银行存款", "6001 主营业务收入"]))
    assert resp.validation.valid is False
    assert any("越白名单" in e.message for e in resp.validation.errors)
