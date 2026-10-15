package com.wdtt.client.account

import org.junit.Assert.*
import org.junit.Test

class TopupStatusTest {
    private val payment = TrackedTopup("platega", 42, 30000, 1)

    @Test fun uuidCheckoutResolvesToLocalRowOnlyAfterExactMatch() {
        val url = "https://pay.example.test/12345678-1234-4234-8234-123456789012"
        val unresolved = TrackedTopup("platega", 0, 30000, 1, TopupStatus.checkoutHash(url))
        assertEquals(42L, TopupStatus.resolvedId(unresolved, "platega", 42, 30000, url))
        assertEquals(64, unresolved.checkoutHash.length)
        assertFalse(unresolved.checkoutHash.contains("https"))
    }

    @Test fun newerSameAmountPaymentCannotResolveAnotherCheckout() {
        val unresolved = TrackedTopup("platega", 0, 30000, 1, TopupStatus.checkoutHash("https://pay.example.test/first"))
        assertNull(TopupStatus.resolvedId(unresolved, "platega", 43, 30000, "https://pay.example.test/second"))
        assertNull(TopupStatus.resolvedId(unresolved, "platega", 43, 30000, ""))
    }

    @Test fun checkoutResolutionRequiresMethodAmountAndPositiveRowId() {
        val url = "https://pay.example.test/invoice"
        val unresolved = TrackedTopup("platega", 0, 30000, 1, TopupStatus.checkoutHash(url))
        assertNull(TopupStatus.resolvedId(unresolved, "yookassa", 42, 30000, url))
        assertNull(TopupStatus.resolvedId(unresolved, "platega", 42, 50000, url))
        assertNull(TopupStatus.resolvedId(unresolved, "platega", 0, 30000, url))
        assertNull(TopupStatus.resolvedId(unresolved.copy(checkoutHash = ""), "platega", 42, 30000, url))
    }

    @Test fun onlyExactServerConfirmedPaymentProducesSuccess() {
        assertTrue(TopupStatus.credited(payment, "platega", 42, 30000, true))
        assertFalse(TopupStatus.credited(payment, "platega", 42, 30000, false))
    }
    @Test fun otherPaymentOrAmountCannotConfirmThisTopup() {
        assertFalse(TopupStatus.credited(payment, "platega", 43, 30000, true))
        assertFalse(TopupStatus.credited(payment, "yookassa", 42, 30000, true))
        assertFalse(TopupStatus.credited(payment, "platega", 42, 50000, true))
    }
    @Test fun paidFlagDominatesOldFailureStatus() {
        assertFalse(TopupStatus.terminalFailure("cancelled", true))
        assertTrue(TopupStatus.terminalFailure("cancelled", false))
        assertTrue(TopupStatus.terminalFailure("EXPIRED", false))
    }
    @Test fun pendingAndUnknownStatusesStayPending() {
        for (status in listOf("pending", "processing", "", "error", "completed")) {
            assertFalse(TopupStatus.terminalFailure(status, false))
        }
    }
    @Test fun pollingBacksOffWhileKeepingAutomaticChecks() {
        assertEquals(4000L, TopupStatus.pollDelay(0))
        assertEquals(4000L, TopupStatus.pollDelay(59999))
        assertEquals(10000L, TopupStatus.pollDelay(60000))
        assertEquals(30000L, TopupStatus.pollDelay(300000))
    }
}
