"""PK 闸集负例"""
from decimal import Decimal

from app.api.schemas import AuxDimension, BizItemDTO, EntryDTO
from app.core.pk_gates import run_pk_gates


def entry(direction="DEBIT", code="1002", name="银行存款", amount="100.00", summary="t", aux=None):
    return EntryDTO(direction=direction, subject_code=code, subject_name=name,
                    amount=Decimal(amount), summary=summary, aux=aux)


def voucher(entries):
    return [BizItemDTO(biz_type="OTHER", summary="t", entries=entries)]


WL = {"1001", "1002", "1122", "1405", "2202", "2221", "6001", "6603"}


class TestPK01Balance:
    def test_balanced_ok(self):
        r = run_pk_gates(voucher([entry("DEBIT", "1002", "银行存款"), entry("CREDIT", "6001", "主营")]),
                         subject_whitelist=WL)
        assert not any(e.message.startswith("[PK-01]") for e in r.errors)

    def test_imbalanced_fatal(self):
        r = run_pk_gates(voucher([entry("DEBIT"), entry("CREDIT", "6001", "主营", "90.00")]),
                         subject_whitelist=WL)
        pk01 = [e for e in r.errors if e.message.startswith("[PK-01]")]
        assert pk01 and pk01[0].level == "FATAL"


class TestPK02Whitelist:
    def test_violation_fatal(self):
        r = run_pk_gates(voucher([entry(), entry("CREDIT", "9999", "幽灵")]),
                         subject_whitelist=WL)
        pk02 = [e for e in r.errors if e.message.startswith("[PK-02]")]
        assert any(e.level == "FATAL" for e in pk02)

    def test_no_whitelist_degrades_warning(self):
        r = run_pk_gates(voucher([entry(), entry("CREDIT", "6001", "主营")]),
                         subject_whitelist=None)
        pk02 = [e for e in r.errors if e.message.startswith("[PK-02]")]
        assert pk02 and all(e.level == "WARNING" for e in pk02)


class TestPK03Direction:
    def test_reverse_normal_amount_warns(self):
        # 6001（贷方性质）以 DEBIT 正额发生 → 提示应红字表达；平衡用 1001（借方性质）反向不触发
        entries = [entry("DEBIT", "6001", "主营", "50.00"),
                   entry("DEBIT", "6603", "管理费用", "50.00"),
                   entry("CREDIT", "1001", "现金", "100.00")]
        r = run_pk_gates(voucher(entries), subject_whitelist=WL)
        pk03 = [e for e in r.errors if e.message.startswith("[PK-03]")]
        assert any(e.level == "WARNING" for e in pk03)


class TestPK04Aux:
    def test_related_without_aux_warns(self):
        r = run_pk_gates(voucher([entry("DEBIT", "1122", "应收", aux=None),
                                  entry("CREDIT", "6001", "主营")]),
                         subject_whitelist=WL)
        pk04 = [e for e in r.errors if e.message.startswith("[PK-04]")]
        assert pk04 and all(e.level == "WARNING" for e in pk04)

    def test_related_with_aux_ok(self):
        r = run_pk_gates(voucher([entry("DEBIT", "1122", "应收",
                                        aux=AuxDimension(customer="演示客户")),
                                  entry("CREDIT", "6001", "主营")]),
                         subject_whitelist=WL)
        assert not [e for e in r.errors if e.message.startswith("[PK-04]")]


class TestPK05Tax:
    def test_gross_mismatch_fatal(self):
        # item.summary 含"含税"触发 PK-05：gross 挂 2202=4300，vat=300 → net 应为 4000
        # 1405 给了 4000.00 而 4300.10-300=4000.10 → 轧差不一致（凭证同时失衡，PK-01 亦拦） → FATAL（凭证仍平衡）
        entries = [entry("DEBIT", "1405", "库存商品", "4000.00"),
                   entry("DEBIT", "2221", "应交税费", "300.00"),
                   entry("CREDIT", "2202", "应付账款", "4300.10")]
        items = [BizItemDTO(biz_type="PURCHASE", summary="含税采购办公用品", entries=entries)]
        r = run_pk_gates(items, subject_whitelist=WL)
        pk05 = [e for e in r.errors if e.message.startswith("[PK-05]")]
        assert pk05 and pk05[0].level == "FATAL"


class TestPK06Amount:
    def test_wrong_scale_fatal(self):
        e = entry()
        e.amount = Decimal("1.2")
        r = run_pk_gates(voucher([e, entry("CREDIT", "6001", "主营", "1.20")]),
                         subject_whitelist=WL)
        pk06 = [x for x in r.errors if x.message.startswith("[PK-06]")]
        assert any(x.level == "FATAL" for x in pk06)


class TestPK07Date:
    def test_bad_date_fatal(self):
        r = run_pk_gates(voucher([entry(), entry("CREDIT", "6001", "主营")]),
                         subject_whitelist=WL, voucher_date="2026-13-99")
        pk07 = [e for e in r.errors if e.message.startswith("[PK-07]")]
        assert pk07 and pk07[0].level == "FATAL"

    def test_good_date_ok(self):
        r = run_pk_gates(voucher([entry(), entry("CREDIT", "6001", "主营")]),
                         subject_whitelist=WL, voucher_date="2026-07-20")
        assert not [e for e in r.errors if e.message.startswith("[PK-07]")]


class TestPK08Summary:
    def test_empty_summary_fatal(self):
        r = run_pk_gates(voucher([entry(summary=""), entry("CREDIT", "6001", "主营", summary="  ")]),
                         subject_whitelist=WL)
        pk08 = [e for e in r.errors if e.message.startswith("[PK-08]")]
        assert any(e.level == "FATAL" for e in pk08)
