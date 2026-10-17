package com.ledgerpilot.voucher.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 期初余额响应（包含全部科目及当前余额状态）
 */
@Data
public class OpeningBalanceVO {

    private Integer fiscalYear;
    private Integer periodNo;
    /** 是否已开账（是否有期初余额记录） */
    private boolean initialized;
    /** 是否有凭证（已开账但有凭证后不可修改） */
    private boolean hasVouchers;
    /** 科目余额列表 */
    private List<SubjectBalanceVO> subjects;

    @Data
    public static class SubjectBalanceVO {
        private String subjectCode;
        private String subjectName;
        private String direction;   // DEBIT / CREDIT
        private String subjectType; // ASSET / LIABILITY / EQUITY / COST / PROFIT_LOSS
        private BigDecimal beginDebit;
        private BigDecimal beginCredit;
        /** 余额方向：借/贷（资产类、成本类=借，负债类、权益类=贷） */
        private String balanceDirection;
    }
}
