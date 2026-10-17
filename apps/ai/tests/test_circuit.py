"""
：熔断器与错误注入四类（行为断言）：
- 仅超时 / 5xx / 429 计入连续失败并可触发开路；
- 4xx 一律不计（请求侧）；坏 JSON/越白名单属解析层不影响计数；
- 400-拒绝 response_format = 配置类错误（ConfigError）不计；
- 开路期跳过不耗重试预算；半开放 1 探针。
"""
import httpx

from app.llm.circuit import BreakerState, CircuitBreaker, classify_llm_failure
from app.llm.errors import ConfigError, LengthLimitError


def timeout_exc():
    return httpx.TimeoutException("connect timeout")


def status_exc(code):
    return httpx.HTTPStatusError(f"HTTP {code}", request=httpx.Request("POST", "http://x"),
                                 response=httpx.Response(code))


class TestClassification:
    def test_timeout_counts(self):
        assert classify_llm_failure(timeout_exc()) == "breaker"

    def test_5xx_counts(self):
        assert classify_llm_failure(status_exc(502)) == "breaker"

    def test_429_counts(self):
        assert classify_llm_failure(status_exc(429)) == "breaker"

    def test_4xx_not_counted(self):
        assert classify_llm_failure(status_exc(401)) is None
        assert classify_llm_failure(status_exc(404)) is None

    def test_config_error_not_counted(self):
        assert classify_llm_failure(ConfigError("reject response_format")) == "config"

    def test_length_limit_not_counted(self):
        assert classify_llm_failure(LengthLimitError("length")) is None


class TestBreaker:
    def test_opens_after_threshold(self):
        b = CircuitBreaker("p", failure_threshold=3, open_seconds=60)
        for _ in range(3):
            b.record_failure("x")
        assert b.state == BreakerState.OPEN
        assert not b.allow_request()

    def test_success_resets(self):
        b = CircuitBreaker("p", failure_threshold=3, open_seconds=60)
        b.record_failure("x")
        b.record_failure("x")
        b.record_success()
        b.record_failure("x")
        b.record_failure("x")
        assert b.state == BreakerState.CLOSED  # 计数被成功清零

    def test_half_open_single_probe(self):
        b = CircuitBreaker("p", failure_threshold=1, open_seconds=0.01)
        b.record_failure("x")
        assert b.state == BreakerState.OPEN
        import time
        time.sleep(0.02)
        assert b.state == BreakerState.HALF_OPEN  # 放行 1 探针
        b.record_failure("probe")  # 探针失败 → 重新开路
        assert b.state == BreakerState.OPEN
        time.sleep(0.02)
        assert b.state == BreakerState.HALF_OPEN
        b.record_success()  # 探针成功 → 闭合
        assert b.state == BreakerState.CLOSED
