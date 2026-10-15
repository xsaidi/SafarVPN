package com.wdtt.client.account

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wdtt.client.DeviceIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

internal data class ServerDevice(val id: String, val revoking: Boolean)
internal val LocalServerDevices = staticCompositionLocalOf<ServerDevices?> { null }
internal data class UnbindOperation(val requestId: String, val deviceId: String) {
    fun json(): JSONObject = JSONObject().put("request_id", requestId).put("kind", "unbind").put("device_id", deviceId)
    companion object {
        fun restore(json: JSONObject): UnbindOperation {
            val id = json.getString("request_id")
            require(UUID.fromString(id).toString() == id && UUID.fromString(id).version() == 4)
            require(json.getString("kind") == "unbind")
            val device = json.getString("device_id")
            require(DeviceIdentity.valid(device))
            return UnbindOperation(id, device)
        }
    }
}

/** Account-owned durable operation; acceptance is never confused with completion. */
internal class ServerDevices(
    val currentId: String,
    restored: JSONObject?,
    private val request: suspend (String, JSONObject?) -> JSONObject,
    restoredLimit: JSONObject? = null,
    private val saveLimit: (JSONObject?) -> Unit = {},
    private val onLimitChanged: () -> Unit = {},
    private val save: (JSONObject?) -> Unit,
) {
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    var devices by mutableStateOf<List<ServerDevice>>(emptyList()); private set
    var maxDevices by mutableStateOf<Int?>(null); private set
    var fresh by mutableStateOf(false); private set
    var limitQuote by mutableStateOf<DeviceLimitQuote?>(null); private set
    var quoteLoading by mutableStateOf(false); private set
    var limitError by mutableStateOf<String?>(null); private set
    private val restoredLimitOperation = runCatching { restoredLimit?.let(DeviceLimitPurchase::restore) }
    var limitPurchase by mutableStateOf(restoredLimitOperation.getOrNull()); private set
    private var limitAccepted = false
    var notice by mutableStateOf<String?>(null); private set
    private val restoredOperation = runCatching {
        restored?.let(UnbindOperation::restore)
    }
    var operation by mutableStateOf(restoredOperation.getOrNull()); private set
    private var stopped = restoredOperation.isFailure || restoredLimitOperation.isFailure
    init {
        if (stopped) notice = "Не удалось прочитать сохранённый запрос отвязки. Обратитесь в поддержку."
    }
    val pending: Boolean get() = operation != null || limitPurchase != null

    suspend fun loadLimitQuote(target: Int) {
        limitQuote = null; limitError = null
        if (target == maxDevices || pending || stopped || !fresh) return
        quoteLoading = true
        try {
            val quote = DeviceLimitQuote.parse(request("/device-limit/quote", JSONObject().put("target_limit", target)))
            check(quote.target == target && quote.from == maxDevices && quote.valid())
            limitQuote = quote
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            limitError = limitFailure(error)
        } finally { quoteLoading = false }
    }

    fun changeLimit() {
        val quote = limitQuote ?: return
        if (pending || !fresh || stopped || quote.from != maxDevices || !quote.valid()) {
            limitQuote = null; limitError = "Расчёт устарел. Выберите лимит заново."; return
        }
        try {
            val next = DeviceLimitPurchase(UUID.randomUUID().toString(), quote.id, quote.target, quote.price)
            saveLimit(next.saved())
            limitPurchase = next; limitQuote = null; limitAccepted = false
            notice = "Изменяем лимит. Ожидаем подтверждения серверов…"
            wake.trySend(Unit)
        } catch (_: Exception) { limitError = "Не удалось сохранить запрос. Повторите попытку." }
    }

    fun unbind(deviceId: String) {
        if (pending || stopped || !fresh || devices.none { it.id == deviceId && !it.revoking }) return
        try {
            val next = UnbindOperation(UUID.randomUUID().toString(), deviceId)
            save(next.json()) // Durable before the first network request.
            operation = next
            notice = "Отвязываем устройство. Ожидаем подтверждения всех серверов…"
            wake.trySend(Unit)
        } catch (_: Exception) { notice = "Не удалось сохранить запрос. Попробуйте ещё раз." }
    }

    suspend fun watch() {
        var failures = 0
        while (true) {
            val ok = tick()
            failures = if (ok) 0 else (failures + 1).coerceAtMost(4)
            val interval = if (failures > 0) (3000L shl failures).coerceAtMost(30000) else if (pending) 3000 else 8000
            withTimeoutOrNull(interval) { wake.receive() }
        }
    }

    internal suspend fun tick(): Boolean = mutex.withLock {
        if (stopped) return@withLock true
        try {
            limitPurchase?.let { purchase ->
                if (!limitAccepted) {
                    val accepted = request("/device-limit/operations", purchase.body())
                    check(purchase.matches(accepted))
                    limitAccepted = true
                }
                val result = request("/device-limit/operations/${purchase.requestId}", null)
                check(purchase.matches(result))
                when (result.getString("status")) {
                    "applied" -> {
                        refresh()
                        check(maxDevices == purchase.target)
                        saveLimit(null); limitPurchase = null
                        notice = "Лимит устройств изменён на ${purchase.target}." +
                            if (purchase.price > 0) " С баланса списано ${money(purchase.price)}." else " Без возврата средств."
                        onLimitChanged()
                    }
                    "pending" -> notice = "Изменяем лимит. Ожидаем подтверждения серверов… Повторная оплата не нужна."
                    else -> error("Invalid limit operation state")
                }
                if (limitPurchase != null) refresh()
                return@withLock true
            }
            val active = operation
            if (active != null) {
                // Reusing the exact durable UUID makes a lost POST response safe to retry.
                val accepted = request("/operations", active.json())
                check(accepted.getString("request_id") == active.requestId && accepted.getString("kind") == "unbind")
                val result = request("/operations/${active.requestId}", null)
                check(result.getString("request_id") == active.requestId && result.getString("kind") == "unbind")
                when (result.getString("status")) {
                    "applied" -> {
                        // Refresh the authoritative global list before promising a free slot.
                        refresh()
                        save(null)
                        operation = null
                        notice = "Устройство успешно отвязано от всех серверов."
                    }
                    "pending" -> notice = "Отвязываем устройство. Ожидаем подтверждения всех серверов…"
                    else -> error("Invalid operation state")
                }
            }
            if (active == null || operation != null) refresh()
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            fresh = false
            if (limitPurchase != null && error is AccountApiException && error.reason in
                setOf("quote_expired", "occupied_devices", "insufficient_balance")) {
                // Fixed non-debit errors allow a newly reviewed quote; transport failures keep the UUID.
                if (runCatching { saveLimit(null) }.isFailure) {
                    notice = "Не удалось сохранить результат. Запрос сохранён; проверка продолжится автоматически."
                    return@withLock false
                }
                limitPurchase = null; limitError = limitFailure(error)
                notice = limitError
                onLimitChanged()
                return@withLock false
            }
            if (error is AccountApiException) {
                val terminal = error.status in setOf(400, 401, 403, 404, 422) || error.reason == "request_conflict" ||
                    (error.status == 409 && !error.retryable)
                if (terminal) stopped = true
                notice = when {
                    error.reason == "request_conflict" -> "Не удалось подтвердить запрос. Обратитесь в поддержку."
                    error.status == 401 -> "Войдите в аккаунт снова."
                    error.status == 403 -> "Доступ изменился. Обновляем подписку."
                    error.status == 404 -> "Подписка или операция недоступна. Обновите аккаунт."
                    error.reason == "device_limit" -> "Достигнут лимит устройств. Отвяжите ненужное устройство."
                    error.reason == "unbind_pending" -> "Ожидаем подтверждения отвязки всех серверов…"
                    terminal -> "Запрос не выполнен. Обратитесь в поддержку."
                    else -> if (pending) "Подтверждение задерживается. Проверка продолжится автоматически." else "Не удалось обновить устройства. Повторим автоматически."
                }
            } else notice = when {
                limitPurchase != null -> "Проверяем изменение лимита. Запрос сохранён; повторная оплата не нужна."
                operation != null -> "Проверяем отвязку. Запрос сохранён, повторим автоматически."
                else -> "Не удалось обновить устройства. Повторим автоматически."
            }
            false
        }
    }

    private fun limitFailure(error: Exception): String = when ((error as? AccountApiException)?.reason) {
        "insufficient_balance" -> "Недостаточно средств. Пополните баланс в разделе «Аккаунт»."
        "occupied_devices" -> "Сначала отвяжите лишние устройства и дождитесь подтверждения серверов."
        "unbind_pending" -> "Дождитесь завершения отвязки устройства."
        "limit_change_pending" -> "Предыдущее изменение лимита ещё подтверждается серверами."
        "quote_expired" -> "Стоимость изменилась или расчёт устарел. Выберите лимит заново."
        "access_revoked", "no_subscription" -> "Для изменения лимита нужна активная подписка."
        else -> "Не удалось рассчитать стоимость. Попробуйте ещё раз."
    }

    private suspend fun refresh() {
        val status = request("/status", null)
        val response = request("/devices", null)
        val limit = status.getInt("max_devices").also { require(it > 0) }
        check(status.optString("paid_until") == status.optString("access_until"))
        val list = response.getJSONArray("devices")
        require(list.length() <= 10000)
        val parsed = List(list.length()) { index ->
            val item = list.getJSONObject(index)
            val id = item.getString("device_id").also { require(DeviceIdentity.valid(it)) }
            val state = item.getString("state").also { require(it in setOf("bound", "revoking")) }
            ServerDevice(id, state == "revoking")
        }
        require(parsed.map { it.id }.distinct().size == parsed.size)
        devices = parsed; maxDevices = limit; fresh = true
    }
}

@Composable internal fun rememberServerDevices(owner: Long?, subscriptionActive: Boolean?): ServerDevices? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(owner, subscriptionActive) { owner?.let {
        val account = AccountRepository.get(context)
        val deviceId = DeviceIdentity.get(context)
        ServerDevices(deviceId, runCatching { account.pendingServerOperation(it) }.getOrNull(),
            request = { path, body -> account.serverRequest(it, path, body) },
            save = { value ->
                account.saveServerOperation(it, value)
                if (value?.optString("device_id") == deviceId) com.wdtt.client.TunnelManager.stop(force = true)
            }, restoredLimit = runCatching { account.pendingDeviceLimit(it) }.getOrNull(),
            saveLimit = { value -> account.saveDeviceLimit(it, value) }, onLimitChanged = account::refreshAccount)
    } }
    LaunchedEffect(controller, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { controller?.watch() }
    }
    return controller
}

@Composable internal fun ServerDevicesDialog(controller: ServerDevices, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf<ServerDevice?>(null) }
    var selectedLimit by remember(controller.maxDevices) { mutableStateOf(controller.maxDevices ?: 1) }
    val dialogScope = rememberCoroutineScope()
    LaunchedEffect(selectedLimit, controller.maxDevices, controller.pending, controller.fresh) {
        controller.loadLimitQuote(selectedLimit)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Устройства") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Общий лимит для всех серверов: ${controller.devices.size} из ${controller.maxDevices ?: "…"}")
            Text("Лимит устройств", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = { selectedLimit-- }, enabled = selectedLimit > 1 && !controller.pending && controller.fresh,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Уменьшить лимит устройств" }) { Text("−") }
                Text("$selectedLimit из 5", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { selectedLimit++ }, enabled = selectedLimit < 5 && !controller.pending && controller.fresh,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Увеличить лимит устройств" }) { Text("+") }
            }
            Text("Одно устройство включено. Дополнительное — 70 ₽ за 30 дней. При увеличении оплачивается оставшийся срок подписки; при уменьшении возврата нет.",
                style = MaterialTheme.typography.bodySmall)
            if (controller.quoteLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            controller.limitQuote?.let { quote ->
                Text(if (quote.target > quote.from) "К оплате с баланса: ${money(quote.price)}" else "Уменьшение лимита без возврата средств.")
                Button(onClick = controller::changeLimit, enabled = quote.valid() && !controller.pending && controller.fresh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (quote.price > 0) "Оплатить ${money(quote.price)}" else "Уменьшить лимит до ${quote.target}")
                }
            }
            controller.limitError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { dialogScope.launch { controller.loadLimitQuote(selectedLimit) } }, enabled = !controller.quoteLoading && !controller.pending) { Text("Повторить расчёт") }
            }
            HorizontalDivider()
            if (!controller.fresh) Text("Обновляем данные. Сохранённый список может быть устаревшим.")
            if (controller.fresh && controller.devices.isEmpty()) Text("Привязанных устройств нет.")
            controller.devices.forEachIndexed { index, device ->
                Column {
                    Text(if (device.id == controller.currentId) "Это устройство" else "Устройство ${index + 1}")
                    if (device.revoking || controller.operation?.deviceId == device.id) Text("Ожидаем подтверждения отвязки всех серверов…")
                    else TextButton(onClick = { target = device }, enabled = controller.fresh && !controller.pending) {
                        Text("Отвязать")
                    }
                }
            }
            controller.notice?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
    target?.let { device ->
        AlertDialog(onDismissRequest = { target = null }, title = { Text("Отвязать устройство?") }, text = {
            Text("Доступ этого устройства будет отозван на всех серверах. Место освободится после их подтверждения." +
                if (device.id == controller.currentId) " VPN на этом устройстве будет отключён." else "")
        }, confirmButton = { TextButton(onClick = { controller.unbind(device.id); target = null }) { Text("Отвязать") } },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Отмена") } })
    }
}
