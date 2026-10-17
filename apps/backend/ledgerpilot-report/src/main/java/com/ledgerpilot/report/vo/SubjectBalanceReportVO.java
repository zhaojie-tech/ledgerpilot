package com.ledgerpilot.report.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 科目余额表VO
 */
@Data
public class SubjectBalanceReportVO {

    private Integer year;
    private Integer period;

    /** 借方合计 */
    private BigDecimal totalDebit;

    /** 贷方合计 */
    private BigDecimal totalCredit;

    /** 期末余额合计（借方-贷方） */
    private BigDecimal netBalance;

    private List<SubjectBalanceRow> rows;

    @Data
    public static class SubjectBalanceRow {
        private String subjectCode;
        private String subjectName;
        private String subjectType;
        private String direction; // DEBIT/CREDIT
        private Boolean isDetail; // 是否末级
        private BigDecimal beginDebit;   // 期初借方余额
        private BigDecimal beginCredit;  // 期初贷方余额
        private BigDecimal currentDebit;  // 本期借方发生
        private BigDecimal currentCredit; // 本期贷方发生
        private BigDecimal endDebit;     // 期末借方余额
        private BigDecimal endCredit;    // 期末贷方余额
        private Integer level;           // 科目级次
    }
}
