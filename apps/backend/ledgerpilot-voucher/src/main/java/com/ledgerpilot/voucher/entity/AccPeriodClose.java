package com.ledgerpilot.voucher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会计期间关账记录
 */
@Data
@TableName("acc_period_close")
public class AccPeriodClose {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("fiscal_year")
    private Integer fiscalYear;

    @TableField("period_no")
    private Integer periodNo;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("closed_by")
    private Long closedBy;

    @TableField("transfer_voucher_id")
    private Long transferVoucherId;

    @TableField("remark")
    private String remark;
}
