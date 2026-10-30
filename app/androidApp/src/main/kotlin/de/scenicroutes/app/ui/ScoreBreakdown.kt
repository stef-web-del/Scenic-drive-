package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Info
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.model.ScoreBreak
import de.scenicroutes.shared.model.ScoreDefs
import de.scenicroutes.shared.model.ScoreDefs.de
import de.scenicroutes.shared.model.ScoreMeta
import kotlin.math.abs
import kotlin.math.max

private val CONTRIB_HELP = mapOf(
    "route" to "40 % × Strecken-Score (Kurven, Relief, Landschaft, abzüglich Ort/B-Straße).",
    "topk" to "25 % × mittlerer Score der drei besten Abschnitte.",
    "flow" to "15 % × Anteil ohne reine Verbindungsetappen.",
    "dram" to "10 % × Verteilung der Highlights über die Runde.",
    "view" to "10 % × Aussichtsanteil (ab 25 % voll).",
    "interrupts" to "Abzug 0,01 je Unterbrechung pro 10 km (max. 0,10).",
)

@Composable
private fun InfoButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(Tokens.touchMin)) { Icon(Lucide.Info, "Erklärung: $title", Modifier.size(Tokens.sizeIconSm), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (open) AlertDialog(onDismissRequest = { open = false }, confirmButton = { TextButton({ open = false }) { Text("OK") } }, title = { Text(title) }, text = { Text(text) })
}

/** „Warum diese Runde?“ – gleiche Reihenfolge/Begriffe wie Web (ScoreBreakdown.tsx). */
@Composable
fun ScoreBreakdown(loop: Loop, sb: ScoreBreak, sbm: ScoreMeta?, seg: Int?, onSeg: (Int?) -> Unit, segCol: Boolean, onSegCol: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("why"), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Tokens.Terms.why, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Tour-Score ", style = MaterialTheme.typography.bodyMedium)
            Text(de(loop.tour, 3), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(" · Klasse ${loop.klasse}", style = MaterialTheme.typography.bodyMedium)
        }
        // Anteile am Tour-Score
        val mx = max(1e-9, ScoreDefs.maxContrib(sb))
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXxs)) {
            ScoreDefs.CONTRIBS.forEach { (k, label) ->
                val v = sb.c[k] ?: 0.0; val avg = sbm?.c?.get(k)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(Tokens.sizeLabelCol), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).height(Tokens.spaceSmd).clip(CircleShape).background(cs.surfaceContainerHigh)) {
                        Box(Modifier.fillMaxWidth((abs(v) / mx).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(if (v < 0) cs.error else cs.primary))
                        if (avg != null) Box(Modifier.fillMaxWidth((abs(avg) / mx).toFloat().coerceIn(0f, 1f)).fillMaxHeight()) { Box(Modifier.align(Alignment.CenterEnd).width(Tokens.strokeThin).fillMaxHeight().background(cs.onSurface)) }
                    }
                    Text((if (v > 0) "+" else "") + de(v, 3), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.width(Tokens.sizeValueCol))
                    InfoButton(label, (CONTRIB_HELP[k] ?: "") + (avg?.let { " Mittel aller Runden: ${de(it, 3)}." } ?: ""))
                }
            }
            Text("Strich = Mittel aller Runden", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        // Streckenanteile
        val hl = sb.v["hl"] ?: 0.0; val tr = sb.v["tr"] ?: 0.0; val nm = max(0.0, 1 - hl - tr)
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
                Legend(cs.primary, "Highlight ${pctI(hl)}"); Legend(Tokens.scoreStops[1].second, "Normal ${pctI(nm)}"); Legend(cs.outline, "Verbindung ${pctI(tr)}")
            }
            Row(Modifier.fillMaxWidth().height(Tokens.spaceMd).clip(CircleShape).semantics { contentDescription = "Streckenanteile: Highlight ${pctI(hl)}, normal ${pctI(nm)}, Verbindung ${pctI(tr)}" }) {
                if (hl > 0) Box(Modifier.weight(hl.toFloat()).fillMaxHeight().background(cs.primary))
                if (nm > 0) Box(Modifier.weight(nm.toFloat()).fillMaxHeight().background(Tokens.scoreStops[1].second))
                if (tr > 0) Box(Modifier.weight(tr.toFloat()).fillMaxHeight().background(cs.outline))
            }
        }
        // 9 Teilwerte als Perzentil
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXxs), modifier = Modifier.testTag("bars")) {
            Text(Tokens.Terms.barsNote.replace("{n}", "${sbm?.n ?: 342}"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            ScoreDefs.BARS.forEach { k ->
                val m = ScoreDefs.metric(k); val p = sb.p[k] ?: 0; val v = sb.v[k] ?: 0.0
                Column(Modifier.testTag("bar_$k").semantics(mergeDescendants = true) { contentDescription = "${m.label}: ${m.fmt(v)}, besser als $p Prozent" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${m.fmt(v)} · $p %", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        InfoButton(m.label, "${m.help} Diese Runde: ${m.fmt(v)}" + (sbm?.med?.get(k)?.let { ", Median aller Runden: ${m.fmt(it)}" } ?: "") + ".")
                    }
                    Box(Modifier.fillMaxWidth().height(Tokens.spaceSm).clip(CircleShape).background(cs.surfaceContainerHigh)) {
                        Box(Modifier.fillMaxWidth((max(2, p) / 100f)).fillMaxHeight().clip(CircleShape).background(cs.tertiary))
                    }
                }
            }
        }
        // Beste Abschnitte
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Tokens.Terms.best, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(Tokens.Terms.segColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = Tokens.spaceSm))
            Switch(segCol, onSegCol, modifier = Modifier.testTag("segCol"))
        }
        val segs = sb.segments
        val best = ScoreDefs.bestSegments(segs)
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXsm)) {
            best.forEach { (i, s) ->
                val on = seg == i
                Surface(onClick = { onSeg(if (on) null else i) }, shape = RoundedCornerShape(Tokens.radiusMd), color = if (on) cs.secondaryContainer else cs.surface,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).border(Tokens.strokeHair, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(Tokens.radiusMd)).semantics { selected = on }.testTag("seg_$i")) {
                    Row(Modifier.padding(horizontal = Tokens.spaceMd, vertical = Tokens.spaceSmd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                        Box(Modifier.size(Tokens.sizeIndicator).clip(CircleShape).background(segColor(s.score)))
                        Column(Modifier.weight(1f)) {
                            Text(s.name.ifBlank { "Ohne Namen" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${ScoreDefs.SEG_KIND[s.kind]} · km ${de(s.kmStart, 1)} · ${de(s.lenM / 1000.0, 1)} km", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        Text(de(s.score, 2), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (best.isEmpty()) Text("Keine Abschnitte ab 1 km.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (segCol) Text("Karte: Abschnitte von gelb (schwach) bis violett (stark, Abschnitts-Score ≥ 0,40).", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

private fun pctI(x: Double) = "${Math.round(x * 100)} %"

@Composable
private fun Legend(c: Color, t: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(Tokens.sizeIndicator).clip(CircleShape).background(c)); Text(" $t", style = MaterialTheme.typography.labelSmall)
}
