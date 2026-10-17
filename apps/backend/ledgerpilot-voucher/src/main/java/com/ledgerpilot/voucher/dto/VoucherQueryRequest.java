package com.ledgerpilot.voucher.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * 凭证查询请求
 */
@Data
public class VoucherQueryRequest {

    /** 凭证号（模糊匹配） */
    private String voucherNo;

    /** 凭证日期-开始 */
    private LocalDate dateFrom;

    /** 凭证日期-结束 */
    private LocalDate dateTo;

    /** 业务类型 */
    private String businessType;

    /** 凭证状态 */
    private String status;

    /** 来源类型：NLP/OCR/MANUAL */
    private String sourceType;

    /** 当前页 */
    private Integer current = 1;

    /** 每页大小 */
    private Integer size = 20;
}
