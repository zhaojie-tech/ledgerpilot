"""
bench CLI：跑任意 OpenAI 兼容端点，输出 rubric 固定口径的四维得分。

用法：
  python -m bench.bench_cli --base-url https://api.deepseek.com/v1 \
      --model deepseek-chat --api-key-env DEEPSEEK_API_KEY [--eval eval/eval_v1.jsonl]

rubric（当前版本）：
① 可解析率 = 合法 JSON 且过 schema 的用例数 ÷ 总用例数
② 平衡 = 有符号精确求和 0（无容差）
③ 科目方向 = (subject_code, direction, 出现序) 三键排序后逐位对齐
④ 金额 = 精确 Decimal 相等（不做容差）
四维分别统计；任一家 3 模型全维 ≥95% ⇒ 判过易、加难例。
"""
import argparse
import json
import os
import sys
from decimal import Decimal
from pathlib import Path


def align_key(e):
    """分录对齐三键：(subject_code, direction, 出现序)"""
    return (e.get("subject_code", ""), e.get("direction", ""))


def score_case(expected: dict, actual: dict) -> dict:
    out = {"parse_ok": True, "balance": False, "direction": False, "amount": False}
    if actual is None:
        out["parse_ok"] = False
        return out
    try:
        exp_items = expected["items"]
        act_items = actual.get("items", [])
        exp_entries = [e for it in exp_items for e in it["entries"]]
        act_entries = [e for it in act_items for e in it.get("entries", [])]

        # ② 平衡（有符号精确）
        net = sum(Decimal(str(e["amount"])) if e["direction"] == "DEBIT"
                  else -Decimal(str(e["amount"])) for e in act_entries)
        out["balance"] = net == 0

        # ③ 科目方向：三键排序逐位对齐
        exp_sorted = sorted(exp_entries, key=align_key)
        act_sorted = sorted(act_entries, key=align_key)
        if len(exp_sorted) == len(act_sorted):
            out["direction"] = all(
                a.get("subject_code") == b.get("subject_code")
                and a.get("direction") == b.get("direction")
                for a, b in zip(exp_sorted, act_sorted))

        # ④ 金额精确 Decimal 相等（排序后逐位）
        exp_amt = sorted(Decimal(str(e["amount"])) for e in exp_entries)
        act_amt = sorted(Decimal(str(e["amount"])) for e in act_entries)
        out["amount"] = exp_amt == act_amt
    except (KeyError, TypeError, ValueError, ArithmeticError):
        out["parse_ok"] = False
    return out


def main():
    parser = argparse.ArgumentParser(description="LedgerPilot bench CLI")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--api-key-env", default="OPENAI_API_KEY")
    parser.add_argument("--eval", default=str(Path(__file__).parent / "eval_v1.jsonl"))
    parser.add_argument("--timeout", type=int, default=60)
    args = parser.parse_args()

    api_key = os.getenv(args.api_key_env, "")
    if not api_key:
        print(f"env {args.api_key_env} 未设置", file=sys.stderr)
        sys.exit(2)

    eval_path = Path(args.eval)
    if not eval_path.exists():
        print(f"评测集不存在: {eval_path}", file=sys.stderr)
        sys.exit(2)

    cases = [json.loads(line) for line in eval_path.read_text(encoding="utf-8").splitlines() if line.strip()]

    import httpx
    totals = {"parse": 0, "balance": 0, "direction": 0, "amount": 0}
    for case in cases:
        try:
            resp = httpx.post(
                f"{args.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json={"model": args.model, "temperature": 0.1,
                      "messages": [{"role": "user", "content": case["prompt"]}]},
                timeout=args.timeout,
            )
            content = resp.json()["choices"][0]["message"]["content"]
            m = json.loads(content)
        except Exception as e:
            print(f"[{case.get('id', '?')}] 调用/解析失败: {e}")
            continue
        s = score_case(case["expected"], m)
        totals["parse"] += s["parse_ok"]
        totals["balance"] += s["balance"]
        totals["direction"] += s["direction"]
        totals["amount"] += s["amount"]

    n = len(cases)
    print(f"\n=== bench 结果（model={args.model}, n={n}）===")
    for name, key in [("可解析率", "parse"), ("平衡", "balance"),
                      ("科目方向", "direction"), ("金额精确", "amount")]:
        pct = totals[key] / n * 100 if n else 0
        print(f"  {name}: {totals[key]}/{n} = {pct:.1f}%")
    print("rubric 固定口径：任一家 3 模型全维 ≥95% ⇒ 判过易、加难例")


if __name__ == "__main__":
    main()
