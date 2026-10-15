package com.wdtt.client.account

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal data class BillingTariff(val days: Int, val price: Long)
internal data class BillingMethod(val id: String, val option: String?, val name: String, val min: Long, val max: Long)
internal data class BillingCatalog(val balance: Long, val tariffs: List<BillingTariff>, val methods: List<BillingMethod>)
internal data class BalanceOperation(val id: Long, val type: String, val amount: Long, val completed: Boolean, val date: String)

internal object BillingAmounts {
    const val MAX_TOPUP = 2_000_000_000L
    fun parse(text: String): Long? {
        val normalized = text.trim().replace(',', '.')
        if (!Regex("[0-9]{1,8}(\\.[0-9]{1,2})?").matches(normalized)) return null
        return runCatching { BigDecimal(normalized).movePointRight(2).longValueExact() }
            .getOrNull()?.takeIf { it in 1..MAX_TOPUP }
    }
    fun allowed(amount: Long?, method: BillingMethod?): Boolean =
        amount != null && method != null && amount in maxOf(1000L, method.min)..minOf(MAX_TOPUP, method.max)

    fun afterTopup(balance: Long, amount: Long): Long = Math.addExact(balance, amount)
    fun afterPurchase(balance: Long, price: Long): Long? = if (price > 0 && balance >= price) balance - price else null
    fun input(kopeks: Long): String = BigDecimal.valueOf(kopeks, 2).stripTrailingZeros().toPlainString().replace('.', ',')
}

internal fun money(kopeks: Long): String = NumberFormat.getNumberInstance(Locale("ru", "RU")).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
    roundingMode = RoundingMode.UNNECESSARY
}.format(BigDecimal.valueOf(kopeks, 2)) + " ₽"
