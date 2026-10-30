package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.scenicroutes.app.data.SaveState
import de.scenicroutes.shared.model.Handoff
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.model.ScoreDefs
import de.scenicroutes.shared.model.ScoreDefs.de
import de.scenicroutes.shared.model.ScoreMeta
import java.io.File

fun mb(b: Long) = if (b < 1_000_000) "${de(b / 1000.0, 0)} kB" else "${de(b / 1_000_000.0, 1)} MB"

private fun openGoogle(ctx: Context, loop: Loop, start: List<Double>?) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(Handoff.googleUrl(loop, start ?: loop.geom.first())))
    try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "Keine Karten-App gefunden", Toast.LENGTH_SHORT).show() }
}

private fun shareGpx(ctx: Context, loop: Loop) {
    val dir = File(ctx.cacheDir, "gpx").apply { mkdirs() }
    val f = File(dir, "scenic-runde-${loop.id}.gpx").apply { writeText(Handoff.gpx(loop)) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "GPX teilen"))
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
    HorizontalDivider(Modifier.padding(vertical = Tokens.spaceSm))
    Text(title, style = MaterialTheme.typography.titleMedium); content()
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) = Surface(modifier, shape = RoundedCornerShape(Tokens.radiusMd), color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(Modifier.padding(horizontal = Tokens.spaceMd, vertical = Tokens.spaceSm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2)
    }
}

/** Detail in Web-Reihenfolge (Tokens.detailOrder): Kopf, Kennzahlen, Stärken, Tags, Text, Aktionen, Warum, Profil, Pausen, Orte, Familie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoopDetailScreen(
    loop: Loop, onBack: () -> Unit, onStart: () -> Unit,
    sbm: ScoreMeta? = null, start: List<Double>? = null, family: List<Loop> = emptyList(), onOpen: (Int) -> Unit = {},
    offline: SaveState = SaveState.None, onSave: () -> Unit = {}, onRemove: () -> Unit = {},
    map: @Composable (Modifier, Boolean, Int?) -> Unit = { m, c, s -> LoopMap(loop, m, c, s) },
    favorite: Boolean? = null, onFavorite: () -> Unit = {}, canNavigate: Boolean = true,
) {
    val ctx = LocalContext.current
    var segCol by rememberSaveable(loop.id) { mutableStateOf(false) }
    var seg by rememberSaveable(loop.id) { mutableStateOf<Int?>(null) }
    var goMenu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = cs.background) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        ExpandableMap(Modifier.fillMaxWidth().height(Tokens.sizeMapLg), underStatusBar = true, map = { m -> map(m, segCol, seg) }, overlay = {
            Text("© OpenStreetMap-Mitwirkende (ODbL) · OpenFreeMap", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).background(cs.surface.copy(alpha = 0.8f)).padding(horizontal = Tokens.spaceXsm, vertical = Tokens.spaceXxs))
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(Tokens.spaceSm)) { Icon(Lucide.ArrowLeft, "Zurück") }
            if (favorite != null) FilledTonalIconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = Tokens.spaceSm, end = Tokens.touchMin + Tokens.spaceMd).testTag("detailFav")) {
                Icon(if (favorite) Lucide.Star else Lucide.Star, if (favorite) "Favorit entfernen" else "Als Favorit merken", tint = if (favorite) cs.primary else cs.onSurfaceVariant)
            }
        })
        Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                Box(Modifier.size(Tokens.sizeIndicatorLg).clip(CircleShape).background(scoreColor(loop.score)))
                Column(Modifier.weight(1f)) {
                    Text(loop.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("detailTitle"))
                    Text("Runde ${loop.id}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(Tokens.radiusSm), color = cs.primaryContainer) { Text("Klasse ${loop.klasse}", Modifier.padding(horizontal = Tokens.spaceSm, vertical = Tokens.spaceXs), style = MaterialTheme.typography.labelLarge) }
            }
            // stats
            val st = listOf(Tokens.Terms.length to "${de(loop.km, 1)} km", Tokens.Terms.time to "${loop.min} min", Tokens.Terms.climb to "${loop.hm} Hm",
                Tokens.Terms.score to de(loop.score, 3), Tokens.Terms.tour to de(loop.tour, 3), Tokens.Terms.region to loop.region.substringBefore(" (").replace("/", "/\u200B"))
            st.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) { row.forEach { (l, v) -> Stat(l, v, Modifier.weight(1f)) } } }
            // strengths + tags
            val strengths = ScoreDefs.strengthLabels(loop.strengths)
            if (strengths.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm), modifier = Modifier.testTag("strengths")) {
                strengths.forEach { Surface(shape = RoundedCornerShape(Tokens.radiusSm), color = cs.secondaryContainer) { Text(it, Modifier.padding(horizontal = Tokens.spaceSmd, vertical = Tokens.spaceXsm), style = MaterialTheme.typography.labelLarge) } }
            }
            if (loop.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) { loop.tags.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) } }
            if (loop.text.isNotBlank()) Text(loop.text, style = MaterialTheme.typography.bodyLarge)
            // actions
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(onClick = { goMenu = true }, enabled = canNavigate, modifier = Modifier.heightIn(min = Tokens.touchMin).testTag("startNav")) { Icon(Lucide.Navigation, null); Spacer(Modifier.size(Tokens.spaceSm)); Text(Tokens.Terms.go) }
                    DropdownMenu(goMenu, { goMenu = false }) {
                        DropdownMenuItem(text = { Column { Text("In der App navigieren"); Text("Exakte Route, offline nutzbar", style = MaterialTheme.typography.labelSmall) } },
                            leadingIcon = { Icon(Lucide.Navigation, null) }, onClick = { goMenu = false; onStart() }, modifier = Modifier.testTag("goApp"))
                        DropdownMenuItem(text = { Column { Text("Google Maps"); Text("Ganze Runde über Lenkpunkte" + (loop.navMatch?.let { " · ${Math.round(it * 100)} % deckungsgleich" } ?: ""), style = MaterialTheme.typography.labelSmall) } },
                            leadingIcon = { Icon(Lucide.Map, null) }, onClick = { goMenu = false; openGoogle(ctx, loop, start) }, modifier = Modifier.testTag("goGoogle"))
                    }
                }
                OutlinedButton(onClick = { shareGpx(ctx, loop) }, modifier = Modifier.heightIn(min = Tokens.touchMin)) { Icon(Lucide.FileDown, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("GPX") }
            }
            if (!canNavigate) Text("Importierte Strecke: nur Ansicht und GPX-Export. Navigation gibt es für berechnete Runden.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (canNavigate) OfflineCard(loop, offline, onSave, onRemove)
            // why
            loop.sb?.let { sb -> HorizontalDivider(Modifier.padding(vertical = Tokens.spaceSm)); ScoreBreakdown(loop, sb, sbm, seg, { seg = it }, segCol, { segCol = it }) }
            Section(Tokens.Terms.profile) { ElevationProfile(loop, Modifier.fillMaxWidth().height(Tokens.sizeChartMd).testTag("profile")) }
            if (loop.pauses.isNotEmpty()) Section(Tokens.Terms.pauses) {
                loop.pauses.forEach { p -> ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("km ${de(p.km, 0)} · ${p.kind}") }, leadingContent = { Icon(Lucide.Coffee, null) }, colors = ListItemDefaults.colors(containerColor = cs.background)) }
            }
            if (loop.orte.isNotEmpty()) Section(Tokens.Terms.places) { Text(loop.orte.joinToString(" · "), style = MaterialTheme.typography.bodyMedium) }
            val fam = family.filter { it.id != loop.id }
            if (fam.isNotEmpty()) Section("Varianten (${fam.size})") {
                fam.take(6).forEach { f -> ListItem(headlineContent = { Text(f.title) }, supportingContent = { Text("${de(f.km, 0)} km · Tour-Score ${de(f.tour, 3)}") },
                    leadingContent = { Box(Modifier.size(Tokens.sizeIndicatorMd).clip(CircleShape).background(scoreColor(f.score))) }, modifier = Modifier.clip(RoundedCornerShape(Tokens.radiusMd)), colors = ListItemDefaults.colors(containerColor = cs.background),
                    trailingContent = { TextButton({ onOpen(f.id) }) { Text("Öffnen") } }) }
            }
            Spacer(Modifier.height(Tokens.spaceXxl))
        }
    }
    }
}

@Composable
fun OfflineCard(loop: Loop, s: SaveState, onSave: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(Tokens.radiusLg), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("offlineCard")) {
        Column(Modifier.padding(Tokens.spaceMd), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
            when (s) {
                is SaveState.None, is SaveState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Tokens.Terms.offline, style = MaterialTheme.typography.titleSmall)
                        Text(if (s is SaveState.Failed) "Fehlgeschlagen: ${s.reason}" else "Route (${mb(loop.navBytes ?: 0)}) + Karte für Funkloch-Strecken", style = MaterialTheme.typography.bodySmall, color = if (s is SaveState.Failed) cs.error else cs.onSurfaceVariant)
                    }
                    Button(onSave, modifier = Modifier.heightIn(min = Tokens.touchMin).testTag("offlineSave")) { Icon(Lucide.Download, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Speichern") }
                }
                is SaveState.Running -> {
                    Text("Wird gespeichert … ${s.step}", style = MaterialTheme.typography.titleSmall)
                    if (s.fraction != null) LinearProgressIndicator({ s.fraction }, Modifier.fillMaxWidth().testTag("offlineProgress")) else LinearProgressIndicator(Modifier.fillMaxWidth().testTag("offlineProgress"))
                    Text("${s.fraction?.let { "${Math.round(it * 100)} % · " } ?: ""}${mb(s.bytes)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                is SaveState.Saved -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.CircleCheck, null, tint = cs.primary); Spacer(Modifier.size(Tokens.spaceSm))
                    Column(Modifier.weight(1f)) {
                        Text("Offline gespeichert", style = MaterialTheme.typography.titleSmall)
                        Text("${mb(s.bytes)} · Route" + if (s.mapComplete) " + Karte" else " (Karte unvollständig)", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    TextButton(onRemove, modifier = Modifier.testTag("offlineRemove")) { Icon(Lucide.Trash2, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Löschen") }
                }
            }
        }
    }
}
