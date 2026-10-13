package com.wdtt.client.account

import android.content.Context
import com.wdtt.client.SecureStringStore
import org.json.JSONObject

/** Isolated account preferences; never reads or clears VPN settings/profiles. */
internal class AccountStore(context: Context) {
    private val preferences = context.getSharedPreferences("safarvpn.account", Context.MODE_PRIVATE)
    private val secrets = SecureStringStore(context, "safarvpn.account.secrets")

    fun read(key: String): JSONObject? = runCatching {
        secrets.decrypt(preferences.getString(key, null))?.let { JSONObject(it) }
    }.getOrNull()

    fun write(key: String, value: JSONObject?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, secrets.encrypt(value.toString()))
        check(editor.commit()) { "Account persistence unavailable" }
    }

    fun clear() {
        // A dedicated key prevents old credentials restoring after a failed disk deletion.
        val erased = runCatching { secrets.eraseKey() }.isSuccess
        val cleared = runCatching { preferences.edit().clear().commit() }.getOrDefault(false)
        check(erased || cleared) { "Account persistence unavailable" }
    }

    fun pending(): PendingLogin? = read("pending")?.let { data ->
        runCatching {
            PendingLogin(data.getString("provider"), data.getString("state"), data.getString("verifier"),
                data.getString("session_id"), data.getLong("expires_at"))
        }.getOrNull()
    }

    fun savePending(pending: PendingLogin?) = write("pending", pending?.let {
        JSONObject().put("provider", it.provider).put("state", it.state).put("verifier", it.verifier)
            .put("session_id", it.sessionId).put("expires_at", it.expiresAt)
    })

    fun saveSession(session: JSONObject) {
        check(preferences.edit().putString("auth", secrets.encrypt(session.toString()))
            .remove("pending").commit()) { "Account persistence unavailable" }
    }
}
