"""few-shot 工资示例科目码锁死（2241→2211 应付职工薪酬；2182→2241 其他应付款）。"""
from app.core.prompt import FEWSHOT_EXAMPLES


def test_wage_payable_is_2211():
    assert '"subject_code":"2211","subject_name":"应付职工薪酬-工资"' in FEWSHOT_EXAMPLES


def test_other_payable_is_2241():
    assert '"subject_code":"2241","subject_name":"其他应付款-应付社保"' in FEWSHOT_EXAMPLES
    assert '"subject_code":"2241","subject_name":"其他应付款-应付公积金"' in FEWSHOT_EXAMPLES


def test_nonexistent_code_2182_removed():
    """2182 在会计科目表中不存在，不得再出现。"""
    assert "2182" not in FEWSHOT_EXAMPLES


def test_2241_never_labeled_as_wage_payable():
    """回归锁：2241 是其他应付款，不得再标注为应付职工薪酬。"""
    assert '"subject_code":"2241","subject_name":"应付职工薪酬' not in FEWSHOT_EXAMPLES


def test_wage_example_still_balanced():
    """修正科目码后示例仍借贷平衡：500000 = 40000+40000+60000+360000。"""
    import json

    lines = FEWSHOT_EXAMPLES.splitlines()
    payload = None
    for i, line in enumerate(lines):
        if line.startswith("用户输入: 发放本月工资"):
            # few-shot 格式：用户输入行 → "输出:" 行 → JSON 行
            assert lines[i + 1].strip() == "输出:"
            payload = lines[i + 2].strip()
            break
    assert payload, "未找到工资示例"
    data = json.loads(payload)
    entries = data["items"][0]["entries"]
    debit = sum(e["amount"] for e in entries if e["direction"] == "DEBIT")
    credit = sum(e["amount"] for e in entries if e["direction"] == "CREDIT")
    assert debit == credit == 500000
