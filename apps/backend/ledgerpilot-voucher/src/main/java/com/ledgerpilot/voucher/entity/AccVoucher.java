package com.ledgerpilot.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ledgerpilot.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 凭证主表
 * id/tenantId/createdAt/updatedAt/deleted/createdBy/updatedBy 继承自 BaseEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_voucher")
public class AccVoucher extends BaseEntity {

    /** 凭证号（按月/按年生成） */
    private String voucherNo;

    /** 凭证日期 */
    private LocalDate voucherDate;

    /** 业务类型 */
    private String businessType;

    /** 业务单号 */
    private String businessNo;

    /** 借方合计 */
    private BigDecimal totalDebit;

    /** 贷方合计 */
    private BigDecimal totalCredit;

    /** 分录条数 */
    private Integer entryCount;

    /** DRAFT / APPROVED / POSTED / VOIDED */
    private String status;

    /** NLP / OCR / MANUAL */
    private String sourceType;

    /** 凭证摘要 */
    private String summary;

    private Long approveBy;
    private LocalDateTime approveAt;
    private Long postBy;
    private LocalDateTime postAt;
}
