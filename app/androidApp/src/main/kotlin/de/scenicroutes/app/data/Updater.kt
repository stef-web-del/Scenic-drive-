package de.scenicroutes.app.data

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Environment
import androidx.core.content.IntentCompat
import de.scenicroutes.app.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** version.json auf der Download-Seite (https://scenic-routes-app-download.vercel.app/version.json). */
@Serializable
data class AppVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val changes: List<String> = emptyList(),
    val minSupported: Int = 0,
    val sizeBytes: Long = 0,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val v: AppVersion, val required: Boolean) : UpdateState
    data class Downloading(val v: AppVersion, val fraction: Float?) : UpdateState
    data class Verifying(val v: AppVersion) : UpdateState
    /** Freigabe „Unbekannte Apps installieren“ fehlt → Knopf zu den Einstellungen, danach geht es automatisch weiter. */
    data class NeedsPermission(val v: AppVersion) : UpdateState
    /** PackageInstaller wartet auf Bestätigung; [intent] startet die Vordergrund-Activity (nicht der Receiver). */
    data class Confirm(val v: AppVersion, val intent: Intent) : UpdateState
    data class Installing(val v: AppVersion) : UpdateState
    data class Failed(val msg: String, val v: AppVersion? = null) : UpdateState
}

/** In-App-Updates: max. 1×/Tag beim Start, manuell in den Einstellungen. Download per DownloadManager, SHA-256, PackageInstaller. */
class Updater(private val ctx: Context, private val settings: Settings, private val currentCode: Int = BuildConfig.VERSION_CODE,
              private val canInstall: () -> Boolean = { android.os.Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls() }) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private var downloadId = -1L
    private var verified: File? = null
    private var sessionId = -1
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @androidx.annotation.VisibleForTesting internal fun forceState(s: UpdateState) { _state.value = s }
    fun parse(s: String) = json.decodeFromString(AppVersion.serializer(), s)
    fun evaluate(v: AppVersion): UpdateState = if (v.versionCode > currentCode) UpdateState.Available(v, currentCode < v.minSupported) else UpdateState.UpToDate

    suspend fun check(force: Boolean, now: Long = System.currentTimeMillis()) {
        if (!force && now - settings.lastUpdateCheck < 24 * 3600_000L) return
        if (busy) return
        _state.value = UpdateState.Checking
        _state.value = runCatching { evaluate(parse(httpGet(Endpoints.VERSION + "?t=" + now / 60000).decodeToString())) }
            .onSuccess { settings.lastUpdateCheck = now }
            .getOrElse { if (force) UpdateState.Failed("Update-Server nicht erreichbar") else UpdateState.Idle }
    }

    private val busy get() = _state.value.let { it is UpdateState.Downloading || it is UpdateState.Verifying || it is UpdateState.Installing || it is UpdateState.Confirm }

    /** Dialog schließen – in jedem Zustand möglich; laufender Download/Session wird abgebrochen. */
    fun dismiss() {
        job?.cancel(); job = null
        if (downloadId >= 0) runCatching { ctx.getSystemService(DownloadManager::class.java).remove(downloadId) }; downloadId = -1
        if (sessionId >= 0) runCatching { ctx.packageManager.packageInstaller.abandonSession(sessionId) }; sessionId = -1
        _state.value = UpdateState.Idle
    }

    fun start(v: AppVersion) { if (busy) return; job = scope.launch { downloadAndInstall(v) } }

    /** Nach Rückkehr aus den Einstellungen / dem Installer (ON_RESUME). */
    fun onResume() {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && canInstall()) verified?.let { f -> job = scope.launch { installVerified(s.v, f) } }
    }

    /** Von der Activity aufgerufen, nachdem sie den Bestätigungsdialog gestartet hat. */
    fun confirmLaunched(ok: Boolean) {
        val s = _state.value as? UpdateState.Confirm ?: return
        _state.value = if (ok) UpdateState.Installing(s.v) else UpdateState.Failed("Installationsdialog ließ sich nicht öffnen", s.v)
        if (ok) armTimeout(s.v)
    }

    suspend fun downloadAndInstall(v: AppVersion) {
        try {
            val dm = ctx.getSystemService(DownloadManager::class.java)
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
            val file = File(dir, "scenic-routes-${v.versionCode}.apk").apply { delete() }
            val id = dm.enqueue(DownloadManager.Request(Uri.parse(v.apkUrl)).setTitle("Scenic Routes ${v.versionName}").setDescription("Update wird geladen")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE).setDestinationUri(Uri.fromFile(file)).setMimeType("application/vnd.android.package-archive"))
            downloadId = id
            _state.value = UpdateState.Downloading(v, 0f)
            var lastBytes = -1L; var stallSince = System.currentTimeMillis()
            while (true) {
                val row = dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (!c.moveToFirst()) null else longArrayOf(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)).toLong(), c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                } ?: run { _state.value = UpdateState.Failed("Download abgebrochen", v); return }
                val (st, done, total) = Triple(row[0].toInt(), row[1], row[2])
                if (st == DownloadManager.STATUS_SUCCESSFUL) break
                if (st == DownloadManager.STATUS_FAILED) { _state.value = UpdateState.Failed("Download fehlgeschlagen – Internetverbindung prüfen", v); return }
                if (done != lastBytes) { lastBytes = done; stallSince = System.currentTimeMillis() }
                else if (System.currentTimeMillis() - stallSince > STALL_MS) { dm.remove(id); _state.value = UpdateState.Failed("Download hängt – bitte erneut versuchen", v); return }
                _state.value = UpdateState.Downloading(v, if (total > 0) done.toFloat() / total else null)
                delay(400)
            }
            downloadId = -1
            _state.value = UpdateState.Verifying(v)
            val sha = withContext(Dispatchers.IO) { fileSha256(file) }
            if (!sha.equals(v.sha256, ignoreCase = true)) { file.delete(); _state.value = UpdateState.Failed("Prüfsumme (SHA-256) stimmt nicht – Update verworfen", v); return }
            verified = file
            installVerified(v, file)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { _state.value = UpdateState.Failed("Update fehlgeschlagen – bitte erneut versuchen", v) }
    }

    internal suspend fun installVerified(v: AppVersion, file: File) {
        if (!canInstall()) { _state.value = UpdateState.NeedsPermission(v); return }
        _state.value = UpdateState.Installing(v)
        runCatching { withContext(Dispatchers.IO) { install(file) } }
            .onSuccess { armTimeout(v) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; _state.value = UpdateState.Failed("Installation konnte nicht gestartet werden", v) }
    }

    private var timeout: Job? = null
    private fun armTimeout(v: AppVersion) {
        timeout?.cancel()
        timeout = scope.launch { delay(INSTALL_TIMEOUT_MS); if (_state.value is UpdateState.Installing) _state.value = UpdateState.Failed("Installation reagiert nicht – erneut versuchen oder im Browser laden", v) }
    }

    private fun install(file: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName); setSize(file.length())
            if (android.os.Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sid = pi.createSession(params); sessionId = sid
        pi.openSession(sid).use { s ->
            s.openWrite("base.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val intent = Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, sid, intent, flags).intentSender)
        }
    }

    /** Status vom PackageInstaller. PENDING_USER_ACTION → Confirm (Activity startet den Intent im Vordergrund). */
    internal fun onInstallStatus(status: Int, msg: String?, confirm: Intent?) {
        val cur = _state.value
        val v = (cur as? UpdateState.Installing)?.v ?: (cur as? UpdateState.Confirm)?.v ?: return
        timeout?.cancel()
        _state.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirm?.let { UpdateState.Confirm(v, it) } ?: UpdateState.Failed("Installationsdialog fehlt", v)
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Failed("Installation abgebrochen", v)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateState.Failed("Installation nicht möglich (andere Signatur?) – im Browser laden", v)
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Zu wenig Speicherplatz", v)
            else -> UpdateState.Failed("Installation fehlgeschlagen", v)
        }
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) sessionId = -1
    }

    companion object {
        const val ACTION_STATUS = "de.scenicroutes.app.INSTALL_STATUS"
        const val STALL_MS = 60_000L
        const val INSTALL_TIMEOUT_MS = 90_000L
        const val BROWSER_URL = "https://scenic-routes-app-download.vercel.app/app.apk"
        fun fileSha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** Rückmeldung des PackageInstallers. Startet selbst KEINE Activity (Android 14+ blockiert Starts aus dem Hintergrund). */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        (context.applicationContext as? de.scenicroutes.app.ScenicApp)?.graph?.updater?.onInstallStatus(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), confirm)
    }
}
