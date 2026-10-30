package de.scenicroutes.app.nav

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.nav.LoopEntry
import de.scenicroutes.shared.nav.Rejoin
import de.scenicroutes.shared.nav.Polyline6
import kotlinx.serialization.json.jsonPrimitive
import de.scenicroutes.shared.nav.LoopEntryPoint
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import android.speech.tts.TextToSpeech
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.core.CorrectiveAction
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.RouteDeviationHandler
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.extensions.fromOsrm
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import de.scenicroutes.app.AppGraph
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.nav.GeoPoint
import de.scenicroutes.shared.nav.NavJson
import de.scenicroutes.shared.nav.OffRouteMonitor
import de.scenicroutes.shared.nav.RejoinRequest
import de.scenicroutes.shared.nav.RouteBundle
import de.scenicroutes.shared.nav.OffRoutePolicy
import de.scenicroutes.shared.nav.StepSync
import de.scenicroutes.shared.nav.ArrivalDetector
import uniffi.ferrostar.TripState
import de.scenicroutes.shared.trip.TrackPoint
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

data class NavExtras(
    val started: Boolean = false,
    val simulate: Boolean = false,
    val offLoop: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val blockedCount: Int = 0,
    val progressKm: Double = 0.0,
    val source: String? = null,
    val hasBackend: Boolean = true,
    val phase: EntryPhase = EntryPhase.NONE,
    val entry: EntryUi? = null,
    /** Abweichungs-Modus für die Karte unten (statt veraltetem Banner). */
    val offMode: OffRoutePolicy.Mode = OffRoutePolicy.Mode.ON_LOOP,
    val arrived: Boolean = false,
)

/** Einstieg ab Standort: Phasen vor dem eigentlichen Start. */
enum class EntryPhase { NONE, LOCATING, CHOOSE, GUIDE, EXTERNAL }

/** Anzeige-Daten für die Einstiegs-Karte (Luftlinie [distanceM], Richtung [bearingDeg] vom Standort zum Einstieg). */
data class EntryUi(
    val point: LatLon, val me: LatLon, val distanceM: Double, val bearingDeg: Double,
    val headingDeg: Double?, val road: String, val atKm: Double, val totalKm: Double, val official: Boolean,
)

/**
 * Konfiguration für FESTE Runden: Start = Ziel, daher Wegpunkt erst entlang des Schritts weiterschalten.
 *
 * 0.4.1 hatte (30/5/32), Ankunft (10/32) und Abweichung erst ab Genauigkeit ≤ 15 m. Folgen (tools/navsim):
 * - Schritt schaltet nur, wenn ein Fix ≤ 30 m vor dem Manöver liegt UND Genauigkeit ≤ 32 m – nach einer
 *   GPS-Lücke, im Wald, nach Abkürzung oder Einstieg mitten in der Runde nie wieder → Banner friert ein
 *   (Bürgstadt-Runde: bis 44 min, 78 % der Fahrt mit veralteter Anweisung).
 * - Abweichung wurde bei typischer Handy-Genauigkeit 16–40 m nie erkannt → altes Banner auch abseits.
 * Jetzt: Grenzen 40 m; das Nachziehen verpasster Schritte macht [StepSync] (Ferrostars DistanceFromStep
 * würde bei Runden mit einem einzigen Ausreißer am Start ans Ende springen – in navsim nachgewiesen).
 */
fun loopNavConfig() = NavigationControllerConfig(
    WaypointAdvanceMode.WaypointAlongAdvancingStep(100.0),
    stepAdvanceDistanceEntryAndExit(30u, 5u, 40u),
    stepAdvanceDistanceToEndOfStep(30u, 40u),
    RouteDeviationTracking.StaticThreshold(40U, 50.0),
    CourseFiltering.SNAP_TO_ROUTE,
)

class NavViewModel private constructor(
    private val graph: AppGraph,
    val loop: Loop,
    val core: FerrostarCore,
    val locationProvider: NavigationLocationProvider,
    private val tts: NavSpeech,
    appCtx: Context,
) : DefaultNavigationViewModel(core, valhallaExtendedOSRMAnnotationPublisher()) {

    private val loopLine = Polyline(loop.coords)
    private var activeLine = loopLine
    private var loopOffsetM = 0.0 // Lage des Einstiegs auf der Original-Runde
    private var monitor = OffRouteMonitor(loopLine)
    private var activeBundle: RouteBundle? = null
    private var bundle: Pair<RouteBundle, de.scenicroutes.app.data.RouteSource>? = null
    private var entryPt: LoopEntryPoint? = null
    private val fused = LocationServices.getFusedLocationProviderClient(appCtx)
    private var guideCb: LocationCallback? = null
    private val blocked = mutableListOf<GeoPoint>()
    private val _extras = MutableStateFlow(NavExtras(hasBackend = !graph.routers().isEmpty))
    val extras = _extras.asStateFlow()
    /** Kurven der aktiven Route ohne Abbiegen/Kreisverkehre (aus den Valhalla-Manövern). */
    private val _curves = MutableStateFlow<List<de.scenicroutes.shared.nav.Curve>?>(null)
    val curves = _curves.asStateFlow()
    private fun routeOf(b: RouteBundle): Route {
        _curves.value = runCatching { de.scenicroutes.shared.nav.Curves.detectRoute(b.route) }.getOrNull()
        return toRoute(b)
    }
    private var lastLoc: LatLon? = null
    private val arrival = ArrivalDetector()
    private var lastHeading: Double? = null
    /** Schritt-Synchronisation + Fortschritt auf der AKTUELLEN Ferrostar-Route (Runde oder Rückweg + Rest). */
    private var stepSync: StepSync? = null
    private var routeTrack: OffRouteMonitor? = null
    private val policy = OffRoutePolicy()

    init {
        // Abweichung wird gegen die FESTE Runde bewertet (OffRouteMonitor), nicht durch Ferrostar-Neuberechnung.
        core.deviationHandler = RouteDeviationHandler { _, _, _ -> CorrectiveAction.DoNothing }
        core.spokenInstructionObserver = tts
        tts.setMuted(!graph.settings.voice)
        tts.start()
        // Einstellungen live übernehmen; Stumm-Taste und Einstellung teilen sich den Schlüssel „voice“.
        viewModelScope.launch { graph.settings.navFlow.collect { p -> tts.apply(p); if (policy.guidanceVisible) tts.setMuted(!p.voiceOn) } }
        viewModelScope.launch {
            navigationUiState.collect { s ->
                // Abweichung immer mit der ROHEN Position bewerten: Ferrostar liefert in s.location die auf die
                // Route gesnappte Position, solange es selbst keine Abweichung erkennt (z. B. Genauigkeit > 40 m
                // oder Abstand nur zum aktuellen Schritt gemessen) – dann sah der OffRouteMonitor nie eine Abweichung.
                val l = (s.tripState as? TripState.Navigating)?.userLocation ?: s.location ?: return@collect
                val p = LatLon(l.coordinates.lat, l.coordinates.lng)
                val roughFix = l.horizontalAccuracy > 60.0
                lastLoc = p
                if (!_extras.value.started) return@collect
                graph.navLocations.tryEmit(TrackPoint(System.currentTimeMillis(), p.lat, p.lon, null, l.speed?.value, l.horizontalAccuracy))
                if (_extras.value.arrived) return@collect
                lastHeading = l.courseOverGround?.degrees?.toDouble()
                // Zeitstempel nur echt fahren: Simulation läuft mit warpFactor 4, dort keine Sprungbegrenzung.
                val now = if (_extras.value.simulate) null else System.currentTimeMillis()
                if (roughFix) { syncSteps(p, now ?: System.currentTimeMillis()); return@collect }
                val changed = monitor.update(p, now)
                val off = monitor.state == OffRouteMonitor.State.OFF_LOOP
                val track = routeTrack
                val offRoute = track != null && track.update(p, now) && track.state == OffRouteMonitor.State.OFF_LOOP
                val t = now ?: System.currentTimeMillis()
                when {
                    changed && off -> when (policy.leftLoop(t)) {
                        OffRoutePolicy.Action.Plan -> rejoin(reason = "Abseits der Runde – Rückweg wird berechnet …")
                        else -> {}
                    }
                    changed && !off -> {
                        val wasPaused = policy.mode != OffRoutePolicy.Mode.ON_LOOP && policy.mode != OffRoutePolicy.Mode.REJOINING
                        policy.backOnLoop(); tts.setMuted(!graph.settings.voice)
                        _extras.value = _extras.value.copy(message = "Zurück auf der Runde")
                        // Nach Pause/Frage ist die Ferrostar-Route evtl. ein alter Rückweg → von hier neu anschließen.
                        if (wasPaused && track?.state == OffRouteMonitor.State.OFF_LOOP) rejoin(reason = "Zurück auf der Runde")
                    }
                    off && offRoute -> when (policy.leftRejoinRoute(t)) {
                        OffRoutePolicy.Action.Plan -> rejoin(reason = "Neuer Rückweg zur Runde …")
                        OffRoutePolicy.Action.Ask -> tts.setMuted(true)
                        else -> {}
                    }
                    // Erster Versuch fehlgeschlagen/übersprungen (kein Netz, busy): nicht still aufgeben.
                    off && policy.retryDue(t) -> rejoin(reason = "Rückweg wird erneut berechnet …")
                    // Anfahrt läuft (Runde noch nicht erreicht) und man hat die Anfahrt verlassen → Anfahrt neu.
                    !monitor.armed && track?.state == OffRouteMonitor.State.OFF_LOOP && policy.approachReplanDue(t) -> reapproach()
                }
                _extras.value = _extras.value.copy(offLoop = off, offMode = policy.mode, progressKm = monitor.progressM / 1000)
                syncSteps(p, t)
            }
        }
    }

    fun setSimulate(on: Boolean) { _extras.value = _extras.value.copy(simulate = on) }

    /**
     * „Losfahren“: Simulation → offizieller Start. Sonst Standort holen, besten Einstieg (Fahrtrichtung, kürzeste Anfahrt)
     * suchen; ≤ 150 m → sofort ab dort, weiter weg → Einstiegs-Karte mit Optionen.
     */
    fun start() {
        if (_extras.value.busy || _extras.value.started) return
        if (_extras.value.simulate) { startLoop(null, null); return }
        _extras.value = _extras.value.copy(busy = true, phase = EntryPhase.LOCATING, message = "Standort wird bestimmt …")
        viewModelScope.launch {
            val loc = currentLocation()
            if (loc == null) { _extras.value = _extras.value.copy(busy = false, phase = EntryPhase.NONE, message = "Standort unbekannt – starte am offiziellen Start"); startLoop(null, null); return@launch }
            evaluate(LatLon(loc.latitude, loc.longitude), if (loc.hasBearing() && loc.speed > 2f) loc.bearing.toDouble() else null, auto = true)
        }
    }

    private suspend fun loadBundle(): Pair<RouteBundle, de.scenicroutes.app.data.RouteSource> =
        bundle ?: graph.data.route(loop.id, loop.navSha).let { (b, s) -> LoopEntry.sanitize(b) to s }.also { bundle = it }

    /** Einstieg neu bewerten; [auto] = bei ≤ 150 m direkt starten. */
    private suspend fun evaluate(p: LatLon, heading: Double?, auto: Boolean) {
        val b = runCatching { loadBundle() }.getOrElse { _extras.value = _extras.value.copy(busy = false, phase = EntryPhase.NONE, message = "Route nicht verfügbar – Runde offline speichern, solange Netz da ist"); return }.first
        val e = LoopEntry.best(b.route, p, heading)
        entryPt = e
        if (auto && e.approachM <= LoopEntry.DIRECT_START_M) { startLoop(e, null); return }
        val phase = _extras.value.phase.takeIf { it == EntryPhase.GUIDE } ?: EntryPhase.CHOOSE
        _extras.value = _extras.value.copy(busy = false, phase = phase, entry = ui(e, p, heading), message = null)
    }

    private fun ui(e: LoopEntryPoint, me: LatLon, heading: Double?) = EntryUi(
        e.point, me, Geo.distanceM(me, e.point), Geo.bearingDeg(me, e.point), heading, e.roadName,
        loopLine.project(e.point).alongM / 1000, loopLine.lengthM / 1000, e.isOfficialStart,
    )

    /** Startet die (ggf. rotierte) Runde, optional mit vorgeschalteter Anfahrt. */
    private fun startLoop(e: LoopEntryPoint?, approach: RouteBundle?) {
        stopGuide()
        _extras.value = _extras.value.copy(busy = true, message = "Route wird geladen …")
        viewModelScope.launch {
            runCatching {
                val (b, src) = loadBundle()
                val rotated = if (e == null || e.isOfficialStart) b else LoopEntry.rotate(b, e)
                activeBundle = rotated
                val full = approach?.let { LoopEntry.prepend(it, rotated) } ?: rotated
                if (e == null || e.isOfficialStart) { loopOffsetM = 0.0; activeLine = loopLine } else {
                    loopOffsetM = loopLine.project(e.point).alongM
                    activeLine = Polyline(LoopEntry.rotateLine(loop.coords, loopOffsetM))
                }
                monitor = OffRouteMonitor(activeLine, armed = approach == null)
                val route = routeOf(full)
                graph.simulating.value = _extras.value.simulate
                graph.activeLoopId.value = loop.id
                if (_extras.value.simulate) locationProvider.enableSimulationOn(route) else locationProvider.disableSimulation()
                core.startNavigation(route)
                trackRoute(route)
                policy.backOnLoop()
                val where = when { e == null || e.isOfficialStart -> "ab offiziellem Start"; approach != null -> "mit Anfahrt zum Einstieg"; else -> "ab Einstieg ${e.roadName}".trim() }
                _extras.value = _extras.value.copy(started = true, busy = false, phase = EntryPhase.NONE, entry = null, source = src.label, message = "Navigation $where (Route ${src.label})")
            }.onFailure { _extras.value = _extras.value.copy(busy = false, message = "Route nicht verfügbar – Runde offline speichern, solange Netz da ist") }
        }
    }

    /** „Anfahrt berechnen“: Valhalla-Anfahrt + nahtlos in die rotierte Runde. */
    fun approachViaServer() {
        val e = entryPt ?: return; val me = _extras.value.entry?.me ?: return
        val chain = graph.routers()
        if (chain.isEmpty) { _extras.value = _extras.value.copy(message = "Offline-Routing nicht installiert – andere Option wählen"); return }
        _extras.value = _extras.value.copy(busy = true, message = "Anfahrt wird berechnet …")
        viewModelScope.launch {
            val b = runCatching { loadBundle().first }.getOrNull()
            runCatching { Rejoin.approach(b ?: return@launch, me, e.point, _extras.value.entry?.headingDeg, chain).first }
                .onSuccess { startLoop(e, it) }
                .onFailure { _extras.value = _extras.value.copy(busy = false, message = "Anfahrt nicht berechenbar – andere Option wählen") }
        }
    }

    /** „Anfahrt mit Google Maps“: Intent zum Einstieg; bei Rückkehr (onResume) wird neu bewertet und ggf. automatisch gestartet. */
    fun googleIntent(): Intent? {
        val e = entryPt ?: return null
        _extras.value = _extras.value.copy(phase = EntryPhase.EXTERNAL)
        return Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${e.point.lat},${e.point.lon}&mode=d"))
    }
    fun googleWebIntent(): Intent? = entryPt?.let {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${it.point.lat},${it.point.lon}&travelmode=driving&dir_action=navigate"))
    }

    /** Rückkehr in die App (nach Google Maps o. Ä.): Standort neu prüfen, an der Runde → automatisch starten. */
    fun onResume() {
        if (_extras.value.started || _extras.value.phase != EntryPhase.EXTERNAL || _extras.value.busy) return
        viewModelScope.launch {
            val loc = currentLocation() ?: return@launch
            evaluate(LatLon(loc.latitude, loc.longitude), if (loc.hasBearing() && loc.speed > 2f) loc.bearing.toDouble() else null, auto = true)
            if (!_extras.value.started) _extras.value = _extras.value.copy(phase = EntryPhase.EXTERNAL)
        }
    }

    /** „Zum Einstieg ohne Navigation“: Pfeil + Luftlinie, automatischer Start, sobald man auf der Runde ist. */
    @SuppressLint("MissingPermission")
    fun guide() {
        if (guideCb != null) return
        _extras.value = _extras.value.copy(phase = EntryPhase.GUIDE)
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                val l = r.lastLocation ?: return
                val p = LatLon(l.latitude, l.longitude)
                val h = if (l.hasBearing() && l.speed > 2f) l.bearing.toDouble() else null
                if (loopLine.project(p).distanceM < 40.0) { viewModelScope.launch { evaluate(p, h, auto = true) }; return }
                val e = entryPt ?: return
                _extras.value = _extras.value.copy(entry = ui(e, p, h))
            }
        }
        guideCb = cb
        runCatching { fused.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build(), cb, Looper.getMainLooper()) }
    }
    private fun stopGuide() { guideCb?.let { runCatching { fused.removeLocationUpdates(it) } }; guideCb = null }

    /** Ab dem gewählten Einstieg sofort starten (z. B. nach Google Maps, wenn man schon dort ist). */
    fun startAtEntry() { startLoop(entryPt, null) }
    fun startOfficial() { startLoop(null, null) }
    fun cancelEntry() { stopGuide(); _extras.value = _extras.value.copy(phase = EntryPhase.NONE, entry = null, busy = false) }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): android.location.Location? = runCatching {
        withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine { c ->
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resume(null) }
            }
        } ?: suspendCancellableCoroutine { c -> fused.lastLocation.addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resume(null) } }
    }.getOrNull()

    /** „Gesperrt“: Straße vor mir ist zu -> Punkt ~150 m voraus auf der Runde meiden und neu anschließen. */
    fun reportClosure() {
        val ahead = activeLine.pointAt(monitor.progressM + 150.0)
        blocked += GeoPoint(ahead.lat, ahead.lon)
        _extras.value = _extras.value.copy(blockedCount = blocked.size)
        rejoin(reason = "Sperrung gemeldet – suche Umleitung …")
    }

    private fun rejoin(reason: String) {
        val here = lastLoc ?: return
        if (_extras.value.busy) return
        val chain = graph.routers()
        val active = activeBundle
        if (chain.isEmpty || active == null) {
            _extras.value = _extras.value.copy(message = "Offline-Routing nicht installiert – bitte selbst zur Runde zurückfahren (Einstellungen → Offline-Routing)"); return
        }
        _extras.value = _extras.value.copy(busy = true, message = reason)
        viewModelScope.launch {
            val res = runCatching {
                val rl = Polyline(Polyline6.decode(active.route["geometry"]!!.jsonPrimitive.content))
                val along = rl.project(activeLine.pointAt(monitor.progressM)).alongM
                Rejoin.plan(active, rl, here, along, null, blocked.toList(), chain)
            }
            res.onSuccess { rj ->
                val b = rj.bundle.copy(skippedM = rj.skippedM)
                val r = routeOf(b)
                core.replaceRoute(r)
                trackRoute(r)
                policy.planSucceeded()
                if (_extras.value.simulate) locationProvider.enableSimulationOn(r)
                val skip = (b.skippedM ?: 0.0) / 1000
                _extras.value = _extras.value.copy(busy = false, message = if (skip > 0.3) "Umleitung: %.1f km der Runde ausgelassen".format(skip) else "Rückweg zur Runde berechnet (${'$'}{rj.via})")
            }.onFailure {
                _extras.value = _extras.value.copy(busy = false, message = "Kein Rückweg gefunden – bitte selbst zur Runde zurückfahren")
            }
        }
    }

    /** Anfahrt verlassen: von hier neu zum Einstieg (Anfang der aktiven Runde) rechnen und davor hängen. */
    private fun reapproach() {
        val here = lastLoc ?: return
        if (_extras.value.busy) return
        val chain = graph.routers(); val active = activeBundle ?: return
        if (chain.isEmpty) { _extras.value = _extras.value.copy(message = "Kein Routing verfügbar – bitte selbst zum Einstieg fahren"); return }
        _extras.value = _extras.value.copy(busy = true, message = "Anfahrt wird neu berechnet …")
        viewModelScope.launch {
            runCatching { Rejoin.approach(active, here, activeLine.points.first(), lastHeading, chain) }
                .onSuccess { (ap, via) ->
                    val r = routeOf(LoopEntry.prepend(ap, active))
                    core.replaceRoute(r); trackRoute(r)
                    if (_extras.value.simulate) locationProvider.enableSimulationOn(r)
                    _extras.value = _extras.value.copy(busy = false, message = "Anfahrt neu berechnet ($via)")
                }
                .onFailure { _extras.value = _extras.value.copy(busy = false, message = "Anfahrt konnte nicht neu berechnet werden – versuche es gleich wieder") }
        }
    }

    /** Neue Ferrostar-Route: Schrittgrenzen + eigener Fortschritt darauf. */
    private fun trackRoute(r: Route) {
        val sync = StepSync(r.steps.map { st -> st.geometry.map { LatLon(it.lat, it.lng) } })
        stepSync = sync; arrival.reset()
        routeTrack = OffRouteMonitor(Polyline(sync.points))
    }

    /** Verpasste Schritte nachziehen (kein eingefrorenes Banner) und Ankunft am Rundenende erkennen. */
    private fun syncSteps(p: LatLon, tMs: Long) {
        val sync = stepSync ?: return; val track = routeTrack ?: return
        // Ankunftsregel: ≤ 50 m vom Ziel, ≥ 95 % Fortschritt, ~10 s Stand. Greift auch bei Waldrauschen,
        // wenn der Fortschritt die letzten Meter nie erreicht. Unabhängig vom ON_LOOP-Zustand (Rauschen am Ziel).
        val end = sync.points.lastOrNull()
        if (end != null && monitor.progressM > activeLine.lengthM * 0.8) {
            val kx = 111_320.0 * kotlin.math.cos(Math.toRadians(end.lat))
            val frac = track.progressM / sync.stepEndM.last().coerceAtLeast(1.0)
            if (arrival.update(Geo.distanceM(p, end), frac, (p.lon - end.lon) * kx, (p.lat - end.lat) * 110_540.0, tMs)) { arrive(); return }
        }
        if (track.state != OffRouteMonitor.State.ON_LOOP || !policy.guidanceVisible) return
        val nav = core.state.value.tripState as? TripState.Navigating ?: return
        val idx = sync.stepEndM.size - nav.remainingSteps.size
        repeat(sync.stepsToAdvance(idx, track.progressM)) { core.advanceToNextStep() }
        if (sync.arrived(track.progressM) && monitor.progressM > activeLine.lengthM * 0.8) {
            arrive()
        }
    }

    private fun arrive() {
        core.stopNavigation()
        _extras.value = _extras.value.copy(arrived = true, message = "Runde geschafft – Ziel erreicht")
    }

    /** „Runde hier fortsetzen“: nächsten sinnvollen Einstieg (Fahrtrichtung) suchen und ab dort weiter. */
    fun continueHere() {
        val here = lastLoc ?: return
        policy.continueHere(System.currentTimeMillis()); tts.setMuted(!graph.settings.voice)
        _extras.value = _extras.value.copy(offMode = policy.mode, busy = true, message = "Runde wird ab hier neu angeschlossen …")
        viewModelScope.launch {
            runCatching {
                val b = loadBundle().first
                val e = LoopEntry.best(b.route, here, lastHeading)
                val chain = graph.routers()
                val approach = if (e.approachM <= LoopEntry.DIRECT_START_M || chain.isEmpty) null else Rejoin.approach(b, here, e.point, lastHeading, chain).first
                _extras.value = _extras.value.copy(busy = false)
                startLoop(e, approach)
            }.onFailure { _extras.value = _extras.value.copy(busy = false, message = "Kein Anschluss gefunden – fahr zur Runde, die Navigation geht dann weiter") }
        }
    }

    /** „Navigation pausieren“: keine Ansagen, kein Banner; Aufzeichnung läuft. Zurück auf der Runde → automatisch weiter. */
    fun pauseGuidance() {
        policy.pause(); tts.setMuted(true)
        _extras.value = _extras.value.copy(offMode = policy.mode, message = "Navigation pausiert – geht automatisch weiter, sobald du auf der Runde bist")
    }

    fun stop() {
        core.stopNavigation()
        locationProvider.disableSimulation()
        stopGuide()
        graph.activeLoopId.value = null
        graph.navSession.value = null
        _extras.value = _extras.value.copy(started = false, phase = EntryPhase.NONE, entry = null, arrived = false, offMode = OffRoutePolicy.Mode.ON_LOOP)
    }

    fun consumeMessage() { _extras.value = _extras.value.copy(message = null) }

    override fun onCleared() { graph.navSession.value = null; stopGuide(); runCatching { core.stopNavigation() }; tts.shutdown(); super.onCleared() }

    companion object {
        fun toRoute(b: RouteBundle): Route = Route.fromOsrm(NavJson.routeBytes(b), NavJson.waypointBytes(b), 6u)

        fun create(ctx: Context, graph: AppGraph, loop: Loop): NavViewModel {
            val app = ctx.applicationContext
            val start = android.location.Location("sim").apply { latitude = loop.geom[0][1]; longitude = loop.geom[0][0] }
            val lp = NavigationLocationProvider(FusedNavigationLocationProvider(app), SimulatedLocationProvider(warpFactor = 4u, initialLocation = start))
            val core = FerrostarCore(
                // Nur Platzhalter: Neuberechnung durch Ferrostar ist abgeschaltet (DoNothing), Rückweg läuft über /scenic/rejoin.
                WellKnownRouteProvider.Valhalla(graph.backend()?.config?.routeUrl ?: "http://127.0.0.1/route", "auto"),
                OkHttpClient().toOkHttpClientProvider(),
                lp,
                loopNavConfig(),
                foregroundServiceManager = null, // Vordergrunddienst = eigener RecordingService
            )
            val tts = NavSpeech(app, graph.settings.nav)
            return NavViewModel(graph, loop, core, lp, tts, app)
        }
    }
}
