package com.wdtt.client.account

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class AccountApiException(val status: Int, val accountInactive: Boolean = false) : IOException("Account request failed")

internal class AccountApi {
    // Never forward bearer credentials or PKCE to a redirected host.
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()

    fun request(path: String, body: JSONObject? = null, accessToken: String? = null): JSONObject {
        val builder = Request.Builder().url("https://safarvpn.ru/api/cabinet$path")
            .header("Accept", "application/json").header("Cache-Control", "no-store")
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")
        if (body != null) builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                // Classify only fixed backend reasons. Raw provider/server errors never leave this method.
                var inactive = false
                if (response.code == 403) runCatching {
                    val source = response.body?.source() ?: return@runCatching
                    source.request(8193)
                    if (source.buffer.size <= 8192) {
                        val error = JSONObject(source.readUtf8())
                        inactive = error.optJSONObject("detail")?.optString("code") in
                            setOf("account_deleted", "blacklisted") ||
                            error.opt("detail") == "User account is not active"
                    }
                }
                throw AccountApiException(response.code, inactive)
            }
            if (response.code == 204) return JSONObject()
            // Read a bounded body; do not include response text/URLs in errors or logs.
            val source = response.body?.source() ?: throw IOException("Empty account response")
            source.request(256 * 1024L + 1)
            if (source.buffer.size > 256 * 1024L) throw IOException("Invalid account response")
            return try { JSONObject(source.readUtf8()) } catch (_: Exception) {
                throw IOException("Invalid account response")
            }
        }
    }
}
