package com.ledgerpilot.report.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 利润表（损益表）VO
 * 小企业会计准则格式
 */
@Data
public class ProfitStatementVO {

    private Integer year;
    private Integer period;

    /** 营业收入 */
    private BigDecimal operatingRevenue;
    private BigDecimal mainBusinessRevenue;    // 主营业务收入
    private BigDecimal otherBusinessRevenue;   // 其他业务收入

    /** 营业成本 */
    private BigDecimal operatingCost;
    private BigDecimal mainBusinessCost;       // 主营业务成本
    private BigDecimal otherBusinessCost;      // 其他业务成本

    /** 税金及附加 */
    private BigDecimal taxesAndSurcharges;

    /** 期间费用 */
    private BigDecimal periodExpenses;
    private BigDecimal sellingExpense;         // 销售费用
    private BigDecimal administrativeExpense;  // 管理费用
    private BigDecimal financialExpense;       // 财务费用

    /** 其他收益/损失 */
    private BigDecimal otherGains;             // 营业外收入（填0）
    private BigDecimal otherLosses;            // 营业外支出（填0）
    private BigDecimal nonOperatingIncome;     // 营业外收入
    private BigDecimal nonOperatingExpense;    // 营业外支出

    /** 计算结果 */
    private BigDecimal grossProfit;             // 毛利润 = 营业收入 - 营业成本
    private BigDecimal operatingProfit;         // 营业利润 = 毛利润 - 税金及附加 - 期间费用 + 其他收益
    private BigDecimal totalProfit;             // 利润总额 = 营业利润 + 营业外收支
    private BigDecimal netProfit;               // 净利润 = 利润总额 - 所得税费用

    /** 明细行 */
    private List<ProfitStatementRow> rows;

    @Data
    public static class ProfitStatementRow {
        private String subjectCode;
        private String subjectName;
        private BigDecimal amount;
        private String direction; // DEBIT/CREDIT
        private String category; // revenue/cost/expense/other
    }
}
