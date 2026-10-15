package com.wdtt.client.account

import com.wdtt.client.DeviceIdentity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ServerDevicesTest {
    private val device = "synthetic-device"
    private class Backend {
        var saved: JSONObject? = null
        var state = "pending"
        var failPost = false
        var failRefresh = false
        var error: AccountApiException? = null
        val posts = mutableListOf<String>()
        suspend fun request(path: String, body: JSONObject?): JSONObject {
            error?.let { throw it }
            if (path == "/operations") {
                check(saved != null) // Must persist before attempting network I/O.
                posts += body!!.getString("request_id")
                if (failPost) throw IOException("synthetic timeout")
                return JSONObject().put("request_id", posts.last()).put("kind", "unbind")
            }
            if (path.startsWith("/operations/")) return JSONObject().put("request_id", path.substringAfterLast('/'))
                .put("kind", "unbind").put("status", state)
            if (failRefresh) throw IOException("synthetic unavailable")
            if (path == "/status") return JSONObject().put("max_devices", 2).put("paid_until", "2026-10-20").put("access_until", "2026-10-20")
            return JSONObject().put("revision", 4).put("devices", JSONArray().apply {
                if (state != "applied") put(JSONObject().put("device_id", "synthetic-device").put("state", "bound"))
            })
        }
        fun controller() = ServerDevices("synthetic-device", saved, ::request) { saved = it }
    }

    @Test fun pendingIsNotSuccessAndRetriesReuseDurableUuidAfterRestart() = runBlocking {
        val backend = Backend()
        val first = backend.controller()
        assertTrue(first.tick())
        first.unbind(device)
        val id = backend.saved!!.getString("request_id")
        backend.failPost = true
        assertFalse(first.tick())
        assertTrue(first.pending)
        val restored = backend.controller()
        backend.failPost = false
        assertTrue(restored.tick())
        assertTrue(restored.pending)
        assertEquals(listOf(id, id), backend.posts)
        assertFalse(restored.notice!!.contains("успешно"))
        backend.state = "applied"
        assertTrue(restored.tick())
        assertFalse(restored.pending)
        assertNull(backend.saved)
        assertTrue(restored.devices.isEmpty())
        assertTrue(restored.notice!!.contains("успешно"))
    }

    @Test fun cannotReportCompletionUntilGlobalDevicesAreRefreshed() = runBlocking {
        val backend = Backend()
        val controller = backend.controller()
        controller.tick(); controller.unbind(device)
        backend.state = "applied"; backend.failRefresh = true
        assertFalse(controller.tick())
        assertTrue(controller.pending)
        assertNotNull(backend.saved)
        assertFalse(controller.notice!!.contains("успешно"))
    }

    @Test fun inventory503PreservesPreviousDevicesAndDisablesActions() = runBlocking {
        val backend = Backend()
        val controller = backend.controller()
        controller.tick()
        backend.error = AccountApiException(503, reason = "inventory_pending", retryable = true)
        assertFalse(controller.tick())
        assertEquals(1, controller.devices.size)
        assertFalse(controller.fresh)
        controller.unbind(device)
        assertNull(backend.saved)
        backend.error = null
        assertTrue(controller.tick())
        assertTrue(controller.fresh)
    }

    @Test fun requestConflictStopsAutomaticSubmissionAndKeepsOriginalAction() = runBlocking {
        val backend = Backend()
        val controller = backend.controller()
        controller.tick(); controller.unbind(device)
        val original = backend.saved!!.toString()
        backend.error = AccountApiException(409, reason = "request_conflict")
        assertFalse(controller.tick())
        backend.error = null
        controller.tick(); controller.unbind("different-device")
        assertEquals(original, backend.saved!!.toString())
        assertTrue(backend.posts.isEmpty())
    }

    @Test fun persistenceFailurePreventsSendingUnbind() = runBlocking {
        val backend = Backend()
        val controller = ServerDevices(device, null, backend::request) { throw IOException("disk") }
        controller.tick(); controller.unbind(device); controller.tick()
        assertFalse(controller.pending)
        assertTrue(backend.posts.isEmpty())
    }

    @Test fun stableIdentityPreservesExistingAndroidIdAndSavedFallback() {
        assertEquals("android-id", DeviceIdentity.select(null, "android-id") { error("must not generate") })
        assertEquals("saved-fallback", DeviceIdentity.select("saved-fallback", "android-id") { error("must not generate") })
        assertEquals("new-fallback", DeviceIdentity.select(null, "unknown") { "new-fallback" })
        for (value in listOf(null, "", "unknown", "multi", "space id", "кириллица", "a".repeat(129))) assertFalse(DeviceIdentity.valid(value))
    }
}
