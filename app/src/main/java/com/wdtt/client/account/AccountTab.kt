package com.wdtt.client.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AccountTab() {
    val context = LocalContext.current
    val account = remember { AccountRepository.get(context) }
    val state by account.state.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Аккаунт",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (MaterialTheme.colorScheme.background.luminance() < 0.22f) Color.White
                    else MaterialTheme.colorScheme.onBackground,
            )
            AccountCard(
                state = state,
                onTelegramLogin = { account.beginLogin(context, "telegram") },
                onGoogleLogin = { account.beginLogin(context, "google") },
                onCancelLogin = account::cancelLogin,
                onRefresh = account::refreshAccount,
                onLogout = account::logout,
            )
        }
    }
}
