package com.ledgerpilot.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MoneyUtil 黄金用例（；BigDecimal 金额纪律的守门测试）。
 */
class MoneyUtilTest {

    @Test
    void addNullSafeAndScale2() {
        assertEquals(new BigDecimal("3.00"), MoneyUtil.add(new BigDecimal("1.00"), new BigDecimal("2")));
        assertEquals(new BigDecimal("1.00"), MoneyUtil.add(null, new BigDecimal("1")));
        assertEquals(0, MoneyUtil.add(null, null).compareTo(BigDecimal.ZERO));
    }

    @Test
    void subtract() {
        assertEquals(new BigDecimal("1.10"), MoneyUtil.subtract(new BigDecimal("3.30"), new BigDecimal("2.20")));
    }

    @Test
    void multiplyHalfUp() {
        assertEquals(new BigDecimal("0.33"), MoneyUtil.multiply(new BigDecimal("0.1"), new BigDecimal("3.333")));
    }

    @Test
    void divideZeroThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MoneyUtil.divide(BigDecimal.TEN, BigDecimal.ZERO));
    }

    @Test
    void divideScale2() {
        assertEquals(new BigDecimal("3.33"), MoneyUtil.divide(BigDecimal.TEN, new BigDecimal("3")));
    }

    @Test
    void formatPlainString() {
        assertEquals("10000.00", MoneyUtil.format(new BigDecimal("10000")));
        assertEquals("0.00", MoneyUtil.format(null));
        assertEquals("-2000.00", MoneyUtil.format(new BigDecimal("-2000")));
    }

    @Test
    void equalsIgnoresScale() {
        assertTrue(MoneyUtil.equals(new BigDecimal("1.0"), new BigDecimal("1.00")));
        assertTrue(MoneyUtil.equals(null, BigDecimal.ZERO));
    }

    @Test
    void signChecks() {
        assertTrue(MoneyUtil.isZero(null));
        assertTrue(MoneyUtil.isPositive(BigDecimal.ONE));
        assertTrue(MoneyUtil.isNegative(BigDecimal.ONE.negate()));
    }

    @Test
    void absAndNegate() {
        assertEquals(new BigDecimal("5.00"), MoneyUtil.abs(new BigDecimal("-5")));
        assertEquals(new BigDecimal("-5.00"), MoneyUtil.negate(new BigDecimal("5")));
        assertEquals(BigDecimal.ZERO, MoneyUtil.negate(null));
    }

    @Test
    void redLetterRoundTrip() {
        // 红字冲销语义：原额 + 负额 = 0（精度守恒）
        BigDecimal original = new BigDecimal("4424.78");
        assertEquals(BigDecimal.ZERO.compareTo(MoneyUtil.add(original, MoneyUtil.negate(original))), 0);
    }
}
