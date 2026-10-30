package de.scenicroutes.app.nav

import androidx.compose.foundation.layout.heightIn
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Navigation
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.scenicroutes.app.ui.Tokens
import de.scenicroutes.shared.model.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

class EntryActions(
    val server: () -> Unit = {}, val google: () -> Unit = {}, val guide: () -> Unit = {},
    val official: () -> Unit = {}, val startHere: () -> Unit = {}, val cancel: () -> Unit = {},
)

fun fmtDist(m: Double): String = if (m < 1000) "${(m / 10).roundToInt() * 10} m" else "%.1f km".format(java.util.Locale.GERMANY, m / 1000)

/** Einstiegs-Karte: wo steige ich ein, wie weit ist es, wie komme ich hin. */
@Composable
fun EntryCard(loopCoords: List<LatLon>, e: EntryUi, phase: EntryPhase, busy: Boolean, hasBackend: Boolean, a: EntryActions) {
    Column(Modifier.padding(Tokens.spaceLg).testTag("entryCard"), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
        Text(Tokens.Terms.entry + (e.road.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""), style = MaterialTheme.typography.titleLarge)
        Text(
            "Du bist ${fmtDist(e.distanceM)} (Luftlinie) von der Runde entfernt. " +
                (if (e.official) "Nächster Einstieg ist der offizielle Start." else "Einstieg bei km %.1f von %.1f – die Runde endet wieder hier.".format(java.util.Locale.GERMANY, e.atKm, e.totalKm)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MiniMap(loopCoords, e, Modifier.fillMaxWidth().height(Tokens.sizeMiniMap))
        when (phase) {
            EntryPhase.GUIDE -> GuideRow(e)
            EntryPhase.EXTERNAL -> Text("Navigation startet automatisch, wenn du zurück in der App und an der Runde bist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {}
        }
        if (phase == EntryPhase.EXTERNAL) {
            Button(onClick = a.startHere, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).testTag("entryStart")) { Icon(Lucide.Navigation, null); Spacer(Modifier.size(Tokens.spaceSm)); Text(Tokens.Terms.entryStart) }
        }
        if (phase == EntryPhase.CHOOSE || phase == EntryPhase.EXTERNAL) {
            if (hasBackend) Button(onClick = a.server, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).testTag("entryServer")) {
                if (busy) CircularProgressIndicator(Modifier.size(Tokens.sizeIconSm), strokeWidth = Tokens.strokeThin, color = MaterialTheme.colorScheme.onPrimary) else Icon(Lucide.Cloud, null)
                Spacer(Modifier.size(Tokens.spaceSm)); Text(Tokens.Terms.entryServer)
            }
            FilledTonalButton(onClick = a.google, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).testTag("entryGoogle")) { Icon(Lucide.Map, null); Spacer(Modifier.size(Tokens.spaceSm)); Text(Tokens.Terms.entryGoogle) }
            OutlinedButton(onClick = a.guide, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).testTag("entryGuide")) { Icon(Lucide.Compass, null); Spacer(Modifier.size(Tokens.spaceSm)); Text(Tokens.Terms.entryGuide) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = a.cancel, modifier = Modifier.testTag("entryCancel")) { Text("Abbrechen") }
            TextButton(onClick = a.official, enabled = !busy, modifier = Modifier.testTag("entryOfficial")) { Icon(Lucide.Flag, null, Modifier.size(Tokens.sizeIconSm)); Spacer(Modifier.size(Tokens.spaceXs)); Text(Tokens.Terms.entryOfficial) }
        }
    }
}

@Composable
private fun GuideRow(e: EntryUi) {
    val rot = ((e.bearingDeg - (e.headingDeg ?: 0.0)) % 360).toFloat()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.spaceMd), modifier = Modifier.testTag("guide")) {
        Icon(Lucide.Navigation, "Richtung zum Einstieg", Modifier.size(Tokens.sizeIconHero).rotate(rot), tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(fmtDist(e.distanceM), style = MaterialTheme.typography.headlineMedium)
            Text(if (e.headingDeg == null) "Pfeil zeigt nach Norden-oben (Richtung unbekannt, bis du fährst)" else "Pfeil relativ zur Fahrtrichtung",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Start automatisch, sobald du auf der Runde bist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Offline-Minikarte (ohne Kacheln): Runde, offizieller Start, Einstieg, eigener Standort. */
@Composable
private fun MiniMap(coords: List<LatLon>, e: EntryUi, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val pts = remember(coords) { coords }
    Surface(modifier.clip(RoundedCornerShape(Tokens.radiusMd)).semantics { contentDescription = "Karte: Runde, Einstieg und dein Standort" }, color = cs.surface) {
        Canvas(Modifier.fillMaxWidth().height(Tokens.sizeMiniMap)) {
            val all = pts + e.me + e.point
            val kx = cos(all.map { it.lat }.average() * PI / 180)
            val minX = all.minOf { it.lon * kx }; val maxX = all.maxOf { it.lon * kx }
            val minY = all.minOf { it.lat }; val maxY = all.maxOf { it.lat }
            val pad = 14f
            val s = minOf((size.width - 2 * pad) / (maxX - minX).coerceAtLeast(1e-6), (size.height - 2 * pad) / (maxY - minY).coerceAtLeast(1e-6)).toFloat()
            val ox = (size.width - (maxX - minX).toFloat() * s) / 2; val oy = (size.height - (maxY - minY).toFloat() * s) / 2
            fun o(p: LatLon) = Offset(ox + ((p.lon * kx - minX) * s).toFloat(), size.height - oy - ((p.lat - minY) * s).toFloat())
            val path = Path().apply { pts.forEachIndexed { i, p -> val q = o(p); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) } }
            drawPath(path, cs.primary, style = Stroke(4f, cap = StrokeCap.Round))
            drawLine(cs.onSurfaceVariant, o(e.me), o(e.point), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            drawCircle(cs.onSurfaceVariant, 7f, o(pts.first()))
            drawCircle(cs.surface, 11f, o(e.point)); drawCircle(cs.tertiary, 8f, o(e.point))
            drawCircle(cs.surface, 10f, o(e.me)); drawCircle(cs.secondary, 7f, o(e.me))
        }
    }
}
