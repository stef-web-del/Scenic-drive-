package de.scenicroutes.app.nav

import androidx.compose.foundation.layout.heightIn
import de.scenicroutes.app.ui.Tokens
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ArrowUpLeft
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.CornerDownLeft
import com.composables.icons.lucide.CornerDownRight
import com.composables.icons.lucide.CornerUpLeft
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Undo2
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stadiamaps.ferrostar.composeui.config.NavigationViewComponentBuilder
import com.stadiamaps.ferrostar.composeui.config.withCustomOverlayView
import com.stadiamaps.ferrostar.composeui.runtime.KeepScreenOnDisposableEffect
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationActivity
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import de.scenicroutes.app.data.MapOrientation
import de.scenicroutes.app.data.NavPrefs
import de.scenicroutes.app.graph
import de.scenicroutes.app.rec.RecordingService
import de.scenicroutes.app.ui.fmtKm
import de.scenicroutes.app.ui.mapStyle
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.nav.OffRoutePolicy
import org.maplibre.compose.style.BaseStyle

@Composable
fun NavigationRoute(loop: Loop, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val vm: NavViewModel = viewModel(key = "nav-${loop.id}", factory = viewModelFactory { initializer { NavViewModel.create(ctx, ctx.graph, loop) } })
    val extras by vm.extras.collectAsState()
    val saves by ctx.graph.saver.states.collectAsState()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[Manifest.permission.ACCESS_FINE_LOCATION] == true }
    val perms = buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray()

    fun startAll() { vm.start() }
    // Aufzeichnung startet automatisch mit der Runde oder der In-App-Anfahrt (verknüpft mit der Runde).
    val guiding = extras.phase == EntryPhase.GUIDE
    LaunchedEffect(extras.started, guiding) {
        if ((extras.started || guiding) && granted && ctx.graph.settings.record) RecordingService.start(ctx, loop.id, loop.titleShort)
    }
    // Zielführung hält immer einen Vordergrunddienst (bei Aufzeichnung derselbe Dienst): GPS + Ansagen auch bei Bildschirm aus.
    LaunchedEffect(extras.started || guiding, granted) {
        if ((extras.started || guiding) && granted) RecordingService.startNav(ctx, loop.titleShort) else RecordingService.endNav(ctx)
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.onResume() }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val actions = EntryActions(
        server = vm::approachViaServer,
        google = { vm.googleIntent()?.let { i -> runCatching { ctx.startActivity(i) }.onFailure { vm.googleWebIntent()?.let { w -> runCatching { ctx.startActivity(w) } } } } },
        guide = vm::guide, official = vm::startOfficial, startHere = vm::startAtEntry, cancel = vm::cancelEntry,
    )
    // Ende/Abbruch: Navigation stoppen, dann „Fahrt speichern?“ (app-weites Sheet) – nichts geht verloren.
    fun stopAll() { vm.stop(); if (ctx.graph.recorder.state.value?.loopId == loop.id) ctx.graph.recorder.requestStop(); onExit() }

    // „Beenden“ in der Benachrichtigung (Modus nur Navigation) → Zielführung hier sauber stoppen.
    val navSession by ctx.graph.navSession.collectAsState()
    LaunchedEffect(navSession) { if (navSession == null && (extras.started || guiding)) stopAll() }
    // A10: System-Zurück im Fahrmodus fragt nach, statt die Zielführung still zu beenden.
    DriveBackGuard(active = extras.started || guiding, onStop = ::stopAll)
    // Fahrmodus: hell/dunkel nach Sonnenstand am Start der Runde, jede Minute neu geprüft.
    val c0 = loop.coords.firstOrNull()
    var night by remember { mutableStateOf(c0 != null && de.scenicroutes.shared.nav.Sun.isDark(c0.lat, c0.lon, System.currentTimeMillis())) }
    LaunchedEffect(c0) { while (c0 != null) { kotlinx.coroutines.delay(60_000); night = de.scenicroutes.shared.nav.Sun.isDark(c0.lat, c0.lon, System.currentTimeMillis()) } }
    // Navi-Einstellungen (Einstellungen → Navigation); Standard = Verhalten von 0.6.0.
    val settings = ctx.graph.settings
    val prefs by settings.navFlow.collectAsState()
    // Bildschirm nur im Fahrmodus anlassen (Einstellung „Bildschirm anlassen“, Standard an).
    if (NavPrefs.keepAwake(prefs, extras.started || guiding)) KeepScreenOnDisposableEffect()
    val camBase = navigationCameraOptions(NavigationActivity.Automotive)
    val follow = if (prefs.orientation == MapOrientation.NORTH) NavigationCameraMode.FOLLOW_USER else NavigationCameraMode.FOLLOW_USER_WITH_BEARING
    val mapState = rememberNavigationMapState(follow, camBase)
    val ui by vm.navigationUiState.collectAsState()
    val speed = ui.location?.speed?.value
    val zoomDelta = NavPrefs.zoomDelta(prefs.zoom, speed)
    LaunchedEffect(zoomDelta, camBase) { mapState.navigationCameraOptions = camBase.copy(navigationZoom = camBase.navigationZoom + zoomDelta) }
    // Ausrichtung umschalten, ohne die Übersicht oder freies Verschieben zu überschreiben.
    LaunchedEffect(follow) { if (mapState.cameraMode.tracksLocation()) mapState.cameraMode = follow }
    val overview = mapState.cameraMode == NavigationCameraMode.OVERVIEW
    val track = remember(loop.id) { de.scenicroutes.shared.nav.LoopTrack(loop.coords, loop.prof, loop.profStep) }
    val loopState = remember(loop.id) { LoopState(track) }
    val gState = rememberGMeter(prefs.showG, speed)
    de.scenicroutes.app.ui.ScenicTheme(dark = NavPrefs.dark(prefs.theme, night)) {
    if (extras.wrongWay) androidx.compose.material3.AlertDialog(
        onDismissRequest = vm::dismissWrongWay,
        icon = { Icon(Lucide.Undo2, null) },
        title = { Text("Falsche Richtung") },
        text = { Text("Du fährst gegen die Richtung der Runde. Bitte wenden – oder die Runde ab hier in Gegenrichtung fahren.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = vm::dismissWrongWay, modifier = Modifier.testTag("wrongWayTurn")) { Text("Ich wende") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = vm::restartReversed, enabled = !extras.busy, modifier = Modifier.testTag("wrongWayReverse")) { Text("Runde in Gegenrichtung") } },
    )
    DynamicallyOrientingNavigationView(
        modifier = Modifier.fillMaxSize(),
        baseStyle = BaseStyle.Uri(mapStyle()),
        navigationMapState = mapState,
        navigationCameraOptions = mapState.navigationCameraOptions,
        viewModel = vm,
        views = NavigationViewComponentBuilder.Default().copy(
            instructionsView = { m, ui -> ui.visualInstruction?.let { vi ->
                val (d, u) = de.scenicroutes.app.ui.driveDistance(ui.progress?.distanceToNextManeuver ?: vi.triggerDistanceBeforeManeuver)
                de.scenicroutes.app.ui.DriveTurnCard(d, u, vi.primaryContent.text, vi.secondaryContent?.text, maneuverIcon(vi.primaryContent.maneuverModifier), m)
            } },
            progressView = { m, ui, exit -> ui.progress?.let { p ->
                val eta = java.time.LocalTime.now().plusSeconds(p.durationRemaining.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                val kmh = ui.location?.speed?.value?.let { (it * 3.6).toInt() }
                val g0 = ui.routeGeometry
                val total = remember(g0) { g0?.zipWithNext { a, b -> de.scenicroutes.app.data.geoDistanceM(a.lat, a.lng, b.lat, b.lng) }?.sum() }
                val curves by vm.curves.collectAsState()
                val done = total?.let { (it - p.distanceRemaining).coerceAtLeast(0.0) }
                // Lage auf der Runde selbst (nicht auf der Anfahrt): Fortschritt, Höhe, Höhenmeter
                val here = ui.location?.let { de.scenicroutes.shared.model.LatLon(it.coordinates.lat, it.coordinates.lng) }
                val onLoop = loopState.update(here)
                val prog = if (total == null) null else track.progress(onLoop)
                val elev = onLoop?.let { track.elevationAt(it) }?.let { Math.round(it).toInt() }
                val asc = onLoop?.let { m -> loopState.startM?.let { track.ascent(it, m) } }?.let { Math.round(it).toInt() }
                val data = de.scenicroutes.app.data.DriveData(eta, p.distanceRemaining / 1000, p.durationRemaining, kmh, prog,
                    curves = done?.let { d -> curves?.let { c -> de.scenicroutes.shared.nav.Curves.remaining(c, d) } },
                    hairpins = done?.let { d -> curves?.count { it.hairpin && it.atM >= d } },
                    elevationM = elev, ascentM = asc, g = gState.value?.first, gMax = gState.value?.second)
                de.scenicroutes.app.ui.DriveBar(NavPrefs.kpis(prefs, data), progress = prog,
                    onStop = exit ?: {}, modifier = m,
                    onMute = { settings.updateNav { it.copy(voiceOn = !it.voiceOn) } }, muted = !prefs.voiceOn,
                    onOverview = { val g = ui.routeGeometry
                        if (overview || g.isNullOrEmpty()) mapState.cameraMode = follow
                        else mapState.showRouteOverview(com.stadiamaps.ferrostar.core.BoundingBox(g.maxOf { it.lat }, g.maxOf { it.lng }, g.minOf { it.lat }, g.minOf { it.lng })) }, overview = overview)
            } },
        ).withCustomOverlayView { m ->
            NavOverlay(m, loop, extras, granted, onRequestPermission = { launcher.launch(perms) }, onSimulate = vm::setSimulate,
                onStart = ::startAll, onClosure = vm::reportClosure, onContinueHere = vm::continueHere, onPause = vm::pauseGuidance, onBack = ::stopAll, onMessageShown = vm::consumeMessage, saved = saves[loop.id] is de.scenicroutes.app.data.SaveState.Saved, entry = actions)
        },
        onTapExit = ::stopAll,
    )
    }
}

private fun maneuverIcon(mod: uniffi.ferrostar.ManeuverModifier?) = when (mod) {
    uniffi.ferrostar.ManeuverModifier.LEFT -> Lucide.CornerUpLeft
    uniffi.ferrostar.ManeuverModifier.SHARP_LEFT -> Lucide.CornerDownLeft
    uniffi.ferrostar.ManeuverModifier.SLIGHT_LEFT -> Lucide.ArrowUpLeft
    uniffi.ferrostar.ManeuverModifier.RIGHT -> Lucide.CornerUpRight
    uniffi.ferrostar.ManeuverModifier.SHARP_RIGHT -> Lucide.CornerDownRight
    uniffi.ferrostar.ManeuverModifier.SLIGHT_RIGHT -> Lucide.ArrowUpRight
    uniffi.ferrostar.ManeuverModifier.U_TURN -> Lucide.Undo2
    else -> Lucide.ArrowUp
}

@Composable
fun NavOverlay(
    modifier: Modifier, loop: Loop, extras: NavExtras, granted: Boolean,
    onRequestPermission: () -> Unit, onSimulate: (Boolean) -> Unit, onStart: () -> Unit, onClosure: () -> Unit, onBack: () -> Unit, onMessageShown: () -> Unit, saved: Boolean = false, entry: EntryActions = EntryActions(),
    onContinueHere: () -> Unit = {}, onPause: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        if (!extras.started) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(Tokens.spaceMd).align(Alignment.TopStart)) { Icon(Lucide.ArrowLeft, "Zurück") }
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(Tokens.spaceMd).testTag("startCard")) {
              val e = extras.entry
              if (e != null && extras.phase in setOf(EntryPhase.CHOOSE, EntryPhase.GUIDE, EntryPhase.EXTERNAL)) {
                EntryCard(loop.coords, e, extras.phase, extras.busy, extras.hasBackend, entry)
              } else {
                Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                    Text(loop.titleShort, style = MaterialTheme.typography.titleLarge)
                    Text("${fmtKm(loop.km)} · feste Runde · Start und Ziel Miltenberg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (if (saved) "Offline gespeichert: Route und Karte auf dem Gerät." else "Vorberechnete Route (deutsch, ${loop.steps} Anweisungen).") +
                            (if (extras.hasBackend) "" else " Ohne Navigations-Server: Rückweg und „Gesperrt“ nicht verfügbar."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("navInfo"))
                    if (!granted) {
                        ListItem(leadingContent = { Icon(Lucide.MapPin, null) },
                            headlineContent = { Text("Standort für Navigation") },
                            supportingContent = { Text("Wird nur während der Fahrt genutzt und lokal gespeichert (Fahrtenbuch).") },
                            trailingContent = { OutlinedButton(onClick = onRequestPermission, modifier = Modifier.testTag("grant")) { Text("Erlauben") } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fahrt simulieren", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = extras.simulate, onCheckedChange = onSimulate, modifier = Modifier.testTag("simulate"))
                    }
                    Button(onClick = onStart, enabled = (granted || extras.simulate) && !extras.busy, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchDrive).testTag("go")) {
                        if (extras.busy) CircularProgressIndicator(Modifier.size(Tokens.sizeIconMd), strokeWidth = Tokens.strokeThin, color = MaterialTheme.colorScheme.onPrimary) else Icon(Lucide.Navigation, null)
                        Spacer(Modifier.size(Tokens.spaceSm)); Text("Navigation starten")
                    }
                }
            }
            }
        } else if (extras.arrived) {
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(Tokens.spaceMd).testTag("arrivedCard")) {
                Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                    Text("Runde geschafft", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("finish")) { Text("Beenden") }
                }
            }
        } else if (extras.offMode == OffRoutePolicy.Mode.ASK || extras.offMode == OffRoutePolicy.Mode.PAUSED) {
            // Rückweg wurde wiederholt nicht genommen: nicht weiter „Bitte wenden“, sondern einmal fragen.
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(Tokens.spaceMd).testTag("offRouteCard")) {
                Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                    if (extras.offMode == OffRoutePolicy.Mode.PAUSED) {
                        Text("Navigation pausiert", style = MaterialTheme.typography.titleMedium)
                        Text("Aufzeichnung läuft weiter. Sobald du wieder auf der Runde bist, geht es automatisch weiter.", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                            Button(onClick = onContinueHere, enabled = !extras.busy, modifier = Modifier.weight(1f).testTag("continueHere")) { Text("Runde hier fortsetzen") }
                            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("end")) { Text("Beenden") }
                        }
                    } else {
                        Text("Eigener Weg?", style = MaterialTheme.typography.titleMedium)
                        Text("Du bist dem Rückweg zur Runde nicht gefolgt.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onContinueHere, enabled = !extras.busy, modifier = Modifier.fillMaxWidth().testTag("continueHere")) { Text("Runde hier fortsetzen") }
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).testTag("pause")) { Text("Navigation pausieren") }
                            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("end")) { Text("Beenden") }
                        }
                    }
                }
            }
        } else if (extras.hasBackend) {
            ExtendedFloatingActionButton(
                onClick = onClosure,
                icon = { if (extras.busy) CircularProgressIndicator(Modifier.size(Tokens.sizeIconLg), strokeWidth = Tokens.strokeBold) else Icon(Lucide.Ban, null) },
                text = { Text(if (extras.blockedCount > 0) "Gesperrt (${extras.blockedCount})" else "Gesperrt") },
                containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = Tokens.spaceMd).testTag("closure"),
            )
        }
        extras.message?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); onMessageShown() }
            Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = Tokens.spaceLg, end = Tokens.spaceLg, bottom = Tokens.sizeHudClearance).testTag("navMessage")) { Text(msg) }
        }
    }
}

/** Merkt sich die Lage auf der Runde zwischen zwei Positionen (Suchfenster) und wo die Runde begonnen wurde. */
private class LoopState(private val track: de.scenicroutes.shared.nav.LoopTrack) {
    var lastM: Double? = null; private set
    var startM: Double? = null; private set
    fun update(p: de.scenicroutes.shared.model.LatLon?): Double? {
        if (p == null) return lastM
        // erst im Fenster um die letzte Lage suchen; nach Umweg/Wiedereinstieg auf der ganzen Runde, dann strenger (30 m)
        val m = track.locate(p, lastM) ?: lastM?.let { track.locate(p, null, maxOffM = 30.0) }
        if (m != null) { lastM = m; if (startM == null) startM = m }
        return m
    }
}

/**
 * g-Werte (0.6.6): Sekundenwerte aus [VehicleFrame] (Quer-g aus Gyro × Tempo, Längs-g erst nach Achsen-Kalibrierung)
 * statt ungefilterter Rohbeschleunigung. Maximum = über ≥ 2 aufeinanderfolgende Sekunden gehaltener Wert.
 * Paar (aktuell, Maximum) oder null ohne Sensor.
 */
@Composable
private fun rememberGMeter(on: Boolean, speedMps: Double?): androidx.compose.runtime.State<Pair<Double, Double?>?> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val out = remember { mutableStateOf<Pair<Double, Double?>?>(null) }
    val speed = rememberUpdatedState(speedMps)
    androidx.compose.runtime.DisposableEffect(on) {
        val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val acc = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (!on || acc == null) { out.value = null; return@DisposableEffect onDispose { } }
        val frame = de.scenicroutes.shared.trip.VehicleFrame(); val hold = de.scenicroutes.shared.trip.GHold()
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) = synchronized(frame) {
                val v = e.values
                if (e.sensor.type == android.hardware.Sensor.TYPE_ACCELEROMETER) frame.onAccel(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), e.timestamp)
                else frame.onGyro(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        listOfNotNull(acc, sm.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)).forEach { sm.registerListener(l, it, 20_000) }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        var lastV: Double? = null
        val tick = object : Runnable {
            override fun run() {
                val v = speed.value; val dvdt = if (v != null && lastV != null) v - lastV!! else null; lastV = v
                val s = synchronized(frame) { frame.flush(v, dvdt) }
                val g = s.latG?.let { kotlin.math.hypot(it, s.lonG ?: 0.0) }
                if ((v ?: 0.0) >= 5 / 3.6) hold.add(g) else hold.add(null)
                out.value = g?.let { it to hold.max }
                h.postDelayed(this, 1000)
            }
        }
        h.postDelayed(tick, 1000)
        onDispose { h.removeCallbacks(tick); sm.unregisterListener(l) }
    }
    return out
}
