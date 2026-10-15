package com.wdtt.client.account

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.random.Random

// Only an artwork seed is stored here. Auth/session storage is not involved.
internal class AccountAvatarPreference(context: Context, userId: Long) {
    private val preferences = context.applicationContext.getSharedPreferences("account_avatars", Context.MODE_PRIVATE)
    private val key = "seed_$userId"
    val intValue: Int = if (preferences.contains(key)) preferences.getInt(key, 0) else {
        Random.nextInt().also { preferences.edit().putInt(key, it).apply() }
    }
}

@Composable
internal fun rememberAccountAvatar(userId: Long): AccountAvatarPreference {
    val context = LocalContext.current
    return remember(context, userId) { AccountAvatarPreference(context, userId) }
}

private val AvatarPalette = listOf(
    Color(0xFF21184E), Color(0xFF7554F6), Color(0xFFB49BFF),
    Color(0xFF83D8F7), Color(0xFF41318A), Color(0xFFE3D9FF),
)

private data class AvatarTile(val background: Color, val foreground: Color, val shape: Int, val rotation: Int)

@Composable
internal fun AccountAvatar(seed: Int, modifier: Modifier = Modifier) {
    val tiles = remember(seed) {
        val random = Random(seed)
        List(16) {
            val index = random.nextInt(AvatarPalette.size)
            AvatarTile(
                AvatarPalette[index],
                AvatarPalette[(index + random.nextInt(1, AvatarPalette.size)) % AvatarPalette.size],
                random.nextInt(3), random.nextInt(4),
            )
        }
    }
    Canvas(modifier.size(88.dp).clip(CircleShape).border(1.dp, Color(0xFFB49BFF), CircleShape)) {
        val cell = size.width / 4f
        tiles.forEachIndexed { index, tile ->
            val left = (index % 4) * cell
            val top = (index / 4) * cell
            drawRect(tile.background, Offset(left, top), Size(cell + 0.5f, cell + 0.5f))
            when (tile.shape) {
                0 -> drawCircle(tile.foreground, cell * 0.44f, Offset(left + cell / 2, top + cell / 2))
                1 -> drawArc(tile.foreground, tile.rotation * 90f, 90f, true, Offset(left, top), Size(cell, cell))
                else -> {
                    val corners = listOf(
                        Offset(left, top), Offset(left + cell, top),
                        Offset(left + cell, top + cell), Offset(left, top + cell),
                    )
                    val path = Path().apply {
                        val first = corners[tile.rotation]
                        moveTo(first.x, first.y)
                        for (step in 1..2) {
                            val point = corners[(tile.rotation + step) % 4]
                            lineTo(point.x, point.y)
                        }
                        close()
                    }
                    drawPath(path, tile.foreground)
                }
            }
        }
    }
}
