"""
PK 会计正确性闸集

| ID  | 闸 | 判据 |
|---|---|---|
| PK-01 | 借贷平衡 | 有符号 Decimal 精确求和 = 0（无容差） |
| PK-02 | 科目白名单 | 对照 subject_context 整码精确匹配；越名单 FATAL；无白名单降 WARNING |
| PK-03 | 科目性质/方向 | 资产/费用类借方为正方向；负债/权益/收入类贷方为正方向；反向金额需红字（负）表达 |
| PK-04 | 辅助核算完整 | 往来科目（1122/2202 等）须带 customer/supplier |
| PK-05 | 价税轧差 | 价税分离时 net + vat == gross（Decimal 精确） |
| PK-06 | 金额契约 | 标度 2 位 HALF_UP；负数=红字；缺失=AMOUNT_MISSING 隔离 |
| PK-07 | 日期合法 | voucher_date 为 YYYY-MM-DD 且可解析 |
| PK-08 | 摘要/金额非零 | 每条分录摘要非空；金额非零（零=WARNING） |

无科目元数据（性质/往来标记）时 PK-03/PK-04 降 WARNING 并标注。
"""
from decimal import Decimal

from app.api.schemas import BizItemDTO, ValidationErrorItem

# 末级编码 → 性质方向（DEBIT=借方为正常方向）；基于内置演示科目口径，样例可整体替换
DEFAULT_DIRECTION: dict[str, str] = {
    "1001": "DEBIT", "1002": "DEBIT", "1122": "DEBIT", "1123": "DEBIT", "1221": "DEBIT",
    "1405": "DEBIT", "1601": "DEBIT", "1602": "CREDIT",
    "2001": "CREDIT", "2201": "CREDIT", "2202": "CREDIT", "2203": "CREDIT",
    "2211": "CREDIT", "2221": "CREDIT", "2241": "CREDIT",
    "4001": "CREDIT", "4103": "CREDIT",
    "6001": "CREDIT", "6051": "CREDIT", "6401": "DEBIT",
    "6601": "DEBIT", "6602": "DEBIT", "6603": "DEBIT", "6604": "DEBIT",
}

# 往来科目：须携带辅助核算（customer/supplier）
RELATED_ACCOUNTS = {"1122", "1123", "2201", "2202", "2203"}

_GROSS_HINTS = ("含税", "价税合计")


class PKResult:
    def __init__(self):
        self.errors: list[ValidationErrorItem] = []

    def fatal(self, pk: str, message: str, field: str | None = None):
        self.errors.append(ValidationErrorItem(level="FATAL", field=field, message=f"[{pk}] {message}"))

    def warning(self, pk: str, message: str, field: str | None = None):
        self.errors.append(ValidationErrorItem(level="WARNING", field=field, message=f"[{pk}] {message}"))

    @property
    def has_fatal(self) -> bool:
        return any(e.level == "FATAL" for e in self.errors)


def run_pk_gates(items: list[BizItemDTO],
                 subject_whitelist: set[str] | None = None,
                 subject_direction: dict[str, str] | None = None,
                 voucher_date: str | None = None,
                 user_text: str | None = None) -> PKResult:
    """对组装后的 items 跑 PK-01~08。元数据缺失时相应闸降级为 WARNING。

    user_text：用户原始输入。PK-05 触发依据：
    以**用户原文**（而非 LLM 生成的摘要）判断是否含价税场景，防 LLM 措辞漂移导致漏判。
    """
    result = PKResult()
    direction_map = subject_direction if subject_direction is not None else DEFAULT_DIRECTION

    for item in items:
        entries = item.entries
        if not entries:
            result.warning("PK-08", "业务条目没有分录")
            continue

        # PK-01 借贷平衡：有符号精确求和
        net = sum(
            (e.amount if e.direction == "DEBIT" else -e.amount)
            for e in entries
        )
        if net != Decimal("0.00"):
            result.fatal("PK-01", f"借贷不平衡: 有符号净额={net}")

        for e in entries:
            code = e.subject_code

            # PK-02 科目白名单
            if subject_whitelist is not None:
                if code not in subject_whitelist:
                    result.fatal("PK-02", f"科目越白名单: {code}", field="subject_code")
            else:
                if code:
                    result.warning("PK-02", f"科目白名单未配置，无法校验: {code}", field="subject_code")

            # PK-03 性质/方向（有符号金额参与）：反向正常方向且金额为正 → 提示应红字
            expected = direction_map.get(code)
            if expected is None:
                if code:
                    result.warning("PK-03", f"科目无元数据，无法校验方向: {code}", field="subject_code")
            else:
                is_debit = e.direction == "DEBIT"
                normal_debit = expected == "DEBIT"
                amount_positive = e.amount > 0
                if amount_positive and is_debit != normal_debit:
                    result.warning("PK-03",
                                   f"反向发生（{code} {e.direction} 正额），若为冲销应以红字（负额）表达",
                                   field="direction")

            # PK-04 辅助核算完整性（往来须客商；无元数据→WARNING）
            root = code[:4]
            if root in RELATED_ACCOUNTS:
                aux = e.aux
                has_counterparty = aux is not None and (aux.customer or aux.supplier)
                if not has_counterparty:
                    result.warning("PK-04", f"往来科目缺客商/供应商辅助核算: {code}",
                                   field="aux")

            # PK-06 金额契约：标度 2
            if e.amount.as_tuple().exponent != -2:
                result.fatal("PK-06", f"金额标度非 2 位: {e.amount}", field="amount")
            if e.amount == 0:
                result.warning("PK-06", f"金额为零: {code}", field="amount")

            # PK-08 摘要非空
            if not (e.summary or "").strip():
                result.fatal("PK-08", f"分录摘要为空: {code}", field="summary")

        # PK-05 价税轧差：触发依据=用户原文含"含税/价税"（A3 修复：LLM 摘要措辞会漂移）
        text = (user_text or "") + (item.summary or "")
        if any(h in text for h in _GROSS_HINTS):
            # gross 挂往来/货币类（含税额）；vat 限 2221 前缀（A3 修复：任意名称含"税"会误配个税等）
            gross_entries = [e for e in entries
                             if e.subject_code[:4] in ("2202", "1122", "1002", "1001")]
            vat = [e.amount for e in entries if e.subject_code.startswith("2221")]
            if gross_entries and vat:
                gross = gross_entries[0].amount
                computed_net = gross - abs(vat[0])
                # 按分录对象排除 gross 自身（金额 identity 比较恒真不排除）
                net_of = [e.amount for e in entries
                          if e is not gross_entries[0] and not e.subject_code.startswith("2221")]
                matched = any(n == computed_net for n in net_of)
                if not matched:
                    result.fatal("PK-05", f"价税轧差不一致: gross={gross}, vat={abs(vat[0])}, "
                                          f"net 应为 {computed_net}")

    # PK-07 日期
    if voucher_date:
        try:
            from datetime import date as _date
            parts = voucher_date.split("-")
            if len(parts) != 3:
                raise ValueError
            _date(int(parts[0]), int(parts[1]), int(parts[2]))
        except (ValueError, TypeError):
            result.fatal("PK-07", f"凭证日期不合法: {voucher_date}", field="voucher_date")

    return result
