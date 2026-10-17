"""
corpus 录制回放测试（CI 零外呼）。

每档 corpus = 输入文本 + 录制的 LLM 原始响应 + 期望不变量。
回放路径：pipeline._parse_json → validator.validate → _build_biz_items，
即真实解析/校验/组装层，LLM 调用被录制响应替换——CI 全程零外呼。
"""
import json
from decimal import Decimal
from pathlib import Path

import pytest

from app.core.pipeline import NLPParsePipeline
from app.core.validator import ResultValidator

CORPUS_PATH = Path(__file__).resolve().parent.parent / "corpus" / "corpus_v2.json"


def load_corpus():
    return json.loads(CORPUS_PATH.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", load_corpus(), ids=[c["name"] for c in load_corpus()])
def test_corpus_case(case):
    pipeline = NLPParsePipeline.__new__(NLPParsePipeline)  # 不走构造（避免 LLMClient 依赖）
    validator = ResultValidator()

    expect = case["expect"]

    if expect.get("pipeline_error"):
        with pytest.raises(ValueError):
            pipeline._parse_json(case["llm_response"])
        return

    parsed = pipeline._parse_json(case["llm_response"])
    validation = validator.validate(parsed)
    items = pipeline._build_biz_items(parsed)

    assert len(items) == expect["items"], f"items 数不符: {len(items)}"
    assert validation.valid == expect["balanced"], f"errors={validation.errors}"
    if "level" in expect:
        assert validation.confidence_level == expect["level"]
    if "has_error_code" in expect:
        codes = [e.message.split(":")[0] for e in validation.errors]
        assert any(expect["has_error_code"] in c for c in codes), f"errors={validation.errors}"
    if "amount_strings" in expect:
        serialized = [str(e.amount) for item in items for e in item.entries]
        for want in expect["amount_strings"]:
            assert want in serialized, f"金额序列化缺失 {want}: {serialized}"
    if "subject_codes_contains" in expect:
        codes = [e.subject_code for item in items for e in item.entries]
        for want in expect["subject_codes_contains"]:
            assert want in codes

    # schema v2 全局不变量：金额全部为字符串型十进制且标度 2
    for item in items:
        for e in item.entries:
            assert isinstance(e.amount, Decimal)
            assert e.amount.as_tuple().exponent == -2, f"标度必须 2 位: {e.amount}"


def test_corpus_size_floor():
    """corpus ≥10 档"""
    assert len(load_corpus()) >= 10
