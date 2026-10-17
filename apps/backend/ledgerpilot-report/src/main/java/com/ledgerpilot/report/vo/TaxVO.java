package com.ledgerpilot.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 税务中心 - 税种汇总 VO
 */
@Data
public class TaxVO {

    /** 税种名称，如"增值税"、"企业所得税" */
    private String taxTypeName;

    /** 税率（如增值税税率 13%/9%/6%，企业所得税 25%） */
    private BigDecimal taxRate;

    /** 计税基础/销售额 */
    private BigDecimal taxBase;

    /** 应纳税额 */
    private BigDecimal taxAmount;

    /** 已交税额（本期已申报缴纳） */
    private BigDecimal taxPaid;

    /** 欠交税额（应交未交） */
    private BigDecimal taxUnpaid;

    /** 所属期间 yyyy-MM */
    private String period;

    /** 明细项目列表 */
    private List<TaxItemVO> items;

    @Data
    public static class TaxItemVO {
        /** 税目名称，如"销项税额"、"进项税额转出" */
        private String itemName;
        /** 金额 */
        private BigDecimal amount;
        /** 备注 */
        private String remark;
    }
}
