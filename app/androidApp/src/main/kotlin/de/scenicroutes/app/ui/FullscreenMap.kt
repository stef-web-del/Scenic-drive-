package de.scenicroutes.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.compose.camera.CameraPosition

/** Zustand, den eine Karte im Vollbild auswertet (über [LocalMapMode], damit Test-Platzhalter unberührt bleiben). */
data class MapMode(val fullscreen: Boolean = false, val me: Pair<Double, Double>? = null, val meTick: Int = 0)
val LocalMapMode = compositionLocalOf { MapMode() }

/** Kartenoptionen: Vorschau ohne Kompass, Vollbild mit Kompass (erscheint beim Drehen) oben links unter der Statusleiste. */
@Composable
fun mapOptions(mode: MapMode) = if (!mode.fullscreen) MapOptions()
    else MapOptions(ornamentOptions = OrnamentOptions(padding = PaddingValues(top = Tokens.sizeIconHero, start = Tokens.spaceSm, end = Tokens.spaceSm, bottom = Tokens.spaceSm),
        isLogoEnabled = true, logoAlignment = Alignment.BottomStart, isAttributionEnabled = true, attributionAlignment = Alignment.BottomEnd,
        isCompassEnabled = true, compassAlignment = Alignment.TopStart, isScaleBarEnabled = false))

/** Route einpassen (fit bounds mit Polsterung) bzw. auf den eigenen Standort springen. */
suspend fun fitRoute(camera: CameraState, lons: List<Double>, lats: List<Double>) {
    if (lons.isEmpty()) return
    runCatching { camera.animateTo(BoundingBox(lons.min(), lats.min(), lons.max(), lats.max()), padding = PaddingValues(Tokens.sizeIconHero)) }
}
suspend fun flyTo(camera: CameraState, me: Pair<Double, Double>) {
    runCatching { camera.animateTo(CameraPosition(target = org.maplibre.spatialk.geojson.Position(me.second, me.first), zoom = 13.0)) }
}

@Composable
fun MapRoundButton(icon: ImageVector, label: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shape = CircleShape, modifier = modifier.size(Tokens.touchMin).testTag(tag)) { Icon(icon, label) }
}

/**
 * Wiederverwendbare Kartenvorschau mit Vollbild: runder Knopf oben rechts (Maximize) öffnet die Karte
 * randlos (Insets beachtet), Route eingepasst, Zoom/Pan aktiv, Standort- und Kompassknopf;
 * schließen per X oder System-Zurück (predictive back über den Dialog-Dispatcher).
 */
@Composable
fun ExpandableMap(
    modifier: Modifier, underStatusBar: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    map: @Composable (Modifier) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        map(Modifier.fillMaxSize())
        overlay()
        MapRoundButton(Lucide.Maximize, "Karte im Vollbild", "mapFullscreen",
            Modifier.align(Alignment.TopEnd).then(if (underStatusBar) Modifier.statusBarsPadding() else Modifier).padding(Tokens.spaceSm)) { open = true }
    }
    if (open) FullscreenMap(onClose = { open = false }, map = map)
}

@Composable
fun FullscreenMap(onClose: () -> Unit, map: @Composable (Modifier) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    fun locate() = scope.launch {
        val l = runCatching { de.scenicroutes.app.data.Locate.current(ctx) }.getOrNull()
        if (l == null) snack.showSnackbar("Standort nicht verfügbar.") else { me = l; tick++ }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.any { g -> g }) locate() else scope.launch { snack.showSnackbar("Ohne Standortfreigabe kein eigener Standort auf der Karte.") }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Surface(Modifier.fillMaxSize().testTag("mapFullscreenDialog"), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalMapMode provides MapMode(true, me, tick)) { map(Modifier.fillMaxSize()) }
                Column(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Tokens.spaceSm), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                    MapRoundButton(Lucide.X, "Vollbild schließen", "mapFullscreenClose", onClick = onClose)
                    MapRoundButton(Lucide.LocateFixed, "Mein Standort", "mapFullscreenLocate") {
                        val ok = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any { p -> ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED }
                        if (ok) locate() else runCatching { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
            }
        }
    }
}
