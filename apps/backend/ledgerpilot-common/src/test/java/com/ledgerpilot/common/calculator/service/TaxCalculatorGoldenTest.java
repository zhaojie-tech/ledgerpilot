package com.ledgerpilot.common.calculator.service;

import com.ledgerpilot.common.entity.RateConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 税务计算器黄金用例：费率桩定，金额精确断言。
 */
class TaxCalculatorGoldenTest {

    private TaxCalculatorService service;

    private static RateConfig rate(String sub, double fixed) {
        RateConfig r = new RateConfig();
        r.setCategory("vat");
        r.setSubCategory(sub);
        r.setEnabled(1);
        r.setFixedRate(BigDecimal.valueOf(fixed));
        return r;
    }

    @BeforeEach
    void setUp() throws Exception {
        RateConfigServiceInterface stub = mock(RateConfigServiceInterface.class);
        when(stub.getByCategory(eq(1L), eq("vat"), eq(null))).thenReturn(rate("general_13", 0.13));
        when(stub.getByCategory(eq(1L), eq("corp_tax"), eq(null))).thenReturn(rate("small_profit_20", 0.20));
        when(stub.getByCategory(eq(1L), anyString(), anyString())).thenReturn(null); // 附加税走默认税率

        service = new TaxCalculatorService();
        Field f = TaxCalculatorService.class.getDeclaredField("rateConfigService");
        f.setAccessible(true);
        f.set(service, stub);
    }

    @Test
    void vat_golden() {
        // 收入 100000×13% = 13000；成本 60000×13%×60% = 4680
        var r = service.calculateVAT(1L, new BigDecimal("100000"), new BigDecimal("60000"), new BigDecimal("0.6"));
        assertEquals(new BigDecimal("13000.00"), r.getOutputVAT());
        assertEquals(new BigDecimal("-4680.00"), r.getInputVAT());
        assertEquals(new BigDecimal("100000"), r.getTaxBase());
    }

    @Test
    void cit_profitable_smallProfit20() {
        // 应税利润 = 200000-120000-30000 = 50000 × 20% = 10000.00
        var r = service.calculateCIT(1L, new BigDecimal("200000"),
                new BigDecimal("120000"), new BigDecimal("30000"));
        assertEquals(new BigDecimal("50000"), r.getTaxableProfit());
        assertEquals("small_profit_20", r.getTaxRateType());
        assertEquals(new BigDecimal("10000.00"), r.getCitAmount());
    }

    @Test
    void cit_lossClampedToZero() {
        // 亏损：应税利润按 0，税额 0
        var r = service.calculateCIT(1L, new BigDecimal("100"), new BigDecimal("500"), new BigDecimal("300"));
        assertEquals(BigDecimal.ZERO.compareTo(r.getTaxableProfit()), 0);
        assertEquals(new BigDecimal("0.00"), r.getCitAmount());
    }

    @Test
    void surtax_defaultRates() {
        // 无配置走默认：城建 7%、教育 3%、地方教育 2%（基数 10000）
        var r = service.calculateSurtax(1L, new BigDecimal("10000"));
        assertEquals(new BigDecimal("700.00"), r.getUrbanConstruction());
        assertEquals(new BigDecimal("300.00"), r.getEduSurtax());
        assertEquals(new BigDecimal("200.00"), r.getLocalEduSurtax());
        assertEquals(new BigDecimal("1200.00"), r.getTotal());
    }

    @Test
    void surtax_zeroBase() {
        var r = service.calculateSurtax(1L, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getTotal()));
    }
}
