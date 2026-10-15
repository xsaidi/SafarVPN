package com.wdtt.client.ui
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wdtt.client.ProfileSubscription
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTrafficMb(value: Double): String {
    return when {
        value >= 1024 -> String.format(Locale.US, "%.2f ГБ", value / 1024.0)
        value >= 1 -> String.format(Locale.US, "%.1f МБ", value)
        value > 0 -> String.format(Locale.US, "%.2f МБ", value)
        else -> "0 МБ"
    }
}

private fun formatSyncTime(ts: Long): String {
    if (ts <= 0L) return "ещё не обновлялась"
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
}

@Composable
fun AddSubscriptionDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (url: String) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Новая подписка", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Адрес подписки") },
                    placeholder = { Text("https://example.com/sub.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                )
                Text(
                    "Вставьте ссылку — название и профили загрузятся автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(urlInput) },
                enabled = !saving && urlInput.isNotBlank()
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Добавить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }, enabled = !saving) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun DeleteSubscriptionDialog(
    sub: ProfileSubscription,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить подписку?") },
        text = { Text("«${sub.name}» и её профили будут удалены.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun SubscriptionInfoCard(
    sub: ProfileSubscription,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: (() -> Unit)?,
    onPingAll: () -> Unit,
    isPinging: Boolean,
    modifier: Modifier = Modifier,
    statusLabel: String? = null,
    active: Boolean = false,
    expiresAt: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(sub.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                statusLabel?.let { label ->
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (active) { if (colors.background.luminance() < 0.22f) Color(0xFF86EBA5) else Color(0xFF146C43) } else colors.onSurfaceVariant)
                }
                expiresAt?.let { Text("Доступ до $it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                if (!sub.isAccount && sub.description.isNotBlank()) Text(sub.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(48.dp)) {
                if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Обновить подписку", tint = colors.primary)
            }
            IconButton(onClick = onPingAll, enabled = !isPinging, modifier = Modifier.size(48.dp)) {
                if (isPinging) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.SignalCellularAlt, contentDescription = "Замер задержки всех серверов", tint = colors.primary)
            }
            if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить подписку", tint = colors.error)
            }
        }
        if (sub.remoteTrafficUsedMb > 0 || sub.trafficLimitMb > 0) Text(
            if (sub.trafficLimitMb > 0) "Трафик: ${formatTrafficMb(sub.remoteTrafficUsedMb)} из ${formatTrafficMb(sub.trafficLimitMb)}"
            else "Трафик: ${formatTrafficMb(sub.remoteTrafficUsedMb)}",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (sub.lastSyncError.isNotBlank()) Text("Не удалось обновить подписку. Повторите обновление.", style = MaterialTheme.typography.bodySmall, color = colors.error)
    }
}