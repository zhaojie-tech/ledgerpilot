# 语料贡献指南（F2 语料飞轮）

## 上报格式（issue 模板 → 可跑资产）

一个用例 = 一行 JSONL，字段：

```json
{
  "name": "简短场景名",
  "input": "原始自然语言输入（逐字）",
  "expected": {"items": 1, "balanced": true, "level": "HIGH",
               "subject_codes_contains": ["1002"], "amount_strings": ["8000.00"]},
  "model": "deepseek-chat",
  "prompt_version": "v1",
  "schema_version": 2,
  "raw_response": "模型当时的原始输出（逐字，用于复现）"
}
```

## 复现纪律（一行命令）

```bash
python -m pytest tests/test_corpus.py -k "你的场景名"
```

贡献语料 hash + 模型版本 + 日期记录在 issue 中；语料入 `corpus/`（**不进** `eval/` 留出集）。

## CI 校验

贡献语料必须通过：JSON 可解析、expect 字段完整、（若含 llm_response）平衡校验通过或标记负例。
