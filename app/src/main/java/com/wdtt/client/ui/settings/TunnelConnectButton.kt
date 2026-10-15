package com.wdtt.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Presentation only: readiness and start/stop gating remain in TunnelManager/SettingsTab. */
@Composable
internal fun TunnelConnectButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val status = when { connecting -> "Подключение…"; connected -> "Подключено"; else -> "Не подключено" }
    val statusColor = when {
        connected -> if (colors.background.luminance() < 0.22f) Color(0xFF86EBA5) else Color(0xFF146C43)
        else -> colors.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Surface(shape = CircleShape, color = statusColor, modifier = Modifier.size(8.dp)) {}
            Text(status, color = statusColor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.primary.copy(alpha = 0.65f), size.minDimension / 2 - 2.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.size(164.dp).semantics {
                stateDescription = status
                contentDescription = if (connected) "Отключить VPN" else if (connecting) "Отменить подключение VPN" else "Подключить VPN"
            }, shape = CircleShape, contentPadding = PaddingValues(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.primary,
                    disabledContainerColor = colors.surface, disabledContentColor = colors.onSurfaceVariant),
                border = BorderStroke(2.dp, if (enabled) colors.primary else colors.outlineVariant)) {
                if (connecting) CircularProgressIndicator(Modifier.size(42.dp), color = colors.primary, strokeWidth = 3.dp)
                else Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(52.dp))
            }
        }
        Text(if (connected) "Нажмите, чтобы отключить" else if (connecting) "Нажмите, чтобы отменить" else "Нажмите, чтобы подключиться",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
