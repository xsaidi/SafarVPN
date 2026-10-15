package com.wdtt.client.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal enum class BillingPage { SUBSCRIPTION, TOPUP, HISTORY }

@Composable
internal fun BillingPages(billing: AccountBilling, renewing: Boolean, initial: BillingPage, onDismiss: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(initial) }
    var topupReturn by rememberSaveable { mutableStateOf<BillingPage?>(null) }
    var selectedDays by rememberSaveable { mutableIntStateOf(30) }
    fun back() {
        if (billing.submitting) return
        val target = topupReturn
        if (page == BillingPage.TOPUP && target != null) { page = target; topupReturn = null }
        else onDismiss()
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = ::back, enabled = !billing.submitting) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") }
                        Text(when (page) {
                            BillingPage.TOPUP -> "Пополнить баланс"
                            BillingPage.SUBSCRIPTION -> if (renewing) "Продлить подписку" else "Купить подписку"
                            BillingPage.HISTORY -> "История операций"
                        }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    if (billing.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    billing.loadError?.let { BillingNotice(it, error = true) }
                    billing.topupNotice?.let { BillingNotice(it) }
                    if (page != BillingPage.HISTORY) billing.message?.let { BillingNotice(it) }
                    when (page) {
                        BillingPage.SUBSCRIPTION -> SubscriptionPage(billing, renewing, selectedDays, { selectedDays = it }, onTopup = {
                            topupReturn = BillingPage.SUBSCRIPTION; page = BillingPage.TOPUP
                        })
                        BillingPage.TOPUP -> TopupPage(billing)
                        BillingPage.HISTORY -> HistoryPage(billing)
                    }
                    if (page != BillingPage.HISTORY) {
                        TextButton(onClick = billing::refreshAll, enabled = !billing.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Обновить баланс")
                        }
                    }
                    SupportLink(Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        billing.topupConfirmation?.let { confirmation ->
            TopupSuccessSheet(confirmation, onDone = billing::acknowledgeTopup, onSubscription = {
                billing.acknowledgeTopup()
                if (billing.topupConfirmation == null) { topupReturn = null; page = BillingPage.SUBSCRIPTION }
            })
        }
    }
}

@Composable
internal fun SupportLink(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri("https://t.me/xsaidi") }, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp)); Text("Поддержка")
    }
}

@Composable
internal fun BillingNotice(text: String, error: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun BalanceSummary(balance: Long?, label: String, large: Boolean = false) {
    AccountSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.AccountBalanceWallet, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(balance?.let(::money) ?: "—", style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.widthIn(max = 180.dp))
    }
}

@Composable
private fun SubscriptionPage(billing: AccountBilling, renewing: Boolean, selectedDays: Int, onSelectedDays: (Int) -> Unit, onTopup: () -> Unit) {
    val data = billing.catalog
    var confirm by remember { mutableStateOf(false) }
    val selected = billing.pendingTariff() ?: data?.tariffs?.firstOrNull { it.days == selectedDays } ?: data?.tariffs?.firstOrNull()
    Text("Выберите срок подписки", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val deviceLimit = LocalServerDevices.current?.maxDevices
    if (billing.pending == null && deviceLimit != null && deviceLimit > 1) {
        Text("Стоимость учитывает лимит: $deviceLimit устройства. Дополнительные места продлеваются вместе с подпиской.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    BalanceSummary(data?.balance, "Доступно на балансе")
    data?.tariffs?.forEach { tariff ->
        val active = selected?.days == tariff.days
        Surface(shape = RoundedCornerShape(18.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().selectable(selected = active, enabled = !billing.busy && billing.pending == null, role = Role.RadioButton, onClick = { onSelectedDays(tariff.days) })) {
            Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${tariff.days} дней", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(money(tariff.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                RadioButton(selected = active, onClick = null, enabled = !billing.busy && billing.pending == null)
            }
        }
    }
    if (billing.purchaseBlocked) BillingNotice("Не удалось прочитать сохранённую покупку. Обратитесь в поддержку перед повторной оплатой.", true)
    if (billing.pending != null) BillingNotice("Сохранён незавершённый запрос. Проверка использует тот же номер покупки и не списывает деньги повторно.")
    if (selected != null && data != null) {
        val remaining = BillingAmounts.afterPurchase(data.balance, selected.price)
        AccountSurface {
            SummaryRow("Срок подписки", "${selected.days} дней")
            SummaryRow("Спишется с баланса", money(selected.price))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (remaining != null) SummaryRow("Останется на балансе", money(remaining), true)
            else SummaryRow("Не хватает", money(selected.price - data.balance), true)
        }
        val pending = billing.pending != null
        Button(onClick = { if (pending || remaining != null) confirm = true else onTopup() },
            enabled = !billing.busy && !billing.purchaseBlocked && (billing.fresh || pending),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (pending) "Проверить покупку" else if (remaining != null) "Оплатить ${money(selected.price)} с баланса" else "Перейти к пополнению")
        }
        Text(if (renewing) "Срок продления добавится к текущей подписке." else "После оплаты подписка появится в аккаунте.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(if (pending) "Проверить покупку?" else "Оплатить подписку?") },
            text = { Text("${selected.days} дней за ${money(selected.price)} с баланса аккаунта. ${if (renewing) "Срок добавится к текущей подписке." else "Оплата относится к текущему аккаунту."}") },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
            confirmButton = { TextButton(onClick = { confirm = false; billing.purchase(selected) }) { Text(if (pending) "Проверить" else "Оплатить") } })
    } else if (!billing.busy && billing.loadError == null) BillingNotice("Сейчас нет доступных тарифов. Обратитесь в поддержку.")
}

@Composable
private fun TopupPage(billing: AccountBilling) {
    val data = billing.catalog
    var amountText by rememberSaveable { mutableStateOf("300") }
    var methodKey by rememberSaveable { mutableStateOf<String?>(null) }
    val method = data?.methods?.firstOrNull { "${it.id}:${it.option}" == methodKey } ?: data?.methods?.firstOrNull()
    val amount = BillingAmounts.parse(amountText)
    val valid = BillingAmounts.allowed(amount, method)
    BalanceSummary(data?.balance, "Сейчас на балансе")
    Text("Сумма пополнения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(value = amountText, onValueChange = { if (it.length <= 12) amountText = it }, enabled = !billing.busy,
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.headlineLarge, suffix = { Text("₽", style = MaterialTheme.typography.headlineSmall) },
        label = { Text("Сумма в рублях") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = amountText.isNotBlank() && !valid,
        supportingText = { Text(if (method != null) "От ${money(maxOf(1000L, method.min))} до ${money(minOf(BillingAmounts.MAX_TOPUP, method.max))}" else "Можно ввести свою сумму") })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10000L, 30000L, 50000L).forEach { quick ->
            OutlinedButton(onClick = { amountText = BillingAmounts.input(quick) }, enabled = !billing.busy && BillingAmounts.allowed(quick, method), modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (amount == quick) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) { Text(money(quick)) }
        }
    }
    Text("Способ оплаты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (data?.methods.isNullOrEmpty() && !billing.busy) BillingNotice("Нет доступных способов оплаты. Обратитесь в поддержку.")
    data?.methods?.forEach { item ->
        val active = item == method
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().selectable(selected = active, enabled = !billing.busy, role = Role.RadioButton, onClick = { methodKey = "${item.id}:${item.option}" })) {
            Row(Modifier.padding(16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CreditCard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("На странице платёжного сервиса", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = active, onClick = null, enabled = !billing.busy)
            }
        }
    }
    AccountSurface {
        SummaryRow("Сумма пополнения", amount?.let(::money) ?: "—")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SummaryRow("Баланс после пополнения", if (data != null && amount != null) money(BillingAmounts.afterTopup(data.balance, amount)) else "—", true)
    }
    Button(onClick = { if (amount != null && method != null) billing.topup(amount, method) }, enabled = !billing.busy && billing.fresh && valid,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text(if (amount != null) "Пополнить на ${money(amount)}" else "Введите сумму") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Откроется защищённая страница оплаты", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Подписка оплачивается отдельно с баланса", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (billing.paymentUrl != null) TextButton(onClick = billing::openPayment, enabled = !billing.busy) {
        Text("Открыть платёж${billing.paymentAmount?.let { " на ${money(it)}" } ?: ""} ещё раз")
    }
}

@Composable
private fun HistoryPage(billing: AccountBilling) {
    LaunchedEffect(billing) { billing.loadHistory() }
    if (billing.historyBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    billing.historyError?.let { BillingNotice(it, true) }
    if (billing.historyLoaded && billing.operations.isEmpty()) Text("Операций пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
    billing.operations.forEach { operation ->
        AccountSurface {
            SummaryRow(when (operation.type) {
                "deposit" -> "Пополнение баланса"
                "subscription_payment" -> "Оплата подписки"
                "refund" -> "Возврат"
                "referral_reward" -> "Реферальное вознаграждение"
                "withdrawal" -> "Вывод средств"
                "gift_payment" -> "Покупка подарка"
                else -> "Операция по балансу"
            }, (if (operation.amount > 0) "+" else "") + money(operation.amount))
            Text(listOf(operation.date, if (operation.completed) "Выполнено" else "Ожидает подтверждения").filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (billing.historyPage < billing.historyPages) OutlinedButton(onClick = { billing.loadHistory(false) }, enabled = !billing.historyBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Показать ещё") }
    TextButton(onClick = { billing.loadHistory() }, enabled = !billing.historyBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Обновить историю") }
}
