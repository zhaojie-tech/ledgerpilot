package com.ledgerpilot.voucher.dto;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 凭证创建请求
 */
@Data
public class VoucherCreateRequest {

    @NotBlank(message = "凭证日期不能为空")
    private String voucherDate;

    private String businessType;

    private String businessNo;

    /** 摘要（可选，自动取第一条分录摘要） */
    private String summary;

    /** 来源：NLP/OCR/MANUAL */
    private String sourceType = "MANUAL";

    @NotEmpty(message = "分录不能为空")
    @Valid
    private List<EntryDTO> entries;

    @Data
    public static class EntryDTO {
        @NotBlank(message = "科目编码不能为空")
        private String subjectCode;
        private String subjectName;
        @NotBlank(message = "方向不能为空")
        private String direction; // DEBIT/CREDIT
        /**
         * 金额（schema v2 先行步）：BigDecimal 承接。
         * Jackson 对 JSON 字符串（"100.00"）与数字（100.00）均可反序列化——前端字符串输入照常兼容；
         * 服务端统一 setScale(2, HALF_UP) 归一，浮点尾巴（如 Double.toString 的
         * 575.2199999999999）在入库前被消灭。
         */
        @NotNull(message = "金额不能为空")
        private BigDecimal amount;
        private String summary;
        /** 辅助核算JSON：{"customer":"xxx","department":"yyy"} */
        private String auxJson;
    }
}
