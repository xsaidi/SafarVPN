package com.wdtt.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** State comes from TunnelManager; tapping never sets a local connected state. */
@Composable
internal fun TunnelConnectButton(
    connected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val label = when {
        connecting -> "Подключение..."
        connected -> "Подключено"
        else -> "Подключиться"
    }
    val accent = when {
        connecting -> Color(0xFFFFCC80)
        connected -> Color(0xFF57E4C3)
        else -> Color(0xFFA9BACB)
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, 280.dp)
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = if (connected || connecting) .27f else .08f), Color.Transparent)), radius)
                listOf(.96f, .89f, .80f, .75f).forEachIndexed { index, ratio ->
                    drawCircle(accent.copy(alpha = .12f + index * .10f), radius * ratio, style = Stroke((if (index == 3) 2.dp else 1.dp).toPx()))
                }
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(diameter * .70f).semantics {
                    stateDescription = label
                    contentDescription = if (connected) "Отключить VPN" else if (connecting) "Отменить подключение VPN" else "Подключить VPN"
                    liveRegion = LiveRegionMode.Polite
                },
                shape = CircleShape,
                contentPadding = PaddingValues(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connected) Color(0xFF063D38) else Color(0xFF202E3D),
                    contentColor = accent,
                    disabledContainerColor = Color(0xFF202E3D),
                    disabledContentColor = accent.copy(alpha = .6f),
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, accent.copy(alpha = if (enabled) .8f else .3f)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (connecting) CircularProgressIndicator(Modifier.size(28.dp), color = accent, strokeWidth = 2.dp)
                    else Icon(if (connected) Icons.Default.Lock else Icons.Default.PowerSettingsNew, null, Modifier.size(32.dp))
                    Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
