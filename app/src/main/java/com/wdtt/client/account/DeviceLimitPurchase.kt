package com.wdtt.client.account

import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal data class DeviceLimitQuote(val id: String, val from: Int, val target: Int, val price: Long, val validUntil: Instant) {
    fun valid(now: Instant = Instant.now()): Boolean = validUntil.isAfter(now)
    companion object {
        fun parse(json: JSONObject): DeviceLimitQuote {
            val id = json.getString("quote_id").also(::requireUuid4)
            val from = json.getInt("from_limit").also { require(it in 1..5) }
            val target = json.getInt("target_limit").also { require(it in 1..5 && it != from) }
            val price = json.getLong("price_kopeks").also { require(it >= 0 && (target > from || it == 0L)) }
            return DeviceLimitQuote(id, from, target, price, Instant.parse(json.getString("valid_until")))
        }
    }
}

internal data class DeviceLimitPurchase(val requestId: String, val quoteId: String, val target: Int, val price: Long) {
    fun body(): JSONObject = JSONObject().put("request_id", requestId).put("quote_id", quoteId)
    fun saved(): JSONObject = body().put("kind", "device_limit").put("target_limit", target).put("price_kopeks", price)
    fun matches(response: JSONObject): Boolean = response.optString("request_id") == requestId &&
        response.optString("kind") == "device_limit" && response.optInt("target_limit") == target &&
        response.optLong("charged_kopeks", -1) == price
    companion object {
        fun restore(json: JSONObject): DeviceLimitPurchase {
            require(json.getString("kind") == "device_limit")
            return DeviceLimitPurchase(json.getString("request_id").also(::requireUuid4),
                json.getString("quote_id").also(::requireUuid4), json.getInt("target_limit").also { require(it in 1..5) },
                json.getLong("price_kopeks").also { require(it >= 0) })
        }
    }
}

private fun requireUuid4(value: String) {
    val uuid = UUID.fromString(value)
    require(uuid.toString() == value && uuid.version() == 4)
}
