package com.ledgerpilot.common.calculator.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 工资计算响应
 * 包含完整的工资条明细
 */
public class SalaryCalculateResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 工资应发项 ====================
    /** 应发工资（税前工资） */
    private BigDecimal grossSalary;

    // ==================== 个人扣款项（从工资中扣除） ====================
    /** 养老保险个人 */
    private BigDecimal personalPension;
    /** 医疗保险个人 */
    private BigDecimal personalMedical;
    /** 失业保险个人 */
    private BigDecimal personalUnemployment;
    /** 工伤保险个人（部分地区不扣除） */
    private BigDecimal personalWorkInjury;
    /** 生育保险个人（部分地区不扣除） */
    private BigDecimal personalMaternity;
    /** 住房公积金个人 */
    private BigDecimal personalHousing;
    /** 个人社保公积金合计 */
    private BigDecimal personalSocialTotal;
    /** 代扣个人所得税 */
    private BigDecimal personalIncomeTax;
    /** 个人扣款合计 */
    private BigDecimal personalDeductionTotal;

    // ==================== 单位缴费项（企业承担） ====================
    /** 养老保险单位 */
    private BigDecimal employerPension;
    /** 医疗保险单位 */
    private BigDecimal employerMedical;
    /** 失业保险单位 */
    private BigDecimal employerUnemployment;
    /** 工伤保险单位 */
    private BigDecimal employerWorkInjury;
    /** 生育保险单位 */
    private BigDecimal employerMaternity;
    /** 住房公积金单位 */
    private BigDecimal employerHousing;
    /** 单位社保公积金合计 */
    private BigDecimal employerSocialTotal;

    // ==================== 实发项 ====================
    /** 实发工资 = 应发 - 个人扣款 */
    private BigDecimal netSalary;

    // ==================== 明细项目列表 ====================
    private List<SalaryItem> items = new ArrayList<>();

    // ==================== 辅助信息 ====================
    /** 社保公积金计算基数 */
    private BigDecimal socialBase;
    /** 适用税率（个税） */
    private BigDecimal taxRate;
    /** 速算扣除数（个税） */
    private BigDecimal quickDeduction;
    /** 备注/提示信息 */
    private String remark;

    // ---- Getters & Setters ----

    public BigDecimal getGrossSalary() { return grossSalary; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; }

    public BigDecimal getPersonalPension() { return personalPension; }
    public void setPersonalPension(BigDecimal personalPension) { this.personalPension = personalPension; }

    public BigDecimal getPersonalMedical() { return personalMedical; }
    public void setPersonalMedical(BigDecimal personalMedical) { this.personalMedical = personalMedical; }

    public BigDecimal getPersonalUnemployment() { return personalUnemployment; }
    public void setPersonalUnemployment(BigDecimal personalUnemployment) { this.personalUnemployment = personalUnemployment; }

    public BigDecimal getPersonalWorkInjury() { return personalWorkInjury; }
    public void setPersonalWorkInjury(BigDecimal personalWorkInjury) { this.personalWorkInjury = personalWorkInjury; }

    public BigDecimal getPersonalMaternity() { return personalMaternity; }
    public void setPersonalMaternity(BigDecimal personalMaternity) { this.personalMaternity = personalMaternity; }

    public BigDecimal getPersonalHousing() { return personalHousing; }
    public void setPersonalHousing(BigDecimal personalHousing) { this.personalHousing = personalHousing; }

    public BigDecimal getPersonalSocialTotal() { return personalSocialTotal; }
    public void setPersonalSocialTotal(BigDecimal personalSocialTotal) { this.personalSocialTotal = personalSocialTotal; }

    public BigDecimal getPersonalIncomeTax() { return personalIncomeTax; }
    public void setPersonalIncomeTax(BigDecimal personalIncomeTax) { this.personalIncomeTax = personalIncomeTax; }

    public BigDecimal getPersonalDeductionTotal() { return personalDeductionTotal; }
    public void setPersonalDeductionTotal(BigDecimal personalDeductionTotal) { this.personalDeductionTotal = personalDeductionTotal; }

    public BigDecimal getEmployerPension() { return employerPension; }
    public void setEmployerPension(BigDecimal employerPension) { this.employerPension = employerPension; }

    public BigDecimal getEmployerMedical() { return employerMedical; }
    public void setEmployerMedical(BigDecimal employerMedical) { this.employerMedical = employerMedical; }

    public BigDecimal getEmployerUnemployment() { return employerUnemployment; }
    public void setEmployerUnemployment(BigDecimal employerUnemployment) { this.employerUnemployment = employerUnemployment; }

    public BigDecimal getEmployerWorkInjury() { return employerWorkInjury; }
    public void setEmployerWorkInjury(BigDecimal employerWorkInjury) { this.employerWorkInjury = employerWorkInjury; }

    public BigDecimal getEmployerMaternity() { return employerMaternity; }
    public void setEmployerMaternity(BigDecimal employerMaternity) { this.employerMaternity = employerMaternity; }

    public BigDecimal getEmployerHousing() { return employerHousing; }
    public void setEmployerHousing(BigDecimal employerHousing) { this.employerHousing = employerHousing; }

    public BigDecimal getEmployerSocialTotal() { return employerSocialTotal; }
    public void setEmployerSocialTotal(BigDecimal employerSocialTotal) { this.employerSocialTotal = employerSocialTotal; }

    public BigDecimal getNetSalary() { return netSalary; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }

    public List<SalaryItem> getItems() { return items; }
    public void setItems(List<SalaryItem> items) { this.items = items; }

    public BigDecimal getSocialBase() { return socialBase; }
    public void setSocialBase(BigDecimal socialBase) { this.socialBase = socialBase; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public BigDecimal getQuickDeduction() { return quickDeduction; }
    public void setQuickDeduction(BigDecimal quickDeduction) { this.quickDeduction = quickDeduction; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    // ---- 内部类：明细项 ----
    public static class SalaryItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 科目名称 */
        private String name;
        /** 金额 */
        private BigDecimal amount;
        /** 类型：SALARY-应发项, PERSONAL-个人扣款, EMPLOYER-单位缴费, TAX-个税, NET-实发 */
        private String type;
        /** 计算说明 */
        private String formula;

        public SalaryItem() {}

        public SalaryItem(String name, BigDecimal amount, String type, String formula) {
            this.name = name;
            this.amount = amount;
            this.type = type;
            this.formula = formula;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getFormula() { return formula; }
        public void setFormula(String formula) { this.formula = formula; }
    }
}
