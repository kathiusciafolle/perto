package com.bitchat.android.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bitchat.android.BuildConfig
import com.bitchat.android.util.ApkDownloader
import com.bitchat.android.util.AppVersion
import com.bitchat.android.util.GitHubReleaseClient
import com.bitchat.android.util.UniversalApkManager
import com.bitchat.android.util.WorkManagerApkDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Perto: atualização pelo próprio app.
// Procura a versão mais nova nas releases do Perto no GitHub, baixa o APK universal
// (o mesmo download do bitchat, que confere se é assinado com a chave do Perto e se é
// o pacote do Perto) e abre o instalador do Android. Nada é instalado sem a pessoa
// tocar em "Instalar".
// ─────────────────────────────────────────────────────────────────────────────

sealed class PertoUpdateState {
    object Idle : PertoUpdateState()
    object Checking : PertoUpdateState()
    object UpToDate : PertoUpdateState()
    data class Available(val version: String, val sizeMB: Int) : PertoUpdateState()
    data class Downloading(val version: String, val percent: Int) : PertoUpdateState()
    data class Ready(val version: String) : PertoUpdateState()
    data class NeedsPermission(val version: String) : PertoUpdateState()
    data class Failed(val message: String) : PertoUpdateState()
}

/** Decide se uma versão encontrada deve ser oferecida para quem está na [installed]. */
internal fun shouldOfferUpdate(installed: String, candidate: String?): Boolean =
    candidate != null && AppVersion.isNewer(installed, candidate)

/** Se a procura automática deve rodar agora (no máximo a cada [intervalMillis]). */
internal fun isAutoCheckDue(nowMillis: Long, lastCheckMillis: Long, intervalMillis: Long): Boolean =
    lastCheckMillis <= 0L || nowMillis - lastCheckMillis !in 0 until intervalMillis

class PertoUpdateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PertoUpdate"
        private const val AUTO_CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
        private const val APK_MIME = "application/vnd.android.package-archive"
    }

    private val app get() = getApplication<Application>()
    private val apkManager = UniversalApkManager(application)
    private val downloader = WorkManagerApkDownloader(application)
    private val releaseClient = GitHubReleaseClient(application)
    private val installedVersion: String = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow<PertoUpdateState>(PertoUpdateState.Idle)
    val state: StateFlow<PertoUpdateState> = _state.asStateFlow()

    /** Se a janela de atualização está aberta. */
    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen: StateFlow<Boolean> = _dialogOpen.asStateFlow()

    /** Só reage ao fim de um download que esta tela pediu (o estado antigo do WorkManager fica guardado). */
    private var waitingForDownload = false

    val currentVersion: String get() = installedVersion

    init {
        viewModelScope.launch {
            downloader.downloadState.collect { onDownloadState(it) }
        }
    }

    /** Procura automática ao abrir o app: só avisa se tiver versão nova que a pessoa não adiou. */
    fun autoCheck() {
        if (!PertoPrefs.autoUpdateCheck.value) return
        val now = System.currentTimeMillis()
        if (!isAutoCheckDue(now, PertoPrefs.lastUpdateCheck(app), AUTO_CHECK_INTERVAL_MILLIS)) {
            // Mesmo sem consultar a rede, um APK novo já baixado continua pronto pra instalar.
            viewModelScope.launch {
                downloadedNewerVersion()?.let { v ->
                    if (v != PertoPrefs.skippedUpdate(app)) {
                        _state.value = PertoUpdateState.Ready(v)
                        _dialogOpen.value = true
                    }
                }
            }
            return
        }
        check(manual = false)
    }

    /** Procura agora. [manual] = a pessoa tocou em "Procurar atualização", então mostra o resultado. */
    fun check(manual: Boolean) {
        val busy = _state.value
        if (busy is PertoUpdateState.Checking || busy is PertoUpdateState.Downloading) {
            if (manual) _dialogOpen.value = true
            return
        }
        if (manual) _dialogOpen.value = true
        _state.value = PertoUpdateState.Checking
        viewModelScope.launch {
            downloadedNewerVersion()?.let { v ->
                _state.value = PertoUpdateState.Ready(v)
                if (!manual && v == PertoPrefs.skippedUpdate(app)) {
                    _state.value = PertoUpdateState.Idle
                } else {
                    _dialogOpen.value = true
                }
                return@launch
            }
            releaseClient.latestRelease()
                .onSuccess { snapshot ->
                    PertoPrefs.setLastUpdateCheck(app, System.currentTimeMillis())
                    val release = snapshot.release
                    if (shouldOfferUpdate(installedVersion, release.versionName)) {
                        _state.value = PertoUpdateState.Available(
                            version = release.versionName,
                            sizeMB = (release.universalApkSize / 1024 / 1024).toInt()
                        )
                        if (manual || release.versionName != PertoPrefs.skippedUpdate(app)) {
                            _dialogOpen.value = true
                        } else {
                            _state.value = PertoUpdateState.Idle
                        }
                    } else {
                        _state.value = if (manual) PertoUpdateState.UpToDate else PertoUpdateState.Idle
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "Could not check for a new Perto release", error)
                    _state.value = if (manual) {
                        PertoUpdateState.Failed(
                            "Não deu pra procurar agora. Confira se o celular está com internet e tente de novo."
                        )
                    } else {
                        PertoUpdateState.Idle
                    }
                }
        }
    }

    fun download() {
        val version = when (val s = _state.value) {
            is PertoUpdateState.Available -> s.version
            is PertoUpdateState.Failed -> null
            else -> return
        }
        waitingForDownload = true
        _state.value = PertoUpdateState.Downloading(version ?: "", 0)
        downloader.startDownload()
    }

    fun cancelDownload() {
        waitingForDownload = false
        downloader.cancelDownload()
        _state.value = PertoUpdateState.Idle
        _dialogOpen.value = false
    }

    /** "Depois": fecha e não insiste nessa versão no aviso automático. */
    fun later() {
        when (val s = _state.value) {
            is PertoUpdateState.Available -> PertoPrefs.setSkippedUpdate(app, s.version)
            is PertoUpdateState.Ready -> PertoPrefs.setSkippedUpdate(app, s.version)
            else -> Unit
        }
        closeDialog()
    }

    fun closeDialog() {
        _dialogOpen.value = false
        val s = _state.value
        if (s is PertoUpdateState.UpToDate || s is PertoUpdateState.Failed) {
            _state.value = PertoUpdateState.Idle
        }
    }

    /** Abre o instalador do Android com o APK baixado e conferido. */
    fun install(context: Context) {
        val version = when (val s = _state.value) {
            is PertoUpdateState.Ready -> s.version
            is PertoUpdateState.NeedsPermission -> s.version
            else -> return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = PertoUpdateState.NeedsPermission(version)
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Log.w(TAG, "Could not open the install-permission screen", it) }
            return
        }
        viewModelScope.launch {
            val file = newerApkFile()
            if (file == null) {
                _state.value = PertoUpdateState.Failed(
                    "O arquivo da atualização sumiu. Toque em Procurar atualização de novo."
                )
                return@launch
            }
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, APK_MIME)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                PertoPrefs.setSkippedUpdate(app, null)
                _dialogOpen.value = false
            }.onFailure { error ->
                Log.w(TAG, "Could not open the Android installer", error)
                _state.value = PertoUpdateState.Failed("Não deu pra abrir o instalador do Android.")
            }
        }
    }

    /** Voltou da tela de permissão: se a pessoa liberou, já abre o instalador. */
    fun onResume(context: Context) {
        val s = _state.value
        if (s is PertoUpdateState.NeedsPermission &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls())
        ) {
            _state.value = PertoUpdateState.Ready(s.version)
            install(context)
        }
    }

    private fun onDownloadState(state: ApkDownloader.DownloadState) {
        if (!waitingForDownload) return
        when (state) {
            is ApkDownloader.DownloadState.Downloading -> _state.update { current ->
                val version = (current as? PertoUpdateState.Downloading)?.version ?: ""
                PertoUpdateState.Downloading(version, state.progressPercent.coerceIn(0, 100))
            }
            is ApkDownloader.DownloadState.Success -> {
                waitingForDownload = false
                viewModelScope.launch {
                    val v = downloadedNewerVersion()
                    _state.value = if (v != null) {
                        PertoUpdateState.Ready(v)
                    } else {
                        PertoUpdateState.UpToDate
                    }
                    _dialogOpen.value = true
                }
            }
            is ApkDownloader.DownloadState.Failed -> {
                waitingForDownload = false
                val message = app.resolveApkFailureMessage(
                    ApkFailureMessage(state.reason.messageRes, state.messageArgs)
                )
                _state.value = PertoUpdateState.Failed(message)
                _dialogOpen.value = true
            }
            ApkDownloader.DownloadState.Idle -> Unit
        }
    }

    /** Versão do APK já baixado, se for mais nova que a instalada. */
    private suspend fun downloadedNewerVersion(): String? = withContext(Dispatchers.IO) {
        apkManager.getCachedApkInfo()
            ?.takeIf { it.source == UniversalApkManager.ApkSource.DOWNLOADED }
            ?.version
            ?.takeIf { shouldOfferUpdate(installedVersion, it) }
    }

    private suspend fun newerApkFile(): File? = withContext(Dispatchers.IO) {
        apkManager.getCachedApkInfo()
            ?.takeIf { shouldOfferUpdate(installedVersion, it.version) }
            ?.file
            ?.takeIf { it.isFile }
    }
}

/** Janela de atualização. Aparece sozinha quando há versão nova, ou pelas Configurações. */
@Composable
fun PertoUpdateDialog(vm: PertoUpdateViewModel) {
    val context = LocalContext.current
    val open by vm.dialogOpen.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!open) return
    val s = state
    if (s is PertoUpdateState.Idle) return

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = {
            if (s is PertoUpdateState.Available || s is PertoUpdateState.Ready) vm.later() else vm.closeDialog()
        },
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        title = {
            Text(
                when (s) {
                    is PertoUpdateState.Checking -> "Procurando atualização…"
                    is PertoUpdateState.UpToDate -> "Você já está na versão mais nova"
                    is PertoUpdateState.Available -> "Versão nova do Perto"
                    is PertoUpdateState.Downloading -> "Baixando atualização…"
                    is PertoUpdateState.Ready, is PertoUpdateState.NeedsPermission -> "Atualização pronta"
                    is PertoUpdateState.Failed -> "Não deu certo"
                    PertoUpdateState.Idle -> ""
                }
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when (s) {
                    is PertoUpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is PertoUpdateState.UpToDate -> Text("Você tem a versão ${vm.currentVersion}.")
                    is PertoUpdateState.Available -> {
                        Text("A versão ${s.version} já saiu. Você está na ${vm.currentVersion}.")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "O download tem uns ${s.sizeMB.coerceAtLeast(1)} MB. Suas conversas continuam no celular.",
                            fontSize = 13.sp,
                            color = muted
                        )
                    }
                    is PertoUpdateState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { s.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${s.percent}% · pode continuar usando o app.",
                            fontSize = 13.sp,
                            color = muted
                        )
                    }
                    is PertoUpdateState.Ready -> Text(
                        "A versão ${s.version} foi baixada e conferida. Toque em Instalar e confirme na tela do Android."
                    )
                    is PertoUpdateState.NeedsPermission -> Text(
                        "Na próxima tela, ligue \"Permitir desta fonte\" para o Perto e volte aqui. " +
                            "É só na primeira vez."
                    )
                    is PertoUpdateState.Failed -> Text(s.message)
                    PertoUpdateState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (s) {
                is PertoUpdateState.Available -> Button(onClick = { vm.download() }) { Text("Atualizar") }
                is PertoUpdateState.Ready, is PertoUpdateState.NeedsPermission ->
                    Button(onClick = { vm.install(context) }) { Text("Instalar") }
                is PertoUpdateState.Failed -> Button(onClick = { vm.check(manual = true) }) { Text("Tentar de novo") }
                is PertoUpdateState.Downloading, is PertoUpdateState.Checking ->
                    TextButton(onClick = { vm.closeDialog() }) { Text("Esconder") }
                else -> TextButton(onClick = { vm.closeDialog() }) { Text("Ok") }
            }
        },
        dismissButton = {
            when (s) {
                is PertoUpdateState.Available, is PertoUpdateState.Ready ->
                    TextButton(onClick = { vm.later() }) { Text("Depois") }
                is PertoUpdateState.Downloading ->
                    TextButton(onClick = { vm.cancelDownload() }) { Text("Cancelar") }
                is PertoUpdateState.NeedsPermission, is PertoUpdateState.Failed ->
                    TextButton(onClick = { vm.closeDialog() }) { Text("Fechar") }
                else -> Unit
            }
        }
    )
}
