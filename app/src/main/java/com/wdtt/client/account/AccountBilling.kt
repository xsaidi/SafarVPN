package com.wdtt.client.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class AccountBilling(private val context: Context, private val owner: Long, private val scope: CoroutineScope) {
    private val account = AccountRepository.get(context)
    private val preferences = context.getSharedPreferences("account_billing", Context.MODE_PRIVATE)
    private val pendingKey = "pending_$owner"
    private val topupsKey = "topups_$owner"
    private val statusMutex = Mutex()
    private var topups by mutableStateOf(readTopups())
    var topupConfirmation by mutableStateOf<TopupConfirmation?>(null)
        private set
    var topupNotice by mutableStateOf<String?>(if (topups.isNotEmpty()) "Ожидаем подтверждение оплаты…" else null)
        private set
    val awaitingTopup: Boolean get() = topups.isNotEmpty()

    private fun readTopups(): List<TrackedTopup> = runCatching {
        val array = JSONArray(preferences.getString(topupsKey, "[]"))
        List(array.length()) {
            val item = array.getJSONObject(it)
            TrackedTopup(item.getString("method"), item.getLong("id"), item.getLong("amount"), item.getLong("created_at"), item.optString("checkout_hash")).also { payment ->
                check(Regex("[a-z][a-z0-9_]{0,31}").matches(payment.method) && payment.id >= 0 && payment.amount > 0)
                check(payment.id > 0 || Regex("[a-f0-9]{64}").matches(payment.checkoutHash))
            }
        }
    }.getOrDefault(emptyList())

    private fun saveTopups(payments: List<TrackedTopup>) {
        val array = JSONArray()
        payments.forEach { array.put(JSONObject().put("method", it.method).put("id", it.id).put("amount", it.amount).put("created_at", it.createdAt).put("checkout_hash", it.checkoutHash)) }
        check(preferences.edit().putString(topupsKey, array.toString()).commit())
        topups = payments
    }

    fun acknowledgeTopup() {
        val confirmation = topupConfirmation ?: return
        try {
            saveTopups(topups.filterNot { it.method == confirmation.payment.method && it.id == confirmation.payment.id })
            topupConfirmation = null
            topupNotice = if (topups.isNotEmpty()) "Ожидаем подтверждение оплаты…" else null
        } catch (_: Exception) { topupNotice = "Не удалось сохранить подтверждение. Повторите попытку." }
    }

    suspend fun watchTopups() {
        val started = System.currentTimeMillis()
        while (true) {
            if (topups.isNotEmpty() && topupConfirmation == null && !submitting) checkTopups()
            delay(if (topups.isEmpty()) 4000 else TopupStatus.pollDelay(System.currentTimeMillis() - started))
        }
    }

    private suspend fun checkTopups() = statusMutex.withLock {
        for (savedPayment in topups.toList()) {
            var payment = savedPayment
            if (submitting || topupConfirmation != null) return@withLock
            try {
                if (payment.id == 0L) {
                    val latest = account.billingRequest(owner, "/balance/pending-payments/${payment.method}/latest")
                    val resolved = TopupStatus.resolvedId(payment, latest.getString("method"), latest.getLong("id"),
                        latest.getLong("amount_kopeks"), latest.optString("payment_url"))
                    if (resolved == null) {
                        topupNotice = "Ожидаем подтверждение этого платежа… Проверка продолжится автоматически."
                        continue
                    }
                    payment = payment.copy(id = resolved)
                    saveTopups(topups.map { if (it == savedPayment) payment else it })
                }
                val result = account.billingRequest(owner, "/balance/pending-payments/${payment.method}/${payment.id}")
                check(TopupStatus.matches(payment, result.getString("method"), result.getLong("id"), result.getLong("amount_kopeks")))
                val paid = result.getBoolean("is_paid")
                if (TopupStatus.credited(payment, result.getString("method"), result.getLong("id"), result.getLong("amount_kopeks"), paid)) {
                    if (busy) continue
                    val balance = account.billingRequest(owner, "/auth/mobile/billing").getLong("balance_kopeks")
                    if (submitting) continue
                    catalog = catalog?.copy(balance = balance)
                    topupConfirmation = TopupConfirmation(payment, balance)
                    paymentUrl = null; paymentAmount = null; message = null; topupNotice = null
                    reload(); account.refreshAccount()
                    return@withLock
                } else if (TopupStatus.terminalFailure(result.optString("status"), paid)) {
                    saveTopups(topups.filterNot { it.method == payment.method && it.id == payment.id })
                    topupNotice = "Платёж не завершён: отменён или истёк срок оплаты."
                } else topupNotice = "Ожидаем подтверждение оплаты… Баланс обновится автоматически."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                topupNotice = "Проверяем оплату. Если подтверждение задерживается, приложение повторит запрос автоматически."
            }
        }
    }
    private var invalidPending = false
    var pending by mutableStateOf(readPending())
        private set
    var catalog by mutableStateOf<BillingCatalog?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var submitting by mutableStateOf(false)
        private set
    var fresh by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var paymentUrl by mutableStateOf<String?>(null)
        private set
    var paymentAmount by mutableStateOf<Long?>(null)
        private set
    var operations by mutableStateOf<List<BalanceOperation>>(emptyList())
        private set
    var historyBusy by mutableStateOf(false)
        private set
    var historyError by mutableStateOf<String?>(null)
        private set
    var historyLoaded by mutableStateOf(false)
        private set
    var historyPage by mutableIntStateOf(0)
        private set
    var historyPages by mutableIntStateOf(1)
        private set

    val purchaseBlocked: Boolean get() = invalidPending
    fun pendingTariff(): BillingTariff? = pending?.let { BillingTariff(it.getInt("period_days"), it.getLong("expected_price_kopeks")) }
    private fun readPending(): JSONObject? {
        val raw = preferences.getString(pendingKey, null) ?: return null
        return try {
            JSONObject(raw).also {
                check(it.getInt("period_days") in 1..366 && it.getLong("expected_price_kopeks") > 0)
                check(UUID.fromString(it.getString("request_id")).toString() == it.getString("request_id"))
            }
        } catch (_: Exception) { invalidPending = true; null }
    }
    private fun clearPending() {
        check(preferences.edit().remove(pendingKey).commit())
        pending = null
    }
    fun reload() {
        if (busy) return
        busy = true; fresh = false; loadError = null
        scope.launch {
            try {
                val json = account.billingRequest(owner, "/auth/mobile/billing")
                val tariffs = json.getJSONArray("tariffs")
                val methods = json.getJSONArray("payment_methods")
                val choices = buildList {
                    for (index in 0 until methods.length()) {
                        val item = methods.getJSONObject(index)
                        val options = item.optJSONArray("options")
                        fun name(raw: String) = raw.filter { it.isLetterOrDigit() || it in " /·()-.,+" }.trim()
                        if (options == null || options.length() == 0) add(BillingMethod(item.getString("id"), null, name(item.getString("name")), item.getLong("min_amount_kopeks"), item.getLong("max_amount_kopeks")))
                        else for (optionIndex in 0 until options.length()) {
                            val option = options.getJSONObject(optionIndex)
                            add(BillingMethod(item.getString("id"), option.getString("id"), name(option.getString("name")), item.getLong("min_amount_kopeks"), item.getLong("max_amount_kopeks")))
                        }
                    }
                }
                catalog = BillingCatalog(json.getLong("balance_kopeks"), List(tariffs.length()) {
                    val item = tariffs.getJSONObject(it)
                    BillingTariff(item.getInt("period_days"), item.getLong("price_kopeks")).also { tariff -> check(tariff.days > 0 && tariff.price > 0) }
                }, choices)
                fresh = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                loadError = "Не удалось обновить баланс. Проверьте соединение и повторите запрос."
            } finally { busy = false }
        }
    }
    fun refreshAll() { reload(); account.refreshAccount() }
    fun openPayment() {
        val url = paymentUrl ?: return
        try {
            val uri = Uri.parse(url)
            check(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { message = "Не удалось открыть страницу оплаты. Попробуйте ещё раз." }
    }
    fun topup(amount: Long, method: BillingMethod) {
        if (busy || !fresh || !BillingAmounts.allowed(amount, method)) return
        busy = true; submitting = true; message = null
        scope.launch {
            try {
                val body = JSONObject().put("amount_kopeks", amount).put("payment_method", method.id)
                method.option?.let { body.put("payment_option", it) }
                val result = account.billingRequest(owner, "/balance/topup", body)
                check(result.getLong("amount_kopeks") == amount)
                val url = result.getString("payment_url")
                val uri = Uri.parse(url)
                check(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
                // Cabinet payment_id may be the provider's UUID, not the database row ID.
                // Resolve the local row using the exact checkout fingerprint, never the latest row alone.
                val checkoutHash = TopupStatus.checkoutHash(url)
                saveTopups(topups + TrackedTopup(method.id, 0, amount, System.currentTimeMillis(), checkoutHash))
                topupNotice = "Ожидаем подтверждение оплаты… Баланс обновится автоматически."
                paymentUrl = url
                paymentAmount = amount
                message = "После оплаты вернитесь в приложение. Баланс обновится после подтверждения платежа. Подписка оплачивается отдельно."
                openPayment()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "Не удалось создать платёж. Проверьте баланс перед повторной попыткой."
            } finally { busy = false; submitting = false }
        }
    }
    fun purchase(tariff: BillingTariff) {
        if (busy || invalidPending || (pending == null && (!fresh || BillingAmounts.afterPurchase(catalog?.balance ?: 0, tariff.price) == null))) return
        busy = true; submitting = true; message = null
        scope.launch {
            try {
                val body = pending ?: JSONObject().put("period_days", tariff.days).put("expected_price_kopeks", tariff.price)
                    .put("request_id", UUID.randomUUID().toString()).also { pending = it }
                // Persist before every send, including retry after a failed disk write.
                check(preferences.edit().putString(pendingKey, body.toString()).commit())
                val result = account.billingRequest(owner, "/auth/mobile/billing/purchase", body)
                clearPending()
                val subscription = result.optJSONObject("subscription")
                message = when {
                    subscription?.optBoolean("is_active") == true -> "Подписка оплачена и активна."
                    subscription?.optString("status") == "pending_activation" -> "Оплата сохранена. Подписка ожидает активации; повторная оплата не требуется."
                    else -> "Покупка подтверждена. Обновите статус подписки."
                }
                account.refreshAccount()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val reason = (error as? AccountApiException)?.reason
                if (reason in setOf("price_changed", "insufficient_balance")) runCatching { clearPending() }
                message = when (reason) {
                    "price_changed" -> "Цена изменилась. Проверьте обновлённый тариф перед оплатой."
                    "insufficient_balance" -> "Недостаточно средств. Сначала пополните баланс."
                    "billing_restricted" -> "Оплата недоступна для этого аккаунта. Обратитесь в поддержку."
                    else -> "Результат пока не подтверждён. Нажмите «Проверить покупку» — повторного списания не будет."
                }
            } finally { busy = false; submitting = false; reload() }
        }
    }
    fun loadHistory(reset: Boolean = true) {
        if (historyBusy) return
        historyBusy = true; historyError = null
        val page = if (reset) 1 else historyPage + 1
        scope.launch {
            try {
                val json = account.billingRequest(owner, "/balance/transactions?page=$page&per_page=20")
                val items = json.getJSONArray("items")
                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault())
                val next = List(items.length()) {
                    val item = items.getJSONObject(it)
                    BalanceOperation(item.getLong("id"), item.getString("type"), item.getLong("amount_kopeks"), item.getBoolean("is_completed"),
                        runCatching { formatter.format(Instant.parse(item.getString("created_at"))) }.getOrDefault(""))
                }
                operations = (if (reset) next else operations + next).distinctBy { it.id }
                historyPage = page; historyPages = json.getInt("pages"); historyLoaded = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                historyError = "Не удалось загрузить историю операций. Повторите запрос."
            } finally { historyBusy = false }
        }
    }
}

@Composable
internal fun rememberAccountBilling(owner: Long): AccountBilling {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val billing = remember(owner) { AccountBilling(context, owner, scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(billing) { billing.reload() }
    LaunchedEffect(billing, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { billing.watchTopups() }
    }
    DisposableEffect(billing, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) billing.reload()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return billing
}
