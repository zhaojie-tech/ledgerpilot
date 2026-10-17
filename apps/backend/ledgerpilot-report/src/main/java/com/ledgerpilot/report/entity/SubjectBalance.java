package com.ledgerpilot.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科目余额表实体（报表模块副本，避免依赖 voucher 模块）
 */
@Data
@TableName("acc_subject_balance")
public class SubjectBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String subjectCode;
    private Integer fiscalYear;
    private Integer periodNo;

    /** 期初借方余额 */
    private BigDecimal beginDebit;

    /** 期初贷方余额 */
    private BigDecimal beginCredit;

    /** 本期借方发生 */
    private BigDecimal currentDebit;

    /** 本期贷方发生 */
    private BigDecimal currentCredit;

    /** 期末借方余额 */
    private BigDecimal endDebit;

    /** 期末贷方余额 */
    private BigDecimal endCredit;

    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
