package com.wdtt.client.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Account data stays independent from the presentation and local avatar preference.
data class AccountUiState(
    val profile: AccountProfileUi? = null,
    val subscription: WdttSubscriptionUi? = null,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val pendingProvider: String? = null,
    val error: String? = null,
    val googleAvailable: Boolean = true,
    val telegramAvailable: Boolean = true,
)

data class AccountProfileUi(
    val displayName: String,
    val email: String? = null,
    val telegramUsername: String? = null,
    val userId: Long,
)

data class WdttSubscriptionUi(
    val hasSubscription: Boolean,
    val isActive: Boolean,
    val statusLabel: String,
    val expiresAtLabel: String? = null,
    val details: String? = null,
)

@Composable
fun AccountCard(
    state: AccountUiState,
    onTelegramLogin: () -> Unit,
    onGoogleLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        state.error?.takeIf(String::isNotBlank)?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val profile = state.profile
        if (profile != null) {
            SignedInAccount(state, profile, onRefresh, onLogout)
        } else {
            AccountSurface {
                Text("Добро пожаловать", style = MaterialTheme.typography.titleLarge)
                if (state.pendingProvider != null || state.busy) {
                    LoginPending(state, onCancelLogin)
                } else {
                    Text(
                        "Войдите, чтобы увидеть свой аккаунт и подписку. Ручные профили и настройки останутся на устройстве.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onTelegramLogin,
                        enabled = state.telegramAvailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Войти через Telegram") }
                    OutlinedButton(
                        onClick = onGoogleLogin,
                        enabled = state.googleAvailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Войти через Google") }
                }
            }
        }
    }
}

@Composable
internal fun AccountSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.22f
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surface,
        contentColor = colors.onBackground,
        border = BorderStroke(1.dp, lerp(colors.outlineVariant, colors.primary, if (dark) 0.45f else 0.22f)),
    ) {
        Column(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(
                    lerp(colors.surface, colors.primaryContainer, if (dark) 0.42f else 0.35f),
                    colors.surface,
                    lerp(colors.surface, colors.primaryContainer, 0.20f),
                )),
            ).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun LoginPending(state: AccountUiState, onCancelLogin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(
            text = state.pendingProvider?.let {
                "Завершите вход в $it. Если приложение не открылось автоматически, вернитесь сюда."
            } ?: "Завершите вход. Если приложение не открылось автоматически, вернитесь сюда.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    TextButton(onClick = onCancelLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Отменить вход")
    }
}

@Composable
private fun SignedInAccount(
    state: AccountUiState,
    profile: AccountProfileUi,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by rememberSaveable(profile.userId) { mutableStateOf(false) }
    var billingPage by rememberSaveable(profile.userId) { mutableStateOf<BillingPage?>(null) }
    var showDevices by remember { mutableStateOf(false) }
    val devices = LocalServerDevices.current
    if (showDevices && devices != null) ServerDevicesDialog(devices) { showDevices = false }
    val billing = rememberAccountBilling(profile.userId)
    LaunchedEffect(devices?.maxDevices) { if (devices?.maxDevices != null) billing.reload() }
    val uriHandler = LocalUriHandler.current
    val avatar = rememberAccountAvatar(profile.userId)
    if (billingPage == null) billing.topupConfirmation?.let { confirmation ->
        TopupSuccessSheet(confirmation, onDone = billing::acknowledgeTopup, onSubscription = {
            billing.acknowledgeTopup()
            if (billing.topupConfirmation == null) billingPage = BillingPage.SUBSCRIPTION
        })
    }
    billingPage?.let { page ->
        key(profile.userId, page) {
            BillingPages(billing, state.subscription?.isActive == true, page, onDismiss = { billingPage = null })
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Серверы аккаунта будут удалены с устройства. Ручные профили и добавленные вручную подписки сохранятся.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
        )
    }
    AccountSurface {
        // Stack the profile at large font scales and on narrow displays instead of truncating it.
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 280.dp || fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AccountAvatar(avatar.intValue, Modifier.size(72.dp))
                    ProfileDetails(profile)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AccountAvatar(avatar.intValue, Modifier.size(72.dp))
                    Column(Modifier.weight(1f)) {
                        ProfileDetails(profile)
                    }
                }
            }
        }
    }
    AccountSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Баланс", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(billing.catalog?.balance?.let(::money) ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            if (billing.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        billing.loadError?.let { BillingNotice(it, true) }
        billing.topupNotice?.let { BillingNotice(it) }
        Button(
            onClick = { billingPage = BillingPage.TOPUP },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Пополнить") }
        TextButton(onClick = { billingPage = BillingPage.HISTORY }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("История операций", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    AccountSurface {
        Text("Подписка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SubscriptionStatus(state)
        Button(
            onClick = { billingPage = BillingPage.SUBSCRIPTION },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (state.subscription?.hasSubscription == true) "Продлить подписку" else "Купить подписку")
        }
        TextButton(
            onClick = { billing.reload(); onRefresh() },
            enabled = !state.busy && !state.refreshing && !billing.busy,
            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
        ) {
            if (state.refreshing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (state.refreshing) "Обновляем…" else "Обновить статус")
        }
    }
    devices?.let { controller ->
        AccountSurface {
            Text("Устройства", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(controller.maxDevices?.let { "${controller.devices.size} из $it · общий лимит для всех серверов" }
                ?: "Загружаем список устройств…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            controller.notice?.let { BillingNotice(it) }
            OutlinedButton(onClick = { showDevices = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)) { Text("Управление устройствами") }
        }
    }
    OutlinedButton(
        onClick = { uriHandler.openUri("https://t.me/xsaidi") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Поддержка", modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
    TextButton(
        onClick = { confirmLogout = true },
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text("Выйти из аккаунта")
    }
}

@Composable
private fun ProfileDetails(profile: AccountProfileUi) {
    Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    profile.telegramUsername?.takeIf(String::isNotBlank)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    profile.email?.takeIf(String::isNotBlank)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SubscriptionStatus(state: AccountUiState) {
    val subscription = state.subscription
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            subscription == null && state.refreshing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Статус подписки загружается…", style = MaterialTheme.typography.bodyMedium)
            }
            subscription == null -> Text("Статус подписки пока недоступен", style = MaterialTheme.typography.bodyMedium)
            else -> {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.22f
                val statusColor = if (subscription.isActive) {
                    if (dark) Color(0xFF4ADE9C) else Color(0xFF146C43)
                } else MaterialTheme.colorScheme.error
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).background(statusColor, CircleShape))
                    Text(
                        if (subscription.hasSubscription) subscription.statusLabel else "Подписка не найдена",
                        style = MaterialTheme.typography.bodyLarge,
                        color = statusColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                subscription.expiresAtLabel?.takeIf(String::isNotBlank)?.let { expiry ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Действует до", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(expiry, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
