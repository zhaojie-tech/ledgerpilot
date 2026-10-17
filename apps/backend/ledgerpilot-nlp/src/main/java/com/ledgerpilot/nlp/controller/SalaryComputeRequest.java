package com.ledgerpilot.nlp.controller;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 工资计算并生成凭证请求
 */
@Data
public class SalaryComputeRequest {

    /** 工资所属期（格式：yyyy-MM，如 2026-04） */
    private String period;

    /** 应发工资（税前） */
    private BigDecimal grossSalary;

    /** 社保公积金计算基数（默认等于应发工资） */
    private BigDecimal socialBase;

    /** 是否计算社保公积金 */
    private Boolean calculateSocial = true;

    /** 是否计算住房公积金 */
    private Boolean calculateHousing = true;

    /** 员工类型：NORMAL / CONTRACT / POSTDOC */
    private String employeeType = "NORMAL";

    /** 专项附加扣除：子女教育 */
    private BigDecimal childrenEduDeduction;

    /** 专项附加扣除：继续教育 */
    private BigDecimal continuingEduDeduction;

    /** 专项附加扣除：职业资格继续教育 */
    private BigDecimal professionalEduDeduction;

    /** 专项附加扣除：大病医疗 */
    private BigDecimal seriousIllnessDeduction;

    /** 专项附加扣除：住房贷款利息 */
    private BigDecimal housingLoanDeduction;

    /** 专项附加扣除：住房租金 */
    private BigDecimal housingRentDeduction;

    /** 专项附加扣除：赡养老人 */
    private BigDecimal elderlyCareDeduction;

    /** 凭证日期（格式：yyyy-MM-dd），默认当天 */
    private String voucherDate;
}
