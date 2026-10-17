package com.ledgerpilot.nlp.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工资参数解析器（从 NlpController 抽出以便表驱动测试）。
 *
 * 金额量纲规则（修复点）：量词必须紧跟数字（允许空白）——
 * 旧实现用 indexOf("万元", 匹配起点) 向后任意匹配，「工资3000元，奖金2万元」会把 3000 误乘 10000。
 * 现在「30000」→30000 元、「3万」→30000、「2.5万元」→25000；中文数字列 vNext（不识别）。
 */
public final class SalaryParamParser {

    /** 解析结果 */
    public static class Params {
        public Double grossSalary;
        public Double socialBase;
        public String period;
        public String employeeType = "NORMAL";
        public String voucherDate;
        public double childrenEduDeduction;
        public double continuingEduDeduction;
        public double seriousIllnessDeduction;
        public double housingLoanDeduction;
        public double housingRentDeduction;
        public double elderlyCareDeduction;
    }

    /** 金额 = 数字 + 紧邻量词（万元/万/元 可省略，省略即元） */
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(万元|万|元)?");
    /** 工资金额：工资/发工资/月薪/年薪/应发/实发/薪资 关键词后的第一个金额 */
    private static final Pattern SALARY = Pattern.compile(
            "(?:工资|发工资|月薪|年薪|应发|实发|薪资)[^\\d一二两三四五六七八九十]{0,12}" + AMOUNT);
    /** 纯数字开头形态：如「30000工资」 */
    private static final Pattern SALARY_PREFIX = Pattern.compile(
            "^" + AMOUNT + "\\s*元?\\s*工资");
    /** 社保基数：社保基数/基数/按 后的第一个金额 */
    private static final Pattern SOCIAL_BASE = Pattern.compile(
            "(?:社保基数|缴纳基数|基数|按)[^\\d一二两三四五六七八九十]{0,8}" + AMOUNT);
    /** 期间：2026年3月 / 2026-03 */
    private static final Pattern PERIOD = Pattern.compile("(\\d{4})[年\\-](\\d{1,2})(?:月|期)?");

    private SalaryParamParser() {
    }

    public static Params parse(String input) {
        Params p = new Params();
        if (input == null || input.trim().isEmpty()) {
            return p;
        }
        p.grossSalary = firstAmount(SALARY.matcher(input));
        if (p.grossSalary == null) {
            p.grossSalary = firstAmount(SALARY_PREFIX.matcher(input));
        }
        p.socialBase = firstAmount(SOCIAL_BASE.matcher(input));
        p.period = parsePeriod(input);
        parseDeductions(input, p);
        parseEmployeeType(input, p);
        return p;
    }

    /** 数字 + 紧邻量词 → 元（万 = ×10000；量词缺省 = 元） */
    private static Double firstAmount(Matcher m) {
        if (!m.find()) {
            return null;
        }
        double value = Double.parseDouble(m.group(1));
        String quantifier = m.group(2);
        if ("万".equals(quantifier) || "万元".equals(quantifier)) {
            value = value * 10000;
        }
        return value;
    }

    private static String parsePeriod(String input) {
        Matcher m = PERIOD.matcher(input);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            if (month >= 1 && month <= 12) {
                return String.format("%04d-%02d", year, month);
            }
        }
        return null;
    }

    private static void parseDeductions(String input, Params p) {
        p.childrenEduDeduction = deduction(input, "子女教育");
        p.continuingEduDeduction = deduction(input, "继续教育");
        p.seriousIllnessDeduction = deduction(input, "大病医疗");
        p.housingLoanDeduction = deduction(input, "住房贷款");
        p.housingRentDeduction = deduction(input, "住房租金");
        p.elderlyCareDeduction = deduction(input, "赡养老人");
    }

    /** 专项附加扣除：关键词后的纯数字（单位默认元） */
    private static double deduction(String input, String keyword) {
        Matcher m = Pattern.compile(keyword + "[^\\d]{0,6}(\\d+(?:\\.\\d+)?)").matcher(input);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private static void parseEmployeeType(String input, Params p) {
        if (input.contains("合同工") || input.contains("派遣")) {
            p.employeeType = "CONTRACT";
        } else if (input.contains("博士后")) {
            p.employeeType = "POSTDOC";
        } else {
            p.employeeType = "NORMAL";
        }
    }
}
