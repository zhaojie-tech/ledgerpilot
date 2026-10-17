package com.ledgerpilot.nlp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 金额量词必须紧邻数字。
 * 表驱动黄金用例（点）。
 */
class SalaryParamParserTest {

    private static SalaryParamParser.Params parse(String input) {
        return SalaryParamParser.parse(input);
    }

    @Test
    void plainYuan() {
        assertEquals(30000.0, parse("本月应发工资30000元").grossSalary);
    }

    @Test
    void bareNumber() {
        assertEquals(30000.0, parse("工资30000").grossSalary);
    }

    @Test
    void wanQuantifier() {
        assertEquals(30000.0, parse("发工资3万").grossSalary);
    }

    @Test
    void wanYuanQuantifier() {
        assertEquals(25000.0, parse("工资2.5万元").grossSalary);
    }

    @Test
    void lateWanMustNotLeakIntoEarlierNumber() {
        // 回归锁：旧实现 indexOf 向后任意匹配，把 3000 误乘 10000
        assertEquals(3000.0, parse("工资3000元，奖金2万元").grossSalary);
    }

    @Test
    void numberFirstOrder() {
        assertEquals(30000.0, parse("30000 工资").grossSalary);
    }

    @Test
    void socialBase() {
        SalaryParamParser.Params p = parse("本月工资20000，社保基数22000");
        assertEquals(20000.0, p.grossSalary);
        assertEquals(22000.0, p.socialBase);
    }

    @Test
    void period() {
        assertEquals("2026-03", parse("2026年3月工资50000元").period);
        assertEquals("2026-10", parse("2026-10工资50000元").period);
    }

    @Test
    void deductions() {
        SalaryParamParser.Params p = parse("工资30000元 子女教育2000 赡养老人3000");
        assertEquals(2000.0, p.childrenEduDeduction);
        assertEquals(3000.0, p.elderlyCareDeduction);
        assertEquals(0.0, p.housingLoanDeduction);
    }

    @Test
    void employeeType() {
        assertEquals("CONTRACT", parse("工资5000 派遣工").employeeType);
        assertEquals("POSTDOC", parse("博士后工资8000").employeeType);
        assertEquals("NORMAL", parse("工资5000").employeeType);
    }

    @Test
    void chineseNumeralsNotParsed_vNext() {
        assertNull(parse("工资三万元").grossSalary);
    }

    @Test
    void emptyInput() {
        assertNull(parse("").grossSalary);
        assertNull(parse(null).grossSalary);
    }
}
