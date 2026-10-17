"""
LedgerPilot AI Service - 配置管理（pydantic-settings）

- 全部配置来自环境变量（.env 经 load_dotenv 加载；模板见 .env.example）；
- 字段名与 v0.1 兼容（settings.xxx 属性不变）；
- 密钥纪律：任何 key 默认值为空串，仅走 env；
- 密钥三态（未配置禁用/显式启用缺 key 报错/LLM_REQUIRED 拒绝启动）见 app/config/providers.py。
"""
import os

from dotenv import load_dotenv
from pydantic import BaseModel, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

load_dotenv()


def _env_list(name: str) -> list:
    raw = os.getenv(name, "")
    return [item.strip() for item in raw.split(",") if item.strip()]


def _env_list_or_default(name: str, default: list) -> list:
    if name in os.environ:
        return _env_list(name)
    return list(default)


class LLMProviderConfig(BaseModel):
    """单个 LLM 提供商配置（仅 OpenAI 兼容协议）"""

    name: str
    api_key: str = ""
    base_url: str = ""
    default_model: str = ""
    enabled: bool = False
    timeout: int = 30


class Settings(BaseSettings):
    """全局配置（pydantic-settings：env 即配置源）"""

    model_config = SettingsConfigDict(env_file=None, extra="ignore")

    # 服务
    service_name: str = "LedgerPilot AI Service"
    service_version: str = "0.1.0"
    debug: bool = False

    # LLM 提供商（降级链路顺序；key 缺失 = 该提供商自动禁用）
    dashscope_api_key: str = ""
    deepseek_api_key: str = ""
    provider_order: list[str] = ["dashscope", "deepseek"]

    # API 鉴权（static token，请求头 X-API-Token）
    api_token: str = ""
    auth_allow_local: bool = False

    # CORS 白名单
    cors_origins: list[str] = []
    cors_allow_credentials: bool = False

    # options.model 白名单
    allowed_models: list[str] = ["deepseek-chat", "qwen-plus"]

    # Java 后端地址（可选内部依赖；空 = 关闭费率注入）
    java_backend_url: str = ""

    @field_validator("provider_order", "cors_origins", "allowed_models", mode="before")
    @classmethod
    def _split_csv(cls, v):
        if isinstance(v, str):
            return [item.strip() for item in v.split(",") if item.strip()]
        return v

    def llm_providers(self) -> dict:
        """提供商目录（属性构造，保证 enabled 始终跟随 key 存在性）"""
        return {
            "dashscope": LLMProviderConfig(
                name="dashscope",
                api_key=self.dashscope_api_key,
                base_url="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                default_model="qwen-plus",
                enabled=bool(self.dashscope_api_key),
                timeout=30,
            ),
            "deepseek": LLMProviderConfig(
                name="deepseek",
                api_key=self.deepseek_api_key,
                base_url="https://api.deepseek.com/v1/chat/completions",
                default_model="deepseek-chat",
                enabled=bool(self.deepseek_api_key),
                timeout=30,
            ),
        }


settings = Settings()
settings.cors_origins = _env_list_or_default("CORS_ORIGINS", [])
settings.allowed_models = _env_list_or_default("ALLOWED_MODELS", ["deepseek-chat", "qwen-plus"])
