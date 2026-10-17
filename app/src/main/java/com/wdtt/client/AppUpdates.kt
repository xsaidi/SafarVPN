package com.wdtt.client

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class AppRelease(val version: String, val url: String, val size: Long, val digest: String?)
internal object ReleasePolicy {
    private val pattern = Regex("^(?:safarvpn-|v)?(\\d+)\\.(\\d+)\\.(\\d+)$", RegexOption.IGNORE_CASE)
    fun version(value: String): List<Int>? = pattern.matchEntire(value)?.groupValues?.drop(1)
        ?.map { it.toIntOrNull() ?: return null }
    fun newer(candidate: String, current: String): Boolean {
        val a = version(candidate) ?: return false
        val b = version(current) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
    fun allowedDownload(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.userInfo == null && uri.port in setOf(-1, 443) &&
            ((uri.host == "github.com" && uri.path.startsWith("/xsaidi/SafarVPN/releases/download/")) ||
                uri.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com"))
    }.getOrDefault(false)
    fun parse(json: JSONObject, current: String): AppRelease? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tag = json.getString("tag_name")
        if (!newer(tag, current)) return null
        val assets = json.getJSONArray("assets")
        require(assets.length() <= 100)
        val asset = (0 until assets.length()).map(assets::getJSONObject).singleOrNull {
            it.getString("name").equals("SafarVPN-${version(tag)!!.joinToString(".")}-universal-release.apk", true)
        } ?: return null
        val url = asset.getString("browser_download_url")
        require(URI(url).host == "github.com" && allowedDownload(url))
        val size = asset.getLong("size").also { require(it in 1..150_000_000L) }
        val digest = asset.optString("digest").takeIf { it.matches(Regex("sha256:[a-fA-F0-9]{64}")) }
            ?.substringAfter(':')?.lowercase()
        return AppRelease(version(tag)!!.joinToString("."), url, size, digest)
    }
}

internal data class UpdateUi(val release: AppRelease? = null, val downloading: Boolean = false,
    val file: File? = null, val error: String? = null)
internal object AppUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).build()
    val state = MutableStateFlow(UpdateUi())
    private var checking = false
    @Synchronized fun check(context: Context) {
        if (checking || state.value.downloading || state.value.release != null) return
        checking = true
        scope.launch {
            try {
                val request = Request.Builder().url("https://api.github.com/repos/xsaidi/SafarVPN/releases/latest")
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "SafarVPN/${BuildConfig.VERSION_NAME}").build()
                val call = client.newCall(request)
                call.timeout().timeout(10, TimeUnit.SECONDS)
                call.execute().use { response ->
                    if (!response.isSuccessful) return@launch
                    val source = response.body?.source() ?: return@launch
                    source.request(512_001)
                    require(source.buffer.size <= 512_000)
                    val release = ReleasePolicy.parse(JSONObject(source.readUtf8()), BuildConfig.VERSION_NAME)
                    if (release != null) state.value = UpdateUi(release)
                }
            } catch (_: Exception) { /* A failed update check must never prevent using the app. */ }
            finally { synchronized(this@AppUpdates) { checking = false } }
        }
    }
    fun dismiss() { if (!state.value.downloading) state.value = UpdateUi() }
    fun download(context: Context) {
        val release = state.value.release ?: return
        if (state.value.downloading) return
        state.value = UpdateUi(release, downloading = true)
        val app = context.applicationContext
        scope.launch {
            val file = File(app.cacheDir, "updates/SafarVPN-${release.version}.apk")
            try {
                file.parentFile!!.mkdirs()
                var url = release.url
                var downloaded = false
                for (hop in 0..5) {
                    require(ReleasePolicy.allowedDownload(url))
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.code in 300..399) {
                            url = URI(url).resolve(response.header("Location") ?: error("redirect")).toString()
                        } else {
                            check(response.isSuccessful)
                            val body = response.body ?: error("empty")
                            var total = 0L
                            body.byteStream().use { input -> file.outputStream().use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    require(total <= release.size)
                                    output.write(buffer, 0, n)
                                }
                            } }
                            check(total == release.size)
                            downloaded = true
                        }
                    }
                    if (downloaded) break
                }
                check(downloaded)
                release.digest?.let { expected ->
                    val sha = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input -> val bytes = ByteArray(65536); while (true) {
                        val n = input.read(bytes); if (n < 0) break; sha.update(bytes, 0, n)
                    } }
                    check(sha.digest().joinToString("") { "%02x".format(it) } == expected)
                }
                validateApk(app, file, release)
                state.value = UpdateUi(release, file = file)
            } catch (_: Exception) {
                file.delete()
                state.value = UpdateUi(release, error = "Не удалось скачать или проверить обновление. Попробуйте ещё раз.")
            }
        }
    }
    @Suppress("DEPRECATION") private fun validateApk(context: Context, file: File, release: AppRelease) {
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("apk")
        check(archive.packageName == context.packageName && archive.longVersionCode > installed.longVersionCode)
        check(archive.versionName == release.version)
        val current = installed.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet()
        check(current.isNotEmpty() && current == archive.signingInfo!!.apkContentsSigners.map { it.toCharsString() }.toSet())
    }
    fun install(context: Context) {
        val ui = state.value
        val file = ui.file ?: return
        try {
            validateApk(context, file, requireNotNull(ui.release))
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                state.value = ui.copy(error = "Разрешите установку из SafarVPN, затем нажмите «Установить».")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { state.value = ui.copy(error = "Не удалось открыть установщик обновления.") }
    }
}

@Composable internal fun AppUpdatePrompt() {
    val ui by AppUpdates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val release = ui.release ?: return
    AlertDialog(onDismissRequest = AppUpdates::dismiss, title = { Text("Доступно обновление") }, text = {
        androidx.compose.foundation.layout.Column {
            Text("SafarVPN ${release.version}\nУстановлена версия ${BuildConfig.VERSION_NAME}")
            if (ui.downloading) { LinearProgressIndicator(); Text("Скачиваем обновление…") }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !ui.downloading, onClick = {
        if (ui.file == null) AppUpdates.download(context) else AppUpdates.install(context)
    }) { Text(if (ui.file == null) "Обновить" else "Установить") } }, dismissButton = {
        TextButton(enabled = !ui.downloading, onClick = AppUpdates::dismiss) { Text("Позже") }
    })
}
