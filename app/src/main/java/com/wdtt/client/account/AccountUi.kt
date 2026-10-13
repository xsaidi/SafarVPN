package com.wdtt.client.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wdtt.client.ui.AppSectionCard

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
    AppSectionCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Аккаунт",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        state.error?.takeIf(String::isNotBlank)?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        when {
            state.profile == null && (state.pendingProvider != null || state.busy) -> {
                LoginPending(state = state, onCancelLogin = onCancelLogin)
            }

            state.profile == null -> {
                Text(
                    text = "Вы используете приложение как гость. Ваши текущие профили и настройки останутся на этом устройстве.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onTelegramLogin,
                    enabled = !state.busy && state.telegramAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Войти через Telegram")
                }
                OutlinedButton(
                    onClick = onGoogleLogin,
                    enabled = !state.busy && state.googleAvailable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Войти через Google")
                }
            }

            else -> {
                SignedInAccount(
                    state = state,
                    profile = state.profile,
                    onRefresh = onRefresh,
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
private fun LoginPending(state: AccountUiState, onCancelLogin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp).semantics { liveRegion = LiveRegionMode.Polite },
            strokeWidth = 2.dp,
        )
        Text(
            text = state.pendingProvider?.let {
                "Завершите вход в $it. Если приложение не открылось автоматически, вернитесь сюда."
            } ?: "Завершите вход. Если приложение не открылось автоматически, вернитесь сюда.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
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
    Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    profile.email?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    profile.telegramUsername?.takeIf(String::isNotBlank)?.let {
        Text("Telegram: $it", style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        text = "ID аккаунта: ${profile.userId}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Подписка VK/TURN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    when {
        state.subscription == null && state.refreshing -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    strokeWidth = 2.dp,
                )
                Text("Статус подписки загружается…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.subscription == null -> {
            Text("Статус подписки пока недоступен", style = MaterialTheme.typography.bodyMedium)
        }

        else -> {
            val subscription = state.subscription
            Text(
                text = if (subscription.hasSubscription) subscription.statusLabel else "Подписка не найдена",
                style = MaterialTheme.typography.bodyMedium,
                color = if (subscription.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            subscription.expiresAtLabel?.takeIf(String::isNotBlank)?.let {
                Text("Действует до: $it", style = MaterialTheme.typography.bodyMedium)
            }
            subscription.details?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    OutlinedButton(
        onClick = onRefresh,
        enabled = !state.busy && !state.refreshing,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        if (state.refreshing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (state.refreshing) "Обновляем…" else "Обновить статус")
    }
    TextButton(
        onClick = onLogout,
        enabled = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("Выйти из аккаунта")
    }
}
