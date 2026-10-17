"""
LedgerPilot AI Service - 核心解析流水线
"""
import asyncio
import json
import logging
import os
import re
import time
import uuid
from contextvars import ContextVar
from datetime import date
from decimal import Decimal

from app.api.schemas import (
    BizItemDTO,
    EntryDTO,
    Evidence,
    MetadataDTO,
    ParseRequest,
    ParseResponse,
    ValidationErrorItem,
    ValidationResult,
)
from app.config.settings import settings

logger = logging.getLogger(__name__)

# request_id 走 contextvars 贯穿 RAG/LLM/费率全链
request_id_var: "ContextVar[str]" = ContextVar("request_id", default="-")


class NLPParsePipeline:
    """
    NLP 解析流水线

    6 步流程:
    1. 输入预处理（清洗 + 日期/金额提取）
    2. Prompt 构建
    3. LLM 调用（含降级链路）
    4. 结果校验（借贷平衡 + 科目存在 + 幻觉检测）
    5. 凭证组装
    6. 输出
    """

    def __init__(self, llm_client=None, prompt_builder=None, validator=None):
        # 依赖注入点：三个协作对象均可注入，测试用假实现零外呼
        from app.core.prompt import PromptBuilder
        from app.core.validator import ResultValidator
        from app.llm.client import LLMClient

        self.prompt_builder = prompt_builder or PromptBuilder()
        self.llm = llm_client or LLMClient()
        self.validator = validator or ResultValidator()

    async def execute(self, request: ParseRequest) -> ParseResponse:
        start = time.time()
        request_id = request.request_id or str(uuid.uuid4())[:8]
        user_input = request.user_input

        try:
            # Step 1: 输入预处理
            cleaned = self._preprocess(user_input)
            extracted_date = self._extract_date(user_input)
            today_str = str(date.today())

            # Step 2: RAG 知识检索
            rag_context = ""
            try:
                from app.rag.service import build_rag_context
                rag_context = build_rag_context(cleaned)
                if rag_context:
                    logger.info(f"RAG 知识注入成功: context_len={len(rag_context)}")
            except Exception as rag_err:
                logger.warning(f"RAG 检索跳过（知识库可能未初始化）: {rag_err}")

            # Step 3: Prompt 构建。费率经 SPI 预取
            rate_hint = ""
            if request.tenant_id is not None or os.getenv("RATE_FILE"):
                from app.core.rate_context import default_provider
                provider = default_provider()
                if provider is not None:
                    rate_hint = await asyncio.to_thread(provider.rate_hint, request.tenant_id)
            prompt = self.prompt_builder.build(
                user_input=cleaned,
                subject_context=request.context,
                rag_context=rag_context,
                tenant_id=request.tenant_id,
                rate_hint=rate_hint,
            )

            # Step 4: LLM 调用。无可用提供商（无 key）时优先走 规则层——
            # 无 key 的默认档即规则层；规则未命中再回落 LLM/兜底链。
            model = request.options.model
            providers_enabled = any(p.enabled for p in settings.llm_providers().values())
            f1_parsed = None
            if not providers_enabled:
                f1_parsed = rule_based_parse(cleaned, voucher_date=extracted_date or today_str)
            if f1_parsed is not None:
                logger.info("规则层命中（无 LLM 离线路径）: items=%d", len(f1_parsed.get("items", [])))
                parsed = f1_parsed
                llm_result = {"content": json.dumps(f1_parsed, ensure_ascii=False),
                              "model": "rule_engine", "token_used": 0,
                              "source": "fallback_rule"}
            else:
                logger.info(f"DEBUG Step4 LLM prompt length: {len(prompt)}")
                llm_result = await self.llm.chat(prompt, model=model)
                raw_text = llm_result.get("content", "")
                logger.info(f"DEBUG Step4 raw_llm_text (first 300): {raw_text[:300]}")
                # Step 5: 解析 JSON 响应
                parsed = self._parse_json(raw_text)

            # Step 6: 结果校验（subject_context → 科目白名单）
            validation = self.validator.validate(parsed, subject_context=request.context)

            # Step 7: 组装响应
            items = self._build_biz_items(parsed)

            # Step 7.5: PK 会计正确性闸集
            from app.core.pk_gates import run_pk_gates
            whitelist = ({c.split(' ')[0] for c in request.context.subject_list}
                         if request.context and request.context.subject_list else None)
            pk_result = run_pk_gates(items,
                                     subject_whitelist=whitelist,
                                     voucher_date=extracted_date or parsed.get('voucher_date'),
                                     user_text=cleaned)
            for err in pk_result.errors:
                validation.errors.append(err)
            if pk_result.has_fatal:
                validation.valid = False
                validation.confidence = round(validation.confidence * 0.3, 4)
                if validation.confidence < 0.5:
                    validation.confidence_level = "FAIL"

            # 日期优先级: Python 提取 > LLM 返回 > 今天
            llm_date = parsed.get("voucher_date")
            voucher_date = extracted_date or (llm_date if llm_date else today_str)

            duration_ms = int((time.time() - start) * 1000)
            confidence = validation.confidence

            # 证据链（来源/rag_refs/prompt_version/闸分解/契约版本）
            rag_refs = []
            if rag_context:
                import re as _re
                rag_refs = [m for m in _re.findall(r"### 知识片段 \d+（(.+?)）", rag_context)]
            evidence = Evidence(
                origin=llm_result.get("source", "llm"),
                rag_refs=rag_refs[:5],
                prompt_version="v1",
                conf_parts={
                    "pk_gates": 0.3 if pk_result.has_fatal else 1.0,
                    "balance": 0.3 if not validation.valid else 1.0,
                },
                schema_version=2,
            )
            return ParseResponse(
                request_id=request_id,
                evidence=evidence,
                original_input=user_input,
                voucher_date=voucher_date,
                items=items,
                confidence=confidence,
                confidence_level=validation.confidence_level,
                validation=validation,
                metadata=MetadataDTO(
                    model=llm_result.get("model", "unknown"),
                    duration_ms=duration_ms,
                    token_used=llm_result.get("token_used", 0),
                    source=llm_result.get("source", "llm"),
                ),
            )

        except Exception as e:
            duration_ms = int((time.time() - start) * 1000)
            return ParseResponse(
                request_id=request_id,
                original_input=user_input,
                confidence=0,
                confidence_level="FAIL",
                validation=ValidationResult(
                    valid=False,
                    confidence=0,
                    confidence_level="FAIL",
                    errors=[ValidationErrorItem(
                        level="FATAL", message=f"解析失败: {e!s}"
                    )],
                ),
                metadata=MetadataDTO(
                    duration_ms=duration_ms,
                    source="error",
                ),
            )

    # ─── 预处理 ───

    def _preprocess(self, text: str) -> str:
        """文本清洗和标准化"""
        cleaned = text.strip()
        cleaned = re.sub(r'[，]', ',', cleaned)
        cleaned = re.sub(r'[。]', '.', cleaned)
        cleaned = re.sub(r'[（]', '(', cleaned)
        cleaned = re.sub(r'[）]', ')', cleaned)
        cleaned = re.sub(r'\s+', ' ', cleaned)
        return cleaned

    def _extract_date(self, text: str) -> str | None:
        """从文本中提取日期"""
        today = date.today()
        year = today.year

        # "12月5日" / "12月5号"
        m = re.search(r'(\d{1,2})月(\d{1,2})[日号]', text)
        if m:
            month, day = int(m.group(1)), int(m.group(2))
            try:
                return date(year, month, day).isoformat()
            except ValueError:
                pass

        # "今天"
        if '今天' in text:
            return today.isoformat()

        # "昨天"
        if '昨天' in text:
            return (today - __import__('datetime').timedelta(days=1)).isoformat()

        return None

    # ─── JSON 解析 ───

    def _parse_json(self, content: str) -> dict:
        """从 LLM 响应中提取 JSON"""
        # 尝试直接解析
        try:
            return json.loads(content)
        except json.JSONDecodeError:
            pass

        # 尝试从 markdown 代码块中提取
        m = re.search(r'```(?:json)?\s*\n?(.*?)\n?\s*```', content, re.DOTALL)
        if m:
            try:
                return json.loads(m.group(1).strip())
            except json.JSONDecodeError:
                pass

        raise ValueError(f"无法解析 LLM 响应为 JSON: {content[:200]}")

    # ─── 组装业务条目 ───

    def _build_biz_items(self, parsed: dict) -> list[BizItemDTO]:
        """从解析结果构建业务条目列表"""
        items = []
        for item in parsed.get("items", []):
            entries = []
            for entry in item.get("entries", []):
                aux = entry.get("aux")
                raw_amount = entry.get("amount")
                # schema v2：金额 Decimal 承接（负=红字）。
                # None/非数值不进 DTO（契约不允许 null amount）——validator 已对该条
                # 报 AMOUNT_MISSING/不可解析，此处跳过组装即可（单条隔离，不整单 FAIL）。
                if raw_amount is None:
                    logger.warning("金额缺失，该条分录被隔离: %s", entry.get("subject_code"))
                    continue
                try:
                    amount = Decimal(str(raw_amount))
                except (ValueError, TypeError):
                    logger.warning("金额不可解析，该条分录被隔离: %s=%r",
                                   entry.get("subject_code"), raw_amount)
                    continue
                entries.append(EntryDTO(
                    direction=entry.get("direction", "DEBIT"),
                    subject_code=entry.get("subject_code", ""),
                    subject_name=entry.get("subject_name", ""),
                    amount=amount,
                    summary=entry.get("summary", ""),
                    aux=aux if aux else None,
                ))
            items.append(BizItemDTO(
                biz_type=item.get("biz_type", "OTHER"),
                summary=item.get("summary", ""),
                contact_name=item.get("contact_name"),
                payment_method=item.get("payment_method"),
                entries=entries,
            ))
        return items


# ─────────────────────────────────────────────────────────────
# 规则层最小切片：确定性规则覆盖收款/工资/报销三高频场景，
# 独立于 prompt 的解析路径；兼作无 key 离线演示（--replay 模式的内核）。
# ─────────────────────────────────────────────────────────────
def rule_based_parse(user_input: str, voucher_date: str | None = None) -> dict | None:
    """确定性规则解析：命中返回凭证 dict（schema v2 兼容结构），未命中返回 None。"""
    text = user_input.strip()

    # 场景一：收款 —— 「收到...货款/回款/往来款...N 元」→ 借银行存款 / 贷应收账款
    m = re.search(r"收到.{0,20}?(?:货款|回款|往来款)[^0-9]{0,6}([\d,]+(?:\.\d+)?)\s*万?元?", text)
    if m:
        amount = float(m.group(1).replace(",", ""))
        if "万" in text[m.start(1):m.end()]:
            amount *= 10000
        counterparty = None
        m2 = re.search(r"收到([\u4e00-\u9fa5A-Za-z0-9]{2,15}?(?:商贸|公司|厂|店|中心))的?", text)
        if m2:
            counterparty = m2.group(1)
        return {
            "voucher_date": voucher_date,
            "items": [{
                "biz_type": "RECEIVE",
                "summary": f"收{(counterparty or '')}货款".strip(),
                "contact_name": counterparty,
                "payment_method": "银行转账",
                "entries": [
                    {"direction": "DEBIT", "subject_code": "1002", "subject_name": "银行存款",
                     "amount": amount, "summary": "收款",
                     "aux": {"customer": counterparty} if counterparty else None},
                    {"direction": "CREDIT", "subject_code": "1122", "subject_name": "应收账款",
                     "amount": amount, "summary": "收款",
                     "aux": {"customer": counterparty} if counterparty else None},
                ],
            }],
        }

    # 场景二：报销/费用 —— 「报销/支付...费...N 元」→ 借管理费用 / 贷库存现金
    m = re.search(r"(?:报销|支付|付)[\u4e00-\u9fa5A-Za-z0-9]{0,10}费[^0-9]{0,6}([\d,]+(?:\.\d+)?)\s*万?元?", text)
    if m:
        amount = float(m.group(1).replace(",", ""))
        if "万" in text[m.start(1):m.end()]:
            amount *= 10000
        return {
            "voucher_date": voucher_date,
            "items": [{
                "biz_type": "EXPENSE",
                "summary": "费用报销",
                "payment_method": "现金",
                "entries": [
                    {"direction": "DEBIT", "subject_code": "6603", "subject_name": "管理费用",
                     "amount": amount, "summary": "费用报销"},
                    {"direction": "CREDIT", "subject_code": "1001", "subject_name": "库存现金",
                     "amount": amount, "summary": "费用报销"},
                ],
            }],
        }

    # 场景三：工资发放 —— 「发放...工资...N 元」→ 借应付职工薪酬 / 贷银行存款
    m = re.search(r"发放[^0-9]{0,8}工资[^0-9]{0,6}([\d,]+(?:\.\d+)?)\s*万?元?", text)
    if m:
        amount = float(m.group(1).replace(",", ""))
        if "万" in text[m.start(1):m.end()]:
            amount *= 10000
        return {
            "voucher_date": voucher_date,
            "items": [{
                "biz_type": "PAY",
                "summary": "发放工资",
                "payment_method": "银行转账",
                "entries": [
                    {"direction": "DEBIT", "subject_code": "2211", "subject_name": "应付职工薪酬-工资",
                     "amount": amount, "summary": "发放工资"},
                    {"direction": "CREDIT", "subject_code": "1002", "subject_name": "银行存款",
                     "amount": amount, "summary": "发放工资"},
                ],
            }],
        }

    return None
