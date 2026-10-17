"""
LLM 熔断器

失败集合：
- **计入连续失败**：超时 / 5xx / 429（provider 不健康信号，可触发开路）；
- **一律不计**：4xx 其余（请求侧问题——配置类上报、请求类返回调用方）；
- **不影响计数**：坏 JSON / 越白名单（属解析层，不影响熔断）。

参数（键名固定）：`llm.circuit.failure_threshold`=5、`llm.circuit.open_seconds`=60、
半开放 1 探针；计数 per-provider（不按 model）；状态 per-process（多 worker 各自为政，文档明示）；
开路期跳过不耗重试预算；探测结果不复用 /readyz；embedding 外呼同受独立熔断实例。
"""
import logging
import os
import threading
import time
from enum import Enum

import httpx

from app.llm.errors import ConfigError, LengthLimitError

logger = logging.getLogger(__name__)

FAILURE_THRESHOLD = int(os.getenv("LLM_CIRCUIT_FAILURE_THRESHOLD", "5"))
OPEN_SECONDS = float(os.getenv("LLM_CIRCUIT_OPEN_SECONDS", "60"))


class BreakerState(Enum):
    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"


class CircuitBreaker:
    """per-provider 连续失败熔断。线程安全（单实例被 to_thread 工作线程并发触碰）。"""

    def __init__(self, name: str,
                 failure_threshold: int = FAILURE_THRESHOLD,
                 open_seconds: float = OPEN_SECONDS):
        self.name = name
        self.failure_threshold = failure_threshold
        self.open_seconds = open_seconds
        self._state = BreakerState.CLOSED
        self._consecutive_failures = 0
        self._opened_at: float = 0.0
        self._probe_in_flight = False
        self._lock = threading.Lock()

    @property
    def state(self) -> BreakerState:
        """只读视图：仅做时钟推进，不认领探针。"""
        with self._lock:
            self._advance_clock()
            return self._state

    def _advance_clock(self) -> None:
        if self._state == BreakerState.OPEN \
                and time.monotonic() - self._opened_at >= self.open_seconds:
            self._state = BreakerState.HALF_OPEN

    def allow_request(self) -> bool:
        """OPEN 拒绝；HALF_OPEN 用原子 check-and-set 只放行 1 个探针（E2 修复：
        半开并发下其余请求按 OPEN 拒绝，探针成败决定闭合/重开）。"""
        with self._lock:
            self._advance_clock()
            if self._state == BreakerState.OPEN:
                return False
            if self._state == BreakerState.HALF_OPEN:
                if self._probe_in_flight:
                    return False
                self._probe_in_flight = True
                logger.info("[%s] 熔断器半开（放行探针）", self.name)
                return True
            return True  # CLOSED

    def record_success(self) -> None:
        with self._lock:
            if self._state != BreakerState.CLOSED:
                logger.info("[%s] 探针成功，熔断器闭合", self.name)
            self._state = BreakerState.CLOSED
            self._consecutive_failures = 0
            self._probe_in_flight = False

    def record_failure(self, reason: str) -> None:
        with self._lock:
            self._consecutive_failures += 1
            if self._state == BreakerState.HALF_OPEN:
                self._probe_in_flight = False
                self._trip("半开探针失败")
                return
            if self._consecutive_failures >= self.failure_threshold:
                self._trip(f"连续失败 {self._consecutive_failures} 次: {reason}")

    def _trip(self, why: str) -> None:
        self._state = BreakerState.OPEN
        self._opened_at = time.monotonic()
        self._consecutive_failures = self.failure_threshold
        self._probe_in_flight = False
        logger.error("[%s] 熔断器打开（%s），%ss 后半开", self.name, why, self.open_seconds)


def classify_llm_failure(exc: Exception) -> str | None:
    """失败分类（固定）：返回 'breaker'（计数）/ 'config'（配置类，不计）/ None（请求侧 4xx，不计）。

    坏 JSON / 越白名单属解析层错误，调用方不应把此类异常传进来。
    """
    if isinstance(exc, ConfigError):
        return "config"
    if isinstance(exc, LengthLimitError):
        return None  # 请求侧：返回调用方，不计
    if isinstance(exc, httpx.TimeoutException):
        return "breaker"
    if isinstance(exc, httpx.HTTPStatusError):
        code = exc.response.status_code
        if code == 429 or code >= 500:
            return "breaker"
        return None  # 其余 4xx：请求侧
    return "breaker"  # 连接错误等未知网络异常视为 provider 不健康
