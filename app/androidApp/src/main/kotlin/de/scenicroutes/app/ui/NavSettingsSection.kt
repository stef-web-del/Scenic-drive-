package de.scenicroutes.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Volume2
import de.scenicroutes.app.data.MapOrientation
import de.scenicroutes.app.data.NavPrefs
import de.scenicroutes.app.data.NavTheme
import de.scenicroutes.app.data.NavZoom
import de.scenicroutes.app.data.Settings
import de.scenicroutes.app.nav.NavSpeech
import java.util.Locale

private data class Choice(val key: String?, val label: String)

/** Unterseite „Ansagen & Stimme“. Alles sofort gespeichert. */
@Composable
fun VoiceSettings(s: Settings, speechEnabled: Boolean = true) {
    val p by s.navFlow.collectAsState()
    val ctx = LocalContext.current
    // Eigene TTS-Instanz nur für Stimmenliste und Test-Ansage; im Test (Robolectric) abschaltbar.
    val speech = remember { if (speechEnabled) NavSpeech(ctx, p) else null }
    DisposableEffect(speech) { speech?.start(); onDispose { speech?.shutdown() } }
    val ready by (speech?.readyState ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }

    SettingsGroup {
        ListItem(headlineContent = { Text("Sprachansagen") }, supportingContent = { Text("Deutsch, auch über die Stumm-Taste in der Fahrleiste") },
            trailingContent = { Switch(p.voiceOn, { v -> s.updateNav { it.copy(voiceOn = v) } }, Modifier.testTag("navVoiceOn")) }, colors = groupItemColors())
        ListItem(headlineContent = { Text("Musik bei Ansagen leiser") }, supportingContent = { Text("Andere Apps werden kurz abgesenkt, auch über Bluetooth im Auto") },
            trailingContent = { Switch(p.duck, { v -> s.updateNav { it.copy(duck = v) } }, Modifier.testTag("navDuck")) }, colors = groupItemColors())
    }
    val engines = if (ready) speech?.engines().orEmpty() else emptyList()
    val voices = if (ready) speech?.germanVoices().orEmpty() else emptyList()
    GroupTitle("Stimme")
    SettingsGroup {
        ListItem(headlineContent = { Text("Sprachausgabe (Engine)") }, modifier = Modifier.testTag("navEngine").clickable(role = Role.Button) { dialog = "engine" },
            supportingContent = { Text(engines.firstOrNull { it.name == p.engine }?.label ?: p.engine ?: "Systemstandard") }, colors = groupItemColors())
        ListItem(headlineContent = { Text("Stimme") }, modifier = Modifier.testTag("navVoiceName").clickable(role = Role.Button) { dialog = "voice" },
            supportingContent = { Text(p.voiceName?.let(::voiceLabel) ?: "Standardstimme (Deutsch)") }, colors = groupItemColors())
    }
    GroupTitle("Klang")
    SettingsGroup {
        Level("Sprechtempo", p.rate, NavPrefs.RATE_MIN, NavPrefs.RATE_MAX, "navRate", ::factor) { v -> s.updateNav { it.copy(rate = v) } }
        Level("Tonhöhe", p.pitch, NavPrefs.PITCH_MIN, NavPrefs.PITCH_MAX, "navPitch", ::factor) { v -> s.updateNav { it.copy(pitch = v) } }
        Level("Lautstärke der Ansagen", p.volume, NavPrefs.VOLUME_MIN, NavPrefs.VOLUME_MAX, "navVolume", { "${(it * 100).toInt()} %" }) { v -> s.updateNav { it.copy(volume = v) } }
    }
    FilledTonalButton(onClick = { speech?.apply(s.nav); speech?.speak(NavSpeech.TEST_TEXT, "test-${System.currentTimeMillis()}") },
        enabled = speech != null, modifier = Modifier.padding(top = Tokens.spaceLg).fillMaxWidth().heightIn(min = Tokens.touchMin).testTag("navTestVoice")) {
        Icon(Lucide.Volume2, null); Text("Test-Ansage", Modifier.padding(start = Tokens.spaceSm))
    }

    when (dialog) {
        "engine" -> ChoiceDialog("Sprachausgabe", listOf(Choice(null, "Systemstandard")) + engines.map { Choice(it.name, it.label) }, p.engine,
            empty = "Keine weitere Sprachausgabe gefunden.", onDismiss = { dialog = null }) { k -> s.updateNav { it.copy(engine = k, voiceName = null) } }
        "voice" -> ChoiceDialog("Stimme", listOf(Choice(null, "Standardstimme (Deutsch)")) + voices.map { Choice(it.name, voiceLabel(it.name) + if (it.isNetworkConnectionRequired) " · online" else "") }, p.voiceName,
            empty = if (ready) "Diese Sprachausgabe bietet keine weiteren deutschen Stimmen." else "Sprachausgabe startet …", onDismiss = { dialog = null }) { k -> s.updateNav { it.copy(voiceName = k) } }
    }
}

/** Unterseite „Karte & Ansicht“. */
@Composable
fun MapSettings(s: Settings) {
    val p by s.navFlow.collectAsState()
    SettingsGroup {
        Chips("Ausrichtung", MapOrientation.entries.map { it.name to it.label }, p.orientation.name, "navOrient") { k -> s.updateNav { it.copy(orientation = MapOrientation.valueOf(k)) } }
        Chips("Zoom", NavZoom.entries.map { it.name to it.label }, p.zoom.name, "navZoom") { k -> s.updateNav { it.copy(zoom = NavZoom.valueOf(k)) } }
        Chips("Tag/Nacht", NavTheme.entries.map { it.name to it.label }, p.theme.name, "navTheme") { k -> s.updateNav { it.copy(theme = NavTheme.valueOf(k)) } }
        Toggle("Bildschirm anlassen", "Während der Zielführung geht das Display nicht aus", p.keepScreenOn, "navKeepScreenOn") { v -> s.updateNav { it.copy(keepScreenOn = v) } }
    }
    Hint("Auto (Tempo): in der Stadt näher, auf schnellen Straßen weiter. Auto (Sonnenstand): dunkel nach Sonnenuntergang am Start der Runde.")
}

/** Unterseite „Fahrleiste“: welche Kennzahlen unten stehen. Die erste eingeschaltete wird groß angezeigt. */
@Composable
fun BarSettings(s: Settings) {
    val p by s.navFlow.collectAsState()
    Hint("Die erste eingeschaltete Kennzahl steht groß links, die anderen klein daneben.")
    SettingsGroup {
        Toggle("Ankunftszeit", "Uhrzeit am Ziel", p.showEta, "barEta") { v -> s.updateNav { it.copy(showEta = v) } }
        Toggle("Restzeit", "Minuten bis zum Ziel", p.showMin, "barMin") { v -> s.updateNav { it.copy(showMin = v) } }
        Toggle("Rest-Kilometer", "Strecke bis zum Ziel", p.showKm, "barKm") { v -> s.updateNav { it.copy(showKm = v) } }
        Toggle("Fortschritt der Runde", "Balken und Prozent, Anfahrt zählt nicht", p.showProgress, "barProgress") { v -> s.updateNav { it.copy(showProgress = v) } }
        Toggle("Aktuelles Tempo", "Aus dem GPS, nicht vom Tacho", p.showSpeed, "barSpeed") { v -> s.updateNav { it.copy(showSpeed = v) } }
        Toggle("Verbleibende Kurven", "Bis zum Ziel, Kehren extra gezählt", p.showCurves, "barCurves") { v -> s.updateNav { it.copy(showCurves = v) } }
        Toggle("Höhe", "Aus dem Geländemodell, offline", p.showElevation, "barElevation") { v -> s.updateNav { it.copy(showElevation = v) } }
        Toggle("Gewonnene Höhenmeter", "Anstiege seit Start der Runde", p.showAscent, "barAscent") { v -> s.updateNav { it.copy(showAscent = v) } }
        Toggle("g-Werte", "Aktuell und Maximum, nur mit Halterung", p.showG, "barG") { v -> s.updateNav { it.copy(showG = v) } }
    }
    Hint("Fehlen Daten, bleibt die Kennzahl weg statt zu raten: Höhe und Höhenmeter gibt es nur auf Katalogrunden mit Höhenprofil und erst auf der Runde. " +
        "g-Werte brauchen eine feste Handyhalterung und werden bei jedem Stillstand neu kalibriert; angezeigt wird die waagrechte Beschleunigung.")
}

/** Kurzfassung für die Hauptseite. */
fun voiceSummary(p: NavPrefs) = if (!p.voiceOn) "Aus" else listOfNotNull("An", p.voiceName?.let(::voiceLabel) ?: "Standardstimme", factor(p.rate).takeIf { p.rate != 1f }).joinToString(" · ")
fun mapSummary(p: NavPrefs) = listOf(p.orientation.label, "Zoom " + if (p.zoom == NavZoom.AUTO) "auto" else p.zoom.label.lowercase(), "Tag/Nacht " + p.theme.label.substringBefore(" (").lowercase()).joinToString(" · ")
fun barSummary(p: NavPrefs) = listOfNotNull("Ankunft".takeIf { p.showEta }, "Restzeit".takeIf { p.showMin }, "km".takeIf { p.showKm }, "Fortschritt".takeIf { p.showProgress }, "Tempo".takeIf { p.showSpeed }, "Kurven".takeIf { p.showCurves }, "Höhe".takeIf { p.showElevation }, "Höhenmeter".takeIf { p.showAscent }, "g".takeIf { p.showG })
    .joinToString(", ").ifEmpty { "Nur Tasten" }

private fun factor(v: Float) = String.format(Locale.GERMANY, "%.1f×", v)
/** „de-de-x-deb-local“ → „deb (lokal)“; unbekannte Formate bleiben, wie sie sind. */
fun voiceLabel(name: String): String {
    val parts = name.split("-x-")
    if (parts.size != 2) return name
    val (id, kind) = parts[1].split("-", limit = 2).let { it[0] to it.getOrNull(1) }
    return id + when (kind) { "local" -> " (lokal)"; "network" -> " (online)"; null -> ""; else -> " ($kind)" }
}

@Composable private fun Toggle(label: String, sub: String, on: Boolean, tag: String, set: (Boolean) -> Unit) =
    ListItem(headlineContent = { Text(label) }, supportingContent = { Text(sub) }, trailingContent = { Switch(on, set, Modifier.testTag(tag)) }, colors = groupItemColors())

@Composable private fun Level(label: String, v: Float, min: Float, max: Float, tag: String, fmt: (Float) -> String, set: (Float) -> Unit) {
    var local by remember(v) { mutableStateOf(v) }
    Column(Modifier.padding(horizontal = Tokens.spaceLg, vertical = Tokens.spaceSm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(fmt(local), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(local, { local = it }, valueRange = min..max, steps = ((max - min) * 10).toInt() - 1,
            onValueChangeFinished = { set((local * 10).let(Math::round) / 10f) }, modifier = Modifier.testTag(tag))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Chips(label: String, items: List<Pair<String, String>>, sel: String, tag: String, set: (String) -> Unit) =
    Column(Modifier.padding(horizontal = Tokens.spaceLg, vertical = Tokens.spaceMd)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm), modifier = Modifier.testTag(tag)) {
            items.forEach { (k, l) -> FilterChip(sel == k, { set(k) }, { Text(l) }, Modifier.testTag("$tag-$k")) }
        }
    }

@Composable private fun ChoiceDialog(title: String, items: List<Choice>, sel: String?, empty: String, onDismiss: () -> Unit, set: (String?) -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            items.forEach { c -> Row(Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).selectable(selected = sel == c.key, role = Role.RadioButton) { set(c.key); onDismiss() }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(sel == c.key, onClick = null); Spacer(Modifier.size(Tokens.spaceMd)); Text(c.label) } }
            if (items.size == 1) Text(empty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
