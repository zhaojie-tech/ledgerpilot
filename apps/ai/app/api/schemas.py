"""
LedgerPilot AI Service - API 数据模型（Pydantic v2）

响应契约见 docs/SCHEMA-V2.md：
- amount 为有符号 Decimal（正=常规方向，负=红字冲销），标度固定 2 位（HALF_UP），
  JSON 序列化为字符串型十进制（"10000.00" / "-2000.00"）；
- 顶层带 schema_version=2。
"""
from decimal import ROUND_HALF_UP, Decimal
from typing import Annotated

from pydantic import BaseModel, Field, field_serializer, field_validator

SCHEMA_VERSION = 2


def normalize_amount(value: Decimal) -> Decimal:
    """金额归一：标度固定 2 位、HALF_UP（契约规定值）"""
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


# ─── 请求模型 ───

# 单条输入限长：防超长文本放大 token 成本与延迟（上限 1000 字符）
UserInput = Annotated[str, Field(min_length=1, max_length=1000, description="用户自然语言输入")]


class SubjectContext(BaseModel):
    """租户科目上下文（调用方传入）"""
    subject_list: list[str] = Field(default_factory=list, description="租户启用的科目列表，如 ['1002 银行存款', '1122 应收账款']")
    aux_dimensions: list[str] = Field(default_factory=list, description="辅助核算维度，如 ['customer', 'supplier', 'department']")
    taxpayer_type: str = Field(default="SMALL_SCALE", description="纳税人类型: GENERAL/SMALL_SCALE")
    industry: str = Field(default="TRADING", description="行业")


class ParseOptions(BaseModel):
    """解析选项"""
    model: str | None = Field(default=None, description="指定 LLM 模型（可选；受 ALLOWED_MODELS 白名单约束）")


class ParseRequest(BaseModel):
    """NLP 解析请求"""
    user_input: UserInput
    tenant_id: int = Field(..., description="租户 ID")
    request_id: str | None = Field(default=None, description="请求追踪 ID")
    context: SubjectContext = Field(default_factory=SubjectContext)
    options: ParseOptions = Field(default_factory=ParseOptions)


class BatchParseRequest(BaseModel):
    """批量解析请求"""
    inputs: list[UserInput] = Field(..., min_length=1, max_length=20, description="多条自然语言输入")
    tenant_id: int = Field(..., description="租户 ID")
    context: SubjectContext = Field(default_factory=SubjectContext)
    options: ParseOptions = Field(default_factory=ParseOptions)


# ─── 响应模型 ───

class AuxDimension(BaseModel):
    """辅助核算维度值"""
    customer: str | None = None
    supplier: str | None = None
    department: str | None = None
    project: str | None = None
    employee: str | None = None


class EntryDTO(BaseModel):
    """分录（schema v2：有符号 Decimal，负数=红字冲销）"""
    direction: str = Field(..., pattern="^(DEBIT|CREDIT)$", description="方向")
    subject_code: str = Field(..., description="科目编码")
    subject_name: str = Field(..., description="科目名称")
    amount: Decimal = Field(..., description="金额（有符号；负=红字）；序列化为字符串型十进制")
    summary: str = Field(default="", description="分录摘要")
    aux: AuxDimension | None = Field(default=None, description="辅助核算")

    @field_validator("amount")
    @classmethod
    def _normalize(cls, v: Decimal) -> Decimal:
        return normalize_amount(v)

    @field_serializer("amount")
    def _serialize(self, v: Decimal) -> str:
        return str(v)


class BizItemDTO(BaseModel):
    """业务条目"""
    biz_type: str = Field(..., description="业务类型: PURCHASE/SALE/EXPENSE/RECEIVE/PAY/TRANSFER")
    summary: str = Field(default="", description="业务摘要")
    contact_name: str | None = Field(default=None, description="客户/供应商名称")
    payment_method: str | None = Field(default=None, description="支付方式")
    entries: list[EntryDTO] = Field(default_factory=list, description="分录列表")


class ValidationErrorItem(BaseModel):
    """校验错误"""
    level: str = Field(..., description="FATAL/WARNING/INFO")
    field: str | None = None
    message: str
    suggestion: str | None = None


class ValidationResult(BaseModel):
    """校验结果"""
    valid: bool = True
    confidence: float = Field(default=1.0, ge=0, le=1)
    confidence_level: str = Field(default="HIGH", pattern="^(HIGH|MEDIUM|LOW|FAIL)$")
    errors: list[ValidationErrorItem] = Field(default_factory=list)


class MetadataDTO(BaseModel):
    """元数据"""
    model: str = ""
    duration_ms: int = 0
    token_used: int = 0
    source: str = ""  # llm / fallback_rule / error


class Evidence(BaseModel):
    """逐响应证据链：置信度分解 + 来源可回链"""
    origin: str = Field(default="llm", description="产出来源: llm / fallback_rule / error")
    rag_refs: list[str] = Field(default_factory=list, description="RAG 命中片段引用（source 标识）")
    prompt_version: str = Field(default="v1", description="prompt 版本")
    conf_parts: dict = Field(default_factory=dict, description="置信度分解（闸名→系数）")
    schema_version: int = Field(default=SCHEMA_VERSION, description="契约版本")


class ParseResponse(BaseModel):
    """NLP 解析响应（schema v2）"""
    schema_version: int = Field(default=SCHEMA_VERSION, description="响应契约版本")
    request_id: str
    evidence: Evidence = Field(default_factory=Evidence, description="证据链")
    original_input: str
    voucher_date: str | None = None
    items: list[BizItemDTO] = Field(default_factory=list)
    confidence: float = Field(default=1.0)
    confidence_level: str = Field(default="HIGH")
    validation: ValidationResult = Field(default_factory=ValidationResult)
    metadata: MetadataDTO = Field(default_factory=MetadataDTO)
    user_note: str | None = Field(default=None, description="给用户的提示信息，如：请录入销售成本")


class BatchErrorDetail(BaseModel):
    """批处理单条失败的原因（错误码族在后续里程碑细化为枚举）"""
    code: str
    message: str


class BatchParseErrorItem(BaseModel):
    """批处理中单条失败的占位结果。

    与 ParseResponse 以字段结构区分（index+error vs request_id+original_input），
    保证单条失败不拖垮整批，且失败原因可定位。
    """
    index: int = Field(..., ge=0, description="对应请求 inputs 的下标")
    error: BatchErrorDetail


class BatchParseResponse(BaseModel):
    """批量解析响应：成功项为 ParseResponse，失败项为 BatchParseErrorItem"""
    results: list[ParseResponse | BatchParseErrorItem] = Field(default_factory=list)


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str = "healthy"
    version: str = "0.1.0"
    python_version: str = ""
    llm_providers: dict = Field(default_factory=dict)


class PingResponse(BaseModel):
    """连通性测试响应"""
    message: str = "pong from LedgerPilot AI Service"
    timestamp: str = ""
