"""Prompt 构建与费率 SPI 测试。"""
from app.core.prompt import PromptBuilder
from app.core.rate_context import FileRateContextProvider


class TestPromptBuilder:
    def test_rate_hint_injected(self):
        pb = PromptBuilder()
        out = pb.build(user_input="发工资", rate_hint="【当前费率】\n  养老保险：个人8.0% 单位16.0%")
        assert "{{WAGE_RATE_HINT}}" not in out
        assert "养老保险" in out

    def test_no_rate_hint_placeholder_removed(self):
        out = PromptBuilder().build(user_input="收到货款100元", rate_hint="")
        assert "{{WAGE_RATE_HINT}}" not in out

    def test_subject_context_whitelist_injected(self):
        from app.api.schemas import SubjectContext
        pb = PromptBuilder()
        out = pb.build(user_input="x", subject_context=SubjectContext(subject_list=["1002 银行存款"]))
        assert "1002 银行存款" in out

    def test_user_input_appended(self):
        out = PromptBuilder().build(user_input="收到张三货款10000元")
        assert "收到张三货款10000元" in out


class TestFileRateProvider:
    def test_rate_file_rendered(self, tmp_path):
        f = tmp_path / "rates.json"
        f.write_text('{"social": {"pension": {"personal": 0.08, "employer": 0.16}}}', encoding="utf-8")
        p = FileRateContextProvider(str(f))
        hint = p.rate_hint(None)
        assert "养老保险" in hint
        assert "8.0%" in hint and "16.0%" in hint

    def test_missing_file_empty_hint(self, tmp_path):
        p = FileRateContextProvider(str(tmp_path / "nope.json"))
        assert p.rate_hint(None) == ""

    def test_tenant_id_ignored_by_file_impl(self, tmp_path):
        f = tmp_path / "rates.json"
        f.write_text('{"vat": {"small_3": 0.03}}', encoding="utf-8")
        p = FileRateContextProvider(str(f))
        assert "3.0%" in p.rate_hint(999)
