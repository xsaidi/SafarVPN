package com.wdtt.client.account

internal data class TrackedTopup(val method: String, val id: Long, val amount: Long, val createdAt: Long, val checkoutHash: String = "")
internal data class TopupConfirmation(val payment: TrackedTopup, val balance: Long)

internal object TopupStatus {
    fun checkoutHash(url: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun resolvedId(payment: TrackedTopup, method: String, id: Long, amount: Long, checkoutUrl: String): Long? =
        id.takeIf { it > 0 && payment.method == method && payment.amount == amount &&
            payment.checkoutHash.isNotBlank() && payment.checkoutHash == checkoutHash(checkoutUrl) }

    fun matches(payment: TrackedTopup, method: String, id: Long, amount: Long): Boolean =
        payment.method == method && payment.id == id && payment.amount == amount

    // A larger account balance alone is never proof of this payment.
    fun credited(payment: TrackedTopup, method: String, id: Long, amount: Long, paid: Boolean): Boolean =
        paid && matches(payment, method, id, amount)

    fun terminalFailure(status: String, paid: Boolean): Boolean = !paid && status.lowercase() in
        setOf("canceled", "cancelled", "expired", "failed", "declined", "rejected")

    fun pollDelay(elapsed: Long): Long = when {
        elapsed < 60_000 -> 4_000
        elapsed < 300_000 -> 10_000
        else -> 30_000
    }
}
