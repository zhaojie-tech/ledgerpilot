package com.ledgerpilot.common.calculator.service;

import com.ledgerpilot.common.entity.RateConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工资计算器黄金用例：确定性、可审计——费率全部桩定，金额精确断言。
 */
class SalaryCalculatorGoldenTest {

    private SalaryCalculatorService service;

    /** 费率桩：类别+子类别 → 费率 */
    private static RateConfig rate(String category, String sub, Double personal, Double employer, Double fixed) {
        RateConfig r = new RateConfig();
        r.setCategory(category);
        r.setSubCategory(sub);
        r.setEnabled(1);
        r.setPersonalRate(personal == null ? null : BigDecimal.valueOf(personal));
        r.setEmployerRate(employer == null ? null : BigDecimal.valueOf(employer));
        r.setFixedRate(fixed == null ? null : BigDecimal.valueOf(fixed));
        return r;
    }

    @BeforeEach
    void setUp() throws Exception {
        RateConfigServiceInterface stub = mock(RateConfigServiceInterface.class);
        when(stub.getByCategory(eq(1L), anyString(), anyString())).thenAnswer(inv -> {
            String category = inv.getArgument(1);
            String sub = inv.getArgument(2);
            switch (category + ":" + sub) {
                case "social:pension":      return rate("social", "pension", 0.08, 0.16, null);
                case "social:medical":      return rate("social", "medical", 0.02, 0.08, null);
                case "social:unemployment": return rate("social", "unemployment", 0.005, 0.005, null);
                case "social:work_injury":  return rate("social", "work_injury", 0.0, 0.002, null);
                case "social:maternity":    return rate("social", "maternity", 0.0, 0.008, null);
                case "housing:housing_fund": return rate("housing", "housing_fund", 0.12, 0.12, null);
                default: return null;
            }
        });

        service = new SalaryCalculatorService();
        Field f = SalaryCalculatorService.class.getDeclaredField("rateConfigService");
        f.setAccessible(true);
        f.set(service, stub);
    }

    private com.ledgerpilot.common.calculator.dto.SalaryCalculateRequest request(double gross) {
        com.ledgerpilot.common.calculator.dto.SalaryCalculateRequest req =
                new com.ledgerpilot.common.calculator.dto.SalaryCalculateRequest();
        req.setTenantId(1L);
        req.setPeriod("2026-10");
        req.setGrossSalary(BigDecimal.valueOf(gross));
        req.setCalculateSocial(true);
        req.setCalculateHousing(true);
        return req;
    }

    @Test
    void golden_gross30000_standardRates() {
        var resp = service.calculate(request(30000));
        // 个人：养老 8%=2400.00，医疗 2%=600.00，失业 0.5%=150.00；公积金 12%=3600.00
        assertEquals(new BigDecimal("2400.00"), resp.getPersonalPension());
        assertEquals(new BigDecimal("600.00"), resp.getPersonalMedical());
        assertEquals(new BigDecimal("150.00"), resp.getPersonalUnemployment());
        assertEquals(new BigDecimal("3600.00"), resp.getPersonalHousing());
        // 单位：养老 16%=4800，医疗 8%=2400，失业 0.5%=150，工伤 0.2%=60，生育 0.8%=240
        // 注意语义（黄金锁定）：employerSocialTotal 包含公积金单位部分
        assertEquals(new BigDecimal("11250.00"), resp.getEmployerSocialTotal());
        assertEquals(new BigDecimal("3600.00"), resp.getEmployerHousing());
    }

    @Test
    void golden_personalTotalIdentity() {
        // 语义（黄金锁定）：personalDeductionTotal = 社保个人 + 公积金个人 + 个税
        var resp = service.calculate(request(30000));
        BigDecimal personal = resp.getPersonalPension().add(resp.getPersonalMedical())
                .add(resp.getPersonalUnemployment()).add(resp.getPersonalHousing());
        BigDecimal tax = resp.getPersonalIncomeTax() == null ? BigDecimal.ZERO : resp.getPersonalIncomeTax();
        assertEquals(personal.add(tax), resp.getPersonalDeductionTotal());
    }

    @Test
    void golden_netEqualsGrossMinusPersonalDeductions() {
        // 语义（黄金锁定）：实发 = 应发 - 个人扣款合计（社保公积金个人 + 个税）
        var resp = service.calculate(request(30000));
        assertEquals(resp.getGrossSalary().subtract(resp.getPersonalDeductionTotal()), resp.getNetSalary());
    }

    @Test
    void golden_socialBaseOverride() {
        var req = request(30000);
        req.setSocialBase(BigDecimal.valueOf(20000));
        var resp = service.calculate(req);
        // 养老按基数：20000 × 8% = 1600.00
        assertEquals(new BigDecimal("1600.00"), resp.getPersonalPension());
    }

    @Test
    void golden_oddGross_roundsHalfUpAt2() {
        // 12345.678 × 8% = 987.65424 → 987.65（HALF_UP，标度 2）
        var resp = service.calculate(request(12345.678));
        assertEquals(new BigDecimal("987.65"), resp.getPersonalPension());
        // 失业 12345.678 × 0.5% = 61.72839 → 61.73
        assertEquals(new BigDecimal("61.73"), resp.getPersonalUnemployment());
    }
}
