package de.scenicroutes.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.model.ScoreDefs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
private fun coords(g: List<List<Double>>) = g.joinToString(",") { "[${it[0]},${it[1]}]" }

/** GeoJSON der Abschnitte mit Farbe je Abschnitts-Score (wie Web „Karte nach Abschnitt färben“). */
fun segmentsGeoJson(loop: Loop, only: Int? = null): String {
    val sg = loop.sb?.segments.orEmpty(); val tot = sg.sumOf { it.lenM }.toDouble()
    val f = sg.withIndex().filter { only == null || it.index == only }.joinToString(",") { (i, s) ->
        """{"type":"Feature","properties":{"i":$i,"c":"${hex(segColor(s.score))}"},"geometry":{"type":"LineString","coordinates":[${coords(ScoreDefs.sliceGeom(loop.geom, s.kmStart, s.lenM, tot))}]}}"""
    }
    return """{"type":"FeatureCollection","features":[$f]}"""
}

/** Kartenvorschau der Runde (MapLibre + OpenFreeMap, kostenlos, kein API-Key). */
@Composable
fun LoopMap(loop: Loop, modifier: Modifier, segCol: Boolean = false, selSeg: Int? = null) {
    val g = loop.geom
    val cam = remember(loop.id) {
        val lons = g.map { it[0] }; val lats = g.map { it[1] }
        val cLat = (lats.min() + lats.max()) / 2
        val span = max((lons.max() - lons.min()) * cos(Math.toRadians(cLat)), (lats.max() - lats.min()) * 1.6)
        CameraPosition(target = Position((lons.min() + lons.max()) / 2, cLat), zoom = (ln(360.0 / max(span, 0.01)) / ln(2.0)) - 1.3)
    }
    val camera = rememberCameraState(firstPosition = cam)
    val line = remember(loop.id) { """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[${coords(g)}]}}""" }
    val segs = remember(loop.id, segCol) { if (segCol) segmentsGeoJson(loop) else """{"type":"FeatureCollection","features":[]}""" }
    val sel = remember(loop.id, selSeg) { selSeg?.let { segmentsGeoJson(loop, it) } ?: """{"type":"FeatureCollection","features":[]}""" }
    val start = remember(loop.id) { """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${g[0][0]},${g[0][1]}]}}""" }
    val mode = LocalMapMode.current
    androidx.compose.runtime.LaunchedEffect(mode.fullscreen) { if (mode.fullscreen) fitRoute(camera, g.map { it[0] }, g.map { it[1] }) }
    androidx.compose.runtime.LaunchedEffect(mode.meTick) { mode.me?.let { if (mode.meTick > 0) flyTo(camera, it) } }
    val mc = LocalMapColors.current; val primary = mc.routeActive
    MaplibreMap(modifier = modifier, baseStyle = BaseStyle.Uri(mapStyle()), cameraState = camera, options = mapOptions(mode)) {
        val src = rememberGeoJsonSource(GeoJsonData.JsonString(line))
        LineLayer(id = "loop-casing", source = src, color = const(mc.routeCasing), width = const(Tokens.routeWidth + Tokens.routeCasing * 2), cap = const(LineCap.Round), join = const(LineJoin.Round))
        LineLayer(id = "loop", source = src, color = const(primary), width = const(Tokens.routeWidth), cap = const(LineCap.Round), join = const(LineJoin.Round))
        val ss = rememberGeoJsonSource(GeoJsonData.JsonString(segs))
        LineLayer(id = "segs", source = ss, color = feature["c"].convertToColor(), width = const(Tokens.sizeMapLine), cap = const(LineCap.Round), join = const(LineJoin.Round))
        val sl = rememberGeoJsonSource(GeoJsonData.JsonString(sel))
        LineLayer(id = "sel-casing", source = sl, color = const(mc.routeCasing), width = const(Tokens.sizeMapLineSelCasing), cap = const(LineCap.Round), join = const(LineJoin.Round))
        LineLayer(id = "sel", source = sl, color = const(MaterialTheme.colorScheme.onSurface), width = const(Tokens.sizeMapLineSel), cap = const(LineCap.Round), join = const(LineJoin.Round))
        val s = rememberGeoJsonSource(GeoJsonData.JsonString(start))
        CircleLayer(id = "start", source = s, color = const(primary), radius = const(Tokens.sizeMapPoint), strokeColor = const(mc.routeCasing), strokeWidth = const(Tokens.strokeBold))
        mode.me?.let { me ->
            val ms = rememberGeoJsonSource(GeoJsonData.JsonString("""{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${me.second},${me.first}]}}"""))
            CircleLayer(id = "me", source = ms, color = const(MaterialTheme.colorScheme.primary), radius = const(Tokens.sizeMapPointPick), strokeColor = const(MaterialTheme.colorScheme.onPrimary), strokeWidth = const(Tokens.strokeBold))
        }
    }
}
