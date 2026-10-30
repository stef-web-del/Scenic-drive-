package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Navigation
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.scenicroutes.app.graph
import de.scenicroutes.shared.grid.GLoop
import de.scenicroutes.shared.grid.Grid
import de.scenicroutes.shared.grid.GridIndex
import de.scenicroutes.shared.grid.PointData
import de.scenicroutes.shared.grid.Profile
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BERLIN = ZoneId.of("Europe/Berlin")
private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** „Runde ab hier“: nächster Rasterpunkt, Anfahrt + Runde, Karussell mit „Mehr Runden zeigen“, Filter, Sonnenuntergang. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbHierScreen(onBack: () -> Unit, onDetail: (Int) -> Unit, onNavigate: (Int) -> Unit,
    map: @Composable (de.scenicroutes.shared.model.Loop, Modifier) -> Unit = { l, m -> LoopMap(l, m) }, embedded: Boolean = false) {
    val ctx = LocalContext.current
    val graph = ctx.graph
    val scope = rememberCoroutineScope()
    val freeApi = remember { graph.freeLoops() }
    var idx by remember { mutableStateOf<GridIndex?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var meName by remember { mutableStateOf<String?>(null) }   // Name des gesuchten Orts bzw. "Punkt auf der Karte"
    var picking by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<PointData?>(null) }
    var locating by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(Profile.SPORTLICH) }
    var filters by remember { mutableStateOf(Grid.Filters()) }
    var pages by remember { mutableIntStateOf(1) }
    var driven by remember { mutableStateOf(emptySet<Int>()) }
    // Keine Datenbank in der Komposition auf dem Hauptthread (0.5.0 stürzte genau hier ab): im Hintergrund laden.
    LaunchedEffect(Unit) {
        val (p, d) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.local.profile to graph.local.driven() }
        profile = p; driven = d
    }
    var sel by remember { mutableStateOf<Int?>(null) }
    var reversed by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<GLoop?>(null) }
    val snack = remember { SnackbarHostState() }

    // Robust: jede Ausnahme (fehlende Berechtigung, keine Play-Dienste, Timeout) endet in einer Meldung, nie im Absturz.
    fun locate() {
        locating = true
        scope.launch {
            val loc = runCatching { de.scenicroutes.app.data.Locate.current(ctx) }.getOrNull()
            locating = false
            if (loc == null) err = "Standort nicht verfügbar – du kannst ab Miltenberg-Zentrum suchen." else { me = loc; meName = "Mein Standort" }
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.any { g -> g }) locate() else err = "Ohne Standortfreigabe geht es nicht ab deinem Standort – suche ab Miltenberg-Zentrum oder erlaube den Standort in den App-Einstellungen." }
    fun askLocation() {
        val ok = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (ok) locate()
        else runCatching { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }.onFailure { err = "Standortabfrage nicht möglich – suche ab Miltenberg-Zentrum." }
    }
    LaunchedEffect(Unit) {
        runCatching { graph.grid.index() }.onSuccess { idx = it }.onFailure { err = "Startraster nicht ladbar (kein Netz?)." }
        askLocation()
    }
    val near = remember(idx, me) { val i = idx; val m = me; if (i != null && m != null) Grid.nearest(i, m.first, m.second) else null }
    val inArea = remember(idx, me) { val i = idx; val m = me; i != null && m != null && Grid.inArea(i, m.first, m.second) }
    LaunchedEffect(near?.point?.pid) {
        val p = near?.point ?: return@LaunchedEffect
        data = null; sel = null; pages = 1
        runCatching { graph.grid.point(p.pid) }.onSuccess { data = it }.onFailure { err = "Runden für ${p.name} nicht ladbar." }
    }
    val page = remember(data, profile, filters, driven, pages) {
        data?.let { d -> Grid.shown(d, profile, filters, driven, d.loops.values.filter { it.id in driven }.map { it.g }, pages) }
    }
    val current = page?.list?.firstOrNull { it.l.id == sel }?.l ?: page?.list?.firstOrNull()?.l
    val shownLoop = current?.let { if (reversed) it.reversed() else it }

    Scaffold(
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = { if (!embedded) TopAppBar(title = { Text("Runde ab hier") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, "Zurück") } }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
      // Karte bewusst AUSSERHALB der LazyColumn: MapView in Lazy-Listen stürzt mit Compose-Prefetch ab (bekanntes Muster).
      Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        if (picking) {
            Box(Modifier.fillMaxWidth().height(Tokens.sizeMapMd)) {
                PickMap(me ?: (idx?.let { it.center[0] to it.center[1] } ?: (49.7018 to 9.2560)), me, Modifier.fillMaxSize().testTag("pickMap")) { la, lo -> me = la to lo; meName = "Punkt auf der Karte" }
                FilledTonalButton(onClick = { picking = false }, modifier = Modifier.align(Alignment.BottomEnd).padding(Tokens.spaceSm)) { Text("Fertig") }
            }
        } else if (shownLoop != null) {
            val ml = remember(shownLoop) { shownLoop.toLoop() }
            key(ml.id, reversed) { map(ml, Modifier.fillMaxWidth().height(Tokens.sizeMapSm).testTag("abHierMap")) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("abHierList"), contentPadding = PaddingValues(start = Tokens.spaceLg, end = Tokens.spaceLg, top = Tokens.spaceMd, bottom = pad.calculateBottomPadding() + Tokens.spaceXxl), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
            item {
                when {
                    locating -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(Tokens.sizeIconSm)); Spacer(Modifier.size(Tokens.spaceSm)); Text("Standort wird bestimmt …") }
                    idx == null && err == null -> Text("Startraster wird geladen …")
                    me != null && !inArea -> ElevatedCard { Column(Modifier.padding(Tokens.spaceMd)) {
                        Text("Außerhalb des Testgebiets", style = MaterialTheme.typography.titleSmall)
                        Text("Runden gibt es bisher nur rund um Miltenberg (Radius ${idx?.radius_km?.toInt() ?: 12} km). Gezeigt wird der nächstgelegene Startpunkt.", style = MaterialTheme.typography.bodySmall)
                    } }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                    OutlinedButton(onClick = { err = null; askLocation() }) { Icon(Lucide.LocateFixed, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Mein Standort") }
                    idx?.let { i -> TextButton(onClick = { err = null; me = i.center[0] to i.center[1]; meName = "Miltenberg-Zentrum" }) { Text("Ab Miltenberg-Zentrum") } }
                }
                TextButton(onClick = { picking = !picking }) { Icon(Lucide.Map, null); Spacer(Modifier.size(Tokens.spaceSm)); Text(if (picking) "Karte schließen" else "Auf Karte wählen") }
                PlaceSearch(me) { name, la, lo -> err = null; picking = false; me = la to lo; meName = name }
            }
            near?.let { n -> item {
                meName?.let { Text("Gewählt: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Start: ${n.point.name} · ${"%.1f".format(n.km)} km entfernt, Anfahrt ca. ${n.approachMin} min", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("abHierStart"))
            } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                        Profile.entries.forEach { p -> FilterChip(selected = profile == p, onClick = { profile = p; scope.launch(kotlinx.coroutines.Dispatchers.IO) { graph.local.profile = p }; pages = 1; sel = null }, label = { Text(p.label) }, modifier = Modifier.testTag("profile_${p.key}")) }
                    }
                    Text(profile.hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchRow("Schon gefahrene ausblenden", filters.hideDriven) { filters = filters.copy(hideDriven = it); pages = 1 }
                    SwitchRow("Mindestqualität lockern", filters.relaxQuality) { filters = filters.copy(relaxQuality = it); pages = 1 }
                    SwitchRow("Weniger Überschneidung mit bekannten Runden", filters.lessOverlap) { filters = filters.copy(lessOverlap = it); pages = 1 }
                }
            }
            page?.let { pg -> item {
                if (pg.list.isEmpty()) Text("Keine Runde passt zu diesen Filtern. Lockere die Mindestqualität oder wähle ein anderes Profil.", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSmd), modifier = Modifier.testTag("carousel")) {
                    items(pg.list, key = { it.l.id }) { s -> GridCard(s, s.l.id == current?.id) { sel = s.l.id; reversed = false } }
                    if (pg.more) item { OutlinedCard(onClick = { pages++ }, modifier = Modifier.width(Tokens.sizeMoreCardW).heightIn(min = Tokens.sizeCardMinH).testTag("more")) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Mehr Runden zeigen\n(${pg.total - pg.list.size} weitere)", Modifier.padding(Tokens.spaceMd)) } } }
                }
                Text("${pg.list.size} von ${pg.total} Runden", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (shownLoop != null && near != null) item {
                val loop = remember(shownLoop) { shownLoop.toLoop() }
                ElevatedCard { Column(Modifier.padding(Tokens.spaceMd), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                    Text(loop.titleShort + if (reversed) " (umgekehrt)" else "", style = MaterialTheme.typography.titleMedium)
                    val now = Instant.now(); val today = now.atZone(BERLIN).toLocalDate()
                    val end = now.plusSeconds(((2 * near.approachMin + loop.min) * 60).toLong())
                    val sunset = Grid.sunsetUtcMs(today.year, today.monthValue, today.dayOfMonth, near.point.lat, near.point.lon)?.let(Instant::ofEpochMilli)
                    Text("Anfahrt ca. ${near.approachMin} min · Runde ${fmtKm(loop.km)}, ${fmtMin(loop.min)}, ${loop.hm} Hm · Rückfahrt ca. ${near.approachMin} min", style = MaterialTheme.typography.bodyMedium)
                    Text("Zurück ca. ${HM.format(end.atZone(BERLIN))} Uhr" + (sunset?.let { " · Sonnenuntergang ${HM.format(it.atZone(BERLIN))} Uhr" } ?: ""), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("tripEnd"))
                    if (sunset != null && end.isAfter(sunset)) Text("Ende nach Sonnenuntergang – Teile der Runde im Dunkeln.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                        Button(onClick = { graph.gridLoops[loop.id] = current!!.toLoop(); onNavigate(loop.id) }, enabled = !reversed && current!!.nav, modifier = Modifier.testTag("startNav")) { Icon(Lucide.Navigation, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Navigation") }
                        OutlinedButton(onClick = { shownLoop.gurl?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }, enabled = shownLoop.gurl != null) { Text("Google Maps") }
                    }
                    if (reversed) Text("In-App-Navigation gibt es vorerst nur in Originalrichtung; umgekehrt über Google Maps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                        FilterChip(selected = reversed, onClick = { reversed = !reversed }, label = { Text("Umgekehrt fahren") }, leadingIcon = { Icon(Lucide.ArrowLeftRight, null) }, modifier = Modifier.testTag("reverse"))
                        FilterChip(selected = loop.id in driven, onClick = { val id = loop.id; val rv = reversed; scope.launch { driven = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.local.toggleDriven(id, rv); graph.local.driven() } } }, label = { Text("Schon gefahren") }, modifier = Modifier.testTag("driven"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                        TextButton(onClick = { graph.gridLoops[loop.id] = current!!.toLoop(); onDetail(loop.id) }) { Text("Details") }
                        TextButton(onClick = { report = current }) { Icon(Lucide.Flag, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Melden") }
                    }
                } }
            }
            if (me != null && !embedded) item { FreeLoopSection(freeApi, me, meName, onNavigate = onNavigate) }
            item { Text("Kartendaten © OpenStreetMap-Mitwirkende (ODbL) · Karte © OpenFreeMap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
      }
    }
    report?.let { l ->
        var kind by remember { mutableStateOf(Grid.REPORT_KINDS.first()) }
        var text by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { report = null }, title = { Text("Problem melden") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXsm)) {
                Text("Wird nur auf diesem Gerät gespeichert (noch kein Versand).", style = MaterialTheme.typography.bodySmall)
                Grid.REPORT_KINDS.forEach { k -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = kind == k, onClick = { kind = k }); Text(k) } }
                OutlinedTextField(text, { text = it.take(500) }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { report = null; scope.launch { val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.local.report(l.id, kind, text) }; snack.showSnackbar(if (ok) "Meldung lokal gespeichert" else "Meldung konnte nicht gespeichert werden") } }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { report = null }) { Text("Abbrechen") } })
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Switch(value, onChange) }

@Composable
private fun GridCard(s: Grid.Shown, selected: Boolean, onClick: () -> Unit) {
    val l = s.l
    val c = if (selected) CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.elevatedCardColors()
    ElevatedCard(onClick = onClick, colors = c, modifier = Modifier.width(Tokens.sizeCardW).heightIn(min = Tokens.sizeCardMinH).testTag("grid_${l.id}")) {
        Column(Modifier.padding(Tokens.spaceMd), verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.e.rank}. ", style = MaterialTheme.typography.titleSmall)
                Text(l.ti.substringBefore(" ("), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text("${klasseLabel(l.kl)} · ${fmtKm(l.km)} · ${fmtMin(l.min)} · ${l.hm} Hm", style = MaterialTheme.typography.bodySmall)
            if (s.driven) Text("Schon gefahren", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (s.ovKnown > 0.05) Text("${(s.ovKnown * 100).toInt()} % schon bekannt", style = MaterialTheme.typography.labelMedium)
            s.e.why?.takeIf { it.isNotEmpty() }?.let { Text("Schwächer: " + it.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}
