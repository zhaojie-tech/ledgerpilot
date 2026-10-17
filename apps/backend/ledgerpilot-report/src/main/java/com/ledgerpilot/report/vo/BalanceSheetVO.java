package com.ledgerpilot.report.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 资产负债表VO
 * 小企业会计准则格式
 */
@Data
public class BalanceSheetVO {

    private Integer year;
    private Integer period;

    // ========== 资产部分 ==========
    /** 流动资产合计 */
    private BigDecimal currentAssets;
    private BigDecimal monetaryFunds;          // 货币资金（1001+1002+1012）
    private BigDecimal notesReceivable;         // 应收票据
    private BigDecimal accountsReceivable;       // 应收账款（1122）
    private BigDecimal prepayments;              // 预付款项
    private BigDecimal otherReceivables;         // 其他应收款
    private BigDecimal inventory;                // 存货（1403+1405）
    private BigDecimal otherCurrentAssets;       // 其他流动资产

    /** 非流动资产合计 */
    private BigDecimal nonCurrentAssets;
    private BigDecimal fixedAssets;              // 固定资产
    private BigDecimal intangibleAssets;         // 无形资产
    private BigDecimal longTermDeferredAssets;   // 长期待摊费用
    private BigDecimal otherNonCurrentAssets;    // 其他非流动资产

    /** 资产总计 */
    private BigDecimal totalAssets;

    // ========== 负债部分 ==========
    /** 流动负债合计 */
    private BigDecimal currentLiabilities;
    private BigDecimal shortTermBorrowings;      // 短期借款
    private BigDecimal notesPayable;             // 应付票据
    private BigDecimal accountsPayable;          // 应付账款（2202）
    private BigDecimal advanceReceipts;          // 预收款项
    private BigDecimal contractLiabilities;      // 合同负债
    private BigDecimal employeeCompensationPayable; // 应付职工薪酬
    private BigDecimal taxesPayable;             // 应交税费（2221）
    private BigDecimal otherPayables;            // 其他应付款

    /** 非流动负债合计 */
    private BigDecimal nonCurrentLiabilities;
    private BigDecimal longTermBorrowings;       // 长期借款
    private BigDecimal otherNonCurrentLiabilities; // 其他非流动负债

    /** 负债合计 */
    private BigDecimal totalLiabilities;

    // ========== 所有者权益部分 ==========
    /** 实收资本 */
    private BigDecimal paidInCapital;            // 3001
    private BigDecimal capitalReserve;           // 资本公积
    private BigDecimal surplusReserve;           // 盈余公积
    private BigDecimal retainedEarnings;         // 未分配利润
    private BigDecimal otherEquity;             // 其他综合收益等

    /** 所有者权益合计 */
    private BigDecimal totalEquity;

    /** 负债和所有者权益总计（应等于资产总计） */
    private BigDecimal totalLiabilitiesAndEquity;

    /** 平衡校验 */
    private Boolean balanced; // totalAssets == totalLiabilitiesAndEquity

    // ========== 辅助 ==========
    /** 注册资金（实收资本期初数） */
    private BigDecimal registeredCapital;

    private List<BalanceSheetRow> rows;

    @Data
    public static class BalanceSheetRow {
        private String subjectCode;
        private String subjectName;
        private BigDecimal amount;
        private String section; // currentAssets/nonCurrentAssets/currentLiabilities/nonCurrentLiabilities/equity
    }
}
