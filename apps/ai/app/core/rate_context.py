"""
费率上下文 SPI

- 确定性上下文（社保/公积金/税率等）通过 SPI 注入 prompt；
- 内置 **file 实现**（开源用户用 YAML/JSON 文件即可用，无需任何后端）；
- 旧 HTTP 实现降级为适配器（可选内部依赖，需 LEDGERPILOT_INTERNAL_TOKEN）；
- tenant_id 改为可选（开源场景无租户概念时用默认上下文）。
"""
import json
import logging
import os
import threading
from abc import ABC, abstractmethod
from pathlib import Path

logger = logging.getLogger(__name__)


class RateContextProvider(ABC):
    """确定性上下文 SPI：返回可直接注入 prompt 的费率文本；无配置返回空串"""

    @abstractmethod
    def rate_hint(self, tenant_id: int | None) -> str:
        ...


class FileRateContextProvider(RateContextProvider):
    """file 实现：从 KNOWLEDGE_DIR 同级的 rates.json（或 RATE_FILE 指定路径）读取。

    文件格式（全部可选）：
    {
      "social": {"pension": {"personal": 0.08, "employer": 0.16}, ...},
      "housing": {"personal": 0.12, "employer": 0.12},
      "vat": {"small_3": 0.03},
      "corp_tax": {"standard_25": 0.25}
    }
    """

    def __init__(self, path: str):
        self.path = Path(path)
        self._lock = threading.Lock()
        self._data: dict = {}
        self._mtime: float | None = None

    def _load(self) -> dict:
        try:
            mtime = self.path.stat().st_mtime
        except OSError:
            return {}
        with self._lock:
            if mtime != self._mtime:
                try:
                    self._data = json.loads(self.path.read_text(encoding="utf-8"))
                    self._mtime = mtime
                    logger.info("费率文件加载: %s", self.path)
                except (OSError, ValueError) as e:
                    logger.error("费率文件解析失败（忽略）: %s, %s", self.path, e)
                    self._data = {}
            return self._data

    @staticmethod
    def _fmt_pct(v) -> str:
        return f"{float(v) * 100:.1f}%"

    def rate_hint(self, tenant_id: int | None) -> str:
        data = self._load()
        if not data:
            return ""
        lines = ["【当前费率】（本租户配置，如有具体金额优先使用用户提供的数字）"]
        label = {
            "pension": "养老保险", "medical": "医疗保险", "unemployment": "失业保险",
            "work_injury": "工伤保险", "maternity": "生育保险", "disability": "残疾人就业保障金",
        }
        for sub, vals in (data.get("social") or {}).items():
            lines.append(f"  {label.get(sub, sub)}：个人{self._fmt_pct(vals.get('personal', 0))} "
                         f"单位{self._fmt_pct(vals.get('employer', 0))}")
        housing = data.get("housing") or {}
        if housing:
            lines.append(f"  住房公积金：个人{self._fmt_pct(housing.get('personal', 0))} "
                         f"单位{self._fmt_pct(housing.get('employer', 0))}")
        vat_label = {"general_13": "一般纳税人13%", "general_9": "一般纳税人9%",
                     "general_6": "一般纳税人6%", "small_3": "小规模纳税人3%", "small_5": "小规模纳税人5%"}
        for sub, rate in (data.get("vat") or {}).items():
            lines.append(f"  {vat_label.get(sub, sub)}：税率{self._fmt_pct(rate)}")
        for sub, rate in (data.get("corp_tax") or {}).items():
            lines.append(f"  企业所得税[{sub}]：税率{self._fmt_pct(rate)}")
        return "\n".join(lines)


class HttpRateContextProvider(RateContextProvider):
    """HTTP 适配器（内部依赖降级实现）：调 Java 后端内部接口。

    安全：必须配置 LEDGERPILOT_INTERNAL_TOKEN，否则该适配器拒绝启用。
    阻塞调用（原实现即在 async 路径同步阻塞 5s）由调用方经 to_thread 包裹。
    """

    def __init__(self, base_url: str, internal_token: str):
        if not internal_token:
            raise ValueError("HttpRateContextProvider 需要 LEDGERPILOT_INTERNAL_TOKEN")
        self.base_url = base_url.rstrip("/")
        self.internal_token = internal_token
        self._cache: dict = {}
        self._lock = threading.Lock()

    def rate_hint(self, tenant_id: int | None) -> str:
        if tenant_id is None:
            return ""
        import time
        with self._lock:
            entry = self._cache.get(tenant_id)
            if entry and time.time() - entry[1] < 300:
                return entry[0]
        try:
            import httpx
            url = f"{self.base_url}/api/rateconfig/internal/list?tenantId={tenant_id}"
            with httpx.Client(timeout=5) as client:
                resp = client.get(url, headers={"X-Internal-Token": self.internal_token})
            if resp.status_code != 200:
                logger.warning("费率接口 HTTP %s", resp.status_code)
                return ""
            rates = resp.json().get("data", [])
        except Exception as e:
            logger.warning("费率接口异常（忽略）: %s", e)
            return ""
        hint = self._format_rates(rates)
        with self._lock:
            self._cache[tenant_id] = (hint, time.time())
        return hint

    @staticmethod
    def _format_rates(rates: list) -> str:
        label = {
            "pension": "养老保险", "medical": "医疗保险", "unemployment": "失业保险",
            "work_injury": "工伤保险", "maternity": "生育保险", "disability": "残疾人就业保障金",
        }
        lines = ["【当前费率】（本租户配置，如有具体金额优先使用用户提供的数字）"]
        for r in rates:
            if r.get("enabled") != 1:
                continue
            sub = r.get("subCategory", "")
            cat = r.get("category", "")
            if cat == "social":
                lines.append(f"  {label.get(sub, sub)}：个人{float(r.get('personalRate') or 0) * 100:.1f}% "
                             f"单位{float(r.get('employerRate') or 0) * 100:.1f}%")
            elif cat == "housing":
                lines.append(f"  住房公积金：个人{float(r.get('personalRate') or 0) * 100:.1f}% "
                             f"单位{float(r.get('employerRate') or 0) * 100:.1f}%")
            elif cat in ("vat", "corp_tax"):
                lines.append(f"  {cat}:{sub}：税率{float(r.get('fixedRate') or 0) * 100:.1f}%")
        return "\n".join(lines) if len(lines) > 1 else ""


def default_provider() -> RateContextProvider:
    """装配顺序：RATE_FILE（file 实现）> JAVA_BACKEND_URL（HTTP 适配器）> 空实现"""
    rate_file = os.getenv("RATE_FILE", "")
    if rate_file:
        return FileRateContextProvider(rate_file)
    java_url = os.getenv("JAVA_BACKEND_URL", "")
    token = os.getenv("LEDGERPILOT_INTERNAL_TOKEN", "")
    if java_url and token:
        return HttpRateContextProvider(java_url, token)
    logger.info("未配置 RATE_FILE / JAVA_BACKEND_URL：费率注入关闭（SPI 空实现）")
    return _NullProvider()


class _NullProvider(RateContextProvider):
    def rate_hint(self, tenant_id: int | None) -> str:
        return ""
