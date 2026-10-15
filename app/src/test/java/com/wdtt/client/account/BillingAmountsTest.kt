package com.wdtt.client.account

import org.junit.Assert.*
import org.junit.Test

class BillingAmountsTest {
    @Test fun decimalAmountsDoNotLoseKopeks() {
        assertEquals(30000L, BillingAmounts.parse("300"))
        assertEquals(13001L, BillingAmounts.parse("130,01"))
        assertEquals(13001L, BillingAmounts.parse("130.01"))
        assertEquals(1L, BillingAmounts.parse("0,01"))
        assertEquals(BillingAmounts.MAX_TOPUP, BillingAmounts.parse("20000000"))
    }
    @Test fun invalidAndOutOfRangeAmountsNeverBecomePayments() {
        for (value in listOf("", "0", "-100", "+100", "NaN", "1e3", "130.001", "20000000.01", "999999999999", "1,2.3")) {
            assertNull(value, BillingAmounts.parse(value))
        }
    }
    @Test fun paymentMethodLimitsAreAppliedWithoutRounding() {
        val method = BillingMethod("test", null, "Test", 10000, 50000)
        assertFalse(BillingAmounts.allowed(9999, method))
        assertTrue(BillingAmounts.allowed(10000, method))
        assertTrue(BillingAmounts.allowed(50000, method))
        assertFalse(BillingAmounts.allowed(50001, method))
        assertFalse(BillingAmounts.allowed(30000, null))
        assertFalse(BillingAmounts.allowed(null, method))
        assertFalse(BillingAmounts.allowed(999, method.copy(min = 1)))
    }
    @Test fun insufficientBalanceDoesNotProduceNegativeRemainder() {
        assertEquals(7000L, BillingAmounts.afterPurchase(20000, 13000))
        assertEquals(0L, BillingAmounts.afterPurchase(13000, 13000))
        assertNull(BillingAmounts.afterPurchase(12999, 13000))
        assertNull(BillingAmounts.afterPurchase(20000, 0))
    }
    @Test fun displayedTopupBalanceUsesExactArithmetic() {
        assertEquals(50000L, BillingAmounts.afterTopup(20000, 30000))
        assertEquals(20002L, BillingAmounts.afterTopup(20001, 1))
        assertThrows(ArithmeticException::class.java) { BillingAmounts.afterTopup(Long.MAX_VALUE, 1) }
    }
    @Test fun formattedAmountsRoundTripWithoutFloatingPoint() {
        for (value in listOf(1L, 13001L, 30000L, BillingAmounts.MAX_TOPUP)) {
            assertEquals(value, BillingAmounts.parse(BillingAmounts.input(value)))
        }
        assertEquals("130,01 ₽", money(13001))
        assertEquals("130 ₽", money(13000))
    }
}
