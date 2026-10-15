package com.wdtt.client.account

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

class DeviceLimitPurchaseTest {
    private val quoteId = "11111111-1111-4111-8111-111111111111"
    private fun quote(target: Int = 3, price: Long = 7000) = JSONObject().put("quote_id", quoteId)
        .put("from_limit", 1).put("target_limit", target).put("price_kopeks", price)
        .put("valid_until", "2099-01-01T00:00:00Z")

    private class Backend {
        var saved: JSONObject? = null
        var limit = 1
        var operationStatus = "pending"
        var failPost = false
        var failSave = false
        var insufficient = false
        var applied = false
        val posts = mutableListOf<String>()
        suspend fun request(path: String, body: JSONObject?): JSONObject {
            if (path == "/status") return JSONObject().put("max_devices", limit).put("paid_until", "same").put("access_until", "same")
            if (path == "/devices") return JSONObject().put("devices", JSONArray())
            if (path == "/device-limit/quote") return JSONObject().put("quote_id", "11111111-1111-4111-8111-111111111111")
                .put("from_limit", limit).put("target_limit", body!!.getInt("target_limit")).put("price_kopeks", 7000)
                .put("valid_until", "2099-01-01T00:00:00Z")
            if (path == "/device-limit/operations") {
                check(saved != null)
                posts += body!!.getString("request_id")
                if (failPost) throw IOException("synthetic lost reply")
                if (insufficient) throw AccountApiException(409, reason = "insufficient_balance")
            }
            if (operationStatus == "applied") limit = 3
            return JSONObject().put("request_id", saved!!.getString("request_id")).put("kind", "device_limit")
                .put("status", operationStatus).put("target_limit", 3).put("charged_kopeks", 7000)
        }
        fun controller() = ServerDevices("synthetic", null, ::request,
            restoredLimit = saved, saveLimit = { if (failSave) throw IOException("disk"); saved = it },
            onLimitChanged = { applied = true }, save = {})
    }

    @Test fun quoteValidationAndExpiry() {
        val parsed = DeviceLimitQuote.parse(quote())
        assertTrue(parsed.valid(Instant.parse("2098-01-01T00:00:00Z")))
        assertFalse(parsed.valid(Instant.parse("2099-01-01T00:00:00Z")))
        assertThrows(IllegalArgumentException::class.java) { DeviceLimitQuote.parse(quote(6)) }
        assertThrows(IllegalArgumentException::class.java) { DeviceLimitQuote.parse(quote(1)) }
        assertThrows(IllegalArgumentException::class.java) { DeviceLimitQuote.parse(quote(price = -1)) }
    }

    @Test fun lostReplyAndRestartReuseExactPurchaseUuidAndWaitForApplied() = runBlocking {
        val backend = Backend()
        val first = backend.controller()
        first.tick(); first.loadLimitQuote(3); first.changeLimit()
        val id = backend.saved!!.getString("request_id")
        backend.failPost = true
        assertFalse(first.tick()); assertTrue(first.pending)
        val restored = backend.controller()
        backend.failPost = false
        assertTrue(restored.tick()); assertTrue(restored.pending)
        assertFalse(backend.applied)
        assertEquals(listOf(id,id),backend.posts)
        backend.operationStatus = "applied"
        assertTrue(restored.tick())
        assertFalse(restored.pending); assertNull(backend.saved)
        assertEquals(3,restored.maxDevices); assertTrue(backend.applied)
        assertEquals(listOf(id,id),backend.posts)
    }

    @Test fun insufficientBalanceClearsNonDebitIntentAndRequiresNewQuote() = runBlocking {
        val backend = Backend(); val controller = backend.controller()
        controller.tick(); controller.loadLimitQuote(3); controller.changeLimit()
        backend.insufficient = true
        assertFalse(controller.tick()); assertFalse(controller.pending)
        assertNull(backend.saved); assertNull(controller.limitQuote)
        assertTrue(controller.limitError!!.contains("Недостаточно"))
    }

    @Test fun failedPersistenceNeverPostsFinancialRequest() = runBlocking {
        val backend = Backend(); val controller = backend.controller()
        controller.tick(); controller.loadLimitQuote(3)
        backend.failSave = true; controller.changeLimit(); controller.tick()
        assertFalse(controller.pending); assertTrue(backend.posts.isEmpty())
    }

    @Test fun responseMustMatchExactOperationAndCharge() {
        val purchase = DeviceLimitPurchase("22222222-2222-4222-8222-222222222222", quoteId, 3, 7000)
        assertEquals(purchase, DeviceLimitPurchase.restore(purchase.saved()))
        val response = JSONObject().put("request_id", purchase.requestId).put("kind", "device_limit")
            .put("target_limit", 3).put("charged_kopeks", 7000)
        assertTrue(purchase.matches(response))
        assertFalse(purchase.matches(response.put("charged_kopeks", 14000)))
    }
}
