package com.wdtt.client

import android.content.Context
import android.provider.Settings
import java.util.UUID

/** Shared by the tunnel and management requests. Never reset when switching accounts. */
internal object DeviceIdentity {
    fun valid(value: String?): Boolean = value != null && value.length in 1..128 &&
        value.all { it.code in 33..126 } && value !in setOf("unknown", "multi")

    internal fun select(saved: String?, androidId: String?, generate: () -> String): String =
        saved?.takeIf(::valid) ?: androidId?.takeIf(::valid) ?: generate().also { require(valid(it)) }

    @Synchronized fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("vpn_device_identity", Context.MODE_PRIVATE)
        prefs.getString("id", null)?.takeIf(::valid)?.let { return it }
        val androidId = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
        val value = select(null, androidId) { UUID.randomUUID().toString() }
        check(prefs.edit().putString("id", value).commit()) { "Device identity persistence unavailable" }
        return value
    }
}
