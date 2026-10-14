package com.wdtt.client.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AccountTab() {
    val context = LocalContext.current
    val account = remember { AccountRepository.get(context) }
    val state by account.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Аккаунт SafarVPN", style = MaterialTheme.typography.headlineSmall)
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
