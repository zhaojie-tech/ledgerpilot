"""
LedgerPilot AI Service - LLM 调用客户端（含降级链路）
"""
import asyncio
import json
import logging
import os
import re

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app.config.settings import settings
from app.llm.circuit import CircuitBreaker, classify_llm_failure
from app.llm.errors import ConfigError, LengthLimitError

logger = logging.getLogger(__name__)

# 全局并发闸（llm.limits.global_concurrency，默认 8）
_GLOBAL_SEMAPHORE = __import__("asyncio").Semaphore(int(os.getenv("LLM_GLOBAL_CONCURRENCY", "8")))


class LLMClient:
    """
    LLM 调用客户端

    降级链路: provider_order（运行时读取，L2）→ 规则模板兜底
    """

    @property
    def PROVIDER_ORDER(self):
        return settings.provider_order

    # per-provider 熔断器（计数 per-provider 不按 model；状态 per-process）
    def __init__(self):
        self._breakers: dict = {}

    def _catalog(self) -> dict:
        """提供商目录（实例方法，便于测试替换）"""
        return settings.llm_providers()

    def _breaker(self, name: str) -> CircuitBreaker:
        if name not in self._breakers:
            self._breakers[name] = CircuitBreaker(name)
        return self._breakers[name]

    # 模型名别名映射（公开常量：/parse 的 options.model 白名单校验同样按别名解析后判断）
    MODEL_ALIASES = {
        "deepseek": "deepseek-chat",
        "dashscope": "qwen-plus",
    }

    def _resolve_model(self, model: str | None, default_model: str) -> str:
        """解析模型名，支持别名"""
        if not model:
            return default_model
        return self.MODEL_ALIASES.get(model, model)

    @retry(stop=stop_after_attempt(2), wait=wait_exponential(multiplier=0.5, max=2))
    async def chat(self, prompt: str, model: str | None = None) -> dict:
        """
        调用 LLM，自动降级

        Returns:
            {
                "content": str,      # LLM 输出文本
                "model": str,        # 实际使用的模型
                "token_used": int,   # Token 消耗
                "source": str,       # llm / fallback_rule
            }
        """
        last_error = None

        for provider_name in self.PROVIDER_ORDER:
            provider = self._catalog().get(provider_name)
            if not provider or not provider.enabled:
                logger.debug(f"跳过未启用的提供商: {provider_name}")
                continue

            breaker = self._breaker(provider_name)
            if not breaker.allow_request():
                # 开路期跳过该提供商，不耗本请求的重试预算
                logger.warning("[%s] 熔断开路，跳过", provider_name)
                continue

            try:
                async with _GLOBAL_SEMAPHORE:
                    result = await self._call_provider(
                        provider_name, provider, prompt, model
                    )
                breaker.record_success()
                logger.info(
                    f"LLM 调用成功: provider={provider_name}, "
                    f"model={result['model']}, tokens={result['token_used']}"
                )
                return result

            except Exception as e:
                cls = classify_llm_failure(e)
                if cls == "breaker":
                    breaker.record_failure(str(e))
                elif cls == "config":
                    logger.error("[%s] 配置类错误（不计熔断，需运维介入）: %s", provider_name, e)
                else:
                    logger.warning("[%s] 请求侧错误（不计熔断）: %s", provider_name, e)
                last_error = e
                logger.warning(
                    f"LLM 提供商 {provider_name} 调用失败: {e}"
                )
                continue

        # 所有 LLM 失败 → 规则兜底
        fallback = self._rule_based_fallback(prompt)
        if fallback:
            logger.info("使用规则模板兜底")
            return {
                "content": fallback,
                "model": "rule_engine",
                "token_used": 0,
                "source": "fallback_rule",
            }

        raise Exception(f"所有 LLM 提供商均不可用: {last_error}")

    async def _call_provider(
        self, name: str, provider, prompt: str, model: str | None
    ) -> dict:
        """调用单个 LLM 提供商"""
        headers = {
            "Authorization": f"Bearer {provider.api_key}",
            "Content-Type": "application/json",
        }

        resolved_model = self._resolve_model(model, provider.default_model)

        payload = {
            "model": resolved_model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.1,
            "max_tokens": 2000,
        }

        # 用 asyncio.to_thread 在线程中执行同步 HTTP 调用，避免 uvicorn event loop 兼容性问题
        try:
            resp = await asyncio.to_thread(self._sync_call, provider.base_url, headers, payload, provider.timeout)
        except httpx.HTTPStatusError as e:
            logger.error(f"LLM {name} HTTP 错误: {e.response.status_code} - {e.response.text[:300]}")
            raise
        except Exception as e:
            logger.error(f"LLM {name} to_thread 异常: {type(e).__name__}: {e}")
            raise

        content = resp["choices"][0]["message"]["content"]
        usage = resp.get("usage", {})
        # token 拆 prompt/completion
        token_used = usage.get("total_tokens", 0)

        return {
            "content": content,
            "model": resolved_model,
            "token_used": token_used,
            "token_prompt": usage.get("prompt_tokens", 0),
            "token_completion": usage.get("completion_tokens", 0),
            "source": "llm",
        }

    def _sync_call(self, url, headers, payload, timeout):
        """同步 HTTP 调用（在线程中执行）。

        payload 携带 response_format=json_object；提供商拒绝该参数的 400
        归类为「配置类错误」（ConfigError）， 熔断明确不对其计数。
        finish_reason=length 单列一类（提示提高 max_tokens），不与解析失败混淆。
        """
        with httpx.Client(timeout=timeout) as client:
            response = client.post(url, headers=headers, json=payload)
            if response.status_code == 400 and "response_format" in response.text:
                raise ConfigError(f"提供商拒绝 response_format（配置类错误）: {response.text[:200]}")
            if response.status_code != 200:
                # 响应体降 DEBUG 且截断
                logger.debug("LLM sync 返回 %s: %s", response.status_code, response.text[:300])
            response.raise_for_status()
            data = response.json()
            choice = (data.get("choices") or [{}])[0]
            finish = choice.get("finish_reason")
            if finish == "length":
                raise LengthLimitError("finish_reason=length：输出被 max_tokens 截断，请提高上限")
            return data

    def _rule_based_fallback(self, prompt: str) -> str | None:
        """基于规则模板的兜底（仅处理高频简单模式）"""
        # 从 prompt 中提取用户输入部分
        m = re.search(r'\[请解析以下输入\]\s*(.+?)\n', prompt)
        if not m:
            return None

        user_input = m.group(1).strip()

        # 只处理包含"收到...货款...金额"的简单收款场景
        receive_match = re.search(
            r'收到.{0,20}?货款.{0,5}?(\d+(?:\.\d+)?)元?',
            user_input
        )
        if receive_match:
            amount = receive_match.group(1)
            from datetime import date
            today = date.today().isoformat()
            return json.dumps({
                "voucher_date": today,
                "items": [{
                    "biz_type": "RECEIVE",
                    "summary": "收款",
                    "payment_method": "银行转账",
                    "entries": [
                        {"direction": "DEBIT", "subject_code": "1002",
                         "subject_name": "银行存款", "amount": float(amount),
                         "summary": "收款"},
                        {"direction": "CREDIT", "subject_code": "1122",
                         "subject_name": "应收账款", "amount": float(amount),
                         "summary": "收款"},
                    ]
                }]
            }, ensure_ascii=False)

        return None

    async def health_check(self) -> dict:
        """检查各提供商健康状态"""
        result = {}
        for provider_name in self.PROVIDER_ORDER:
            provider = self._catalog().get(provider_name)
            if not provider or not provider.enabled:
                result[provider_name] = "disabled"
                continue

            try:
                async with httpx.AsyncClient(timeout=3) as client:
                    resp = await client.get(
                        provider.base_url.replace("/chat/completions", "/models")
                        if "/chat/completions" in provider.base_url
                        else "https://httpbin.org/get",
                        headers={"Authorization": f"Bearer {provider.api_key}"},
                    )
                    result[provider_name] = "ok" if resp.status_code < 500 else "error"
            except Exception:
                result[provider_name] = "down"

        return result
