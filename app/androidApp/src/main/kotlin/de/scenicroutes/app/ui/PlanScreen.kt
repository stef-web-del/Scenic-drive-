package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.BookmarkCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.X
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.scenicroutes.app.data.FreeLoopApi
import de.scenicroutes.app.data.FreeLoopFiles
import de.scenicroutes.app.graph
import de.scenicroutes.shared.freeloop.FreeLoop
import de.scenicroutes.shared.freeloop.JobView
import de.scenicroutes.shared.grid.Grid
import de.scenicroutes.shared.grid.Profile
import de.scenicroutes.shared.routes.Origin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE = 6
/** Umrechnung für die km-Ansicht: Kurvenstrecken fahren sich mit etwa 70 km/h Schnitt (Server-Mittel der Runden). */
private const val KMH = 70.0

/** Ort mit Namen (Start oder Zwischenziel). */
data class Spot(val name: String, val lat: Double, val lon: Double)

/** Ein Vorschlag für die Ergebnisliste: frisch vom Server oder offline aus den fertigen Runden. */
private sealed interface Suggestion { val key: String }
private data class ServerSug(val l: FreeLoop) : Suggestion { override val key get() = "s${l.id}" }
private data class GridSug(val s: Grid.Shown) : Suggestion { override val key get() = "g${s.l.id}" }

/**
 * Tab „Planen“: Runde ab einem Startpunkt, optional über einen Ort, mit Wunschlänge und Fahrprofil.
 * Rechnet der Server, kommen bis zu 6 Vorschläge mit Fortschritt; ohne Server die besten fertigen Runden in der Nähe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onNavigate: (Int) -> Unit, onDetail: (Int) -> Unit,
               map: @Composable (Pair<Double, Double>, Pair<Double, Double>?, Modifier, (Double, Double) -> Unit) -> Unit = { c, p, m, f -> PickMap(c, p, m, f) },
               initialStart: Spot? = null) {
    val ctx = LocalContext.current
    val graph = ctx.graph
    val scope = rememberCoroutineScope()
    val api: FreeLoopApi? = remember { graph.freeLoops() }
    var start by remember { mutableStateOf(initialStart) }
    var via by remember { mutableStateOf<Spot?>(null) }
    var viaOpen by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }   // "start" | "via"
    var minutes by rememberSaveable { mutableIntStateOf(45) }
    var inKm by rememberSaveable { mutableStateOf(false) }
    var profile by remember { mutableStateOf(Profile.SPORTLICH) }
    var locating by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { profile = withContext(Dispatchers.IO) { graph.local.profile } }

    // Ergebnis
    var job by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf<JobView?>(null) }
    var limit by remember { mutableIntStateOf(PAGE) }
    var offline by remember { mutableStateOf<List<Grid.Shown>?>(null) }
    var offlineMore by remember { mutableStateOf(false) }
    var offlinePages by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf<String?>(null) }    // Key des Vorschlags, für den gerade die Navi-Datei kommt
    var msg by remember { mutableStateOf<String?>(null) }
    val ready = remember { mutableStateMapOf<Int, Int>() }   // Server-ID -> App-ID
    val book by graph.routes.book.collectAsState()

    fun locate() {
        locating = true
        scope.launch {
            val loc = runCatching { de.scenicroutes.app.data.Locate.current(ctx) }.getOrNull()
            locating = false
            if (loc == null) err = "Standort nicht verfügbar. Such einen Ort oder tipp auf die Karte." else { start = Spot("Mein Standort", loc.first, loc.second); err = null }
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.any { g -> g }) locate() else err = "Ohne Standortfreigabe: Ort suchen oder auf der Karte wählen." }
    fun askLocation() {
        val ok = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (ok) locate() else runCatching { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }.onFailure { err = "Standortabfrage nicht möglich." }
    }
    // Ist der Standort schon freigegeben, gleich als Start nehmen (fragt nicht ungefragt nach).
    LaunchedEffect(Unit) {
        if (start == null && listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) locate()
    }

    fun reset() { job?.let { j -> scope.launch { runCatching { api?.cancel(j) } } }; job = null; view = null; offline = null; limit = PAGE; offlinePages = 1; msg = null; ready.clear() }

    suspend fun loadOffline(s: Spot) {
        val r = runCatching {
            val idx = graph.grid.index()
            val near = Grid.nearest(idx, s.lat, s.lon) ?: error("kein Startpunkt")
            val d = graph.grid.point(near.point.pid)
            val driven = withContext(Dispatchers.IO) { graph.local.driven() }
            val lo = minutes - 15; val hi = minutes + 15
            val page = Grid.shown(d, profile, Grid.Filters(), driven, emptyList(), 50)
            val fit = page.list.filter { it.l.min in lo..hi }.ifEmpty { page.list }
            fit
        }
        r.onSuccess { offline = it; offlineMore = it.size > PAGE * offlinePages }.onFailure { offline = emptyList(); offlineMore = false }
    }

    fun compute() {
        val s = start ?: return
        reset()
        scope.launch {
            if (api == null) { loadOffline(s); return@launch }
            runCatching { api.start(s.lat, s.lon, s.name, minutes, profile.key, via?.let { it.lat to it.lon }) }
                .onSuccess { job = it }.onFailure { loadOffline(s) }
        }
    }
    LaunchedEffect(job, limit) {
        val j = job ?: return@LaunchedEffect
        while (true) {
            val v = try { api!!.view(j, limit) } catch (e: CancellationException) { throw e } catch (_: Exception) { job = null; start?.let { loadOffline(it) }; return@LaunchedEffect }
            view = v
            if (!v.running) break
            delay(700)
        }
    }

    suspend fun prepare(v: JobView, l: FreeLoop): Int {
        ready[l.id]?.let { return it }
        val a = api!!
        val id = FreeLoopFiles.appId(v.id, l.id)
        FreeLoopFiles.save(ctx, id, a.nav(v.id, l.id))
        val loop = FreeLoopFiles.toLoop(id, l, a.geometry(v.id, l.id), start?.name ?: "hier")
        graph.gridLoops[id] = loop
        withContext(Dispatchers.IO) {
            graph.routes.keep(loop.copy(title = "Runde ab ${start?.name ?: "hier"}" + (l.einstieg.takeIf { it.isNotBlank() }?.let { " über $it" } ?: "")), Origin.SERVER, "server:${v.id}:${l.id}") {
                it.copy(approachMin = l.approachMin.toInt(), profile = profile.key, text = l.text,
                    waypoints = listOfNotNull(start?.let { s -> listOf(s.lon, s.lat) }, via?.let { w -> listOf(w.lon, w.lat) }))
            }
        }
        ready[l.id] = id
        return id
    }
    fun act(v: JobView, l: FreeLoop, go: Boolean) {
        busy = "s${l.id}"; msg = null
        scope.launch {
            runCatching { prepare(v, l) }
                .onSuccess { id -> msg = if (go) null else "Unter „Meine Routen“ gespeichert."; if (go) onNavigate(id) }
                .onFailure { msg = "Navi-Datei nicht verfügbar: ${it.message}" }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.spaceLg).padding(bottom = Tokens.spaceXxl).testTag("plan"),
        verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
        Spacer(Modifier.height(Tokens.spaceXs))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = true, onClick = {}, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Lucide.Repeat, null, Modifier.size(Tokens.sizeIconSm)) }) { Text("Runde") }
            SegmentedButton(selected = false, onClick = {}, enabled = false, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("modeAB")) { Text("A → B · kommt bald", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }

        // Start
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
            Text("Start", style = MaterialTheme.typography.titleSmall)
            val s = start
            if (s != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.CircleDot, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(Tokens.spaceSm))
                Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag("planStart"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (locating) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(Tokens.sizeIconSm)); Spacer(Modifier.size(Tokens.spaceSm)); Text("Standort wird bestimmt …") }
            else Text("Wo soll die Runde starten?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                AssistChip(onClick = { err = null; askLocation() }, label = { Text("Mein Standort") }, leadingIcon = { Icon(Lucide.LocateFixed, null, Modifier.size(Tokens.sizeIconSm)) }, modifier = Modifier.testTag("planLocate"))
                AssistChip(onClick = { picking = if (picking == "start") null else "start" }, label = { Text(if (picking == "start") "Karte schließen" else "Auf Karte wählen") }, leadingIcon = { Icon(Lucide.Map, null, Modifier.size(Tokens.sizeIconSm)) })
            }
            if (picking == "start") Box(Modifier.fillMaxWidth().height(Tokens.sizeMapPicker)) {
                map(start?.let { it.lat to it.lon } ?: (49.7018 to 9.2560), start?.let { it.lat to it.lon }, Modifier.fillMaxSize().testTag("planMap")) { la, lo -> start = Spot("Punkt auf der Karte", la, lo); reset() }
                FilledTonalButton(onClick = { picking = null }, modifier = Modifier.align(Alignment.BottomEnd).padding(Tokens.spaceSm)) { Text("Fertig") }
            }
            PlaceSearch(start?.let { it.lat to it.lon }) { name, la, lo -> start = Spot(name, la, lo); picking = null; err = null; reset() }

            // Zwischenziel
            val w = via
            when {
                w != null -> InputChip(selected = true, onClick = { via = null; reset() }, label = { Text("Über ${w.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Lucide.MapPin, null, Modifier.size(Tokens.sizeIconSm)) }, trailingIcon = { Icon(Lucide.X, "Entfernen", Modifier.size(Tokens.sizeIconSm)) }, modifier = Modifier.testTag("viaChip"))
                !viaOpen -> TextButton(onClick = { viaOpen = true }, contentPadding = PaddingValues(Tokens.spaceNone), modifier = Modifier.testTag("viaAdd")) { Icon(Lucide.Plus, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Über einen Ort") }
                else -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
                    Text("Die Runde führt höchstens 2 km an diesem Ort vorbei.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PlaceSearch(start?.let { it.lat to it.lon }) { name, la, lo -> via = Spot(name, la, lo); viaOpen = false; reset() }
                    TextButton(onClick = { viaOpen = false }) { Text("Ohne Zwischenziel") }
                }
            }
        } }

        // Länge
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Länge der Runde", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(selected = !inKm, onClick = { inKm = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("min") }
                    SegmentedButton(selected = inKm, onClick = { inKm = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("unitKm")) { Text("km") }
                }
            }
            val km = (minutes * KMH / 60).toInt()
            Text(if (inKm) "etwa $km km" else "etwa $minutes min", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("planLength"))
            Slider(value = minutes.toFloat(), onValueChange = { minutes = (it / 5).toInt() * 5 }, valueRange = 30f..90f, steps = 11, modifier = Modifier.testTag("planSlider"))
            Text(if (inKm) "Ungefähr ${minutes} min reine Fahrzeit auf der Runde. Anfahrt und Rückweg kommen dazu." else "Ungefähr $km km. Anfahrt und Rückweg kommen dazu.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }

        // Profil
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
            Text("Fahrstil", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                Profile.entries.forEach { p -> FilterChip(selected = profile == p, onClick = { profile = p; scope.launch(Dispatchers.IO) { graph.local.profile = p } },
                    label = { Text(p.label) }, modifier = Modifier.testTag("plan_${p.key}")) }
            }
            Text(profile.hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val v = view
        val running = v?.running == true
        Button(onClick = { compute() }, enabled = start != null && !running, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).testTag("planGo")) {
            Icon(Lucide.Sparkles, null); Spacer(Modifier.size(Tokens.spaceSm)); Text(if (v != null || offline != null) "Neu berechnen" else "Vorschläge berechnen")
        }
        if (start == null) Text("Erst einen Start wählen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Fortschritt
        if (v != null && running) Column(Modifier.testTag("planProgress"), verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
            LinearProgressIndicator(progress = { v.fraction }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${v.progress.phase.ifBlank { "Suche" }} · ${v.count} gefunden", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { val j = job; scope.launch { if (j != null) { runCatching { api?.cancel(j) }; view = runCatching { api?.view(j, limit) }.getOrNull() ?: view; job = null } } }, modifier = Modifier.testTag("planCancel")) { Text("Abbrechen") }
            }
        } else if (v != null) Text(when { v.status == "cancelled" -> "Abgebrochen. ${v.count} Runden gefunden."; v.error != null -> "Fehler beim Rechnen: ${v.error}"
            v.count == 0 -> "Keine passende Runde gefunden. Probier eine andere Länge oder lass das Zwischenziel weg."; else -> "${v.count} Runden gefunden." },
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("planStatus"))

        offline?.let { list ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(Tokens.spaceMd), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.CloudOff, null); Spacer(Modifier.width(Tokens.spaceSm))
                    Text(if (api == null) "Kein Server eingestellt. Hier sind die besten fertigen Runden in der Nähe." else "Server nicht erreichbar. Hier sind die besten fertigen Runden in der Nähe.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("planOffline"))
                }
            }
            if (list.isEmpty()) Text("Auch offline keine Runde gefunden (Startraster nicht geladen?).", style = MaterialTheme.typography.bodySmall)
        }

        val sugs: List<Suggestion> = v?.loops?.map { ServerSug(it) } ?: offline?.take(PAGE * offlinePages)?.map { GridSug(it) } ?: emptyList()
        sugs.forEachIndexed { i, s ->
            when (s) {
                is ServerSug -> SuggestionCard(i + 1, s.l.einstieg.ifBlank { "ab ${start?.name ?: "Start"}" }, "${s.l.loopMin} min · ${s.l.km.toInt()} km · ${s.l.hm} Hm",
                    approach = if (s.l.approachMin < 1) "Start direkt an der Runde" else "Anfahrt ${s.l.approachMin.toInt()} min, Rückweg ${s.l.returnMin.toInt()} min",
                    why = why(s.l, profile, via), busy = busy == s.key, saved = book.byParent("server:${v!!.id}:${s.l.id}") != null,
                    onGo = { act(v!!, s.l, true) }, onSave = { act(v!!, s.l, false) }, testTag = "sug${i + 1}")
                is GridSug -> SuggestionCard(i + 1, s.s.l.ti.substringBefore(" ("), "${s.s.l.min} min · ${s.s.l.km.toInt()} km · ${s.s.l.hm} Hm",
                    approach = "Fertige Runde ${klasseLabel(s.s.l.kl)}", why = whyGrid(s.s, profile), busy = false, saved = book.byParent("raster:${s.s.l.id}") != null,
                    onGo = { graph.gridLoops[s.s.l.id] = s.s.l.toLoop(); if (s.s.l.nav) onNavigate(s.s.l.id) else onDetail(s.s.l.id) },
                    onSave = { val l = s.s.l.toLoop(); graph.gridLoops[l.id] = l; scope.launch(Dispatchers.IO) { graph.routes.keep(l, Origin.KATALOG, "raster:${l.id}") }; msg = "Unter „Meine Routen“ gespeichert." },
                    testTag = "sug${i + 1}")
            }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("planMsg")) }
        if ((v != null && v.more) || (offline != null && offlineMore)) OutlinedButton(onClick = {
            if (v != null) limit += PAGE else { offlinePages++; offlineMore = (offline?.size ?: 0) > PAGE * offlinePages }
        }, modifier = Modifier.fillMaxWidth().testTag("planMore")) { Text("Mehr anzeigen") }
        Text("Kartendaten © OpenStreetMap-Mitwirkende (ODbL)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ein Satz, warum die Runde passt. Vorrang hat der Text vom Server. */
private fun why(l: FreeLoop, p: Profile, via: Spot?): String {
    val base = l.text.takeIf { it.isNotBlank() }?.let { it.substringBefore(". ").trimEnd('.') + "." }
    val viaS = if (via != null && l.viaM != null) " Kommt bis auf ${if (l.viaM!! < 1000) "${l.viaM} m" else "%.1f km".format(l.viaM!! / 1000.0)} an ${via.name} heran." else ""
    return (base ?: when (p) {
        Profile.SPORTLICH -> "Viele Kurven in gutem Rhythmus" + (l.tags.firstOrNull()?.let { ", $it" } ?: "") + "."
        Profile.GENIESSER -> "Ruhige Strecke mit Aussicht, kaum Ortsdurchfahrten."
        Profile.ENTDECKER -> "Abwechslungsreich mit ${l.hm} Höhenmetern."
    }) + viaS
}
private fun whyGrid(s: Grid.Shown, p: Profile): String {
    val t = s.l.tags.take(2).joinToString(" und ")
    return when (p) {
        Profile.SPORTLICH -> "Fertige Runde mit gutem Kurvenwert" + (if (t.isNotEmpty()) ", $t" else "") + "."
        Profile.GENIESSER -> "Fertige Runde, ruhig und mit wenig Orten" + (if (t.isNotEmpty()) ", $t" else "") + "."
        Profile.ENTDECKER -> "Fertige Runde mit ${s.l.hm} Höhenmetern" + (if (t.isNotEmpty()) ", $t" else "") + "."
    }
}

@Composable
private fun SuggestionCard(rank: Int, title: String, facts: String, approach: String, why: String, busy: Boolean, saved: Boolean,
                           onGo: () -> Unit, onSave: () -> Unit, testTag: String) {
    ElevatedCard(Modifier.fillMaxWidth().testTag(testTag)) {
        Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceXsm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) { Text("$rank", Modifier.padding(horizontal = Tokens.spaceSm, vertical = Tokens.spaceXxs), style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.size(Tokens.spaceSm))
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(facts, style = MaterialTheme.typography.bodyMedium)
            Text(approach, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onGo, enabled = !busy, modifier = Modifier.testTag("${testTag}Go")) { Icon(Lucide.Navigation, null, Modifier.size(Tokens.sizeIconSm)); Spacer(Modifier.size(Tokens.spaceSm)); Text("Losfahren") }
                OutlinedButton(onClick = onSave, enabled = !busy && !saved, modifier = Modifier.testTag("${testTag}Save")) {
                    Icon(if (saved) Lucide.BookmarkCheck else Lucide.Bookmark, null, Modifier.size(Tokens.sizeIconSm)); Spacer(Modifier.size(Tokens.spaceSm)); Text(if (saved) "Gespeichert" else "Speichern")
                }
                if (busy) CircularProgressIndicator(Modifier.size(Tokens.sizeIconMd))
            }
        }
    }
}
