"""
LedgerPilot AI Service - 结果校验器

schema v2 契约（docs/SCHEMA-V2.md）+ 白名单代码级：
- 借贷平衡 = 有符号金额精确求和：Σ(DEBIT 计 +amount / CREDIT 计 −amount) = 0（无容差）；
- 负数金额 = 红字冲销，合法；
- null 金额逐条隔离为 AMOUNT_MISSING，不整单 FAIL；
- 科目白名单：subject_context.subject_list 非空时整码精确匹配，越名单 FATAL；
  白名单缺省 → 降 WARNING 并在响应标注（不阻断开源零配置使用）。
"""
from decimal import Decimal

from app.api.schemas import (
    SubjectContext,
    ValidationErrorItem,
    ValidationResult,
)


def _parse_subject_list(subject_list: list) -> set:
    """'1002 银行存款' → {'1002'}（整码精确匹配口径，PK-02）"""
    codes = set()
    for entry in subject_list or []:
        code = str(entry).strip().split(" ")[0]
        if code:
            codes.add(code)
    return codes


class ResultValidator:
    """多级校验：白名单 + 借贷平衡 + 金额合理性"""

    def validate(self, parsed: dict, subject_context: SubjectContext = None) -> ValidationResult:
        errors = []
        confidence = 1.0

        whitelist = None
        if subject_context is not None and getattr(subject_context, "subject_list", None):
            whitelist = _parse_subject_list(subject_context.subject_list)
        # 无白名单的缺省口径（WARNING 标注）由 PK-02 闸执行

        for item in parsed.get("items", []):
            entries = item.get("entries", [])

            if not entries:
                errors.append(ValidationErrorItem(
                    level="WARNING", message="该业务条目没有分录"
                ))
                confidence *= 0.5
                continue

            # 借贷平衡（schema v2）：有符号精确求和，无容差
            try:
                net = sum(
                    (Decimal(str(e["amount"])) if e.get("direction") == "DEBIT"
                     else -Decimal(str(e["amount"])))
                    for e in entries if e.get("amount") is not None
                )
            except (KeyError, ValueError, TypeError):
                errors.append(ValidationErrorItem(
                    level="FATAL",
                    message="金额存在不可解析值，无法执行平衡校验",
                ))
                confidence *= 0.3
                continue

            if net != 0:
                errors.append(ValidationErrorItem(
                    level="FATAL",
                    message=f"借贷不平衡: 有符号净额={net}（契约：精确为 0）",
                ))
                confidence *= 0.3

            # 金额合理性与科目校验
            for entry in entries:
                amount = entry.get("amount")
                subject_code = str(entry.get("subject_code") or "")
                summary = entry.get("summary", "")
                is_estimated = "估" in summary or "待核对" in summary

                # 科目白名单：整码精确匹配，越名单 FATAL
                if whitelist is not None:
                    if subject_code and subject_code not in whitelist:
                        errors.append(ValidationErrorItem(
                            level="FATAL",
                            field="subject_code",
                            message=f"科目越白名单: {subject_code}（SUBJECT_NOT_IN_WHITELIST）",
                        ))
                        confidence *= 0.3
                elif subject_code:
                    errors.append(ValidationErrorItem(
                        level="WARNING",
                        field="subject_code",
                        message=f"科目白名单未配置，无法校验科目合法性: {subject_code}",
                    ))

                # schema v2：负数=红字（合法）；缺失/非数值=FATAL 隔离
                if amount is None:
                    errors.append(ValidationErrorItem(
                        level="FATAL",
                        field="amount",
                        message=f"AMOUNT_MISSING: 金额缺失 {subject_code}",
                    ))
                    confidence *= 0.3
                elif isinstance(amount, str):
                    errors.append(ValidationErrorItem(
                        level="FATAL",
                        message=f"金额不可解析: {subject_code}",
                    ))
                    confidence *= 0.3
                elif amount == 0 and not is_estimated:
                    errors.append(ValidationErrorItem(
                        level="WARNING",
                        message=f"金额为零，可能未录入: {subject_code}",
                    ))
                    confidence *= 0.8
                elif isinstance(amount, (int, float)) and amount > 99999999:
                    errors.append(ValidationErrorItem(
                        level="WARNING",
                        message=f"金额异常大: {amount}",
                    ))
                    confidence *= 0.9

                # 科目编码长度
                if not subject_code or len(subject_code) < 4:
                    errors.append(ValidationErrorItem(
                        level="WARNING",
                        message=f"科目编码异常: {subject_code or '(空)'}",
                    ))
                    confidence *= 0.8

        # 置信度等级
        if confidence >= 0.9:
            level = "HIGH"
        elif confidence >= 0.7:
            level = "MEDIUM"
        elif confidence >= 0.5:
            level = "LOW"
        else:
            level = "FAIL"

        has_fatal = any(e.level == "FATAL" for e in errors)

        return ValidationResult(
            valid=not has_fatal,
            confidence=round(confidence, 4),
            confidence_level=level,
            errors=errors,
        )
