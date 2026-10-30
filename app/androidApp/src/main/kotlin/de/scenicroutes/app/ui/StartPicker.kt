package de.scenicroutes.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.scenicroutes.app.data.Places
import de.scenicroutes.shared.places.PlaceHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position

/** Ortssuche (offline, Tippfehler-tolerant). Liefert den gewählten Ort über onPick(name, lat, lon). */
@Composable
fun PlaceSearch(near: Pair<Double, Double>?, onPick: (String, Double, Double) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<PlaceHit>()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(150)   // erst suchen, wenn kurz nicht getippt wird
        runCatching { val idx = Places.index(ctx); withContext(Dispatchers.Default) { idx.search(q, 6, near?.first, near?.second) } }
            .onSuccess { hits = it; failed = false }.onFailure { failed = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
        OutlinedTextField(q, { q = it.take(60) }, singleLine = true, label = { Text("Ort suchen") }, leadingIcon = { Icon(Lucide.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Lucide.X, "Leeren") } },
            modifier = Modifier.fillMaxWidth().testTag("placeSearch"))
        if (failed) Text("Ortsliste nicht ladbar.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (q.trim().length >= 2 && hits.isEmpty() && !failed) Text("Kein Ort gefunden.", style = MaterialTheme.typography.bodySmall)
        hits.forEach { h ->
            Row(Modifier.fillMaxWidth().heightIn(min = Tokens.touchMin).clickable(role = Role.Button) { onPick(h.place.name, h.place.lat, h.place.lon); q = ""; hits = emptyList() }.padding(vertical = Tokens.spaceSm).testTag("placeHit"),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.MapPin, null); Spacer(Modifier.width(Tokens.spaceSm))
                Column(Modifier.weight(1f)) {
                    Text(h.place.name, style = MaterialTheme.typography.bodyLarge)
                    val sub = listOfNotNull(de.scenicroutes.shared.places.Place.KIND_LABEL[h.place.kind], h.place.near.takeIf { it.isNotEmpty() }?.let { "bei $it" }, h.distKm?.let { "%.0f km".format(it) }).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Karte zum Antippen des Startpunkts (außerhalb von Lazy-Listen verwenden). */
@Composable
fun PickMap(center: Pair<Double, Double>, picked: Pair<Double, Double>?, modifier: Modifier, onPick: (Double, Double) -> Unit) {
    val camera = rememberCameraState(firstPosition = CameraPosition(target = Position(center.second, center.first), zoom = 10.5))
    MaplibreMap(modifier = modifier, baseStyle = BaseStyle.Uri(mapStyle()), cameraState = camera,
        onMapClick = { pos, _ -> onPick(pos.latitude, pos.longitude); ClickResult.Consume }) {
        val pt = picked?.let { """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${it.second},${it.first}]}}""" } ?: """{"type":"FeatureCollection","features":[]}"""
        val s = rememberGeoJsonSource(GeoJsonData.JsonString(pt))
        LaunchedEffect(pt) { s.setData(GeoJsonData.JsonString(pt)) }
        CircleLayer(id = "pick", source = s, color = const(LocalMapColors.current.routeActive), radius = const(Tokens.sizeMapPointPick), strokeColor = const(LocalMapColors.current.routeCasing), strokeWidth = const(Tokens.strokeBold))
    }
}
