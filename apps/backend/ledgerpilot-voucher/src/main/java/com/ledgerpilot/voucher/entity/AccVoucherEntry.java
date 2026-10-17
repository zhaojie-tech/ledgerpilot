package com.ledgerpilot.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ledgerpilot.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 凭证分录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_voucher_entry")
public class AccVoucherEntry extends BaseEntity {

    /**
     * 创建人ID — 分录表无此字段，覆盖父类
     */
    @TableField(exist = false)
    private Long createdBy;

    /**
     * 更新人ID — 分录表无此字段，覆盖父类
     */
    @TableField(exist = false)
    private Long updatedBy;

    /** 凭证ID */
    private Long voucherId;

    /** 分录序号 */
    private Integer seq;

    /** 科目ID */
    private Long subjectId = 0L;

    /** 科目编码（冗余存储） */
    private String subjectCode;

    /** 科目名称（冗余存储） */
    private String subjectName;

    /** DEBIT / CREDIT */
    private String direction;

    /** 金额 */
    private BigDecimal amount;

    /** 分录摘要 */
    private String summary;

    /** 辅助核算JSON */
    private String auxJson;
}
