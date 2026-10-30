package de.scenicroutes.app.data

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import de.scenicroutes.app.ui.STYLE_DARK
import de.scenicroutes.app.ui.STYLE_LIGHT
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.offline.OfflineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflineManager
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.getOfflineManager
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position

/** Fortschritt einer Offline-Speicherung. fraction = null → noch unbestimmt. */
sealed interface SaveState {
    data object None : SaveState
    data class Running(val step: String, val fraction: Float?, val bytes: Long) : SaveState
    data class Saved(val bytes: Long, val mapComplete: Boolean) : SaveState
    data class Failed(val reason: String) : SaveState
}

/**
 * „Offline speichern“: Navigation (OSRM-JSON von Vercel, SHA-256-geprüft) in SQLDelight +
 * MapLibre-Offline-Paket entlang der Runde (Zoom 6–14, OpenFreeMap-Vektorkacheln; darüber Overzoom), hell + dunkel.
 */
class OfflineSaver(private val ctx: Context, private val data: LoopData, val repo: OfflineRepository, private val alsoRouting: () -> Unit = {}) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<Int, Job>()
    private val _states = MutableStateFlow<Map<Int, SaveState>>(emptyMap())
    val states = _states.asStateFlow()
    private val manager: OfflineManager? by lazy { runCatching { getOfflineManager(ctx).also { it.setTileCountLimit(20_000) } }.getOrNull() }

    init { scope.launch { refresh() } }

    private suspend fun refresh() {
        val m = withContext(Dispatchers.IO) { repo.all() }
        _states.update { cur -> cur.filterValues { it is SaveState.Running } + m.associate { it.loopId to SaveState.Saved(it.totalBytes, it.mapComplete) } }
    }

    private fun set(id: Int, s: SaveState) = _states.update { it + (id to s) }

    private fun packsFor(id: Int): List<OfflinePack> = manager?.packs?.filter { it.metadata?.decodeToString()?.startsWith("loop:$id:") == true }.orEmpty()

    fun save(loop: Loop) {
        if (jobs[loop.id]?.isActive == true) return
        alsoRouting() // Offline-Routing-Paket (einmalig) mitladen
        jobs[loop.id] = scope.launch {
            try {
                set(loop.id, SaveState.Running("Navigation laden …", null, 0))
                val nav = runCatching { data.fetchNav(loop.id, loop.navSha) }.getOrElse { data.bundled(loop.id) ?: throw it }
                val navBytes = nav.length.toLong()
                withContext(Dispatchers.IO) { repo.save(loop.id, nav, loop.navSha, System.currentTimeMillis()) }
                val mgr = manager ?: run { set(loop.id, SaveState.Saved(navBytes, false)); return@launch }
                packsFor(loop.id).forEach { mgr.delete(it) }
                val line = LineString(loop.geom.map { Position(it[0], it[1]) })
                var mapBytes = 0L; var ok = true
                val styles = listOf(STYLE_LIGHT, STYLE_DARK)
                styles.forEachIndexed { i, style ->
                    val pack = mgr.create(OfflinePackDefinition.Shape(style, line, 6, 14), "loop:${loop.id}:$i".encodeToByteArray())
                    mgr.resume(pack)
                    val end = snapshotFlow { pack.downloadProgress }.first { p ->
                        when (p) {
                            is DownloadProgress.Healthy -> {
                                val f = if (p.requiredResourceCount > 0) p.completedResourceCount.toFloat() / p.requiredResourceCount else null
                                val total = f?.let { (i + it) / styles.size }
                                set(loop.id, SaveState.Running(if (i == 0) "Karte laden …" else "Dunkle Karte laden …", total, navBytes + mapBytes + p.completedResourceBytes))
                                p.status == DownloadStatus.Complete
                            }
                            is DownloadProgress.Error, is DownloadProgress.TileLimitExceeded -> true
                            else -> false
                        }
                    }
                    if (end is DownloadProgress.Healthy) mapBytes += end.completedResourceBytes else ok = false
                }
                withContext(Dispatchers.IO) { repo.setMap(loop.id, mapBytes, ok) }
                set(loop.id, SaveState.Saved(navBytes + mapBytes, ok))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                set(loop.id, SaveState.Failed("Speichern nicht möglich – Internetverbindung prüfen"))
            }
        }
    }

    fun remove(id: Int) {
        jobs.remove(id)?.cancel()
        scope.launch {
            manager?.let { m -> packsFor(id).forEach { m.delete(it) } }
            withContext(Dispatchers.IO) { repo.remove(id) }
            _states.update { it - id }
        }
    }
}
