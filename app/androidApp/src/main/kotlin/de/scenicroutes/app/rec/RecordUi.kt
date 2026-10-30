package de.scenicroutes.app.rec

import androidx.compose.foundation.layout.heightIn
import de.scenicroutes.app.ui.Tokens
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BatteryWarning
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Square
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.scenicroutes.app.graph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val RecRed: Color @Composable get() = de.scenicroutes.app.ui.statusColors().record

fun fmtDur(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
fun fmtKm1(m: Double) = "%.1f km".format(Locale.GERMANY, m / 1000)
fun freeTitle(now: Long = System.currentTimeMillis()) = RideRecorder.FREE + " · " + SimpleDateFormat("EEE d. MMM, HH:mm", Locale.GERMANY).format(Date(now))

@Composable
fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    return now
}

@Composable
fun RecDot(paused: Boolean, size: Int = 12) {
    val a = if (paused) 1f else rememberInfiniteTransition(label = "rec").animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a").value
    Box(Modifier.size(size.dp).alpha(a).clip(CircleShape).background(if (paused) MaterialTheme.colorScheme.outline else RecRed))
}

/** Statuszeile „● 0:42 · 12,3 km“ – ersetzt den Start-Knopf, solange aufgezeichnet wird. */
@Composable
fun RecordingPill(s: RecState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(!s.paused)
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier.heightIn(min = Tokens.touchDrive).testTag("recPill").semantics { contentDescription = "Aufzeichnung läuft, öffnen" }) {
        Row(Modifier.padding(horizontal = Tokens.spaceXl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSmd)) {
            RecDot(s.paused)
            Text(if (s.paused) "Pausiert" else "Aufnahme", style = MaterialTheme.typography.labelLarge)
            Text(fmtDur(s.elapsedMs(now)) + " · " + fmtKm1(s.distM), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Start-Check ----------

data class Perms(val location: Boolean, val notifications: Boolean, val battery: Boolean)

fun perms(ctx: Context) = Perms(
    location = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
    notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    battery = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(onBack: () -> Unit, onRides: () -> Unit, permsOverride: Perms? = null) {
    val ctx = LocalContext.current; val rec = ctx.graph.recorder
    val s by rec.state.collectAsState()
    var p by remember { mutableStateOf(permsOverride ?: perms(ctx)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && permsOverride == null) p = perms(ctx) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p = perms(ctx) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (s == null) "Fahrt aufzeichnen" else "Aufzeichnung") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, "Zurück") } },
            actions = { TextButton(onClick = onRides) { Text("Meine Fahrten") } })
    }) { pad ->
        val st = s
        if (st == null) StartCheck(Modifier.padding(pad), p,
            onLocation = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            onNotifications = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
            onBattery = { runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
            onStart = { RecordingService.start(ctx, null, freeTitle()) })
        else LiveRecording(Modifier.padding(pad), st, onPause = { ctx.startService(Intent(ctx, RecordingService::class.java).setAction(RecordingService.ACTION_PAUSE)); rec.pause() },
            onResume = { rec.resume(); RecordingService.ensureRunning(ctx) }, onStop = { rec.requestStop() })
    }
}

@Composable
fun StartCheck(modifier: Modifier, p: Perms, onLocation: () -> Unit, onNotifications: () -> Unit, onBattery: () -> Unit, onStart: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
        Text("Zeichnet deine Strecke auf – auch ohne Runde. Alles bleibt auf dem Gerät.", style = MaterialTheme.typography.bodyLarge)
        Card(Modifier.fillMaxWidth().testTag("startCheck")) {
            PermRow(Lucide.MapPin, "Standort (genau)", "Pflicht. Für die Strecke, 1× pro Sekunde. „Immer erlauben“ ist nicht nötig: Die Aufzeichnung läuft als Vordergrund-Dienst mit Benachrichtigung weiter, auch bei Bildschirm aus.", p.location, "Erlauben", onLocation, "permLocation")
            PermRow(Lucide.Bell, "Benachrichtigungen", "Empfohlen. Zeigt Zeit und km und bietet Pause/Stopp, ohne die App zu öffnen.", p.notifications, "Erlauben", onNotifications, "permNotif")
            PermRow(Lucide.BatteryWarning, "Akku-Optimierung", "Empfohlen. Manche Hersteller beenden Hintergrund-Apps trotzdem. Stelle Scenic Routes auf „Nicht optimieren“. Bricht die Aufzeichnung ab, bietet die App beim nächsten Start an, sie fortzusetzen oder zu speichern.", p.battery, "Einstellungen", onBattery, "permBattery")
        }
        Text("Bewegungssensoren (Beschleunigung, Drehrate) werden ohne Erlaubnis gelesen und zu Längs- und Quer-g verrechnet – eine Näherung, abhängig davon, wie das Handy befestigt ist.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Tokens.spaceSm))
        Button(onClick = onStart, enabled = p.location, colors = ButtonDefaults.buttonColors(containerColor = RecRed, contentColor = de.scenicroutes.app.ui.statusColors().onRecord),
            modifier = Modifier.fillMaxWidth().height(Tokens.sizeRecStart).testTag("recStart")) {
            Icon(Lucide.Circle, null); Spacer(Modifier.size(Tokens.spaceSmd)); Text("Aufzeichnung starten", style = MaterialTheme.typography.titleLarge)
        }
        if (!p.location) Text("Zum Starten bitte zuerst den Standort erlauben.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PermRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, ok: Boolean, action: String, onClick: () -> Unit, tag: String) {
    ListItem(
        leadingContent = { Icon(if (ok) Lucide.CircleCheck else icon, null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title + if (ok) " ✓" else "") },
        supportingContent = { Text(text) },
        trailingContent = { if (!ok) OutlinedButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Text(action) } },
    )
}

@Composable
fun LiveRecording(modifier: Modifier, s: RecState, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    val now = rememberNow(!s.paused)
    Column(modifier.fillMaxSize().padding(Tokens.spaceLg).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
            RecDot(s.paused, 16); Text(if (s.paused) "Pausiert" else "Aufzeichnung läuft", style = MaterialTheme.typography.titleMedium)
        }
        Text(s.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Text(fmtDur(s.elapsedMs(now)), fontSize = Tokens.driveTurn, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("recTime"))
        Text("Fahrzeit", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Tokens.spaceXxl))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Big("%.1f".format(Locale.GERMANY, s.distM / 1000), "km", "recKm")
            Big(s.kmh?.let { "%.0f".format(it) } ?: "–", "km/h", "recKmh")
            Big(s.g?.let { "%.2f".format(Locale.GERMANY, it) } ?: "–", "g (Näherung)", "recG")
        }
        Spacer(Modifier.weight(1f))
        if (s.fixes == 0 && !s.paused) Text("Warte auf GPS …", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Tokens.spaceMd))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
            if (s.paused) FilledTonalButton(onClick = onResume, modifier = Modifier.weight(1f).height(Tokens.sizeRecButton).testTag("recResume")) { Icon(Lucide.Play, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Fortsetzen", style = MaterialTheme.typography.titleMedium) }
            else FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).height(Tokens.sizeRecButton).testTag("recPause")) { Icon(Lucide.Pause, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Pause", style = MaterialTheme.typography.titleMedium) }
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = RecRed, contentColor = de.scenicroutes.app.ui.statusColors().onRecord), modifier = Modifier.weight(1f).height(Tokens.sizeRecButton).testTag("recStop")) {
                Icon(Lucide.Square, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("Stopp", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text("Stopp fragt nach, ob die Fahrt gespeichert wird.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Tokens.spaceSm))
    }
}

@Composable
private fun Big(v: String, unit: String, tag: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(v, fontSize = Tokens.driveKpi, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag(tag)); Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------- „Fahrt speichern?“ ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveRideSheet(a: SaveAsk, onSave: () -> Unit, onContinue: (() -> Unit)?, onDiscard: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { onContinue?.invoke() ?: Unit }, sheetState = state) { SaveRideContent(a, onSave, onContinue, onDiscard) }
}

@Composable
fun SaveRideContent(a: SaveAsk, onSave: () -> Unit, onContinue: (() -> Unit)?, onDiscard: () -> Unit) {
    var confirmDiscard by remember(a.tripId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.spaceXl).padding(bottom = Tokens.spaceXxl).navigationBarsPadding().testTag("saveSheet"), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
        Text("Fahrt speichern?", style = MaterialTheme.typography.headlineSmall)
        Text(a.loopTitle?.let { "Runde: $it" } ?: a.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(fmtDur(a.summary.durationS * 1000), "Fahrzeit"); Stat(fmtKm1(a.summary.distanceM), "Strecke")
            a.loopShare?.let { Stat("%.0f %%".format(it * 100), "der Runde") }
        }
        if (a.loopShare != null) Text(
            if (a.willMarkDriven) "Ab 80 % gilt die Runde als „Schon gefahren“ – wird beim Speichern markiert."
            else "Unter 80 % der Runde: wird nicht als „Schon gefahren“ markiert.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("drivenHint"))
        if (a.short) Card(Modifier.fillMaxWidth().testTag("shortHint")) {
            Text("Sehr kurze Fahrt (unter 2 min oder 500 m) – vermutlich versehentlich gestartet. Verwerfen?", Modifier.padding(Tokens.spaceMd), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.touchDrive).testTag("saveRide")) { Text("Speichern", style = MaterialTheme.typography.titleMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
            if (onContinue != null) OutlinedButton(onClick = onContinue, modifier = Modifier.weight(1f).heightIn(min = Tokens.touchMin).testTag("continueRide")) { Text("Weiter aufzeichnen") }
            TextButton(onClick = { if (confirmDiscard) onDiscard() else confirmDiscard = true }, modifier = Modifier.weight(1f).heightIn(min = Tokens.touchMin).testTag("discardRide"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (confirmDiscard) "Wirklich verwerfen" else "Verwerfen") }
        }
        if (confirmDiscard) Text("Nochmal tippen löscht die Aufzeichnung endgültig.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Stat(v: String, l: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(v, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold); Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Nach Absturz/Kill: „Unterbrochene Fahrt gefunden“. */
@Composable
fun RecoveryDialog(title: String, startedMs: Long, onResume: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = {}, modifier = Modifier.testTag("recovery"),
        title = { Text("Unterbrochene Fahrt gefunden") },
        text = { Text("„$title“ (gestartet ${SimpleDateFormat("EEE d. MMM, HH:mm", Locale.GERMANY).format(Date(startedMs))}) wurde nicht beendet – vermutlich wurde die App vom System geschlossen. Die Punkte bis dahin sind gespeichert." + if (confirm) "\n\nNochmal „Verwerfen“ löscht sie endgültig." else "") },
        confirmButton = { Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onResume, modifier = Modifier.testTag("recResumeOld")) { Text("Fortsetzen") }
            TextButton(onClick = onSave, modifier = Modifier.testTag("recSaveOld")) { Text("Speichern") }
            TextButton(onClick = { if (confirm) onDiscard() else confirm = true }, modifier = Modifier.testTag("recDiscardOld"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (confirm) "Wirklich verwerfen" else "Verwerfen") }
        } })
}
