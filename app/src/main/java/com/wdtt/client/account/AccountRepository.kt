package com.wdtt.client.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.ProfilesStore
import com.wdtt.client.SubscriptionImport

private class StaleAccountRequest : IOException("Account request superseded")

private class AccountSession(
    val access: String,
    val refresh: String,
    val profile: JSONObject,
    val subscription: JSONObject? = null,
    val checkedAt: Long? = null,
) {
    fun json(): JSONObject = JSONObject().put("access_token", access).put("refresh_token", refresh)
        .put("profile", profile).put("subscription", subscription).put("checked_at", checkedAt)
}

/** Application-scoped, with all state/persistence mutations guarded against stale requests. */
class AccountRepository private constructor(context: Context) {
    private val store = AccountStore(context.applicationContext)
    private val api = AccountApi()
    private val profiles = ProfilesStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AccountGeneration()
    private val refreshMutex = Mutex()
    private val profilesMutex = Mutex()
    private var session: AccountSession? = restoreSession()
    private var pending = store.pending()?.takeIf {
        it.provider in setOf("google", "telegram") && MobileAuthProtocol.isOpaque(it.state) &&
            MobileAuthProtocol.isOpaque(it.verifier) && MobileAuthProtocol.isOpaque(it.sessionId) &&
            it.expiresAt > System.currentTimeMillis() && it.expiresAt <= System.currentTimeMillis() + 300_000L
    }
    private var exchanging = false
    private val mutableState = MutableStateFlow(AccountUiState(
        profile = session?.profile?.toProfile(),
        subscription = session?.toSubscription(),
        pendingProvider = pending?.provider?.displayProvider(),
        error = if (session != null) "Показаны сохранённые данные аккаунта." else null,
    ))
    val state: StateFlow<AccountUiState> = mutableState.asStateFlow()

    init {
        if (pending == null) runCatching { store.savePending(null) }
        pending?.let { scheduleExpiry(it, generation.current()) }
        if (session == null) clearAccountProfiles()
    }

    fun beginLogin(context: Context, provider: String) {
        if (provider !in setOf("google", "telegram")) return
        val secretState = MobileAuthProtocol.randomSecret()
        val verifier = MobileAuthProtocol.randomSecret()
        val login = PendingLogin(provider, secretState, verifier, "", System.currentTimeMillis() + 300_000)
        var previous: PendingLogin? = null
        var accepted = false
        var gen = generation.current()
        generation.apply(gen) {
            if (session != null) return@apply
            accepted = true
            gen = generation.advance {
                previous = pending
                pending = login
                exchanging = false
                mutableState.value = mutableState.value.copy(busy = true, refreshing = false,
                    pendingProvider = provider.displayProvider(), error = null)
            }
        }
        if (!accepted) return
        previous?.let { cancelRemotely(it) }
        scope.launch {
            try {
                // The start proof is durable before sending it; incomplete starts are discarded on restore.
                if (!generation.apply(gen) { store.savePending(login) }) return@launch
                val result = api.request("/auth/mobile/start", JSONObject().put("provider", provider)
                    .put("return_target", "android").put("client_state", secretState)
                    .put("code_challenge", MobileAuthProtocol.challenge(verifier)).put("code_challenge_method", "S256"))
                val sessionId = result.getString("session_id")
                val launchUrl = result.getString("launch_url")
                val ttl = result.getLong("expires_in")
                check(result.getString("provider") == provider && MobileAuthProtocol.isOpaque(sessionId) && ttl in 1..300)
                check(MobileAuthProtocol.trustedLaunch(launchUrl, provider, sessionId))
                val active = PendingLogin(provider, secretState, verifier, sessionId,
                    minOf(login.expiresAt, System.currentTimeMillis() + ttl * 1000))
                if (!generation.apply(gen) {
                    store.savePending(active)
                    pending = active
                    mutableState.value = mutableState.value.copy(busy = false)
                }) {
                    cancelRemotely(active)
                    return@launch
                }
                scheduleExpiry(active, gen)
                withContext(Dispatchers.Main) {
                    generation.apply(gen) {
                        // Trusted external browser/Telegram handles HTTPS; no embedded OAuth WebView.
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(launchUrl))
                            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            } catch (_: Exception) {
                generation.apply(gen) {
                    pending?.let { cancelRemotely(it) }
                    pending = null
                    runCatching { clearPending() }
                    mutableState.value = mutableState.value.copy(busy = false, pendingProvider = null,
                        error = "Не удалось начать вход. Проверьте подключение и попробуйте снова.")
                }
            }
        }
    }

    /** Called before any ACTION_VIEW profile import. Invalid callbacks cannot disturb a valid login. */
    fun handleReturn(value: String) {
        val gen = generation.current()
        var login: PendingLogin? = null
        var returned: AuthReturn? = null
        generation.apply(gen) {
            returned = MobileAuthProtocol.parseReturn(value, pending, System.currentTimeMillis())
            if (returned == null) {
                mutableState.value = mutableState.value.copy(error = "Ссылка входа недействительна или устарела. Начните вход заново.")
            } else if (!exchanging) {
                login = pending
                if (returned?.error == null) {
                    exchanging = true
                    mutableState.value = mutableState.value.copy(busy = true, error = null)
                }
            }
        }
        val active = login ?: return
        val callback = returned ?: return
        if (callback.error != null) {
            cancelLogin("Вход не завершён. Попробуйте снова.")
            return
        }
        scope.launch {
            try {
                val response = api.request("/auth/mobile/exchange", proof(active)
                    .put("handoff_code", callback.code))
                val signedIn = tokens(response, safeProfile(response.getJSONObject("user")))
                val applied = generation.apply(gen) {
                    try { store.saveSession(signedIn.json()) }
                    catch (error: Exception) {
                        runCatching { store.clear() }
                        revokeRemotely(signedIn.refresh)
                        throw error
                    }
                    generation.advance {
                        session = signedIn
                        pending = null
                        exchanging = false
                        mutableState.value = AccountUiState(profile = signedIn.profile.toProfile())
                    }
                }
                if (applied) refreshAccount() else revokeRemotely(signedIn.refresh)
            } catch (error: Exception) {
                generation.apply(gen) {
                    pending = null
                    exchanging = false
                    runCatching { clearPending() }
                    mutableState.value = mutableState.value.copy(busy = false, pendingProvider = null,
                        error = if (error is AccountApiException && error.status == 410)
                            "Срок ссылки входа истёк. Начните вход заново."
                        else "Не удалось завершить вход. Начните вход заново.")
                }
            }
        }
    }

    fun cancelLogin() = cancelLogin(null)

    private fun cancelLogin(message: String?) {
        var previous: PendingLogin? = null
        generation.advance {
            previous = pending
            pending = null
            exchanging = false
            runCatching { clearPending() }
            mutableState.value = mutableState.value.copy(busy = false, refreshing = false,
                pendingProvider = null, error = message)
        }
        previous?.let { cancelRemotely(it) }
    }

    fun logout() {
        var previousSession: AccountSession? = null
        var previousLogin: PendingLogin? = null
        generation.advance {
            previousSession = session
            previousLogin = pending
            session = null
            pending = null
            exchanging = false
            val cleared = runCatching { store.clear() }.isSuccess
            mutableState.value = AccountUiState(error = if (cleared) null else
                "Не удалось очистить хранилище аккаунта. Повторите выход.")
        }
        clearAccountProfiles()
        previousSession?.let { revokeRemotely(it.refresh) }
        previousLogin?.let { cancelRemotely(it) }
    }

    fun onForeground() {
        val gen = generation.current()
        var login: PendingLogin? = null
        var signedIn = false
        generation.apply(gen) {
            login = pending
            signedIn = session != null
        }
        val active = login
        if (active != null && System.currentTimeMillis() >= active.expiresAt) {
            cancelLogin("Время ожидания входа истекло. Попробуйте снова.")
        } else if (active?.provider == "telegram" && active.sessionId.isNotEmpty()) {
            // Status ONLY. The handoff is available solely to the approving bot user's return button.
            scope.launch {
                try {
                    val response = api.request("/auth/mobile/poll", proof(active))
                    if (response.getString("status") == "approved") generation.apply(gen) {
                        if (pending === active && !exchanging) mutableState.value = mutableState.value.copy(
                            error = "Подтверждение получено. Нажмите «Вернуться в приложение» в Telegram.")
                    }
                } catch (error: Exception) {
                    if (error is AccountApiException && error.status == 410) generation.apply(gen) {
                        if (pending === active && !exchanging) cancelLogin("Время ожидания входа истекло. Попробуйте снова.")
                    }
                }
            }
        }
        if (signedIn) refreshAccount()
    }

    fun refreshAccount() {
        val gen = generation.current()
        var start = false
        generation.apply(gen) {
            if (session != null && pending == null && !mutableState.value.refreshing) {
                start = true
                mutableState.value = mutableState.value.copy(refreshing = true, error = null)
            }
        }
        if (!start) return
        scope.launch {
            var failed = false
            try {
                val profile = safeProfile(authorized("/auth/me", gen))
                generation.apply(gen) {
                    val current = session ?: throw StaleAccountRequest()
                    val updated = AccountSession(current.access, current.refresh, profile,
                        current.subscription, current.checkedAt)
                    store.write("auth", updated.json())
                    session = updated
                    mutableState.value = mutableState.value.copy(profile = profile.toProfile())
                }
            } catch (_: Exception) { failed = true }
            try {
                val owner = currentSession(gen).profile.getLong("id")
                val response = authorized("/auth/mobile/subscription/profiles", gen)
                val subscription = safeSubscription(response)
                val configuration = response.optJSONObject("configuration")
                val active = subscription.optJSONObject("subscription")?.optBoolean("is_active") == true
                check(active == (configuration != null))
                val parsed = configuration?.let {
                    SubscriptionImport.parsePayload(it.toString()) ?: throw IOException("Invalid account profiles")
                }
                profilesMutex.withLock {
                    val retained = if (parsed == null && subscription.optBoolean("has_subscription")) {
                        AccountProfiles.retained(owner, profiles.profiles.first()).takeIf { it.isNotEmpty() }
                            ?.let { com.wdtt.client.ParsedRemoteSubscription(it) }
                    } else null
                    profiles.syncAccountSubscription(owner, parsed ?: retained) { action -> generation.apply(gen, action) }
                    generation.apply(gen) {
                        val current = session ?: throw StaleAccountRequest()
                        val updated = AccountSession(current.access, current.refresh, current.profile,
                            subscription, System.currentTimeMillis())
                        store.write("auth", updated.json())
                        session = updated
                        mutableState.value = mutableState.value.copy(subscription = updated.toSubscription())
                    }
                }
            } catch (_: Exception) { failed = true }
            generation.apply(gen) {
                mutableState.value = mutableState.value.copy(refreshing = false, error = if (failed)
                    "Данные аккаунта сейчас недоступны. Сохранённый статус может быть устаревшим. Попробуйте обновить позже."
                    else null)
            }
        }
    }

    internal suspend fun billingRequest(owner: Long, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val history = body == null && Regex("^/balance/transactions\\?page=[1-9][0-9]{0,5}&per_page=20$").matches(path)
        val paymentStatus = body == null && Regex("^/balance/pending-payments/[a-z][a-z0-9_]{0,31}/([1-9][0-9]{0,18}|latest)$").matches(path)
        check(history || paymentStatus || (path == "/balance/topup" && body != null) || path in setOf("/auth/mobile/billing", "/auth/mobile/billing/topup", "/auth/mobile/billing/purchase"))
        val gen = generation.current()
        check(currentSession(gen).profile.getLong("id") == owner)
        val result = authorized(path, gen, body)
        check(currentSession(gen).profile.getLong("id") == owner)
        result
    }

    internal fun pendingServerOperation(owner: Long): JSONObject? = synchronized(generation) {
        check(session?.profile?.getLong("id") == owner)
        store.read("server_operation")?.takeIf { it.optLong("owner") == owner }
    }

    internal fun saveServerOperation(owner: Long, value: JSONObject?) = synchronized(generation) {
        check(session?.profile?.getLong("id") == owner)
        store.write("server_operation", value?.put("owner", owner))
    }

    internal fun currentDeviceUnbindPending(deviceId: String): Boolean = synchronized(generation) {
        val owner = session?.profile?.getLong("id") ?: return@synchronized false
        val operation = store.read("server_operation") ?: return@synchronized false
        operation.optLong("owner") == owner && operation.optString("kind") == "unbind" &&
            operation.optString("device_id") == deviceId
    }

    /** Only query this account for a matching account-owned profile, never for manual servers. */
    internal suspend fun connectionDeviceProblem(profileId: String, peer: String, password: String,
        deviceId: String): String? {
        if (!profileId.startsWith(AccountProfiles.PREFIX)) return null
        val gen = generation.current()
        return try {
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                val owner = currentSession(gen).profile.getLong("id")
                val profile = profiles.profiles.first().firstOrNull { it.id == profileId } ?: return@withTimeoutOrNull null
                if (profile.peer != peer || profile.password != password ||
                    AccountProfiles.prepare(owner, listOf(profile)).single().id != profileId) return@withTimeoutOrNull null
                val subscription = currentSession(gen).toSubscription()
                if (subscription?.hasSubscription == true && !subscription.isActive) {
                    return@withTimeoutOrNull when (subscription.statusLabel) {
                        "Срок подписки истёк" -> "Подписка истекла. Продлите подписку в разделе «Аккаунт». Серверы сохранены."
                        "Ожидает активации" -> "Подписка активируется. Дождитесь подтверждения серверов и обновите статус аккаунта."
                        else -> "Подписка неактивна. Обновите статус или продлите её в разделе «Аккаунт»."
                    }
                }
                val problem = ConnectionDeviceCheck.check(deviceId) { suffix ->
                    // Diagnostics must not hold up connection startup or trigger a slow token refresh.
                    withContext(Dispatchers.IO) {
                        check(suffix in setOf("/status", "/devices"))
                        api.request("/auth/mobile/server/v1$suffix", accessToken = currentSession(gen).access,
                            callTimeoutMillis = 1_500)
                    }
                }
                currentSession(gen) // Discard advice if the user logged out or changed account.
                problem
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            null // An unavailable account API must not break an otherwise working tunnel.
        }
    }

    internal fun pendingDeviceLimit(owner: Long): JSONObject? = synchronized(generation) {
        check(session?.profile?.getLong("id") == owner)
        store.read("device_limit_operation")?.takeIf { it.optLong("owner") == owner }
    }

    internal fun saveDeviceLimit(owner: Long, value: JSONObject?) = synchronized(generation) {
        check(session?.profile?.getLong("id") == owner)
        store.write("device_limit_operation", value?.put("owner", owner))
    }

    internal suspend fun serverRequest(owner: Long, suffix: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        check((body == null && suffix in setOf("/status", "/devices", "/device-limit")) ||
            (body != null && suffix in setOf("/operations", "/device-limit/quote", "/device-limit/operations")) ||
            (body == null && Regex("^/(device-limit/)?operations/[a-f0-9-]{36}$").matches(suffix)))
        val gen = generation.current()
        check(currentSession(gen).profile.getLong("id") == owner)
        try {
            val result = authorized("/auth/mobile/server/v1$suffix", gen, body)
            check(currentSession(gen).profile.getLong("id") == owner)
            result
        } catch (error: AccountApiException) {
            if (error.status == 403) refreshAccount()
            throw error
        }
    }

    private suspend fun authorized(path: String, gen: Long, body: JSONObject? = null): JSONObject {
        val current = currentSession(gen)
        try { return api.request(path, body, accessToken = current.access) }
        catch (error: AccountApiException) {
            if (error.accountInactive) expireSession(gen)
            if (error.status != 401) throw error
        }
        refreshMutex.withLock {
            val latest = currentSession(gen)
            if (latest.access == current.access) {
                try {
                    val response = api.request("/auth/refresh", JSONObject().put("refresh_token", latest.refresh))
                    val refreshed = tokens(response, latest.profile, latest.subscription, latest.checkedAt)
                    if (!generation.apply(gen) {
                        store.write("auth", refreshed.json())
                        session = refreshed
                    }) throw StaleAccountRequest()
                } catch (error: AccountApiException) {
                    if (error.status in setOf(401, 403)) expireSession(gen)
                    throw error
                }
            }
        }
        try { return api.request(path, body, accessToken = currentSession(gen).access) }
        catch (error: AccountApiException) {
            if (error.status == 401 || error.accountInactive) expireSession(gen)
            throw error
        }
    }

    private fun currentSession(gen: Long): AccountSession {
        var current: AccountSession? = null
        generation.apply(gen) { current = session }
        return current ?: throw StaleAccountRequest()
    }

    private fun expireSession(gen: Long) {
        generation.apply(gen) {
            generation.advance {
                session = null
                pending = null
                exchanging = false
                runCatching { store.clear() }
                mutableState.value = AccountUiState(error = "Сессия завершена. Войдите в аккаунт снова.")
            }
            clearAccountProfiles()
        }
    }

    private fun clearAccountProfiles() {
        scope.launch {
            runCatching {
                profilesMutex.withLock {
                    // A queued logout may run after a new login starts. Inspect current authority under
                    // the same lock instead of skipping cleanup merely because its generation changed.
                    profiles.syncAccountSubscription(null, null) { action ->
                        synchronized(generation) {
                            if (session == null) { action(); true } else false
                        }
                    }
                }
            }
        }
    }

    /** Selecting an account profile must use the same logout fence as downloading it. */
    internal fun profileGuard(profile: ConnectionProfile): ((() -> Unit) -> Boolean)? {
        val gen = generation.current()
        var valid = false
        generation.apply(gen) {
            val signedIn = session
            valid = signedIn?.toSubscription()?.hasSubscription == true &&
                AccountProfiles.prepare(signedIn.profile.getLong("id"), listOf(profile)).single().id == profile.id
        }
        return if (valid) ({ action -> generation.apply(gen, action) }) else null
    }

    private fun scheduleExpiry(login: PendingLogin, gen: Long) {
        scope.launch {
            delay((login.expiresAt - System.currentTimeMillis()).coerceAtLeast(1))
            generation.apply(gen) {
                if (pending === login && !exchanging) cancelLogin("Время ожидания входа истекло. Попробуйте снова.")
            }
        }
    }

    private fun proof(login: PendingLogin): JSONObject = JSONObject().put("session_id", login.sessionId)
        .put("client_state", login.state).put("code_verifier", login.verifier)

    private fun clearPending() {
        // Pending login belongs to a guest. Erasing its dedicated key also invalidates a failed disk clear.
        if (session == null) store.clear() else store.savePending(null)
    }

    private fun cancelRemotely(login: PendingLogin) {
        if (login.sessionId.isNotEmpty()) scope.launch { runCatching { api.request("/auth/mobile/cancel", proof(login)) } }
    }

    private fun revokeRemotely(refresh: String) {
        scope.launch { runCatching { api.request("/auth/logout", JSONObject().put("refresh_token", refresh)) } }
    }

    private fun restoreSession(): AccountSession? = store.read("auth")?.let {
        runCatching {
            AccountSession(it.getString("access_token").also { token -> check(token.isNotBlank()) },
                it.getString("refresh_token").also { token -> check(token.isNotBlank()) },
                safeProfile(it.getJSONObject("profile")),
                it.optJSONObject("subscription")?.let(::safeSubscription),
                if (it.isNull("checked_at")) null else it.getLong("checked_at"))
        }.getOrNull()
    }

    companion object {
        @Volatile private var instance: AccountRepository? = null
        fun get(context: Context): AccountRepository = instance ?: synchronized(this) {
            instance ?: AccountRepository(context.applicationContext).also { instance = it }
        }
    }
}

private fun tokens(json: JSONObject, profile: JSONObject, subscription: JSONObject? = null,
    checkedAt: Long? = null): AccountSession {
    check(json.getString("token_type").equals("bearer", ignoreCase = true))
    val access = json.getString("access_token")
    val refresh = json.getString("refresh_token")
    check(access.isNotBlank() && access.length <= 16384 && refresh.isNotBlank() && refresh.length <= 16384)
    return AccountSession(access, refresh, profile, subscription, checkedAt)
}

private fun safeProfile(json: JSONObject): JSONObject {
    val result = JSONObject().put("id", json.getLong("id").also { check(it > 0) })
    for (key in listOf("first_name", "last_name", "email", "username")) {
        result.put(key, json.nullableString(key)?.take(500))
    }
    return result
}

private fun safeSubscription(json: JSONObject): JSONObject {
    val has = json.getBoolean("has_subscription")
    val details = if (has) json.getJSONObject("subscription").let {
        JSONObject().put("status", it.getString("status").take(64)).put("is_active", it.getBoolean("is_active"))
            .put("is_trial", it.optBoolean("is_trial", false))
            .put("is_expired", it.optBoolean("is_expired", false))
            .put("expires_at", it.nullableString("expires_at"))
    } else null
    return JSONObject().put("has_subscription", has).put("subscription", details)
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.toProfile(): AccountProfileUi {
    val name = listOfNotNull(nullableString("first_name"), nullableString("last_name")).joinToString(" ")
    return AccountProfileUi(name.ifBlank { nullableString("username") ?: "Аккаунт SafarVPN" },
        nullableString("email"), nullableString("username")?.let { "@$it" }, getLong("id"))
}

private fun AccountSession.toSubscription(): WdttSubscriptionUi? = subscription?.let { subscriptionUi(it, checkedAt) }

internal fun subscriptionUi(cached: JSONObject, checkedAt: Long? = null): WdttSubscriptionUi {
    val has = cached.getBoolean("has_subscription")
    val sub = cached.optJSONObject("subscription")
    val expiry = sub?.nullableString("expires_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val active = sub?.optBoolean("is_active") == true && (expiry == null || expiry.isAfter(Instant.now()))
    val expired = sub?.optBoolean("is_expired") == true || (expiry != null && !expiry.isAfter(Instant.now()))
    val status = if (expired) "expired" else sub?.optString("status")
    val label = when (status) {
        "active" -> if (sub?.optBoolean("is_trial") == true) "Пробный период · 2 дня" else "Активна"
        "expired" -> "Срок подписки истёк"
        "pending_payment" -> "Ожидает оплаты"
        "pending_activation" -> "Ожидает активации"
        "revoked" -> "Отозвана"
        "fulfilled" -> "Завершена"
        "activation_failed" -> "Не удалось активировать"
        else -> "Статус подписки неизвестен"
    }
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    return WdttSubscriptionUi(has, active, label, expiry?.let(formatter::format),
        checkedAt?.let { "Проверено: ${formatter.format(Instant.ofEpochMilli(it))}" })
}

private fun String.displayProvider(): String = if (this == "google") "Google" else "Telegram"
