package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Mountain
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Settings
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.model.LoopCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopListScreen(catalog: LoopCatalog, onOpen: (Int) -> Unit, onTrips: () -> Unit, onSettings: () -> Unit, saved: Set<Int> = emptySet(), onAbHier: () -> Unit = {}, recording: de.scenicroutes.app.rec.RecState? = null, onRecord: () -> Unit = {}, embedded: Boolean = false) {
    var onlyRep by rememberSaveable { mutableStateOf(true) }
    var klassen by rememberSaveable { mutableStateOf(setOf<String>()) }
    val list = remember(onlyRep, klassen) { catalog.filtered(onlyRep, klassen) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = if (embedded) Modifier else Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = { if (!embedded)
            LargeTopAppBar(
                title = { Column { Text("Runden ab Miltenberg"); Text("${list.size} von ${catalog.loops.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                actions = {
                    IconButton(onClick = onTrips, modifier = Modifier.testTag("myRides")) { Icon(Lucide.History, "Meine Fahrten") }
                    IconButton(onClick = onSettings) { Icon(Lucide.Settings, "Einstellungen") }
                },
                scrollBehavior = scroll,
            )
        },
        // Eindeutiger Einstieg (wie Web-Knopf „Runde ab hier“): Extended FAB unten rechts, immer sichtbar.
        // Zwei klare Einstiege unten rechts: „Fahrt aufzeichnen“ (ohne Route) und „Runde ab hier“.
        // Während einer Aufzeichnung ersetzt eine Statuszeile (● Zeit · km) den Aufnahme-Knopf.
        floatingActionButton = { if (!embedded)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
                if (recording != null) de.scenicroutes.app.rec.RecordingPill(recording, onRecord)
                else ExtendedFloatingActionButton(onClick = onRecord, icon = { Icon(Lucide.Circle, null, tint = de.scenicroutes.app.rec.RecRed) }, text = { Text("Fahrt aufzeichnen") },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.testTag("recordFab"))
                ExtendedFloatingActionButton(onClick = onAbHier, icon = { Icon(Lucide.Compass, null) }, text = { Text("Runde ab hier") }, modifier = Modifier.testTag("abHier"))
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().testTag("loopList"), contentPadding = PaddingValues(start = Tokens.spaceLg, end = Tokens.spaceLg, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + if (embedded) Tokens.spaceXxl else Tokens.sizeListEndMap), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                    item { FilterChip(selected = onlyRep, onClick = { onlyRep = !onlyRep }, label = { Text("Unterschiedliche Runden") }, modifier = Modifier.testTag("chipRep")) }
                    items(listOf("A", "B", "C")) { k ->
                        FilterChip(selected = k in klassen, onClick = { klassen = if (k in klassen) klassen - k else klassen + k }, label = { Text("${klasseLabel(k)} ($k)") }, modifier = Modifier.testTag("chip$k"))
                    }
                }
            }
            items(list, key = { it.id }) { LoopCard(it, { onOpen(it.id) }, it.id in saved) }
        }
    }
}

@Composable
fun LoopCard(loop: Loop, onClick: () -> Unit, saved: Boolean = false) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("loop_${loop.id}").semantics { contentDescription = "Runde ${loop.titleShort}, ${fmtKm(loop.km)}" }) {
        Row(Modifier.padding(Tokens.spaceMd), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Tokens.radiusLg), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(Tokens.sizeThumb)) {
                RouteShape(loop, Modifier.fillMaxSize())
            }
            Column(Modifier.padding(start = Tokens.spaceMdl).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(loop.titleShort, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Box(Modifier.padding(start = Tokens.spaceSm)) { Badge(containerColor = if (loop.klasse == "A") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Text(klasseLabel(loop.klasse), Modifier.padding(horizontal = Tokens.spaceXs)) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(Tokens.sizeIndicator).clip(androidx.compose.foundation.shape.CircleShape).background(scoreColor(loop.score)))
                    Text(" ${de.scenicroutes.shared.model.ScoreDefs.de(loop.tour, 3)} · " + loop.region.substringBefore(" ("), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (saved) androidx.compose.material3.Icon(Lucide.CircleCheck, "Offline gespeichert", Modifier.padding(start = Tokens.spaceXsm).size(Tokens.sizeIconXs).testTag("savedBadge"), tint = MaterialTheme.colorScheme.primary)
                }
                de.scenicroutes.shared.model.ScoreDefs.strengthLabels(loop.strengths).firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
                Row(Modifier.padding(top = Tokens.spaceSm), horizontalArrangement = Arrangement.spacedBy(Tokens.spaceMdl)) {
                    Fact(Lucide.Route, fmtKm(loop.km), "Länge")
                    Fact(Lucide.Clock, fmtMin(loop.min), "Dauer")
                    Fact(Lucide.Mountain, "${loop.hm} Hm", "Höhenmeter")
                }
            }
        }
    }
}
