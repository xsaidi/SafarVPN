package com.wdtt.client.account

import com.wdtt.client.DeviceIdentity
import org.json.JSONObject

/** Read-only advice; node admission remains authoritative and reserves the actual slot. */
internal object ConnectionDeviceCheck {
    const val LIMIT = "Достигнут лимит устройств. Отвяжите ненужное устройство или увеличьте лимит в разделе «Устройства аккаунта»."
    const val REVOKING = "Ожидается подтверждение отвязки устройства от всех серверов. Дождитесь завершения операции."

    suspend fun check(deviceId: String, request: suspend (String) -> JSONObject): String? {
        require(DeviceIdentity.valid(deviceId))
        val before = request("/status")
        val response = request("/devices")
        val after = request("/status")
        // A renewal or a limit change during these reads makes the advice inconclusive.
        if (before.getLong("generation") != after.getLong("generation") ||
            before.getInt("max_devices") != after.getInt("max_devices")) return null
        val limit = after.getInt("max_devices").also { require(it > 0) }
        val list = response.getJSONArray("devices")
        require(list.length() <= 10000)
        val devices = List(list.length()) { index ->
            val item = list.getJSONObject(index)
            val id = item.getString("device_id").also { require(DeviceIdentity.valid(it)) }
            val state = item.getString("state").also { require(it in setOf("bound", "revoking")) }
            id to state
        }
        require(devices.map { it.first }.distinct().size == devices.size)
        return when {
            devices.any { it.second == "revoking" } -> REVOKING
            devices.any { it.first == deviceId } -> null
            devices.size >= limit -> LIMIT
            else -> null
        }
    }
}
