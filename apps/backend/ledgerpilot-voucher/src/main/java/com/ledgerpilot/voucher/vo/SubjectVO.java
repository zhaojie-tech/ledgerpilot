package com.ledgerpilot.voucher.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SubjectVO {

    private Long id;

    private String subjectCode;

    private String subjectName;

    private String subjectType;

    private Integer level;

    private String parentCode;

    private String direction;

    private Integer isDetail;

    private String auxDimensions;

    private Integer isEnabled;

    private Integer sysSort;

    private LocalDateTime createdAt;

    private List<SubjectVO> children;
}
