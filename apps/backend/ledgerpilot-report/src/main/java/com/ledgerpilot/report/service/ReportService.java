package com.ledgerpilot.report.service;

public interface ReportService {

    /**
     * 利润表（损益表）
     * 营业收入 - 营业成本 = 毛利润
     * 毛利润 - 税金及附加 - 期间费用 = 营业利润
     * 营业利润 + 营业外收支 = 利润总额
     */
    Object getProfitStatement(Integer year, Integer period);

    /**
     * 资产负债表
     * 资产 = 负债 + 所有者权益
     * 流动资产 + 非流动资产 = 资产合计
     * 流动负债 + 非流动负债 = 负债合计
     * 实收资本 + 未分配利润 = 所有者权益合计
     */
    Object getBalanceSheet(Integer year, Integer period);

    /**
     * 现金流量表（直接法简化版）
     * 经营活动 + 投资活动 + 筹资活动 = 现金净增加额
     */
    Object getCashFlowStatement(Integer year, Integer period);

    /**
     * 科目余额表
     * 某个期间内所有科目的期初/本期/期末余额
     */
    Object getSubjectBalanceReport(Integer year, Integer period);

    /**
     * 导出报表为Excel
     */
    String exportReport(String type, Integer year, Integer period);
}
