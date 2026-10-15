package com.wdtt.client.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopupSuccessSheet(confirmation: TopupConfirmation, onDone: () -> Unit, onSubscription: () -> Unit) {
    val success = if (MaterialTheme.colorScheme.background.luminance() < 0.22f) Color(0xFF86EBA5) else Color(0xFF146C43)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(64.dp).background(success.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(36.dp), tint = success)
            }
            Text("Баланс пополнен", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("Оплата прошла успешно", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccountSurface {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Зачислено", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("+${money(confirmation.payment.amount)}", color = success, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("На балансе", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(money(confirmation.balance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Text("Теперь можно оплатить подписку с баланса", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Готово") }
            TextButton(onClick = onSubscription, modifier = Modifier.heightIn(min = 48.dp)) { Text("К подписке") }
        }
    }
}
