package com.ledgerpilot.common.calculator.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 工资计算请求
 * 用于计算工资发放及相关社保公积金扣款
 */
public class SalaryCalculateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 工资所属期间，格式 yyyy-MM */
    private String period;

    /** 税前工资总额（应发工资） */
    private BigDecimal grossSalary;

    /** 是否计算社保（养老、医疗、失业、工伤、生育） */
    private Boolean calculateSocial = true;

    /** 是否计算住房公积金 */
    private Boolean calculateHousing = true;

    /** 专项附加扣除（个税前扣除） */
    private BigDecimal specialDeduction = BigDecimal.ZERO;

    /** 子女教育专项附加扣除（每月） */
    private BigDecimal childrenEduDeduction = BigDecimal.ZERO;

    /** 继续教育专项附加扣除（每月） */
    private BigDecimal continuingEduDeduction = BigDecimal.ZERO;

    /** 大病医疗专项附加扣除（年度，摊到每月） */
    private BigDecimal medicalDeduction = BigDecimal.ZERO;

    /** 住房贷款利息专项附加扣除（每月） */
    private BigDecimal housingLoanDeduction = BigDecimal.ZERO;

    /** 住房租金专项附加扣除（每月） */
    private BigDecimal housingRentDeduction = BigDecimal.ZERO;

    /** 赡养老人专项附加扣除（每月） */
    private BigDecimal elderlyCareDeduction = BigDecimal.ZERO;

    /** 员工性质：NORMAL-普通员工，DTP-残疾员工 */
    private String employeeType = "NORMAL";

    /** 社保公积金计算基数（默认等于应发工资，上限为当地封顶基数） */
    private BigDecimal socialBase;

    // ---- Getters & Setters ----

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public BigDecimal getGrossSalary() { return grossSalary; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; }

    public Boolean getCalculateSocial() { return calculateSocial; }
    public void setCalculateSocial(Boolean calculateSocial) { this.calculateSocial = calculateSocial; }

    public Boolean getCalculateHousing() { return calculateHousing; }
    public void setCalculateHousing(Boolean calculateHousing) { this.calculateHousing = calculateHousing; }

    public BigDecimal getSpecialDeduction() { return specialDeduction; }
    public void setSpecialDeduction(BigDecimal specialDeduction) { this.specialDeduction = specialDeduction; }

    public BigDecimal getChildrenEduDeduction() { return childrenEduDeduction; }
    public void setChildrenEduDeduction(BigDecimal childrenEduDeduction) { this.childrenEduDeduction = childrenEduDeduction; }

    public BigDecimal getContinuingEduDeduction() { return continuingEduDeduction; }
    public void setContinuingEduDeduction(BigDecimal continuingEduDeduction) { this.continuingEduDeduction = continuingEduDeduction; }

    public BigDecimal getMedicalDeduction() { return medicalDeduction; }
    public void setMedicalDeduction(BigDecimal medicalDeduction) { this.medicalDeduction = medicalDeduction; }

    public BigDecimal getHousingLoanDeduction() { return housingLoanDeduction; }
    public void setHousingLoanDeduction(BigDecimal housingLoanDeduction) { this.housingLoanDeduction = housingLoanDeduction; }

    public BigDecimal getHousingRentDeduction() { return housingRentDeduction; }
    public void setHousingRentDeduction(BigDecimal housingRentDeduction) { this.housingRentDeduction = housingRentDeduction; }

    public BigDecimal getElderlyCareDeduction() { return elderlyCareDeduction; }
    public void setElderlyCareDeduction(BigDecimal elderlyCareDeduction) { this.elderlyCareDeduction = elderlyCareDeduction; }

    public String getEmployeeType() { return employeeType; }
    public void setEmployeeType(String employeeType) { this.employeeType = employeeType; }

    public BigDecimal getSocialBase() { return socialBase; }
    public void setSocialBase(BigDecimal socialBase) { this.socialBase = socialBase; }

    /** 汇总所有专项附加扣除（按月） */
    public BigDecimal getTotalMonthlySpecialDeduction() {
        BigDecimal total = BigDecimal.ZERO;
        if (specialDeduction != null) total = total.add(specialDeduction);
        if (childrenEduDeduction != null) total = total.add(childrenEduDeduction);
        if (continuingEduDeduction != null) total = total.add(continuingEduDeduction);
        if (medicalDeduction != null) total = total.add(medicalDeduction.divide(new BigDecimal("12"), 2, java.math.RoundingMode.DOWN));
        if (housingLoanDeduction != null) total = total.add(housingLoanDeduction);
        if (housingRentDeduction != null) total = total.add(housingRentDeduction);
        if (elderlyCareDeduction != null) total = total.add(elderlyCareDeduction);
        return total;
    }
}
