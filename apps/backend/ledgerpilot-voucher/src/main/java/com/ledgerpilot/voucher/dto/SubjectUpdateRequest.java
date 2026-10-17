package com.ledgerpilot.voucher.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class SubjectUpdateRequest {

    @NotNull(message = "科目ID不能为空")
    private Long id;

    private String subjectName;

    private String subjectType;

    private String auxDimensions;

    private Integer isEnabled;

    private Integer sysSort;
}
