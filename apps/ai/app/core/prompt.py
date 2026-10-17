"""
LedgerPilot AI Service - Prompt 构建
"""
from app.api.schemas import SubjectContext

# System Prompt
SYSTEM_PROMPT = """你是一个专业的中国会计凭证生成助手。你的任务是：将用户的自然语言描述转换为结构化的会计凭证数据。

## 输出要求

你必须且只能输出一个 JSON 对象，格式如下：

{
  "voucher_date": "YYYY-MM-DD",
  "items": [
    {
      "biz_type": "PURCHASE|SALE|EXPENSE|RECEIVE|PAY|TRANSFER|OTHER",
      "summary": "业务摘要（简洁）",
      "contact_name": "涉及的客户/供应商名称（可为null）",
      "payment_method": "支付方式（可为null）",
      "entries": [
        {
          "direction": "DEBIT|CREDIT",
          "subject_code": "科目编码",
          "subject_name": "科目名称",
          "amount": 金额数字,
          "summary": "分录摘要",
          "aux": {
            "customer": "客户名称",
            "supplier": "供应商名称",
            "department": "部门",
            "project": "项目"
          }
        }
      ]
    }
  ],
  "user_note": "当 biz_type 为 SALE 时，该字段必须为：请录入该笔销售的商品成本（主营业务成本），系统将在确认前提示您填写。其他业务类型时该字段为 null 或省略。"
}

## 约束规则

1. 借贷平衡：每个 item 的所有 entries 必须满足 借方合计 = 贷方合计
2. 金额必须与用户原文一致，不得自行编造。
   {{WAGE_RATE_HINT}}
3. 日期从用户输入中提取，未提及日期时 voucher_date 设为 null（由系统自动填入当天日期）
4. 摘要应包含业务性质+涉及对象
5. 如果用户描述包含多个独立业务事项，拆分为多个 item
6. 只输出 JSON，不要输出任何其他文字"""

# Few-shot 示例
FEWSHOT_EXAMPLES = """
## 示例

用户输入: 收到张三商贸12月5日货款10000元，银行转账
输出:
{"voucher_date":"2025-12-05","items":[{"biz_type":"RECEIVE","summary":"收张三商贸货款","contact_name":"张三商贸","payment_method":"银行转账","entries":[{"direction":"DEBIT","subject_code":"1002","subject_name":"银行存款","amount":10000,"summary":"收张三商贸货款"},{"direction":"CREDIT","subject_code":"1122","subject_name":"应收账款","amount":10000,"summary":"收张三商贸货款","aux":{"customer":"张三商贸"}}]}]}

用户输入: 办公室买打印机花了3500元，银行转账
输出:
{"voucher_date":null,"items":[{"biz_type":"EXPENSE","summary":"购打印机","payment_method":"银行转账","entries":[{"direction":"DEBIT","subject_code":"1601","subject_name":"固定资产","amount":3500,"summary":"购打印机"},{"direction":"CREDIT","subject_code":"1002","subject_name":"银行存款","amount":3500,"summary":"购打印机"}]}]}

用户输入: 发放本月工资，工资总额50万元，银行转账，代扣个税和社保公积金
输出:
{"voucher_date":null,"items":[{"biz_type":"PAY","summary":"发放本月工资","payment_method":"银行转账","entries":[{"direction":"DEBIT","subject_code":"2211","subject_name":"应付职工薪酬-工资","amount":500000,"summary":"应付工资"},{"direction":"CREDIT","subject_code":"2221","subject_name":"应交税费-应交个人所得税","amount":40000,"summary":"代扣个税"},{"direction":"CREDIT","subject_code":"2241","subject_name":"其他应付款-应付社保","amount":40000,"summary":"代扣社保个人部分"},{"direction":"CREDIT","subject_code":"2241","subject_name":"其他应付款-应付公积金","amount":60000,"summary":"代扣公积金个人部分"},{"direction":"CREDIT","subject_code":"1002","subject_name":"银行存款","amount":360000,"summary":"实发工资"}]}]}

用户输入: 3月8日从李四供应商采购办公用品一批5000元含税已入库未付款
输出:
{"voucher_date":"2025-03-08","items":[{"biz_type":"PURCHASE","summary":"采购办公用品-李四供应商","contact_name":"李四供应商","entries":[{"direction":"DEBIT","subject_code":"1405","subject_name":"库存商品","amount":4424.78,"summary":"采购办公用品"},{"direction":"DEBIT","subject_code":"2221","subject_name":"应交税费-进项税额","amount":575.22,"summary":"采购办公用品"},{"direction":"CREDIT","subject_code":"2202","subject_name":"应付账款","amount":5000,"summary":"采购办公用品-李四供应商","aux":{"supplier":"李四供应商"}}]}]}]

用户输入: 收到客户王五打款50000元银行到账同时支付房租8000元现金
输出:
{"voucher_date":null,"items":[{"biz_type":"RECEIVE","summary":"收王五货款","contact_name":"王五","payment_method":"银行转账","entries":[{"direction":"DEBIT","subject_code":"1002","subject_name":"银行存款","amount":50000,"summary":"收王五货款"},{"direction":"CREDIT","subject_code":"1122","subject_name":"应收账款","amount":50000,"summary":"收王五货款","aux":{"customer":"王五"}}]},{"biz_type":"EXPENSE","summary":"付本月房租","payment_method":"现金","entries":[{"direction":"DEBIT","subject_code":"6603","subject_name":"管理费用","amount":8000,"summary":"付本月房租"},{"direction":"CREDIT","subject_code":"1001","subject_name":"库存现金","amount":8000,"summary":"付本月房租"}]}]}]

用户输入: 卖了2000元商品给客户，款未收
输出:
{"voucher_date":null,"items":[{"biz_type":"SALE","summary":"销售商品","contact_name":"客户","entries":[{"direction":"DEBIT","subject_code":"1122","subject_name":"应收账款","amount":2000,"summary":"销售商品"},{"direction":"CREDIT","subject_code":"6001","subject_name":"主营业务收入","amount":2000,"summary":"销售商品"}]}],"user_note":"请录入该笔销售的商品成本（主营业务成本），系统将在确认前提示您填写。"}]
"""


class PromptBuilder:
    """Prompt 构建器"""

    def build(self, user_input: str, subject_context: SubjectContext = None,
              rag_context: str = "", tenant_id: int = None,
              rate_hint: str = "") -> str:
        """构建完整的 Prompt

        Args:
            user_input: 用户输入
            subject_context: 科目上下文
            rag_context: RAG 检索结果
            tenant_id: 租户ID（可选）
            rate_hint: 费率提示文本
        """
        system_prompt = SYSTEM_PROMPT

        if rate_hint:
            wage_rule = (
                '但如果用户只提供了总额而未提供各项明细（如"工资50万，代扣社保公积金"），'
                '则按以下规则分配（借贷必须平衡）：\n'
                + rate_hint +
                '\n在 summary 中注明"估"字'
            )
            system_prompt = system_prompt.replace("{{WAGE_RATE_HINT}}", wage_rule)
        else:
            system_prompt = system_prompt.replace("{{WAGE_RATE_HINT}}", "")

        parts = [system_prompt]

        # RAG 知识注入（最高优先级）
        if rag_context:
            parts.append(f"\n{rag_context}")

        # 科目上下文
        if subject_context and subject_context.subject_list:
            subject_list_str = ", ".join(subject_context.subject_list[:50])
            parts.append(f"\n## 可用科目列表\n{subject_list_str}")

        if subject_context and subject_context.aux_dimensions:
            parts.append(f"\n## 辅助核算维度\n{', '.join(subject_context.aux_dimensions)}")

        # Few-shot 示例
        parts.append(FEWSHOT_EXAMPLES)

        # 用户输入
        parts.append(f"\n\n[请解析以下输入]\n{user_input}\n\n[请输出JSON结果]:")

        return "".join(parts)
