package com.ledgerpilot.voucher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 凭证详情VO（包含主表+分录列表）
 */
@Data
public class VoucherDetailVO {

    // ========== 主表字段 ==========
    private Long id;
    private Long tenantId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate voucherDate;

    private String voucherNo;
    private String businessType;
    private String businessNo;

    /** 借方合计 */
    private BigDecimal totalDebit;

    /** 贷方合计 */
    private BigDecimal totalCredit;

    private Integer entryCount;
    private String status;
    private String sourceType;
    private String summary;

    private Long approveBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime approveAt;

    private Long postBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime postAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private String createdByName;
    private String approveByName;
    private String postByName;

    // ========== 分录列表 ==========
    private List<VoucherEntryVO> entries;

    // ========== 分录VO ==========
    @Data
    public static class VoucherEntryVO {
        private Long id;
        private Integer seq;
        private Long subjectId;
        private String subjectCode;
        private String subjectName;
        private String direction;   // DEBIT / CREDIT
        private BigDecimal amount;
        private String summary;
        private String auxJson;    // 辅助核算JSON
        private String auxDisplay; // 辅助核算展示文本
    }
}
