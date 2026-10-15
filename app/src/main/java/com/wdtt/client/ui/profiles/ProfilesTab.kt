package com.wdtt.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.wdtt.client.account.AccountSurface
import com.wdtt.client.account.SupportLink
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.PlatformTextStyle
import com.wdtt.client.PeerAddress
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.ProfilesStore
import com.wdtt.client.account.AccountRepository
import com.wdtt.client.account.AccountProfiles
import com.wdtt.client.DeviceIdentity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.wdtt.client.TunnelManager
import com.wdtt.client.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import com.wdtt.client.ProfileSubscription
import java.util.UUID
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ButtonDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ProfilesTab(
    onProfileApplied: suspend () -> Unit = {},
    importFileUri: android.net.Uri? = null,
    onImportHandled: () -> Unit = {},
    requestCreateProfile: Boolean = false,
    onCreateProfileHandled: () -> Unit = {},
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profilesStore = remember { ProfilesStore(context) }
    val settingsStore = remember { SettingsStore(context) }
    val account = remember { AccountRepository.get(context) }
    val accountState by account.state.collectAsStateWithLifecycle()

    val profiles by profilesStore.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val tunnelConnecting by TunnelManager.isConnecting.collectAsStateWithLifecycle()
    val tunnelReconnecting by TunnelManager.isReconnecting.collectAsStateWithLifecycle()
    val connectionReady by TunnelManager.connectionReady.collectAsStateWithLifecycle()
    var applyingProfile by remember { mutableStateOf(false) }
    val canSelectServer = !applyingProfile && !tunnelConnecting && !tunnelReconnecting && (!tunnelRunning || connectionReady)
    val subscriptions by profilesStore.subscriptions.collectAsStateWithLifecycle(initialValue = emptyList())

    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val globalHashes by settingsStore.globalVkHashes.collectAsStateWithLifecycle(initialValue = "")

    LaunchedEffect(requestCreateProfile) {
        if (requestCreateProfile) {
            showCreateSheet = true
            onCreateProfileHandled()
        }
    }

    val pingResults = com.wdtt.client.PingHelper.pingResults
    val pingingState = com.wdtt.client.PingHelper.pingingState
    val pingJobs = remember { mutableMapOf<String, Job>() }

    fun pingProfile(profile: ConnectionProfile) {
        if (pingingState[profile.id] == true) return
        pingingState[profile.id] = true
        pingJobs[profile.id] = scope.launch {
            try {
                // Resolve effective hashes: use global if profile says so
                val effectiveHashes = if (profile.useGlobalHashes) globalHashes.ifEmpty { profile.vkHashes } else profile.vkHashes
                val profileWithHashes = profile.copy(vkHashes = effectiveHashes)
                val result = com.wdtt.client.PingHelper.measurePing(context, profileWithHashes)
                pingResults[profile.id] = result
            } finally {
                pingingState[profile.id] = false
                pingJobs.remove(profile.id)
            }
        }
    }

    fun pingAllProfiles(list: List<ConnectionProfile>) {
        if (pingingState.values.any { it }) return
        list.forEach { pingProfile(it) }
    }

    val importZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    var importedCount = 0
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            ZipInputStream(inputStream).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    if (entry.name.endsWith(".json")) {
                                        val content = zis.bufferedReader(Charsets.UTF_8).readText()
                                        val jsonArray = JSONArray(content)
                                        val localGroupsCache = mutableMapOf<String, String>()

                                        for (i in 0 until jsonArray.length()) {
                                            val obj = jsonArray.getJSONObject(i)
                                            val name = obj.optString("name", "Imported")
                                            val peer = obj.optString("peer", "")
                                            val vkHashes = obj.optString("vkHashes", "")
                                            val workers = obj.optInt("workersPerHash", 9)
                                            val port = obj.optInt("listenPort", 9000)
                                            val pass = obj.optString("password", "")
                                            val groupName = obj.optString("groupName", "")
                                            
                                            val targetGroupId = if (groupName.isNotBlank()) {
                                                localGroupsCache.getOrPut(groupName.trim().lowercase()) {
                                                    profilesStore.resolveGroupIdForImport(groupName)
                                                }
                                            } else {
                                                ""
                                            }
                                            
                                            profilesStore.createProfile(name, peer, vkHashes, workers, port, pass, targetGroupId)
                                            importedCount++
                                        }
                                        break
                                    }
                                    entry = zis.nextEntry
                                }
                            }
                        }
                    }
                    Toast.makeText(context, "Импортировано профилей: $importedCount", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val sortByPing by settingsStore.sortProfilesByPing.collectAsStateWithLifecycle(initialValue = false)
    val displayProfiles = remember(profiles, pingResults.toMap(), sortByPing) {
        if (sortByPing) {
            profiles.sortedWith(compareBy<ConnectionProfile> {
                val ping = pingResults[it.id]
                if (ping != null && ping >= 0) ping else Long.MAX_VALUE
            }.thenBy { it.name })
        } else {
            profiles
        }
    }
    val currentPeer by settingsStore.peer.collectAsStateWithLifecycle(initialValue = "")
    val currentHashes by settingsStore.vkHashes.collectAsStateWithLifecycle(initialValue = "")
    val currentWorkers by settingsStore.workersPerHash.collectAsStateWithLifecycle(initialValue = 9)
    val currentPort by settingsStore.listenPort.collectAsStateWithLifecycle(initialValue = 9000)
    val currentPassword by settingsStore.connectionPassword.collectAsStateWithLifecycle(initialValue = "")
    val currentProfileId by settingsStore.currentProfileId.collectAsStateWithLifecycle(initialValue = "")

    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var peerInput by rememberSaveable { mutableStateOf("") }
    var hashesInput by rememberSaveable { mutableStateOf("") }
    var workersInput by rememberSaveable { mutableStateOf("9") }
    var portInput by rememberSaveable { mutableStateOf("9000") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var useGlobalHashesInput by rememberSaveable { mutableStateOf(true) }
    var showAddSubscriptionDialog by rememberSaveable { mutableStateOf(false) }
    var savingSubscription by remember { mutableStateOf(false) }
    var refreshingSubId by remember { mutableStateOf<String?>(null) }
    var deleteSubTarget by remember { mutableStateOf<ProfileSubscription?>(null) }
    var scannedProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var scannedMultipleProfiles by remember { mutableStateOf<ParsedSubscription?>(null) }
    var showFormatsInfoDialog by rememberSaveable { mutableStateOf(false) }
    var deviceStatuses by remember { mutableStateOf<Map<String, ProfileDeviceStatus>>(emptyMap()) }
    var unbindTarget by remember { mutableStateOf<ConnectionProfile?>(null) }
    var unbindOnlyCurrent by remember { mutableStateOf(true) }
    val savedServerDtlsPort by settingsStore.serverDtlsPort.collectAsStateWithLifecycle(initialValue = 56000)
    val savedManualPortsEnabled by settingsStore.manualPortsEnabled.collectAsStateWithLifecycle(initialValue = false)

    val visibleSubscriptions = subscriptions
    val visibleProfiles = displayProfiles

    val density = LocalDensity.current
    val spacingPx = with(density) { 12.dp.toPx() }

    var draggingList by remember { mutableStateOf(emptyList<ConnectionProfile>()) }
    var isDragging by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var itemHeights by remember { mutableStateOf(mapOf<String, Int>()) }

    LaunchedEffect(visibleProfiles, isDragging) {
        if (!isDragging) {
            draggingList = visibleProfiles
        }
    }

    // Лаунчер системного выборщика файлов
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val parsed = parseMultipleConfigs(text)
                if (parsed != null) {
                    if (parsed.profiles.size == 1) {
                        scannedProfile = parsed.profiles.first()
                    } else {
                        scannedMultipleProfiles = parsed
                    }
                } else {
                    Toast.makeText(context, "Неверный формат файла", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Автообработка URI если приложение открыли через файл
    LaunchedEffect(importFileUri) {
        val uri = importFileUri ?: return@LaunchedEffect
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            val parsed = parseMultipleConfigs(text)
            if (parsed != null) {
                if (parsed.profiles.size == 1) {
                    scannedProfile = parsed.profiles.first()
                } else {
                    scannedMultipleProfiles = parsed
                }
            } else {
                Toast.makeText(context, "Неверный формат файла", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        onImportHandled()
    }

    val profileLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(profiles, profileLifecycle, savedServerDtlsPort, savedManualPortsEnabled) {
        val androidId = DeviceIdentity.get(context)
        val dtlsPort = if (savedManualPortsEnabled) savedServerDtlsPort else 56000
        profileLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) {
            profiles.filterNot { it.id.startsWith(AccountProfiles.PREFIX) }.forEach { profile ->
                if (profile.password.isNotBlank() && profile.peer.isNotBlank()) {
                    launch {
                        val status = fetchProfileStatus(profile.peer, dtlsPort, profile.password, androidId)
                        if (status != null) {
                            deviceStatuses = deviceStatuses + (profile.id to status)
                        } else {
                            deviceStatuses = deviceStatuses + (profile.id to ProfileDeviceStatus(isError = true))
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(8000)
        }
        }
    }

    fun openEditor(profile: ConnectionProfile? = null) {
        editingProfileId = profile?.id
        nameInput = profile?.name ?: ""
        peerInput = profile?.peer ?: currentPeer
        hashesInput = profile?.vkHashes ?: currentHashes
        workersInput = (profile?.workersPerHash ?: currentWorkers).toString()
        portInput = (profile?.listenPort ?: currentPort).toString()
        portInput = (profile?.listenPort ?: currentPort).toString()
        passwordInput = profile?.password ?: currentPassword
        useGlobalHashesInput = profile?.useGlobalHashes ?: true
        editorVisible = true
    }

    fun saveEditor() {
        if (!editorVisible) return
        editorVisible = false
        
        val name = nameInput.trim()
        val peer = peerInput.trim()
        val hashes = hashesInput.trim()
        val workers = workersInput.toIntOrNull()?.coerceIn(1, 128) ?: 9
        val port = portInput.toIntOrNull()?.coerceIn(1, 65535) ?: 9000
        val password = passwordInput
        
        if (name.isBlank() || peer.isBlank() || (!useGlobalHashesInput && hashes.isBlank()) || password.isBlank()) {
            editorVisible = true // Revert if validation fails
            return
        }

        val currentEditId = editingProfileId
        val useGlobal = useGlobalHashesInput
        scope.launch {
            if (currentEditId == null) {
                val p = profilesStore.createProfile(name, peer, hashes, workers, port, password, "")
                profilesStore.saveProfile(p.copy(useGlobalHashes = useGlobal))
            } else {
                val existingProfile = profiles.firstOrNull { it.id == currentEditId }
                val existingTraffic = existingProfile?.trafficMb ?: 0.0
                val existingGroupId = existingProfile?.groupId ?: ""
                profilesStore.saveProfile(
                    ConnectionProfile(
                        id = currentEditId,
                        name = name,
                        peer = peer,
                        vkHashes = hashes,
                        workersPerHash = workers,
                        listenPort = port,
                        password = password,
                        trafficMb = existingTraffic,
                        groupId = existingGroupId,
                        useGlobalHashes = useGlobal
                    )
                )
            }
        }
    }

    if (editorVisible) {
        AlertDialog(
            onDismissRequest = { editorVisible = false },
            properties = androidx.compose.ui.window.DialogProperties(decorFitsSystemWindows = false),
            modifier = Modifier.imePadding(),
            title = { Text(if (editingProfileId == null) "Новый профиль" else "Редактировать профиль") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = peerInput, onValueChange = { peerInput = it }, label = { Text("Peer") }, singleLine = true)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useGlobalHashesInput = !useGlobalHashesInput }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Использовать общие хеши",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Switch(
                            checked = useGlobalHashesInput,
                            onCheckedChange = { useGlobalHashesInput = it }
                        )
                    }

                    if (!useGlobalHashesInput) {
                        OutlinedTextField(value = hashesInput, onValueChange = { hashesInput = it }, label = { Text("VK-хеши") }, minLines = 2)
                    } else {
                        OutlinedTextField(
                            value = globalHashes.ifEmpty { "Не заданы (настройте на главной)" },
                            onValueChange = {},
                            label = { Text("VK Хеши (из главной вкладки)") },
                            minLines = 2,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    OutlinedTextField(
                        value = workersInput,
                        onValueChange = { workersInput = it.filter(Char::isDigit) },
                        label = { Text("Потоки") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = passwordInput, onValueChange = { passwordInput = it }, label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { saveEditor() }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editorVisible = false }) { Text("Отмена") }
            }
        )
    }

    if (showAddSubscriptionDialog) {
        AddSubscriptionDialog(
            saving = savingSubscription,
            onDismiss = { if (!savingSubscription) showAddSubscriptionDialog = false },
            onConfirm = { url ->
                savingSubscription = true
                scope.launch {
                    val result = profilesStore.addSubscription(url)
                    savingSubscription = false
                    result.fold(
                        onSuccess = {
                            Toast.makeText(context, "Подписка «${it.name}» добавлена", Toast.LENGTH_SHORT).show()
                            showAddSubscriptionDialog = false
                        },
                        onFailure = { e ->
                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    deleteSubTarget?.let { sub ->
        DeleteSubscriptionDialog(
            sub = sub,
            onDismiss = { deleteSubTarget = null },
            onConfirm = {
                scope.launch {
                    profilesStore.deleteSubscription(sub.id)
                    deleteSubTarget = null
                    Toast.makeText(context, "Подписка удалена", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (unbindTarget != null) {
        val target = unbindTarget!!
        AlertDialog(
            onDismissRequest = { unbindTarget = null },
            title = {
                Text(
                    text = "Отвязать устройство",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    if (unbindOnlyCurrent) "Вы действительно хотите отвязать текущее устройство от этого профиля?"
                    else "Вы действительно хотите сбросить ВСЕ привязанные устройства от этого профиля?\n\nЭто освободит все лимиты подключения."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val androidId = DeviceIdentity.get(context)
                        val deviceIdToSend = if (unbindOnlyCurrent) androidId else ""
                        val dtlsPort = if (savedManualPortsEnabled) savedServerDtlsPort else 56000
                        deviceStatuses = deviceStatuses + (target.id to (deviceStatuses[target.id] ?: ProfileDeviceStatus()).copy(isLoading = true))
                        scope.launch {
                            val result = sendUnbindRequest(target.peer, dtlsPort, target.password, deviceIdToSend)
                            if (result == LegacyUnbind.APPLIED) {
                                Toast.makeText(context, if (unbindOnlyCurrent) "Устройство успешно отвязано!" else "Все привязки успешно сброшены!", Toast.LENGTH_SHORT).show()
                                val status = fetchProfileStatus(target.peer, dtlsPort, target.password, androidId)
                                if (status != null) {
                                    deviceStatuses = deviceStatuses + (target.id to status)
                                } else {
                                    deviceStatuses = deviceStatuses + (target.id to ProfileDeviceStatus(isError = true))
                                }
                            } else {
                                Toast.makeText(context, if (result == LegacyUnbind.PENDING)
                                    "Запрос принят. Ожидается подтверждение всех серверов. Проверить завершение можно через аккаунт."
                                    else "Не удалось отвязать устройства", Toast.LENGTH_LONG).show()
                                deviceStatuses = deviceStatuses + (target.id to (deviceStatuses[target.id] ?: ProfileDeviceStatus()).copy(isLoading = false))
                            }
                        }
                        unbindTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (unbindOnlyCurrent) "Отвязать" else "Сбросить", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { unbindTarget = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (scannedProfile != null) {
        val profile = scannedProfile!!
        AlertDialog(
            onDismissRequest = { scannedProfile = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Импорт профиля", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Найден новый профиль для импорта!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📝 Название: ${profile.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("🌐 Сервер: ${profile.peer}", style = MaterialTheme.typography.bodyMedium)
                            Text("⚡ Потоков: ${profile.workersPerHash}", style = MaterialTheme.typography.bodyMedium)
                            val hashCount = if (profile.vkHashes.isBlank()) 0 else profile.vkHashes.trim().split(",").count { it.isNotBlank() }
                            Text("🔑 Хешей: $hashCount", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (profile.vkHashes.isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Хеш звонка ВКонтакте не указан. После сохранения добавьте его вручную в настройках профиля.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = "Вы хотите сохранить его в список профилей?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            profilesStore.saveProfile(profile)
                            Toast.makeText(context, "Профиль «${profile.name}» сохранен!", Toast.LENGTH_SHORT).show()
                            scannedProfile = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedProfile = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (scannedMultipleProfiles != null) {
        val parsed = scannedMultipleProfiles!!
        val list = parsed.profiles
        AlertDialog(
            onDismissRequest = { scannedMultipleProfiles = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Импорт серверов",
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Найдено профилей: ${list.size}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (parsed.description.isNotBlank()) {
                        Text(
                            text = parsed.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (parsed.trafficUsedMb > 0 || parsed.trafficLimitMb > 0) {
                        val trafficLine = buildString {
                            if (parsed.trafficUsedMb > 0) append("Трафик: ${parsed.trafficUsedMb} МБ")
                            if (parsed.trafficLimitMb > 0) {
                                if (isNotEmpty()) append(" / ")
                                append("лимит ${parsed.trafficLimitMb} МБ")
                            }
                        }
                        Text(
                            text = trafficLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                // Import as new manual profiles: never replace an existing namespace.
                                list.forEach { profile ->
                                    profilesStore.saveProfile(profile.copy(id = UUID.randomUUID().toString(), groupId = ""))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Ошибка импорта", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            Toast.makeText(context, "Импортировано профилей: ${list.size}", Toast.LENGTH_SHORT).show()
                            scannedMultipleProfiles = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedMultipleProfiles = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showFormatsInfoDialog) {
        AlertDialog(
            onDismissRequest = { showFormatsInfoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Поддерживаемые форматы",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "SafarVPN автоматически считывает и импортирует настройки из ссылок, файлов и QR-кодов.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. URI ссылки
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("1. Ссылки / QR-коды", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "qwdtt://config?peer=IP:порт&pass=пароль&workers=потоки&port=локальный_порт&name=имя&hashes=vk_хеши",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }

                    // 2. Файлы
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("2. Файлы конфигурации", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Могут иметь любое расширение. Внутри должна быть конфигурация в виде URI-ссылки или JSON-структуры.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 3. JSON структура
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("3. JSON структура", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Поддерживаются как стандартные ключи, так и альтернативные (pass, vkHashes, workersPerHash, listenPort):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "{\n" +
                                "  \"name\": \"Имя профиля\",\n" +
                                "  \"peer\": \"IP_сервера\",\n" +
                                "  \"password\": \"пароль\",\n" +
                                "  \"hashes\": \"vk_хеши\",\n" +
                                "  \"workers\": 9,\n" +
                                "  \"port\": 9000\n" +
                                "}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }

                    // 4. Группы / подписки
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("4. Подписки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "JSON на HTTPS: subscriptionName, description, trafficUsedMb, trafficLimitMb, profiles[]. Добавление: + → Подписка.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "{\n" +
                                "  \"subscriptionName\": \"Моя группа\",\n" +
                                "  \"description\": \"Описание для пользователя\",\n" +
                                "  \"trafficUsedMb\": 512.5,\n" +
                                "  \"trafficLimitMb\": 10240,\n" +
                                "  \"updatedAt\": \"2026-06-24\",\n" +
                                "  \"profiles\": [\n" +
                                "    { \"name\": \"Сервер 1\", \"peer\": \"IP:56000\", \"password\": \"...\" }\n" +
                                "  ]\n" +
                                "}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormatsInfoDialog = false }) {
                    Text("Понятно", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(28.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        header()
        if (accountState.profile != null && (accountState.refreshing || accountState.error != null)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accountState.refreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(if (accountState.refreshing) "Обновляем подписку аккаунта…" else accountState.error.orEmpty(),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (!accountState.refreshing) TextButton(onClick = account::refreshAccount) { Text("Повторить") }
            }
        }

        AccountSurface {
        visibleSubscriptions.forEach { sub ->
            SubscriptionInfoCard(
                sub = sub,
                isRefreshing = if (sub.isAccount) accountState.refreshing else refreshingSubId == sub.id,
                onRefresh = {
                    if (sub.isAccount) {
                        account.refreshAccount()
                    } else {
                        refreshingSubId = sub.id
                        scope.launch {
                            val result = profilesStore.refreshSubscription(sub.id)
                            refreshingSubId = null
                            result.fold(
                                onSuccess = { count ->
                                    Toast.makeText(context, "Обновлено профилей: $count", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { e ->
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                },
                onDelete = if (sub.isAccount) null else ({ deleteSubTarget = sub }),
                onPingAll = { pingAllProfiles(profiles) },
                isPinging = pingingState.values.any { it },
                statusLabel = if (sub.isAccount) accountState.subscription?.statusLabel else null,
                active = sub.isAccount && accountState.subscription?.isActive == true,
                expiresAt = if (sub.isAccount) accountState.subscription?.expiresAtLabel else null,
            )
        }

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Пока нет сохранённых профилей",

                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Нажмите + внизу экрана, чтобы добавить профиль",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        "Импортируйте ссылку, файл или QR-код через кнопку +. Помощь — в настройках, раздел «Поддержка».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (visibleProfiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Серверов пока нет. Обновите подписку.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            draggingList.forEachIndexed { index, profile ->
                androidx.compose.runtime.key(profile.id) {
                    val isActive = profile.id == currentProfileId
                    val effectiveHashes = if (profile.useGlobalHashes) globalHashes.ifEmpty { profile.vkHashes } else profile.vkHashes
                    val hasIssue = effectiveHashes.isBlank() || profile.password.isBlank()

                    val isCurrentDragged = draggedIndex != null && draggingList.getOrNull(draggedIndex!!)?.id == profile.id
                    val translationY = if (isCurrentDragged) dragOffset else 0f
                    val scale = if (isCurrentDragged) 1.04f else 1.0f
                    val elevation = if (isCurrentDragged) 8.dp else 0.dp

                    Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            itemHeights = itemHeights + (profile.id to coords.size.height)
                        }
                        .graphicsLayer {
                            this.translationY = translationY
                            this.scaleX = scale
                            this.scaleY = scale
                            this.shadowElevation = elevation.toPx()
                            this.shape = RoundedCornerShape(28.dp)
                            this.clip = isCurrentDragged
                        }
                        .pointerInput(profile.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    if (sortByPing) {
                                        Toast.makeText(context, "Отключите сортировку по пингу для ручного перемещения", Toast.LENGTH_SHORT).show()
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    isDragging = true
                                    draggedIndex = draggingList.indexOfFirst { it.id == profile.id }
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                    dragOffset += dragAmount.y

                                    // Swap down
                                    if (dragOffset > 0f && currentIdx < draggingList.size - 1) {
                                        val nextProfile = draggingList[currentIdx + 1]
                                        val nextHeight = itemHeights[nextProfile.id] ?: 0
                                        val swapThreshold = (nextHeight + spacingPx) / 2f
                                        if (dragOffset > swapThreshold) {
                                            val newList = draggingList.toMutableList()
                                            newList[currentIdx] = nextProfile
                                            newList[currentIdx + 1] = profile
                                            draggingList = newList
                                            draggedIndex = currentIdx + 1
                                            dragOffset -= (nextHeight + spacingPx)
                                        }
                                    }
                                    // Swap up
                                    else if (dragOffset < 0f && currentIdx > 0) {
                                        val prevProfile = draggingList[currentIdx - 1]
                                        val prevHeight = itemHeights[prevProfile.id] ?: 0
                                        val swapThreshold = (prevHeight + spacingPx) / 2f
                                        if (dragOffset < -swapThreshold) {
                                            val newList = draggingList.toMutableList()
                                            newList[currentIdx] = prevProfile
                                            newList[currentIdx - 1] = profile
                                            draggingList = newList
                                            draggedIndex = currentIdx - 1
                                            dragOffset += (prevHeight + spacingPx)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    draggedIndex = null
                                    dragOffset = 0f
                                    scope.launch {
                                        val idsList = draggingList.map { it.id }
                                        profilesStore.reorderProfiles(idsList)
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    draggedIndex = null
                                    dragOffset = 0f
                                }
                            )
                        }
                ) {
                    AppSectionCard(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else if (hasIssue) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        else null,
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier.clickable(enabled = canSelectServer) {
                            if (profile.id == currentProfileId || applyingProfile) return@clickable
                            applyingProfile = true
                            scope.launch {
                                try {
                                try { profilesStore.applyProfile(context = context, id = profile.id) }
                                catch (_: Exception) {
                                    Toast.makeText(context, "Не удалось выбрать профиль. Обновите подписку аккаунта.", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                Toast.makeText(context, "Применено", Toast.LENGTH_SHORT).show()
                                onProfileApplied()
                                } finally { applyingProfile = false }
                            }
                        }
                    ) {
                        Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val flag = getCountryFlag(profile.name)
                            if (flag.isNotEmpty()) Text(flag, style = MaterialTheme.typography.titleLarge)
                            else Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground)
                                val status = deviceStatuses[profile.id]
                                if (!profile.id.startsWith(AccountProfiles.PREFIX)) {
                                Text(when {
                                    status == null || status.isLoading -> "Устройства: …"
                                    status.isError -> "Устройства: —"
                                    else -> "Устройства: ${status.boundDevices} из ${status.maxDevices}"
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val pingMs = pingResults[profile.id]
                            if (pingingState[profile.id] == true) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else if (pingMs != null) {
                                Text(
                                    text = if (pingMs < 0) "Недоступен" else "$pingMs мс",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (pingMs < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            RadioButton(selected = isActive, onClick = null, enabled = canSelectServer)
                        }

                    }
                }
                }
            }
        }
        SupportLink(Modifier.fillMaxWidth())
        }
        footer()
        }
    }
        
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp))

    run {
        FloatingActionButton(
            onClick = { showCreateSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Добавить")
        }
    }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "Добавить",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showCreateSheet = false
                        showAddSubscriptionDialog = true
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Filled.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Подписка", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Профили по адресу JSON на сервере", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showCreateSheet = false
                        openEditor()
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Вручную", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Создать новый профиль с нуля", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showCreateSheet = false
                        filePickerLauncher.launch("*/*")
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Из файла", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Выбрать файл конфигурации на устройстве", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showCreateSheet = false
                        try {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                val parsed = parseMultipleConfigs(text)
                                if (parsed != null) {
                                    if (parsed.profiles.size == 1) {
                                        scannedProfile = parsed.profiles.first()
                                    } else {
                                        scannedMultipleProfiles = parsed
                                    }
                                    Toast.makeText(context, "Конфигурация успешно прочитана!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "В буфере обмена нет подходящей конфигурации", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Не удалось прочитать буфер: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Из буфера", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Вставить скопированную конфигурацию", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showCreateSheet = false
                        try {
                            val activity = run {
                                val currentAct = com.wdtt.client.MainActivity.currentActivity
                                if (currentAct != null) return@run currentAct
                                var c = context
                                while (c is android.content.ContextWrapper) {
                                    if (c is android.app.Activity) return@run c
                                    c = c.baseContext
                                }
                                context
                            }
                            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(activity)
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val rawText = barcode.rawValue ?: ""
                                    val parsed = parseMultipleConfigs(rawText)
                                    if (parsed != null) {
                                        if (parsed.profiles.size == 1) {
                                            scannedProfile = parsed.profiles.first()
                                        } else {
                                            scannedMultipleProfiles = parsed
                                        }
                                    } else {
                                        Toast.makeText(context, "Неверный формат QR-кода", Toast.LENGTH_LONG).show()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Ошибка сканирования: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } catch (e: Exception) {
                            val msg = e.message ?: "неизвестная ошибка"
                            if (msg.contains("null", ignoreCase = true) || msg.contains("NullPointerException", ignoreCase = true)) {
                                Toast.makeText(context, "Не удалось запустить сканер: отсутствует или отключен Google Play Services", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Не удалось запустить сканер: $msg", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Сканировать QR-код", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Считать профиль с другого устройства", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }


}


private fun parseQrConfig(rawText: String): ConnectionProfile? {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return null

    // 0. Try legacy original WDTT scheme
    if (trimmed.startsWith("wdtt://")) {
        try {
            // wdtt://<server_ip>:<dtls_port>:<wg_port>:<local_port>:<password>:<vk_hash>
            val parts = trimmed.removePrefix("wdtt://").split(":")
            if (parts.size >= 6) {
                val ip = parts[0]
                val dtlsPort = parts[1]
                val localPort = parts[3].toIntOrNull() ?: 9000
                val pass = parts[4]
                val hash = parts.drop(5).joinToString(":")
                return ConnectionProfile(
                    id = UUID.randomUUID().toString(),
                    name = "WDTT $ip",
                    peer = "$ip:$dtlsPort",
                    vkHashes = hash,
                    workersPerHash = 9,
                    listenPort = localPort,
                    password = pass
                )
            }
        } catch (e: Exception) {
            // continue parsing if failed
        }
    }

    // 1. Try URL scheme
    if (trimmed.startsWith("qwdtt://config") || trimmed.startsWith("qwdtt:config")) {
        try {
            val uri = android.net.Uri.parse(trimmed.replace("qwdtt:config", "qwdtt://config"))
            val name = uri.getQueryParameter("name") ?: "QR Профиль"
            val peerRaw = uri.getQueryParameter("peer") ?: return null
            val dtlsPortParam = uri.getQueryParameter("dtls_port") ?: uri.getQueryParameter("server_port")
            val peer = if (dtlsPortParam != null) {
                PeerAddress.ensurePort(peerRaw, dtlsPortParam.toIntOrNull()?.coerceIn(1, 65535) ?: 56000)
            } else {
                peerRaw
            }
            val hashes = uri.getQueryParameter("hashes") ?: ""
            val workers = uri.getQueryParameter("workers")?.toIntOrNull() ?: 18
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 9000
            val pass = uri.getQueryParameter("pass") ?: ""
            return ConnectionProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                peer = peer,
                vkHashes = hashes,
                workersPerHash = workers,
                listenPort = port,
                password = pass
            )
        } catch (e: Exception) {
            // fallback
        }
    }

    // 2. Try JSON (raw or base64)
    var jsonStr = trimmed
    if (!trimmed.startsWith("{")) {
        try {
            val decodedBytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            jsonStr = String(decodedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            // not base64
        }
    }

    if (jsonStr.startsWith("{")) {
        try {
            val jsonObj = JSONObject(jsonStr)
            val name = jsonObj.optString("name", "QR Профиль")
            val peer = jsonObj.getString("peer")
            val hashes = jsonObj.optString("hashes", jsonObj.optString("vkHashes", ""))
            val workers = jsonObj.optInt("workers", jsonObj.optInt("workersPerHash", 18))
            val port = jsonObj.optInt("port", jsonObj.optInt("listenPort", 9000))
            val pass = jsonObj.optString("password", jsonObj.optString("pass", ""))
            return ConnectionProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                peer = peer,
                vkHashes = hashes,
                workersPerHash = workers,
                listenPort = port,
                password = pass
            )
        } catch (e: Exception) {
            // invalid json
        }
    }

    return null
}

private data class ParsedSubscription(
    val profiles: List<ConnectionProfile>,
    val suggestedName: String? = null,
    val description: String = "",
    val trafficUsedMb: Double = 0.0,
    val trafficLimitMb: Double = 0.0,
    val updatedAt: String = ""
)

private fun parseMultipleConfigs(rawText: String): ParsedSubscription? {
    val parsed = com.wdtt.client.SubscriptionImport.parsePayload(rawText) ?: run {
        val single = parseQrConfig(rawText) ?: return null
        return ParsedSubscription(profiles = listOf(single), suggestedName = single.name)
    }
    return ParsedSubscription(
        profiles = parsed.profiles,
        suggestedName = parsed.subscriptionName,
        description = parsed.description,
        trafficUsedMb = parsed.trafficUsedMb,
        trafficLimitMb = parsed.trafficLimitMb,
        updatedAt = parsed.updatedAt
    )
}

// ==================== Profile Device Management API Client ====================

data class ProfileDeviceStatus(
    val maxDevices: Int = 1,
    val boundDevices: Int = 0,
    val activeDevices: Int = 0,
    val isCurrentBound: Boolean = false,
    val expiresAt: Long = 0L,
    val isError: Boolean = false,
    val isLoading: Boolean = false
)

private suspend fun fetchProfileStatus(
    peer: String,
    dtlsPort: Int,
    password: String,
    deviceId: String
): ProfileDeviceStatus? = withContext(Dispatchers.IO) {
	var conn: HttpURLConnection? = null
	try {
		val endpoint = "http://${PeerAddress.httpEndpoint(peer, dtlsPort)}"
		val nonce = requestProfileChallenge(endpoint) ?: return@withContext null
		val url = URL("$endpoint/api/profile/status")
		conn = url.openConnection() as HttpURLConnection
		conn.requestMethod = "POST"
		conn.connectTimeout = 4000
		conn.readTimeout = 4000
		conn.useCaches = false
		conn.doOutput = true
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
		conn.setRequestProperty("Accept", "application/json")
		val postData = profileProofBody("status", password, deviceId, nonce)
		conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            ProfileDeviceStatus(
                maxDevices = json.optInt("max_devices", 1),
                boundDevices = json.optInt("bound_devices", 0),
                activeDevices = json.optInt("active_devices", 0),
                isCurrentBound = json.optBoolean("is_current_bound", false),
                expiresAt = json.optLong("expires_at", 0L)
            )
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        conn?.disconnect()
    }
}

private enum class LegacyUnbind { APPLIED, PENDING, FAILED }

private suspend fun sendUnbindRequest(
    peer: String,
    dtlsPort: Int,
    password: String,
    deviceId: String
): LegacyUnbind = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
	try {
		val endpoint = "http://${PeerAddress.httpEndpoint(peer, dtlsPort)}"
		val nonce = requestProfileChallenge(endpoint) ?: return@withContext LegacyUnbind.FAILED
		val url = URL("$endpoint/api/profile/unbind")
        conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

		val postData = profileProofBody("unbind", password, deviceId, nonce)
        conn.outputStream.use { os ->
            os.write(postData.toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        val json = if (responseCode in 200..299) JSONObject(conn.inputStream.bufferedReader().use { it.readText() }) else null
        when {
            responseCode == 202 || json?.optString("status") == "pending" -> LegacyUnbind.PENDING
            responseCode == 200 && json?.optBoolean("success") == true -> LegacyUnbind.APPLIED
            else -> LegacyUnbind.FAILED
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        LegacyUnbind.FAILED
    } finally {
        conn?.disconnect()
	}
}

private fun requestProfileChallenge(endpoint: String): String? {
	var conn: HttpURLConnection? = null
	return try {
		conn = URL("$endpoint/api/profile/challenge").openConnection() as HttpURLConnection
		conn.requestMethod = "POST"
		conn.connectTimeout = 4000
		conn.readTimeout = 4000
		conn.doOutput = true
		conn.outputStream.use { it.write(ByteArray(0)) }
		if (conn.responseCode != 200) null
		else JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString("nonce").takeIf { it.isNotBlank() }
	} catch (_: Exception) {
		null
	} finally {
		conn?.disconnect()
	}
}

private fun profileProofBody(action: String, password: String, deviceId: String, nonce: String): String {
	val idHash = MessageDigest.getInstance("SHA-256")
		.digest(("WDTT-PROFILE-ID-v1\u0000" + password).toByteArray(Charsets.UTF_8))
	val keyId = idHash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
	val mac = Mac.getInstance("HmacSHA256")
	mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
	val proof = mac.doFinal("$action\n$deviceId\n$nonce".toByteArray(Charsets.UTF_8))
		.joinToString("") { "%02x".format(it.toInt() and 0xff) }
	return "device_id=${URLEncoder.encode(deviceId, "UTF-8")}" +
		"&nonce=${URLEncoder.encode(nonce, "UTF-8")}" +
		"&key_id=$keyId&proof=$proof"
}

fun getCountryFlag(profileName: String): String {
    val name = profileName.lowercase()
    return when {
        name.contains("герман") || name.contains("germany") -> "🇩🇪"
        name.contains("финлянд") || name.contains("finland") -> "🇫🇮"
        name.contains("нидерланд") || name.contains("netherlands") -> "🇳🇱"
        name.contains("сша") || name.contains("usa") || name.contains("америк") -> "🇺🇸"
        name.contains("росси") || name.contains("russia") || name.contains("рф") -> "🇷🇺"
        name.contains("франци") || name.contains("france") -> "🇫🇷"
        name.contains("швеци") || name.contains("sweden") -> "🇸🇪"
        name.contains("англи") || name.contains("великобритани") || name.contains("uk") -> "🇬🇧"
        name.contains("польш") || name.contains("poland") -> "🇵🇱"
        name.contains("турци") || name.contains("turkey") -> "🇹🇷"
        name.contains("канад") || name.contains("canada") -> "🇨🇦"
        name.contains("япони") || name.contains("japan") -> "🇯🇵"
        name.contains("украин") || name.contains("ukraine") -> "🇺🇦"
        name.contains("казахстан") || name.contains("kazakhstan") -> "🇰🇿"
        name.contains("испани") || name.contains("spain") -> "🇪🇸"
        name.contains("итали") || name.contains("italy") -> "🇮🇹"
        else -> ""
    }
}

private fun obfuscatePeer(peer: String): String {
    val ipv4Regex = Regex("^(\\d{1,3}\\.\\d{1,3}\\.)\\d{1,3}\\.\\d{1,3}(:\\d+)?$")
    if (ipv4Regex.matches(peer)) {
        return ipv4Regex.replace(peer, "$1***.***$2")
    }
    return peer
}
