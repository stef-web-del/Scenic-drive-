package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Bike
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Split
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.scenicroutes.app.graph
import de.scenicroutes.app.rec.RecState
import de.scenicroutes.shared.model.LoopCatalog

/** Die vier Bereiche unten in der Leiste. */
enum class Tab(val label: String, val icon: ImageVector, val iconOn: ImageVector) {
    PLANEN("Planen", Lucide.Split, Lucide.Split),
    ENTDECKEN("Entdecken", Lucide.Compass, Lucide.Compass),
    ROUTEN("Meine Routen", Lucide.Bookmark, Lucide.Bookmark),
    FAHRTEN("Fahrten", Lucide.Bike, Lucide.Bike),
}

/**
 * Hauptgerüst: oben die Titelleiste je Tab, unten die Navigationsleiste. Detail, Navigation, Aufzeichnung
 * und Einstellungen laufen weiter als eigene Ziele im NavHost darüber (Fahrbildschirm ohne Leiste).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(catalog: LoopCatalog, saved: Set<Int>, recording: RecState?,
              onOpen: (Int) -> Unit, onNavigate: (Int) -> Unit, onRide: (Long) -> Unit, onRecord: () -> Unit, onSettings: () -> Unit,
              initial: Tab = Tab.PLANEN,
              planMap: (@Composable (Pair<Double, Double>, Pair<Double, Double>?, Modifier, (Double, Double) -> Unit) -> Unit)? = null,
              aroundMap: (@Composable (de.scenicroutes.shared.model.Loop, Modifier) -> Unit)? = null,
              discoverInitial: Int = 1, planStart: Spot? = null) {
    var tab by rememberSaveable { mutableStateOf(initial) }
    var discover by rememberSaveable { mutableIntStateOf(discoverInitial) }   // 0 = rund um dich, 1 = Katalog
    var importReq by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(if (tab == Tab.PLANEN) "Runde planen" else tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (tab == Tab.ENTDECKEN && discover == 1) Text("${catalog.loops.size} Runden ab Miltenberg", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, actions = {
                if (tab == Tab.ROUTEN) IconButton(onClick = { importReq++ }, modifier = Modifier.testTag("gpxImport")) { Icon(Lucide.FolderOpen, "GPX importieren") }
                IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) { Icon(Lucide.Settings, "Einstellungen") }
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(if (tab == t) t.iconOn else t.icon, null) },
                        label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.testTag("tab_${t.name}"))
                }
            }
        },
        floatingActionButton = {
            // Aufzeichnung immer erreichbar: läuft sie, zeigt die Pille Zeit und Strecke; sonst der Knopf im Tab „Fahrten“.
            if (recording != null) de.scenicroutes.app.rec.RecordingPill(recording, onRecord)
            else if (tab == Tab.FAHRTEN) ExtendedFloatingActionButton(onClick = onRecord, icon = { Icon(Lucide.Circle, null, tint = de.scenicroutes.app.rec.RecRed) },
                text = { Text("Fahrt aufzeichnen") }, modifier = Modifier.testTag("recordFab"))
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (tab) {
                Tab.PLANEN -> if (planMap != null) PlanScreen(onNavigate, onOpen, planMap, planStart) else PlanScreen(onNavigate, onOpen, initialStart = planStart)
                Tab.ENTDECKEN -> Column(Modifier.fillMaxSize()) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Tokens.spaceLg, vertical = Tokens.spaceSm)) {
                        SegmentedButton(selected = discover == 1, onClick = { discover = 1 }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.testTag("discoverCatalog")) { Text("Katalog") }
                        SegmentedButton(selected = discover == 0, onClick = { discover = 0 }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("discoverAround")) { Text("Rund um dich") }
                    }
                    Box(Modifier.weight(1f)) {
                        if (discover == 1) LoopListScreen(catalog, onOpen, {}, onSettings, saved = saved, embedded = true)
                        else if (aroundMap != null) AbHierScreen({}, onOpen, onNavigate, map = aroundMap, embedded = true)
                        else AbHierScreen({}, onOpen, onNavigate, embedded = true)
                    }
                }
                Tab.ROUTEN -> MyRoutesScreen(onOpen, onNavigate, snack, importReq)
                Tab.FAHRTEN -> RidesScreen({}, onRide, onRecord, embedded = true)
            }
        }
    }
}
