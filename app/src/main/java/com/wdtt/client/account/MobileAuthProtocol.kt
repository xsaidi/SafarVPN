package com.wdtt.client.account

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal class PendingLogin(
    val provider: String,
    val state: String,
    val verifier: String,
    val sessionId: String,
    val expiresAt: Long,
)

internal class AuthReturn(val code: String?, val error: String?)

internal object MobileAuthProtocol {
    private val opaque = Regex("[A-Za-z0-9_-]{43}")
    private val errors = setOf("access_denied", "login_failed")

    fun randomSecret(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun isOpaque(value: String): Boolean = opaque.matches(value)

    // Broad classification ensures malformed auth links never become VPN imports.
    fun isAuthCandidate(value: String): Boolean {
        if (value.startsWith("safarvpn:", ignoreCase = true)) return true
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("https", "http") &&
            uri.host.equals("safarvpn.ru", ignoreCase = true) && uri.path?.startsWith("/mobile-auth") == true
    }

    fun parseReturn(value: String, pending: PendingLogin?, now: Long): AuthReturn? {
        if (value.length > 4096) return null
        if (pending == null || pending.sessionId.isEmpty() || now >= pending.expiresAt) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val validAddress = (uri.scheme == "https" && uri.rawAuthority == "safarvpn.ru" &&
            uri.rawPath == "/mobile-auth/return") ||
            (uri.scheme == "safarvpn" && uri.rawAuthority == "mobile-auth" && uri.rawPath == "/return")
        if (!validAddress || uri.rawFragment != null) return null
        val query = strictQuery(uri.rawQuery) ?: return null
        if (query.keys != setOf("code", "state") && query.keys != setOf("error", "state")) return null
        val state = query["state"] ?: return null
        if (!isOpaque(state) || !MessageDigest.isEqual(state.toByteArray(), pending.state.toByteArray())) return null
        val code = query["code"]
        if (code != null && !isOpaque(code)) return null
        val error = query["error"]
        if (error != null && error !in errors) return null
        return AuthReturn(code, error)
    }

    fun trustedLaunch(value: String, provider: String, sessionId: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.rawFragment != null) return false
        val query = strictQuery(uri.rawQuery) ?: return false
        return when (provider) {
            "google" -> uri.rawAuthority == "safarvpn.ru" &&
                uri.rawPath == "/api/cabinet/auth/mobile/google/start" && query == mapOf("session_id" to sessionId)
            "telegram" -> uri.rawAuthority == "t.me" &&
                Regex("/[A-Za-z0-9_]{5,32}").matches(uri.rawPath ?: "") &&
                query == mapOf("start" to "mobileauth_$sessionId")
            else -> false
        }
    }

    private fun strictQuery(raw: String?): Map<String, String>? {
        if (raw.isNullOrEmpty() || raw.length > 1024) return null
        val result = mutableMapOf<String, String>()
        for (part in raw.split('&')) {
            val pair = part.split('=', limit = 2)
            if (pair.size != 2) return null
            val key = runCatching { URLDecoder.decode(pair[0], "UTF-8") }.getOrNull() ?: return null
            val value = runCatching { URLDecoder.decode(pair[1], "UTF-8") }.getOrNull() ?: return null
            if (key in result) return null
            result[key] = value
        }
        return result
    }
}

/** Every async completion mutates state under the same generation lock as logout/new login. */
internal class AccountGeneration {
    private var value = 0L

    @Synchronized fun current(): Long = value
    @Synchronized fun advance(action: () -> Unit = {}): Long {
        value++
        action()
        return value
    }
    @Synchronized fun apply(expected: Long, action: () -> Unit): Boolean {
        if (expected != value) return false
        action()
        return true
    }
}
