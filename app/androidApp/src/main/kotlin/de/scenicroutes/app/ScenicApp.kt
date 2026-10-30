package de.scenicroutes.app

import android.app.Application
import android.content.Context
import de.scenicroutes.app.data.LoopData
import de.scenicroutes.app.data.OfflineSaver
import de.scenicroutes.app.data.OkHttpNavBackend
import de.scenicroutes.app.data.Settings
import de.scenicroutes.app.data.Updater
import de.scenicroutes.shared.createDriver
import de.scenicroutes.shared.model.LoopCatalog
import de.scenicroutes.shared.nav.BackendConfig
import de.scenicroutes.shared.offline.OfflineRepository
import de.scenicroutes.shared.trip.TrackPoint
import de.scenicroutes.shared.trip.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Minimaler Service-Locator (bewusst ohne DI-Framework). */
class AppGraph(private val ctx: Context) {
    val settings = Settings(ctx)
    private val driver by lazy { createDriver(ctx) }
    val trips: TripRepository by lazy { TripRepository(driver) }
    val offlineRepo: OfflineRepository by lazy { OfflineRepository(driver) }
    val data = LoopData(ctx) { offlineRepo }
    val grid = de.scenicroutes.app.data.GridData(ctx)
    val local by lazy { de.scenicroutes.app.data.LocalStore(driver) }
    /** Runden aus „Runde ab hier“ (Raster), für Detail und Navigation. */
    val gridLoops = java.util.concurrent.ConcurrentHashMap<Int, de.scenicroutes.shared.model.Loop>()
    fun loop(id: Int): de.scenicroutes.shared.model.Loop? = catalog.loops.firstOrNull { it.id == id } ?: gridLoops[id]
    private val _catalog = MutableStateFlow(data.initialCatalog())
    /** Katalog: sofort aus Cache/APK, im Hintergrund aktualisiert von Vercel. */
    val catalogFlow = _catalog.asStateFlow()
    val catalog: LoopCatalog get() = _catalog.value
    val catalogOnline = MutableStateFlow<Boolean?>(null)
    val saver by lazy { OfflineSaver(ctx, data, offlineRepo) { routing.download() } }
    val updater by lazy { Updater(ctx, settings) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun refreshCatalog() = scope.launch {
        val c = data.refreshCatalog(); catalogOnline.value = c != null; if (c != null) _catalog.value = c
    }
    val routing by lazy { de.scenicroutes.app.data.OfflineRouting(ctx) }
    /** Routing-Reihenfolge: lokal (Offline-Paket) zuerst, optionaler Server als Rückfall. */
    fun routers(): de.scenicroutes.shared.nav.RouterChain = de.scenicroutes.shared.nav.RouterChain(listOfNotNull(
        routing.router(), settings.backendUrl.takeIf { it.isNotBlank() }?.let { de.scenicroutes.app.data.HttpRouter(BackendConfig(it)) }))
    /** null = kein Server eingestellt (Navigation dann rein offline). */
    fun backend(): OkHttpNavBackend? = settings.backendUrl.takeIf { it.isNotBlank() }?.let { OkHttpNavBackend(BackendConfig(it)) }

    /** Client für „Runde ab Punkt“; in Tests austauschbar. null = kein Server eingestellt. */
    var freeLoops: () -> de.scenicroutes.app.data.FreeLoopApi? = {
        settings.freeLoopUrl.takeIf { it.isNotBlank() }?.let { de.scenicroutes.app.data.HttpFreeLoopApi(it) }
    }

    /** „Meine Routen“; gespeicherte Runden werden beim Start für Detail und Navigation angemeldet. */
    val routes by lazy {
        de.scenicroutes.app.data.RouteRepo(ctx, owner = { local.uuid }).also { repo ->
            repo.book.value.visible.forEach { r -> if (catalog.loops.none { it.id == r.appLoopId }) repo.loopOf(r).let { gridLoops.putIfAbsent(it.id, it) } }
        }
    }

    /** Positionen aus der Navigation (echt oder simuliert) -> Aufzeichnung. */
    val navLocations = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64)
    val simulating = MutableStateFlow(false)
    val activeLoopId = MutableStateFlow<Int?>(null)
    /** Laufende Zielführung (Titel), null = keine. Hält den Vordergrunddienst im Modus „nur Navigation“. */
    val navSession = MutableStateFlow<String?>(null)

    /** Benachrichtigung angetippt → Aufzeichnungs-Ansicht öffnen. */
    val openRecord = MutableStateFlow(false)
    /** Fahrtaufzeichnung (frei oder mit Runde), schreibt laufend in die DB. */
    val recorder by lazy {
        de.scenicroutes.app.rec.RideRecorder(trips,
            loopGeom = { id -> loop(id)?.let { de.scenicroutes.shared.geo.Polyline(it.coords) to it.titleShort } },
            markDriven = { id, rev -> local.markDriven(id, rev) },
            placeName = { la, lo -> placeName(la, lo) })
    }
    @Suppress("DEPRECATION")
    private fun placeName(lat: Double, lon: Double): String? = runCatching {
        if (!android.location.Geocoder.isPresent()) return null
        android.location.Geocoder(ctx, java.util.Locale.GERMANY).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
    }.getOrNull()
}

class ScenicApp : Application() {
    lateinit var graph: AppGraph; private set
    override fun onCreate() { super.onCreate(); de.scenicroutes.app.data.CrashLog.install(this, BuildConfig.VERSION_NAME); graph = AppGraph(this); graph.refreshCatalog() }
}

val Context.graph: AppGraph get() = (applicationContext as ScenicApp).graph
