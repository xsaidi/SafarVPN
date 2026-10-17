package com.wdtt.client.account
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SubscriptionExpiryTest {
    private fun subscription(active: Boolean, expires: String, status: String = "active", trial: Boolean = false) =
        JSONObject().put("has_subscription", true).put("subscription", JSONObject().put("is_active", active)
            .put("expires_at", expires).put("status", status).put("is_trial", trial))
    @Test fun expiryAfterCacheLoadIsDetectedWithoutRemovingSubscription() {
        val ui = subscriptionUi(subscription(true, Instant.now().minusSeconds(1).toString()))
        assertTrue(ui.hasSubscription); assertFalse(ui.isActive)
        assertEquals("Срок подписки истёк", ui.statusLabel)
    }
    @Test fun expiredServerRevokeIsExplainedAsExpiry() {
        val ui = subscriptionUi(subscription(false, Instant.now().minusSeconds(10).toString(), "revoked"))
        assertEquals("Срок подписки истёк", ui.statusLabel)
    }
    @Test fun activeTrialHasVisibleLabelAndDeadline() {
        val ui = subscriptionUi(subscription(true, Instant.now().plusSeconds(172800).toString(), trial = true))
        assertTrue(ui.isActive); assertEquals("Пробный период · 2 дня", ui.statusLabel)
        assertNotNull(ui.expiresAtLabel)
    }
}
