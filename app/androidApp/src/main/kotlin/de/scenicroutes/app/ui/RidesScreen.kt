package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bike
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Route
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.scenicroutes.app.graph
import de.scenicroutes.app.rec.RecRed
import de.scenicroutes.app.rec.fmtDur
import de.scenicroutes.app.rec.fmtKm1
import de.scenicroutes.shared.db.Trip
import de.scenicroutes.shared.trip.TrackPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** „Meine Fahrten“: alle gespeicherten Fahrten, neueste zuerst. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onRecord: () -> Unit, embedded: Boolean = false) {
    val g = LocalContext.current.graph
    val trips by remember { g.trips.tripsFlow(Dispatchers.IO) }.collectAsState(emptyList())
    val recording by g.recorder.state.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Meine Fahrten") }, navigationIcon = { if (!embedded) IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, "Zurück") } }) },
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        floatingActionButton = { if (recording == null) ExtendedFloatingActionButton(onClick = onRecord, icon = { Icon(Lucide.Circle, null, tint = RecRed) }, text = { Text("Fahrt aufzeichnen") }) },
    ) { pad ->
        if (trips.isEmpty()) Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Tokens.spaceXxxl)) {
                Icon(Lucide.Bike, null, Modifier.size(Tokens.sizeIconXl), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Noch keine Fahrten", style = MaterialTheme.typography.titleMedium)
                Text("Starte „Fahrt aufzeichnen“ oder eine Runde – die Strecke wird lokal gespeichert und ausgewertet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.fillMaxSize().testTag("rides"), contentPadding = PaddingValues(Tokens.spaceLg, pad.calculateTopPadding() + Tokens.spaceSm, Tokens.spaceLg, Tokens.sizeListEnd), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
            items(trips, key = { it.id }) { t -> RideCard(t) { onOpen(t.id) } }
        }
    }
}

@Composable
fun RideCard(t: Trip, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val df = remember { SimpleDateFormat("EEE, d. MMM yyyy · HH:mm", Locale.GERMANY) }
    val pts by produceState<List<TrackPoint>>(emptyList(), t.id) { value = withContext(Dispatchers.IO) { ctx.graph.trips.points(t.id).let { p -> p.filterIndexed { i, _ -> i % max(1, p.size / 150) == 0 } } } }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("ride_${t.id}")) {
        Row(Modifier.padding(Tokens.spaceMd), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Tokens.radiusLg), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(Tokens.sizeThumb)) { TrackShape(pts, Modifier.fillMaxSize()) }
            Column(Modifier.padding(start = Tokens.spaceMdl).weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(df.format(Date(t.startedMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = Tokens.spaceSm), horizontalArrangement = Arrangement.spacedBy(Tokens.spaceMdl)) {
                    Fact(Lucide.Clock, fmtDur((t.durationS ?: 0) * 1000), "Dauer")
                    Fact(Lucide.Route, fmtKm1(t.distanceM ?: 0.0), "Strecke")
                    Fact(Lucide.Mountain, "%.0f Hm".format(t.ascentM ?: 0.0), "Höhenmeter")
                }
                t.loopCoverage?.let { Text("%.0f %% der Runde".format(it * 100), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Tokens.spaceXs)) }
            }
        }
    }
}

/** Schematische Spur (ohne Kacheln) für Liste und Test-Platzhalter. */
@Composable
fun TrackShape(pts: List<TrackPoint>, modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val stc = de.scenicroutes.app.ui.statusColors(); val endC = RecRed
    Canvas(modifier) {
        if (pts.size < 2) return@Canvas
        val k = cos(Math.toRadians(pts[0].lat)); val xs = pts.map { it.lon * k }; val ys = pts.map { it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = Tokens.spaceSm.toPx(); val s = minOf((size.width - 2 * pad) / max(1e-9, maxX - minX), (size.height - 2 * pad) / max(1e-9, maxY - minY))
        val ox = (size.width - (maxX - minX) * s) / 2; val oy = (size.height - (maxY - minY) * s) / 2
        fun p(i: Int) = Offset((ox + (xs[i] - minX) * s).toFloat(), (oy + (maxY - ys[i]) * s).toFloat())
        val path = Path().apply { moveTo(p(0).x, p(0).y); for (i in 1 until pts.size) { if (pts[i].seg != pts[i - 1].seg) moveTo(p(i).x, p(i).y) else lineTo(p(i).x, p(i).y) } }
        drawPath(path, color, style = Stroke(Tokens.strokeMedium.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(stc.success, Tokens.sizeDotSm.toPx(), p(0)); drawCircle(endC, Tokens.sizeDotSm.toPx(), p(pts.lastIndex))
    }
}
