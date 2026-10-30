package de.scenicroutes.app.data

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import com.valhalla.valhalla.config.ValhallaConfigManager
import com.valhalla.valhalla.files.ValhallaFile
import de.scenicroutes.app.BuildConfig
import de.scenicroutes.shared.nav.BackendConfig
import de.scenicroutes.shared.nav.NavRouter
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface RoutingPackState {
    data object Missing : RoutingPackState
    data class Downloading(val fraction: Float) : RoutingPackState
    data class Ready(val bytes: Long) : RoutingPackState
    data class Failed(val reason: String) : RoutingPackState
}

/**
 * Offline-Routing auf dem Gerät: Valhalla (valhalla-mobile) mit Auto-Kacheln für Miltenberg + ~60 km.
 * Paket einmalig von Vercel Blob (gzip, SHA-256-geprüft), entpackt als Tile-Extract (.tar, mmap).
 */
class OfflineRouting(private val ctx: Context, private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()) {
    private val dir = File(ctx.filesDir, "routing")
    val tar = File(dir, "miltenberg-car-v1.tar")
    private val _state = MutableStateFlow<RoutingPackState>(if (tar.exists()) RoutingPackState.Ready(tar.length()) else RoutingPackState.Missing)
    val state = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val lock = Mutex()
    private var engine: Valhalla? = null

    val installed get() = tar.exists()

    fun download() {
        if (job?.isActive == true || installed) return
        job = scope.launch {
            _state.value = RoutingPackState.Downloading(0f)
            val tmp = File(dir, "pack.tmp"); dir.mkdirs()
            try {
                val md = MessageDigest.getInstance("SHA-256")
                http.newCall(Request.Builder().url(BuildConfig.ROUTING_PACK_URL).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val total = r.body.contentLength().takeIf { it > 0 } ?: BuildConfig.ROUTING_PACK_BYTES
                    var read = 0L
                    val counting = object : java.io.FilterInputStream(r.body.byteStream()) {
                        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
                            if (n > 0) { md.update(b, off, n); read += n; _state.value = RoutingPackState.Downloading((read.toFloat() / total).coerceAtMost(1f)) }
                        }
                    }
                    GZIPInputStream(counting, 1 shl 16).use { gz -> tmp.outputStream().buffered(1 shl 16).use { gz.copyTo(it, 1 shl 16) } }
                }
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                if (sha != BuildConfig.ROUTING_PACK_SHA256) throw IOException("Prüfsumme falsch")
                if (!tmp.renameTo(tar)) throw IOException("Speichern fehlgeschlagen")
                _state.value = RoutingPackState.Ready(tar.length())
            } catch (e: Exception) {
                tmp.delete()
                if (e is kotlinx.coroutines.CancellationException) { _state.value = RoutingPackState.Missing; throw e }
                _state.value = RoutingPackState.Failed(if (e is IOException && e.message == "Prüfsumme falsch") "Download beschädigt – bitte erneut versuchen" else "Download nicht möglich – Internetverbindung prüfen")
            }
        }
    }

    fun remove() {
        job?.cancel(); scope.launch { lock.withLock { engine?.close(); engine = null; tar.delete() }; _state.value = RoutingPackState.Missing }
    }

    /** Lokaler Router (null, wenn das Paket fehlt). */
    fun router(): NavRouter? = if (!installed) null else object : NavRouter {
        override val label = "offline"
        override suspend fun route(bodyJson: String): String = withContext(Dispatchers.Default) {
            lock.withLock {
                val v = engine ?: Valhalla(ctx, ValhallaConfigFactory.usingTileExtract(tar.absolutePath),
                    ValhallaConfigManager(ctx)).also { engine = it }
                v.routeRaw(bodyJson)
            }
        }
    }
}

/** Optionaler Server (navserver / scenic-routes-nav): nur Valhalla-/route im gleichen Format. */
class HttpRouter(private val config: BackendConfig, private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()) : NavRouter {
    override val label = "Server"
    override suspend fun route(bodyJson: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(config.routeUrl).post(bodyJson.toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
    }
}
