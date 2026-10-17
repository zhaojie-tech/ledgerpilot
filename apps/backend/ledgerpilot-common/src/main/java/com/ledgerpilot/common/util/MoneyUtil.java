package com.ledgerpilot.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额工具类
 * 所有金额计算必须使用此类，避免浮点数精度问题
 */
public class MoneyUtil {

    /** 金额精度：2位小数 */
    public static final int SCALE = 2;

    /** 金额取整模式：四舍五入 */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private MoneyUtil() {}

    /**
     * 加法
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.add(b).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 减法
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.subtract(b).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 乘法
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.multiply(b).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 除法
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("除数不能为零");
        }
        return a.divide(b, SCALE, ROUNDING_MODE);
    }

    /**
     * 金额格式化（保留2位小数）
     */
    public static String format(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(SCALE, ROUNDING_MODE).toPlainString();
    }

    /**
     * 判断是否相等（比较两个金额是否相等）
     */
    public static boolean equals(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.setScale(SCALE, ROUNDING_MODE).compareTo(b.setScale(SCALE, ROUNDING_MODE)) == 0;
    }

    /**
     * 判断是否为零
     */
    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 判断是否正数
     */
    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 判断是否负数
     */
    public static boolean isNegative(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * 取绝对值
     */
    public static BigDecimal abs(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.abs().setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 金额反sign（借方变贷方，贷方变借方）
     */
    public static BigDecimal negate(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.negate().setScale(SCALE, ROUNDING_MODE);
    }
}
