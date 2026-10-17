"""密钥只走 env、默认值为空、enabled 跟随 key 是否存在。"""
from app.config.settings import Settings, settings


def test_default_api_keys_are_empty():
    s = Settings()
    providers = s.llm_providers()
    assert providers["deepseek"].api_key == ""
    assert providers["dashscope"].api_key == ""


def test_provider_disabled_without_key():
    s = Settings()
    providers = s.llm_providers()
    assert providers["deepseek"].enabled is False
    assert providers["dashscope"].enabled is False


def test_provider_enabled_with_key(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key-for-unit-test")
    s = Settings()
    providers = s.llm_providers()
    assert providers["deepseek"].enabled is True
    assert providers["dashscope"].enabled is False


def test_no_hardcoded_deepseek_enabled():
    """回归锁：旧实现 deepseek.enabled=True 硬编码，导致无 key 也尝试外呼。"""
    s = Settings()
    providers = s.llm_providers()
    assert providers["deepseek"].enabled == bool(providers["deepseek"].api_key)


def test_singleton_token_defaults_empty():
    assert settings.api_token == ""
