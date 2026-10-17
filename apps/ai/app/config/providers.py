"""
LedgerPilot AI Service - 提供商目录（配置外置）

提供商与 embedding 均声明于 env（YAML 目录为后续项，env 已覆盖全部字段）。
仅支持 OpenAI 兼容协议。PROVIDER_ORDER 运行时读取（类属性快照已废除）。

密钥三态：
- 未配置 key → 该提供商自动禁用，服务可启动（degraded，/readyz 明示）；
- 显式 `X_ENABLED=true` 而缺 key → 启动 fail-fast（列明项）；
- `LLM_REQUIRED=true` 且零可用提供商 → 拒绝启动（生产档）；
- embedding 同义：`EMBEDDING_ENABLED` 显式 true 而缺 ZHIPU_API_KEY → fail-fast。
"""
import os

from app.config.settings import Settings


class ProviderCatalogEntry:
    """提供商目录条目（env 字段：name/base_url/default_model/timeout/enabled/rps/api_key_env/max_tokens/别名）"""

    def __init__(self, name: str, base_url: str, default_model: str,
                 enabled: bool, timeout: int = 30, rps: float = 5.0,
                 max_tokens: int = 2000, aliases: list | None = None):
        self.name = name
        self.base_url = base_url
        self.default_model = default_model
        self.enabled = enabled
        self.timeout = timeout
        self.rps = rps
        self.max_tokens = max_tokens
        self.aliases = aliases or []

    def __repr__(self):  # 脱敏：绝不打印 api_key
        return (f"ProviderCatalogEntry(name={self.name!r}, enabled={self.enabled}, "
                f"model={self.default_model!r})")


_LLM_BASE = {
    "dashscope": ("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus"),
    "deepseek": ("https://api.deepseek.com/v1/chat/completions", "deepseek-chat"),
}


def build_provider_catalog(settings: Settings) -> dict:
    """构造提供商目录（含密钥三态语义）。enabled 语义：

    - key 存在 → enabled（默认开）；
    - key 缺失且未显式 `X_ENABLED` → 自动禁用（degraded 可启动）；
    - key 缺失但显式 `X_ENABLED=true` → 此处仍构造为 enabled=True，
      由 lifespan 校验 fail-fast（列明缺 key 项后拒绝启动）。
    """
    catalog = {}
    for name, (base_url, default_model) in _LLM_BASE.items():
        key = (settings.dashscope_api_key if name == "dashscope" else settings.deepseek_api_key)
        enabled_flag = os_get_bool(f"{name.upper()}_ENABLED")
        enabled = bool(key) or enabled_flag
        catalog[name] = ProviderCatalogEntry(
            name=name,
            base_url=base_url,
            default_model=default_model,
            enabled=enabled,
            timeout=int(os.getenv(f"{name.upper()}_TIMEOUT", "30")),
            rps=float(os.getenv(f"{name.upper()}_RPS", "5")),
            max_tokens=int(os.getenv(f"{name.upper()}_MAX_TOKENS", "2000")),
            aliases=[name],
        )
    return catalog


def embedding_entry(settings: Settings) -> dict:
    """embedding 提供商（智谱）目录条目 + 三态语义。"""
    key = os.getenv("ZHIPU_API_KEY", "")
    enabled_flag = os_get_bool("EMBEDDING_ENABLED")
    return {
        "name": "zhipu-embedding-3",
        "api_key_env": "ZHIPU_API_KEY",
        "has_key": bool(key),
        "enabled": bool(key) or enabled_flag,
        "enabled_explicit": enabled_flag,
    }


def os_get_bool(name: str) -> bool:
    return os.getenv(name, "").lower() == "true"


def validate_llm_config(settings: Settings) -> None:
    """启动期配置校验（在 app lifespan 内调用，非 import 期）：

    - 显式 enabled=true 却缺 key → RuntimeError（列明项）；
    - LLM_REQUIRED=true 且零可用提供商 → RuntimeError。
    """
    catalog = build_provider_catalog(settings)
    failures = []
    for name, entry in catalog.items():
        key_present = bool(settings.dashscope_api_key if name == "dashscope"
                           else settings.deepseek_api_key)
        if entry.enabled and not key_present:
            failures.append(f"{name}: {name.upper()}_ENABLED=true 但 {name.upper()}_API_KEY 缺失")
    if failures:
        raise RuntimeError("LLM 配置 fail-fast: " + "; ".join(failures))

    if os.getenv("LLM_REQUIRED", "").lower() == "true":
        if not any(e.enabled and (
                bool(settings.dashscope_api_key if n == "dashscope" else settings.deepseek_api_key))
                for n, e in catalog.items()):
            raise RuntimeError("LLM_REQUIRED=true 但零可用提供商（key 均缺失），拒绝启动")

    emb = embedding_entry(settings)
    if emb["enabled_explicit"] and not emb["has_key"]:
        raise RuntimeError("LLM 配置 fail-fast: EMBEDDING_ENABLED=true 但 ZHIPU_API_KEY 缺失")
