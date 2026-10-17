"""规则层切片测试：确定性、离线、独立于 prompt。"""
from app.core.pipeline import rule_based_parse


class TestReceive:
    def test_basic_receive(self):
        r = rule_based_parse("收到张三商贸货款10000元，银行转账")
        assert r is not None
        e = r["items"][0]["entries"]
        assert e[0]["subject_code"] == "1002" and e[0]["amount"] == 10000
        assert e[1]["subject_code"] == "1122"
        assert e[0]["aux"]["customer"] == "张三商贸"

    def test_wan_quantifier(self):
        r = rule_based_parse("收到宜昌公司回款3万元")
        assert r["items"][0]["entries"][0]["amount"] == 30000

    def test_comma_amount(self):
        r = rule_based_parse("收到李四往来款1,200元")
        assert r["items"][0]["entries"][0]["amount"] == 1200


class TestExpense:
    def test_baoxiao(self):
        r = rule_based_parse("报销差旅费350元")
        assert r["items"][0]["entries"][0]["subject_code"] == "6603"
        assert r["items"][0]["entries"][0]["amount"] == 350

    def test_zhifu(self):
        r = rule_based_parse("支付水电费1.5万元")
        assert r["items"][0]["entries"][0]["amount"] == 15000


class TestPayroll:
    def test_gongzi(self):
        r = rule_based_parse("发放本月工资50000元")
        assert r["items"][0]["entries"][0]["subject_code"] == "2211"
        assert r["items"][0]["entries"][1]["subject_code"] == "1002"
        assert r["items"][0]["entries"][0]["amount"] == 50000


class TestNoMatch:
    def test_unmatched_returns_none(self):
        assert rule_based_parse("计提无形资产摊销") is None
        assert rule_based_parse("") is None


class TestBalance:
    def test_all_scenarios_balance(self):
        for text in ["收到张三货款10000元", "报销差旅费350元", "发放工资50000元"]:
            r = rule_based_parse(text)
            entries = r["items"][0]["entries"]
            net = sum(e["amount"] if e["direction"] == "DEBIT" else -e["amount"] for e in entries)
            assert net == 0
