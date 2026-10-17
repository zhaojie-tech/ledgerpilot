package com.ledgerpilot.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("acc_subject")
public class AccSubject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String subjectCode;

    private String subjectName;

    private String subjectType; // ASSET / LIABILITY / EQUITY / COST / PROFIT_LOSS

    private Integer level;

    private String parentCode;

    private String direction; // DEBIT / CREDIT

    private Integer isDetail;

    private String auxDimensions;

    private Integer isEnabled;

    private Integer sysSort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Long createdBy;

    private Long updatedBy;
}
