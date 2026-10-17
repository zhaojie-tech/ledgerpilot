package com.ledgerpilot.voucher.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/**
 * 期初余额保存请求
 */
@Data
public class OpeningBalanceDTO {

    private Integer fiscalYear;
    private Integer periodNo;

    @NotBlank(message = "科目余额列表不能为空")
    private List<SubjectBalance> balances;

    @Data
    public static class SubjectBalance {
        @NotBlank(message = "科目编码不能为空")
        private String subjectCode;

        /** 借方余额（资产类、成本类科目填写） */
        private BigDecimal debitBalance;

        /** 贷方余额（负债类、权益类科目填写） */
        private BigDecimal creditBalance;
    }
}
