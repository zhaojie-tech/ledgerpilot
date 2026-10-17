package com.ledgerpilot.voucher.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class SubjectCreateRequest {

    @NotBlank(message = "科目编码不能为空")
    private String subjectCode;

    @NotBlank(message = "科目名称不能为空")
    private String subjectName;

    @NotBlank(message = "科目类型不能为空")
    private String subjectType; // ASSET / LIABILITY / EQUITY / COST / PROFIT_LOSS

    private String parentCode; // 上级编码，可为空

    @NotBlank(message = "余额方向不能为空")
    private String direction; // DEBIT / CREDIT

    private Integer isDetail = 0; // 是否末级

    private String auxDimensions; // 逗号分隔: customer,supplier,department,project
}
