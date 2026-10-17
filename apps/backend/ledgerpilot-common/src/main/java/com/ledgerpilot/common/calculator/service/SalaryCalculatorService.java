package com.ledgerpilot.common.calculator.service;

import com.ledgerpilot.common.calculator.dto.SalaryCalculateRequest;
import com.ledgerpilot.common.calculator.dto.SalaryCalculateResponse;
import com.ledgerpilot.common.entity.RateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 工资计算服务
 *
 * 确定性工资计算引擎，从 rate_config 表读取实际费率，
 * 计算个人社保公积金扣款、单位社保公积金缴费、以及个人所得税。
 *
 * 计算逻辑完全确定性，不依赖 LLM，结果可审计、可复现。
 */
@Service
public class SalaryCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(SalaryCalculatorService.class);

    /** 金额精度：保留2位小数 */
    private static final int SCALE = 2;

    /** 养老保险-子分类 */
    private static final String SC_PENSION = "pension";
    /** 医疗保险-子分类 */
    private static final String SC_MEDICAL = "medical";
    /** 失业保险-子分类 */
    private static final String SC_UNEMPLOYMENT = "unemployment";
    /** 工伤保险-子分类 */
    private static final String SC_WORK_INJURY = "work_injury";
    /** 生育保险-子分类 */
    private static final String SC_MATURITY = "maternity";
    /** 住房公积金-子分类 */
    private static final String SC_HOUSING = "housing_fund";

    @Autowired
    private RateConfigServiceInterface rateConfigService;

    /**
     * 计算工资发放及相关扣款
     *
     * @param request 工资计算请求
     * @return 完整工资条明细
     */
    public SalaryCalculateResponse calculate(SalaryCalculateRequest request) {
        Long tenantId = request.getTenantId();
        BigDecimal grossSalary = request.getGrossSalary();
        BigDecimal socialBase = request.getSocialBase() != null
                ? request.getSocialBase() : grossSalary;

        SalaryCalculateResponse resp = new SalaryCalculateResponse();
        List<SalaryCalculateResponse.SalaryItem> items = new ArrayList<>();
        resp.setItems(items);

        // 精度处理
        socialBase = socialBase.setScale(SCALE, RoundingMode.HALF_UP);
        resp.setSocialBase(socialBase);

        // ===================== 应发工资 =====================
        items.add(new SalaryCalculateResponse.SalaryItem(
                "应发工资", grossSalary, "SALARY",
                "税前应发工资总额"
        ));
        resp.setGrossSalary(grossSalary);

        // ===================== 个人社保公积金扣款 =====================
        BigDecimal personalSocialTotal = BigDecimal.ZERO;

        if (Boolean.TRUE.equals(request.getCalculateSocial())) {
            // 养老保险
            RateConfig pension = rateConfigService.getByCategory(tenantId, "social", SC_PENSION);
            if (pension != null && pension.getEnabled() == 1 && pension.getPersonalRate() != null) {
                BigDecimal amount = socialBase.multiply(pension.getPersonalRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setPersonalPension(amount);
                personalSocialTotal = personalSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "养老保险（个人）", amount.negate(), "PERSONAL",
                        socialBase + " × " + pension.getPersonalRate() + "%"
                ));
            }

            // 医疗保险
            RateConfig medical = rateConfigService.getByCategory(tenantId, "social", SC_MEDICAL);
            if (medical != null && medical.getEnabled() == 1 && medical.getPersonalRate() != null) {
                BigDecimal amount = socialBase.multiply(medical.getPersonalRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setPersonalMedical(amount);
                personalSocialTotal = personalSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "医疗保险（个人）", amount.negate(), "PERSONAL",
                        socialBase + " × " + medical.getPersonalRate() + "%"
                ));
            }

            // 失业保险
            RateConfig unemployment = rateConfigService.getByCategory(tenantId, "social", SC_UNEMPLOYMENT);
            if (unemployment != null && unemployment.getEnabled() == 1 && unemployment.getPersonalRate() != null) {
                BigDecimal amount = socialBase.multiply(unemployment.getPersonalRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setPersonalUnemployment(amount);
                personalSocialTotal = personalSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "失业保险（个人）", amount.negate(), "PERSONAL",
                        socialBase + " × " + unemployment.getPersonalRate() + "%"
                ));
            }

            // 工伤保险（个人不扣费，部分地区为0）
            RateConfig workInjury = rateConfigService.getByCategory(tenantId, "social", SC_WORK_INJURY);
            if (workInjury != null && workInjury.getEnabled() == 1 && workInjury.getPersonalRate() != null
                    && workInjury.getPersonalRate().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal amount = socialBase.multiply(workInjury.getPersonalRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setPersonalWorkInjury(amount);
                personalSocialTotal = personalSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "工伤保险（个人）", amount.negate(), "PERSONAL",
                        socialBase + " × " + workInjury.getPersonalRate() + "%"
                ));
            }

            // 生育保险（个人不扣费，部分地区为0）
            RateConfig maternity = rateConfigService.getByCategory(tenantId, "social", SC_MATURITY);
            if (maternity != null && maternity.getEnabled() == 1 && maternity.getPersonalRate() != null
                    && maternity.getPersonalRate().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal amount = socialBase.multiply(maternity.getPersonalRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setPersonalMaternity(amount);
                personalSocialTotal = personalSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "生育保险（个人）", amount.negate(), "PERSONAL",
                        socialBase + " × " + maternity.getPersonalRate() + "%"
                ));
            }
        }

        // 住房公积金
        if (Boolean.TRUE.equals(request.getCalculateHousing())) {
            RateConfig housing = rateConfigService.getByCategory(tenantId, "housing", SC_HOUSING);
            if (housing != null && housing.getEnabled() == 1 && housing.getPersonalRate() != null) {
                BigDecimal amount = socialBase.multiply(housing.getPersonalRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setPersonalHousing(amount);
                personalSocialTotal = personalSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "住房公积金（个人）", amount.negate(), "PERSONAL",
                        socialBase + " × " + housing.getPersonalRate() + "%"
                ));
            }
        }

        resp.setPersonalSocialTotal(personalSocialTotal);

        // ===================== 个人所得税 =====================
        // 应税工资 = 应发工资 - 社保公积金个人 - 5000起征点 - 专项附加扣除
        BigDecimal taxableBase = grossSalary
                .subtract(personalSocialTotal)
                .subtract(new BigDecimal("5000"))
                .subtract(request.getTotalMonthlySpecialDeduction())
                .setScale(SCALE, RoundingMode.HALF_UP);

        if (taxableBase.compareTo(BigDecimal.ZERO) < 0) {
            taxableBase = BigDecimal.ZERO;
        }

        BigDecimal personalTax = calculateIndividualIncomeTax(taxableBase);
        resp.setPersonalIncomeTax(personalTax);
        items.add(new SalaryCalculateResponse.SalaryItem(
                "代扣个人所得税", personalTax.negate(), "TAX",
                "应税工资 " + taxableBase + " - 5000 - 专项附加 " + request.getTotalMonthlySpecialDeduction()
        ));

        // 个人扣款合计
        BigDecimal personalDeductionTotal = personalSocialTotal.add(personalTax);
        resp.setPersonalDeductionTotal(personalDeductionTotal);

        // ===================== 单位社保公积金 =====================
        BigDecimal employerSocialTotal = BigDecimal.ZERO;

        if (Boolean.TRUE.equals(request.getCalculateSocial())) {
            // 养老单位
            RateConfig pensionE = rateConfigService.getByCategory(tenantId, "social", SC_PENSION);
            if (pensionE != null && pensionE.getEnabled() == 1 && pensionE.getEmployerRate() != null) {
                BigDecimal amount = socialBase.multiply(pensionE.getEmployerRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setEmployerPension(amount);
                employerSocialTotal = employerSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "养老保险（单位）", amount, "EMPLOYER",
                        socialBase + " × " + pensionE.getEmployerRate() + "%"
                ));
            }

            // 医疗单位
            RateConfig medicalE = rateConfigService.getByCategory(tenantId, "social", SC_MEDICAL);
            if (medicalE != null && medicalE.getEnabled() == 1 && medicalE.getEmployerRate() != null) {
                BigDecimal amount = socialBase.multiply(medicalE.getEmployerRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setEmployerMedical(amount);
                employerSocialTotal = employerSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "医疗保险（单位）", amount, "EMPLOYER",
                        socialBase + " × " + medicalE.getEmployerRate() + "%"
                ));
            }

            // 失业单位
            RateConfig unemploymentE = rateConfigService.getByCategory(tenantId, "social", SC_UNEMPLOYMENT);
            if (unemploymentE != null && unemploymentE.getEnabled() == 1 && unemploymentE.getEmployerRate() != null) {
                BigDecimal amount = socialBase.multiply(unemploymentE.getEmployerRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setEmployerUnemployment(amount);
                employerSocialTotal = employerSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "失业保险（单位）", amount, "EMPLOYER",
                        socialBase + " × " + unemploymentE.getEmployerRate() + "%"
                ));
            }

            // 工伤单位
            RateConfig workInjuryE = rateConfigService.getByCategory(tenantId, "social", SC_WORK_INJURY);
            if (workInjuryE != null && workInjuryE.getEnabled() == 1 && workInjuryE.getEmployerRate() != null) {
                BigDecimal amount = socialBase.multiply(workInjuryE.getEmployerRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setEmployerWorkInjury(amount);
                employerSocialTotal = employerSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "工伤保险（单位）", amount, "EMPLOYER",
                        socialBase + " × " + workInjuryE.getEmployerRate() + "%"
                ));
            }

            // 生育单位
            RateConfig maternityE = rateConfigService.getByCategory(tenantId, "social", SC_MATURITY);
            if (maternityE != null && maternityE.getEnabled() == 1 && maternityE.getEmployerRate() != null) {
                BigDecimal amount = socialBase.multiply(maternityE.getEmployerRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setEmployerMaternity(amount);
                employerSocialTotal = employerSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "生育保险（单位）", amount, "EMPLOYER",
                        socialBase + " × " + maternityE.getEmployerRate() + "%"
                ));
            }
        }

        // 住房公积金单位
        if (Boolean.TRUE.equals(request.getCalculateHousing())) {
            RateConfig housingE = rateConfigService.getByCategory(tenantId, "housing", SC_HOUSING);
            if (housingE != null && housingE.getEnabled() == 1 && housingE.getEmployerRate() != null) {
                BigDecimal amount = socialBase.multiply(housingE.getEmployerRate())
                        .setScale(SCALE, RoundingMode.HALF_UP);
                resp.setEmployerHousing(amount);
                employerSocialTotal = employerSocialTotal.add(amount);
                items.add(new SalaryCalculateResponse.SalaryItem(
                        "住房公积金（单位）", amount, "EMPLOYER",
                        socialBase + " × " + housingE.getEmployerRate() + "%"
                ));
            }
        }

        resp.setEmployerSocialTotal(employerSocialTotal);

        // ===================== 实发工资 =====================
        BigDecimal netSalary = grossSalary.subtract(personalDeductionTotal)
                .setScale(SCALE, RoundingMode.HALF_UP);
        resp.setNetSalary(netSalary);
        items.add(new SalaryCalculateResponse.SalaryItem(
                "实发工资", netSalary, "NET",
                "应发 " + grossSalary + " - 个人扣款 " + personalDeductionTotal
        ));

        // ===================== 总成本 =====================
        BigDecimal totalCost = grossSalary.add(employerSocialTotal)
                .setScale(SCALE, RoundingMode.HALF_UP);
        items.add(new SalaryCalculateResponse.SalaryItem(
                "人工总成本（单位）", totalCost, "SALARY",
                "应发工资 + 单位社保公积金 " + employerSocialTotal
        ));

        log.info("[工资计算] tenantId={}, 应发={}, 实发={}, 单位成本={}, 个人税={}, 社保个人={}",
                tenantId, grossSalary, netSalary, totalCost, personalTax, personalSocialTotal);

        return resp;
    }

    /**
     * 计算个人所得税（按月计算，累计预扣法简化版）
     * 使用七级超额累进税率（2019年后标准）
     *
     * @param taxableMonthlyIncome 月应税工资（已扣除社保、5000起征点、专项附加）
     * @return 月度个人所得税
     */
    private BigDecimal calculateIndividualIncomeTax(BigDecimal taxableMonthlyIncome) {
        if (taxableMonthlyIncome == null || taxableMonthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 七级超额累进税率
        BigDecimal tax;
        BigDecimal quickDeduction;

        if (taxableMonthlyIncome.compareTo(new BigDecimal("3000")) <= 0) {
            // 第1级：不超过3000元，税率3%，速算扣除数0
            tax = taxableMonthlyIncome.multiply(new BigDecimal("0.03"));
            quickDeduction = BigDecimal.ZERO;
            tax = tax.setScale(SCALE, RoundingMode.HALF_UP);
        } else if (taxableMonthlyIncome.compareTo(new BigDecimal("12000")) <= 0) {
            // 第2级：超过3000至12000元部分，税率10%，速算扣除数210
            tax = taxableMonthlyIncome.multiply(new BigDecimal("0.10"))
                    .subtract(new BigDecimal("210"));
            quickDeduction = new BigDecimal("210");
            tax = tax.setScale(SCALE, RoundingMode.HALF_UP);
        } else if (taxableMonthlyIncome.compareTo(new BigDecimal("25000")) <= 0) {
            // 第3级：超过12000至25000元部分，税率20%，速算扣除数1410
            tax = taxableMonthlyIncome.multiply(new BigDecimal("0.20"))
                    .subtract(new BigDecimal("1410"));
            quickDeduction = new BigDecimal("1410");
            tax = tax.setScale(SCALE, RoundingMode.HALF_UP);
        } else if (taxableMonthlyIncome.compareTo(new BigDecimal("35000")) <= 0) {
            // 第4级：超过25000至35000元部分，税率25%，速算扣除数2660
            tax = taxableMonthlyIncome.multiply(new BigDecimal("0.25"))
                    .subtract(new BigDecimal("2660"));
            quickDeduction = new BigDecimal("2660");
            tax = tax.setScale(SCALE, RoundingMode.HALF_UP);
        } else if (taxableMonthlyIncome.compareTo(new BigDecimal("55000")) <= 0) {
            // 第5级：超过35000至55000元部分，税率30%，速算扣除数4410
            tax = taxableMonthlyIncome.multiply(new BigDecimal("0.30"))
                    .subtract(new BigDecimal("4410"));
            quickDeduction = new BigDecimal("4410");
            tax = tax.setScale(SCALE, RoundingMode.HALF_UP);
        } else if (taxableMonthlyIncome.compareTo(new BigDecimal("80000")) <= 0) {
            // 第6级：超过55000至80000元部分，税率35%，速算扣除数7160
            tax = taxableMonthlyIncome.multiply(new BigDecimal("0.35"))
                    .subtract(new BigDecimal("7160"));
            quickDeduction = new BigDecimal("7160");
            tax = tax.setScale(SCALE, RoundingMode.HALF_UP);
        } else {
            // 第7级：超过80000元部分，税率45%，速算扣除数15160
            tax = taxableMonthlyIncome.multiply(new BigDecimal("0.45"))
                    .subtract(new BigDecimal("15160"));
            quickDeduction = new BigDecimal("15160");
            tax = tax.setScale(SCALE, RoundingMode.HALF_UP);
        }

        return tax.max(BigDecimal.ZERO);
    }
}
