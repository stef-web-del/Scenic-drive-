package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Trash2
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.scenicroutes.app.graph
import de.scenicroutes.app.rec.fmtDur
import de.scenicroutes.app.rec.fmtKm1
import de.scenicroutes.shared.db.Trip
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.trip.Csv
import de.scenicroutes.shared.trip.Gpx
import de.scenicroutes.shared.trip.RideMath
import de.scenicroutes.shared.trip.RideSample
import de.scenicroutes.shared.trip.RideSummary
import de.scenicroutes.shared.trip.TrackPoint
import de.scenicroutes.shared.trip.TripMath
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

enum class ColorBy(val label: String) { SPEED("Tempo"), LAT("Quer-g") }

data class RideData(val trip: Trip, val pts: List<TrackPoint>, val samples: List<RideSample>, val sum: RideSummary, val coverage: BooleanArray?, val loopTitle: String?)

fun loadRide(ctx: Context, id: Long): RideData? {
    val g = ctx.graph; val t = g.trips.trip(id) ?: return null
    val raw = g.trips.points(id); val pts = TripMath.clean(raw)
    val loop = t.loopId?.let { g.loop(it.toInt()) }
    return RideData(t, pts, RideMath.samples(raw), RideMath.summary(raw), loop?.let { TripMath.coverage(Polyline(it.coords), raw) }, loop?.titleShort)
}

/** Farbskala: Tempo 0–130 km/h bzw. |Quer-g| 0–0,6 (Blau → Gold → Rot aus den Tokens), neutral, ohne Wertung. */
fun rampColor(x: Double): Color {
    val t = x.coerceIn(0.0, 1.0).toFloat()
    val stops = Tokens.speedStops
    val f = t * (stops.size - 1); val i = f.toInt().coerceAtMost(stops.size - 2); val r = f - i
    val a = stops[i]; val b = stops[i + 1]
    return Color(a.red + (b.red - a.red) * r, a.green + (b.green - a.green) * r, a.blue + (b.blue - a.blue) * r)
}
fun sampleColor(s: RideSample, by: ColorBy) = if (by == ColorBy.SPEED) rampColor(s.kmh / 130) else rampColor(abs(s.latG) / 0.6)

private fun hexC(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

/** GeoJSON: Abschnitte gleicher Farbstufe zusammengefasst (wenige Features). */
fun trackGeoJson(pts: List<TrackPoint>, sm: List<RideSample>, by: ColorBy): String {
    if (pts.size < 2) return """{"type":"FeatureCollection","features":[]}"""
    val fs = mutableListOf<String>(); var cur = mutableListOf(0); var bucket = -1
    fun key(i: Int) = ((if (by == ColorBy.SPEED) sm[i].kmh / 130 else abs(sm[i].latG) / 0.6).coerceIn(0.0, 1.0) * 8).toInt()
    fun flush() { if (cur.size >= 2) fs += """{"type":"Feature","properties":{"c":"${hexC(rampColor(bucket / 8.0 + 0.06))}"},"geometry":{"type":"LineString","coordinates":[${cur.joinToString(",") { "[${pts[it].lon},${pts[it].lat}]" }}]}}""" }
    for (i in 1 until pts.size) {
        if (pts[i].seg != pts[i - 1].seg) { flush(); cur = mutableListOf(i); bucket = -1; continue }
        val k = key(i); if (bucket == -1) bucket = k
        cur.add(i)
        if (k != bucket) { flush(); cur = mutableListOf(i); bucket = k }
    }
    flush()
    return """{"type":"FeatureCollection","features":[${fs.joinToString(",")}]}"""
}

@Composable
fun RideMap(pts: List<TrackPoint>, sm: List<RideSample>, by: ColorBy, sel: Int?, modifier: Modifier) {
    val cam = remember(pts.size) {
        val lons = pts.map { it.lon }; val lats = pts.map { it.lat }; val cLat = (lats.min() + lats.max()) / 2
        val span = max((lons.max() - lons.min()) * cos(Math.toRadians(cLat)), (lats.max() - lats.min()) * 1.6)
        CameraPosition(target = Position((lons.min() + lons.max()) / 2, cLat), zoom = (ln(360.0 / max(span, 0.005)) / ln(2.0)) - 1.3)
    }
    val camera = rememberCameraState(firstPosition = cam)
    val gj = remember(pts.size, by) { trackGeoJson(pts, sm, by) }
    val marker = sel?.let { pts.getOrNull(it) }?.let { """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${it.lon},${it.lat}]}}""" } ?: """{"type":"FeatureCollection","features":[]}"""
    val mode = LocalMapMode.current
    androidx.compose.runtime.LaunchedEffect(mode.fullscreen) { if (mode.fullscreen) fitRoute(camera, pts.map { it.lon }, pts.map { it.lat }) }
    androidx.compose.runtime.LaunchedEffect(mode.meTick) { mode.me?.let { if (mode.meTick > 0) flyTo(camera, it) } }
    val mc = LocalMapColors.current; val fg = MaterialTheme.colorScheme.onSurface
    MaplibreMap(modifier = modifier, baseStyle = BaseStyle.Uri(mapStyle()), cameraState = camera, options = mapOptions(mode)) {
        val src = rememberGeoJsonSource(GeoJsonData.JsonString(gj))
        LineLayer(id = "trk-casing", source = src, color = const(mc.routeCasing), width = const(Tokens.sizeMapLineCasing), cap = const(LineCap.Round), join = const(LineJoin.Round))
        LineLayer(id = "trk", source = src, color = feature["c"].convertToColor(), width = const(Tokens.sizeMapLine), cap = const(LineCap.Round), join = const(LineJoin.Round))
        val m = rememberGeoJsonSource(GeoJsonData.JsonString(marker))
        CircleLayer(id = "sel", source = m, color = const(fg), radius = const(Tokens.sizeMapPointSel), strokeColor = const(mc.land), strokeWidth = const(Tokens.strokeBold))
        mode.me?.let { me ->
            val ms = rememberGeoJsonSource(GeoJsonData.JsonString("""{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${me.second},${me.first}]}}"""))
            CircleLayer(id = "me", source = ms, color = const(MaterialTheme.colorScheme.primary), radius = const(Tokens.sizeMapPointPick), strokeColor = const(MaterialTheme.colorScheme.onPrimary), strokeWidth = const(Tokens.strokeBold))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    id: Long, onBack: () -> Unit, onOpenLoop: (Int) -> Unit = {},
    map: @Composable (RideData, ColorBy, Int?, Modifier) -> Unit = { d, by, sel, m -> RideMap(d.pts, d.samples, by, sel, m) },
) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val data by produceState<RideData?>(null, id, reload) { value = withContext(Dispatchers.IO) { loadRide(ctx, id) } }
    var by by remember { mutableStateOf(ColorBy.SPEED) }
    var sel by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val d = data
    Scaffold(topBar = {
        TopAppBar(title = { Text(d?.trip?.title ?: "Fahrt", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, "Zurück") } },
            actions = {
                IconButton(onClick = { renaming = true }, modifier = Modifier.testTag("rename")) { Icon(Lucide.Pencil, "Umbenennen") }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("export")) { Icon(Lucide.Share2, "Exportieren") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Als GPX teilen") }, onClick = { menu = false; d?.let { scope.launch { shareRide(ctx, it.trip, gpx = true) } } }, modifier = Modifier.testTag("exportGpx"))
                        DropdownMenuItem(text = { Text("Als CSV teilen (alle Messwerte)") }, onClick = { menu = false; d?.let { scope.launch { shareRide(ctx, it.trip, gpx = false) } } }, modifier = Modifier.testTag("exportCsv"))
                    }
                }
                IconButton(onClick = { deleting = true }, modifier = Modifier.testTag("delete")) { Icon(Lucide.Trash2, "Löschen") }
            })
    }) { pad ->
        if (d == null) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Tokens.spaceXxxl)) {
            Text(SimpleDateFormat("EEEE, d. MMMM yyyy · HH:mm", Locale.GERMANY).format(Date(d.trip.startedMs)), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Tokens.spaceLg))
            if (d.pts.size >= 2) {
                Surface(shape = RoundedCornerShape(Tokens.radiusMap), modifier = Modifier.padding(Tokens.spaceLg).fillMaxWidth().height(Tokens.sizeChartLg)) {
                    ExpandableMap(Modifier.fillMaxSize()) { m -> map(d, by, sel, m) }
                }
                SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = Tokens.spaceLg).fillMaxWidth()) {
                    ColorBy.entries.forEachIndexed { i, c -> SegmentedButton(selected = by == c, onClick = { by = c }, shape = SegmentedButtonDefaults.itemShape(i, ColorBy.entries.size), modifier = Modifier.testTag("color_${c.name}")) { Text("Farbe: ${c.label}") } }
                }
                Legend(by)
            } else Text("Zu wenige GPS-Punkte für Karte und Diagramme.", Modifier.padding(Tokens.spaceLg))
            Stats(d.sum)
            if (d.loopTitle != null && d.coverage != null) LoopSection(d, onOpenLoop)
            if (d.samples.size >= 3) {
                Charts(d.samples, sel) { sel = it }
                Header("g-g-Diagramm (Reibungskreis)")
                GgDiagram(d.samples, sel, Modifier.padding(horizontal = Tokens.spaceLg).fillMaxWidth().height(Tokens.sizeChartLg))
                Text(if (d.sum.hasSensors) "Längs-g aus Beschleunigungssensor (Fahrtrichtung aus GPS gelernt), Quer-g aus Drehrate × Tempo. Näherungen – abhängig von Halterung und Sensorqualität."
                    else "Ohne Sensordaten: g-Werte nur aus GPS (Tempo- und Richtungsänderung) geschätzt, grob.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Tokens.spaceLg))
            }
            Card(Modifier.padding(horizontal = Tokens.spaceLg).fillMaxWidth().testTag("safety")) {
                Row(Modifier.padding(Tokens.spaceMd)) {
                    Icon(Lucide.Info, null); Spacer(Modifier.width(Tokens.spaceSm))
                    Text("Zur Erinnerung, nicht zum Vergleichen: keine Bestzeiten, keine Ranglisten. Auf öffentlichen Straßen gelten StVO und Tempolimits.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (renaming && d != null) {
        var name by remember { mutableStateOf(d.trip.title) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Fahrt umbenennen") },
            text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true, modifier = Modifier.testTag("renameField")) },
            confirmButton = { TextButton(onClick = { renaming = false; scope.launch(Dispatchers.IO) { ctx.graph.trips.rename(id, name.ifBlank { d.trip.title }, System.currentTimeMillis()); reload++ } }, modifier = Modifier.testTag("renameOk")) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Abbrechen") } })
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Fahrt löschen?") }, text = { Text("Strecke und Messwerte werden endgültig vom Gerät gelöscht.") },
        confirmButton = { TextButton(onClick = { deleting = false; scope.launch { withContext(Dispatchers.IO) { ctx.graph.trips.delete(id, System.currentTimeMillis()) }; onBack() } }, modifier = Modifier.testTag("deleteOk")) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Abbrechen") } })
}

@Composable private fun Header(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Tokens.spaceLg, end = Tokens.spaceLg, top = Tokens.spaceXl, bottom = Tokens.spaceSm))

@Composable
private fun Legend(by: ColorBy) {
    Row(Modifier.padding(horizontal = Tokens.spaceLg, vertical = Tokens.spaceXsm), verticalAlignment = Alignment.CenterVertically) {
        Text(if (by == ColorBy.SPEED) "0" else "0 g", style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.padding(horizontal = Tokens.spaceXsm).weight(1f).height(Tokens.spaceSm)) { val n = 40; for (i in 0 until n) drawRect(rampColor(i / (n - 1.0)), Offset(size.width * i / n, 0f), androidx.compose.ui.geometry.Size(size.width / n + 1, size.height)) }
        Text(if (by == ColorBy.SPEED) "130 km/h" else "0,6 g", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Stats(s: RideSummary) {
    val items = listOf(
        "Dauer" to fmtDur(s.durationS * 1000), "In Bewegung" to fmtDur(s.movingS * 1000), "Strecke" to fmtKm1(s.distanceM),
        "Ø Tempo" to "%.0f km/h".format(s.avgKmh), "Max. Tempo" to "%.0f km/h".format(s.maxKmh), "Kurven" to "${s.curves}",
        "Bergauf" to "%.0f m".format(s.ascentM), "Bergab" to "%.0f m".format(s.descentM), "Max. Quer-g" to "%.2f g".format(Locale.GERMANY, s.maxLatG),
        "Max. Beschl." to "%.2f g".format(Locale.GERMANY, s.maxLonAccG), "Max. Verzög." to "%.2f g".format(Locale.GERMANY, s.maxBrakeG), "Punkte" to "${s.points}",
    )
    Column(Modifier.padding(Tokens.spaceLg).testTag("stats"), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) { row.forEach { (l, v) -> Column(Modifier.weight(1f)) { Text(v, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        }
        Text("Kurve = mind. 2 s mit über 0,2 g Querbeschleunigung bei über 20 km/h.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoopSection(d: RideData, onOpenLoop: (Int) -> Unit) {
    val cov = d.coverage!!; val share = cov.count { it }.toDouble() / max(1, cov.size)
    Header("Runde: ${d.loopTitle}")
    Column(Modifier.padding(horizontal = Tokens.spaceLg).testTag("loopSection")) {
        Text("%.0f %% der Runde gefahren (250-m-Abschnitte)%s".format(share * 100, if (share >= RideMath.DRIVEN_SHARE) " · als „Schon gefahren“ markiert" else ""), style = MaterialTheme.typography.bodyMedium)
        val on = MaterialTheme.colorScheme.primary; val off = MaterialTheme.colorScheme.surfaceContainerHighest
        Canvas(Modifier.padding(vertical = Tokens.spaceSm).fillMaxWidth().height(Tokens.spaceMdl)) {
            val w = size.width / cov.size
            cov.forEachIndexed { i, b -> drawRect(if (b) on else off, Offset(i * w, 0f), androidx.compose.ui.geometry.Size(w + 0.5f, size.height)) }
        }
        Row { Text("Start", style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text("Ziel", style = MaterialTheme.typography.labelSmall) }
        d.trip.loopId?.let { OutlinedButton(onClick = { onOpenLoop(it.toInt()) }, modifier = Modifier.padding(top = Tokens.spaceXs)) { Text("Runde öffnen") } }
    }
}

/** Diagramme Tempo/Höhe/g über Strecke oder Zeit; Ziehen markiert die Stelle auf der Karte. */
@Composable
private fun Charts(sm: List<RideSample>, sel: Int?, onSel: (Int?) -> Unit) {
    var byTime by remember { mutableStateOf(false) }
    Row(Modifier.padding(start = Tokens.spaceLg, end = Tokens.spaceLg, top = Tokens.spaceXl), verticalAlignment = Alignment.CenterVertically) {
        Text("Verlauf", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(!byTime, { byTime = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Strecke") }
            SegmentedButton(byTime, { byTime = true }, SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("xTime")) { Text("Zeit") }
        }
    }
    val xs = sm.map { if (byTime) it.tS else it.distM }
    sel?.let { i -> sm.getOrNull(i)?.let { s ->
        Text("%s · %s · %.0f km/h%s · Längs %+.2f g · Quer %+.2f g".format(Locale.GERMANY, fmtKm1(s.distM), fmtDur((s.tS * 1000).toLong()), s.kmh, s.altM?.let { " · %.0f m".format(it) } ?: "", s.lonG, s.latG),
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = Tokens.spaceLg, vertical = Tokens.spaceXs).testTag("scrubInfo"))
    } }
    val c1 = MaterialTheme.colorScheme.primary; val c2 = MaterialTheme.colorScheme.tertiary; val c3 = MaterialTheme.colorScheme.secondary
    Chart("Tempo (km/h)", xs, sm.map { it.kmh }, c1, sel, onSel, "chartSpeed")
    if (sm.any { it.altM != null }) Chart("Höhe (m)", xs, sm.map { it.altM ?: Double.NaN }, c2, sel, onSel, "chartAlt")
    Chart("Quer-g (+ links / − rechts)", xs, sm.map { it.latG }, c3, sel, onSel, "chartLat", zero = true)
    Chart("Längs-g (+ beschleunigen / − bremsen)", xs, sm.map { it.lonG }, c3, sel, onSel, "chartLon", zero = true)
}

@Composable
private fun Chart(title: String, xs: List<Double>, ys: List<Double>, color: Color, sel: Int?, onSel: (Int?) -> Unit, tag: String, zero: Boolean = false) {
    val grid = MaterialTheme.colorScheme.outlineVariant; val fg = MaterialTheme.colorScheme.onSurface
    val valid = ys.filter { !it.isNaN() }; if (valid.isEmpty()) return
    var lo = valid.min(); var hi = valid.max(); if (zero) { val m = max(abs(lo), abs(hi)).coerceAtLeast(0.1); lo = -m; hi = m }; if (hi - lo < 1e-6) hi = lo + 1
    val x0 = xs.first(); val x1 = xs.last().coerceAtLeast(x0 + 1e-6)
    Text("$title  %s … %s".format(fmtV(lo), fmtV(hi)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Tokens.spaceLg, top = Tokens.spaceSmd))
    fun idxAt(px: Float, w: Float): Int { val x = x0 + (px / w).coerceIn(0f, 1f) * (x1 - x0); return xs.indices.minBy { abs(xs[it] - x) } }
    Canvas(Modifier.padding(horizontal = Tokens.spaceLg).fillMaxWidth().height(Tokens.sizeChartSm).testTag(tag)
        .pointerInput(xs) { detectTapGestures { onSel(idxAt(it.x, size.width.toFloat())) } }
        .pointerInput(xs) { detectDragGestures { ch, _ -> onSel(idxAt(ch.position.x, size.width.toFloat())) } }) {
        fun px(i: Int) = ((xs[i] - x0) / (x1 - x0) * size.width).toFloat()
        fun py(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
        if (zero) drawLine(grid, Offset(0f, py(0.0)), Offset(size.width, py(0.0)), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        val p = Path(); var open = false
        ys.forEachIndexed { i, v -> if (v.isNaN()) open = false else if (!open) { p.moveTo(px(i), py(v)); open = true } else p.lineTo(px(i), py(v)) }
        drawPath(p, color, style = Stroke(Tokens.strokeThin.toPx()))
        sel?.takeIf { it in xs.indices }?.let { i -> drawLine(fg, Offset(px(i), 0f), Offset(px(i), size.height), strokeWidth = Tokens.strokeFine.toPx()); if (!ys[i].isNaN()) drawCircle(fg, Tokens.sizeDotSm.toPx(), Offset(px(i), py(ys[i]))) }
    }
}
private fun fmtV(v: Double) = if (abs(v) < 5) "%.2f".format(Locale.GERMANY, v) else "%.0f".format(v)

/** g-g-Diagramm: x = Quer-g, y = Längs-g; Ringe bei 0,25/0,5/0,75 g. */
@Composable
fun GgDiagram(sm: List<RideSample>, sel: Int?, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant; val dot = MaterialTheme.colorScheme.primary; val fg = MaterialTheme.colorScheme.onSurface
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.testTag("gg"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = minOf(size.width, size.height) / 2 * 0.95f; val c = Offset(size.width / 2, size.height / 2); val maxG = 1.0
            listOf(0.25, 0.5, 0.75, 1.0).forEach { drawCircle(grid, (r * it / maxG).toFloat(), c, style = Stroke(Tokens.strokeHair.toPx())) }
            drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y)); drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r))
            sm.forEach { s -> if (s.kmh >= 5) drawCircle(dot.copy(alpha = 0.35f), Tokens.sizeDotXs.toPx(), Offset(c.x - (s.latG / maxG * r).toFloat(), c.y - (s.lonG / maxG * r).toFloat())) }
            sel?.let { sm.getOrNull(it) }?.let { s -> drawCircle(fg, Tokens.sizeDotMd.toPx(), Offset(c.x - (s.latG / maxG * r).toFloat(), c.y - (s.lonG / maxG * r).toFloat())) }
        }
        Text("Beschl.", style = MaterialTheme.typography.labelSmall, color = label, modifier = Modifier.align(Alignment.TopCenter))
        Text("Bremsen", style = MaterialTheme.typography.labelSmall, color = label, modifier = Modifier.align(Alignment.BottomCenter))
        Text("links", style = MaterialTheme.typography.labelSmall, color = label, modifier = Modifier.align(Alignment.CenterStart))
        Text("rechts", style = MaterialTheme.typography.labelSmall, color = label, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** Export über das Teilen-Menü (FileProvider, Cache). Rückgabe: Datei (für Tests). */
fun exportRide(ctx: Context, t: Trip, gpx: Boolean): File {
    val pts = ctx.graph.trips.points(t.id)
    val body = if (gpx) Gpx.write(t.title, pts) { Instant.ofEpochMilli(it).toString() } else Csv.write(pts) { Instant.ofEpochMilli(it).toString() }
    val safe = t.title.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]+"), "-").trim('-').take(40).ifBlank { "fahrt" }
    return File(ctx.cacheDir, "gpx").apply { mkdirs() }.resolve("$safe-${t.id}.${if (gpx) "gpx" else "csv"}").apply { writeText(body) }
}

suspend fun shareRide(ctx: Context, t: Trip, gpx: Boolean) {
    val f = withContext(Dispatchers.IO) { exportRide(ctx, t, gpx) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (gpx) "application/gpx+xml" else "text/csv").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, t.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), if (gpx) "GPX teilen" else "CSV teilen")) }
}
