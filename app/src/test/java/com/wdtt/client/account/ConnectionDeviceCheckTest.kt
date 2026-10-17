package com.wdtt.client.account

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConnectionDeviceCheckTest {
    private fun status(limit: Int = 1, generation: Int = 1) =
        JSONObject().put("max_devices", limit).put("generation", generation)
    private fun devices(vararg ids: Pair<String, String>) = JSONObject().put("devices",
        org.json.JSONArray(ids.map { (id, state) -> JSONObject().put("device_id", id).put("state", state) }))
    private fun check(list: JSONObject, before: JSONObject = status(), after: JSONObject = before): String? = runBlocking {
        var reads = 0
        ConnectionDeviceCheck.check("this-device") { path ->
            if (path == "/devices") list else if (reads++ == 0) before else after
        }
    }

    @Test fun newDeviceIsRejectedWhenAllSlotsAreOccupied() {
        assertEquals(ConnectionDeviceCheck.LIMIT, check(devices("another-device" to "bound")))
    }
    @Test fun boundDeviceCanReconnectAtFullCapacity() {
        assertNull(check(devices("this-device" to "bound")))
    }
    @Test fun freeSlotAllowsConnection() {
        assertNull(check(devices("another-device" to "bound"), status(2)))
    }
    @Test fun pendingUnbindDoesNotFreeSlotOrAllowReconnect() {
        assertEquals(ConnectionDeviceCheck.REVOKING, check(devices("this-device" to "revoking")))
        assertEquals(ConnectionDeviceCheck.REVOKING, check(devices("another-device" to "revoking"), status(2)))
    }
    @Test fun changingLimitOrCredentialGenerationMakesAdviceInconclusive() {
        val list = devices("another-device" to "bound")
        assertNull(check(list, status(), status(2)))
        assertNull(check(list, status(), status(generation = 2)))
    }
    @Test(expected = IllegalArgumentException::class) fun malformedInventoryCannotBeUsedToReportLimit() {
        check(devices("another-device" to "bound", "another-device" to "bound"))
    }
}
