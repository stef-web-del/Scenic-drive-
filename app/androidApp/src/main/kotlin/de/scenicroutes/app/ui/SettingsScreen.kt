package de.scenicroutes.app.ui

import androidx.compose.ui.semantics.Role
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bug
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudDownload
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.PanelBottom
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Volume2
import de.scenicroutes.app.BuildConfig
import de.scenicroutes.app.data.CrashLog
import de.scenicroutes.app.data.RoutingPackState
import de.scenicroutes.app.data.SaveState
import de.scenicroutes.app.data.UpdateState
import de.scenicroutes.app.graph
import kotlinx.coroutines.launch

/** Unterseiten der Einstellungen. */
enum class SettingsPage(val title: String, val icon: ImageVector) {
    VOICE("Ansagen & Stimme", Lucide.Volume2),
    MAP("Karte & Ansicht", Lucide.Map),
    BAR("Fahrleiste", Lucide.PanelBottom),
    RIDES("Fahrten & Aufzeichnung", Lucide.Route),
    OFFLINE("Offline-Daten", Lucide.CloudDownload),
    SERVER("Server", Lucide.Server),
    ABOUT("App & Info", Lucide.Info),
}

/**
 * Einstellungen: ruhige Hauptseite mit Gruppen (Icon, Titel, aktueller Wert, Chevron), jede Zeile öffnet eine Unterseite.
 * Zurück (Pfeil oder System-Zurück) führt von der Unterseite auf die Hauptseite, von dort aus den Einstellungen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, speechEnabled: Boolean = true, start: SettingsPage? = null) {
    var page by rememberSaveable { mutableStateOf(start) }
    BackHandler(enabled = page != null) { page = null }
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.surface, topBar = {
        TopAppBar(title = { Text(page?.title ?: "Einstellungen", modifier = Modifier.testTag("settingsTitle")) },
            navigationIcon = { IconButton(onClick = { if (page != null) page = null else onBack() }, Modifier.testTag("settingsBack")) { Icon(Lucide.ArrowLeft, "Zurück") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface))
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.spaceLg).padding(bottom = Tokens.spaceXxl)) {
            when (page) {
                null -> MainPage { page = it }
                SettingsPage.VOICE -> VoiceSettings(LocalContext.current.graph.settings, speechEnabled)
                SettingsPage.MAP -> MapSettings(LocalContext.current.graph.settings)
                SettingsPage.BAR -> BarSettings(LocalContext.current.graph.settings)
                SettingsPage.RIDES -> RidesPage()
                SettingsPage.OFFLINE -> OfflinePage()
                SettingsPage.SERVER -> ServerPage()
                SettingsPage.ABOUT -> AboutPage()
            }
        }
    }
}

@Composable
private fun MainPage(open: (SettingsPage) -> Unit) {
    val g = LocalContext.current.graph; val s = g.settings
    val p by s.navFlow.collectAsState()
    val saves by g.saver.states.collectAsState()
    val rs by g.routing.state.collectAsState()
    val saved = saves.values.filterIsInstance<SaveState.Saved>()
    GroupTitle("Navigation")
    SettingsGroup {
        Entry(SettingsPage.VOICE, voiceSummary(p), open)
        Entry(SettingsPage.MAP, mapSummary(p), open)
        Entry(SettingsPage.BAR, barSummary(p), open)
    }
    GroupTitle("Fahrten & Daten")
    SettingsGroup {
        Entry(SettingsPage.RIDES, if (s.record) "Aufzeichnung an" else "Aufzeichnung aus", open)
        Entry(SettingsPage.OFFLINE, (if (rs is RoutingPackState.Ready) "Routing installiert" else "Routing nicht installiert") + " · ${saved.size} Runden", open)
    }
    GroupTitle("App")
    SettingsGroup {
        Entry(SettingsPage.SERVER, if (s.backendUrl.isBlank() && s.freeLoopUrl.isBlank()) "Nur auf dem Gerät" else "Eigener Server eingetragen", open)
        Entry(SettingsPage.ABOUT, "Version ${BuildConfig.VERSION_NAME}", open)
    }
}

@Composable
private fun Entry(page: SettingsPage, value: String, open: (SettingsPage) -> Unit) =
    ListItem(headlineContent = { Text(page.title) }, supportingContent = { Text(value, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingContent = { Icon(page.icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Lucide.ChevronRight, null, Modifier.size(Tokens.spaceXl), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = groupItemColors(), modifier = Modifier.heightIn(min = Tokens.touchDrive).testTag("settings-${page.name}").clickable(role = Role.Button) { open(page) })

@Composable
private fun RidesPage() {
    val s = LocalContext.current.graph.settings
    var rec by remember { mutableStateOf(s.record) }
    SettingsGroup {
        ListItem(headlineContent = { Text("Fahrten aufzeichnen") }, supportingContent = { Text("Nur lokal auf dem Gerät, Export als GPX") },
            trailingContent = { Switch(rec, { rec = it; s.record = it }, Modifier.testTag("recordRides")) }, colors = groupItemColors())
    }
}

@Composable
private fun OfflinePage() {
    val g = LocalContext.current.graph
    val saves by g.saver.states.collectAsState()
    val rs by g.routing.state.collectAsState()
    val saved = saves.values.filterIsInstance<SaveState.Saved>()
    SettingsGroup {
        ListItem(headlineContent = { Text("Offline-Routing (Auto)") }, modifier = Modifier.testTag("offlineRouting").clickable(role = Role.Button) { if (rs !is RoutingPackState.Ready) g.routing.download() },
            supportingContent = { Text(when (val r = rs) {
                is RoutingPackState.Ready -> "Installiert · ${mb(r.bytes)} · Anfahrt, Rückweg und Umleitung ohne Netz"
                is RoutingPackState.Downloading -> "Wird geladen … ${(r.fraction * 100).toInt()} %"
                is RoutingPackState.Failed -> r.reason
                else -> "Nicht installiert · tippen zum Laden (${mb(BuildConfig.ROUTING_PACK_BYTES)}, nur Autostraßen ~60 km um Miltenberg)"
            }) }, colors = groupItemColors())
        ListItem(headlineContent = { Text("Offline gespeichert") }, supportingContent = { Text("${saved.size} Runden · ${mb(saved.sumOf { it.bytes })}") }, colors = groupItemColors())
    }
}

@Composable
private fun ServerPage() {
    val g = LocalContext.current.graph; val s = g.settings; val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(s.backendUrl) }
    var free by remember { mutableStateOf(s.freeLoopUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    Hint("Gerechnet wird zuerst auf dem Gerät (Offline-Routing). Server sind nur Rückfall bzw. für Tests.")
    OutlinedTextField(url, { url = it; s.backendUrl = it }, label = { Text("Navigations-Server (Basis-URL)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("backendUrl"),
        supportingText = { Text("Optional, nur Rückfall.") })
    FilledTonalButton(onClick = { scope.launch { status = if ((g.backend()?.health() ?: false)) "Server erreichbar ✓" else "Server nicht erreichbar" } }, modifier = Modifier.padding(top = Tokens.spaceSm)) { Text("Verbindung testen") }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Tokens.spaceSm)) }
    OutlinedTextField(free, { free = it; s.freeLoopUrl = it }, label = { Text("Server für Runde ab Punkt") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = Tokens.spaceXl).testTag("freeLoopUrl"),
        supportingText = { Text("Nur lokal, z. B. http://10.0.2.2:8765 im Emulator. Leer lassen, dann gibt es nur die fertigen Runden.") })
}

@Composable
private fun AboutPage() {
    val g = LocalContext.current.graph; val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    val upd by g.updater.state.collectAsState()
    var crash by remember { mutableStateOf<String?>(null) }
    SettingsGroup {
        ListItem(headlineContent = { Text("Nach Updates suchen") }, modifier = Modifier.testTag("checkUpdates").clickable(role = Role.Button) { scope.launch { g.updater.check(force = true) } },
            supportingContent = { Text("Version ${BuildConfig.VERSION_NAME} · " + when (val u = upd) {
                is UpdateState.Checking -> "suche …"
                is UpdateState.UpToDate -> "aktuell ✓"
                is UpdateState.Failed -> u.msg
                else -> "prüft beim Start max. 1× täglich" }) },
            leadingContent = { Icon(Lucide.RefreshCw, null) }, colors = groupItemColors())
        ListItem(headlineContent = { Text("Fehlerbericht anzeigen und kopieren") }, modifier = Modifier.testTag("crashReport").clickable(role = Role.Button) { crash = CrashLog.read(ctx) ?: "" },
            supportingContent = { Text("Letzter Absturz, nur auf diesem Gerät gespeichert. Wird nie automatisch gesendet.") }, leadingContent = { Icon(Lucide.Bug, null) }, colors = groupItemColors())
        var raw by remember { mutableStateOf(g.settings.rawSensors) }
        ListItem(headlineContent = { Text("Sensor-Rohdaten für Fehlerbericht") },
            supportingContent = { Text("Beschleunigung/Gyro mit 50 Hz mitschreiben (braucht Speicher, 3 Tage aufbewahrt). Fehlerbericht als ZIP: Fahrt öffnen → Teilen → „Fehlerbericht (ZIP)“.") },
            leadingContent = { Icon(Lucide.Bug, null) },
            trailingContent = { Switch(raw, { raw = it; g.settings.rawSensors = it }, Modifier.testTag("rawSensors")) }, colors = groupItemColors())
    }
    GroupTitle("Datenquellen")
    SettingsGroup {
        ListItem(headlineContent = { Text("Straßendaten und Karte") }, modifier = Modifier.testTag("attribution"), colors = groupItemColors(),
            supportingContent = { Text("© OpenStreetMap-Mitwirkende, Lizenz ODbL (openstreetmap.org/copyright). Kartenstil OpenFreeMap, Routing Valhalla.") })
    }
    crash?.let { txt ->
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(onDismissRequest = { crash = null }, title = { Text("Fehlerbericht") },
            text = { Text(txt.ifEmpty { "Kein Absturz gespeichert." }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = Tokens.touchDrive * 6).verticalScroll(rememberScrollState()).testTag("crashText")) },
            confirmButton = { TextButton(enabled = txt.isNotEmpty(), onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(txt)); crash = null }, modifier = Modifier.testTag("crashCopy")) { Text("Kopieren") } },
            dismissButton = { Row { TextButton(enabled = txt.isNotEmpty(), onClick = { CrashLog.clear(ctx); crash = null }) { Text("Löschen") }; TextButton(onClick = { crash = null }) { Text("Schließen") } } })
    }
}

/** Abgerundete Karte für eine Gruppe von Einträgen, mit feinen Trennlinien. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) =
    Surface(shape = RoundedCornerShape(Tokens.radiusLg), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = Tokens.spaceSm)) {
        Column(content = content)
    }

@Composable
fun GroupTitle(t: String) =
    Text(t.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Tokens.spaceXxl, start = Tokens.spaceLg, bottom = Tokens.spaceXs))

@Composable
fun Hint(t: String) =
    Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Tokens.spaceLg, vertical = Tokens.spaceMd))

@Composable
fun groupItemColors(): ListItemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
