package de.scenicroutes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.ui.*
import de.scenicroutes.shared.model.Loop
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos

/**
 * Vorschau des Designvorschlags Dunkelblau/Bronze (Tokens.Brand) für Stefans Freigabe.
 * Ausgabe: docs/screenshots/vorschau-design/. Die Karte ist eine Skizze aus den Karten-Tokens
 * (echte Waldflächen aus landcover_osm, Straßen = Katalog-Geometrien), weil MapLibre in Robolectric nicht rendert.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi", application = ScenicApp::class)
class DesignPreviewTest {
    @get:Rule val rule = createComposeRule()
    private val top get() = catalog.loops.filter { it.rep }.maxBy { it.tour }
    private val wald by lazy { Json.parseToJsonElement(File("src/test/resources/preview_wald.json").readText()).jsonObject }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent { ScenicTheme(dark = dark, brand = true) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/vorschau-design/$name-${if (dark) "dark" else "light"}.png")
    }

    /** Kartenskizze: Land, Wald, Straßen entsättigt, Alternativen grau, Anfahrt gestrichelt, Route Bronze mit Kontur. */
    @Composable fun Sketch(loop: Loop, modifier: Modifier, zoom: Double = 1.0, approach: Boolean = true) {
        val m = LocalMapColors.current
        val alts = catalog.loops.filter { it.family == loop.family && it.id != loop.id }.take(2)
        val bb = wald["bbox"]!!.jsonArray.map { it.jsonPrimitive.double }
        val forest = wald["forest"]!!.jsonArray.map { p -> p.jsonArray.map { q -> q.jsonArray.let { it[0].jsonPrimitive.double to it[1].jsonPrimitive.double } } }
        Canvas(modifier.clipToBounds().background(m.land)) {
            val k = cos(Math.toRadians(loop.geom[0][1]))
            val xs = loop.geom.map { it[0] * k }; val ys = loop.geom.map { it[1] }
            val cx = (xs.min() + xs.max()) / 2; val cy = (ys.min() + ys.max()) / 2
            val s = minOf(size.width / (xs.max() - xs.min()), size.height / (ys.max() - ys.min())) * 0.82 * zoom
            fun p(lon: Double, lat: Double) = Offset((size.width / 2 + (lon * k - cx) * s).toFloat(), (size.height / 2 - (lat - cy) * s).toFloat())
            fun path(pts: List<Pair<Double, Double>>, close: Boolean = false) = Path().apply { pts.forEachIndexed { i, (x, y) -> val o = p(x, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; if (close) close() }
            forest.forEach { drawPath(path(it, true), m.forest) }
            catalog.loops.forEach { l -> drawPath(path(l.geom.map { it[0] to it[1] }), m.road, style = Stroke(1.5f.dp2(this), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            alts.forEach { l -> drawPath(path(l.geom.map { it[0] to it[1] }), m.routeAlt, style = Stroke(Tokens.altWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            val g = loop.geom.map { it[0] to it[1] }
            if (approach) {
                val a = g[0]; val from = (a.first - 0.025) to (a.second - 0.018)
                drawLine(m.approach, p(from.first, from.second), p(a.first, a.second), Tokens.altWidth.toPx() * 0.75f, StrokeCap.Round,
                    PathEffect.dashPathEffect(floatArrayOf(Tokens.altWidth.toPx() * 2, Tokens.altWidth.toPx() * 2)))
                drawCircle(m.approach, Tokens.altWidth.toPx() * 1.5f, p(from.first, from.second))
            }
            drawPath(path(g), m.routeCasing, style = Stroke((Tokens.routeWidth + Tokens.routeCasing * 2).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path(g), m.routeActive, style = Stroke(Tokens.routeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(m.routeCasing, Tokens.routeWidth.toPx() * 1.6f, p(g[0].first, g[0].second)); drawCircle(m.routeActive, Tokens.routeWidth.toPx(), p(g[0].first, g[0].second))
        }
    }
    private fun Float.dp2(d: androidx.compose.ui.graphics.drawscope.DrawScope) = with(d) { this@dp2 * density }

    private val spot = Spot("Miltenberg, Engelplatz", 49.7036, 9.2554)
    private fun plan(dark: Boolean) = shot("1-planen", dark) {
        MainShell(catalog, emptySet(), null, {}, {}, {}, {}, {}, initial = Tab.PLANEN, planStart = spot)
    }
    private fun karte(dark: Boolean) = shot("2-karte-route", dark) {
        LoopDetailScreen(top, {}, {}, sbm = catalog.sbm, start = catalog.start, family = catalog.loops.filter { it.family == top.family },
            map = { mod, _, _ -> Sketch(top, mod) }, favorite = true)
    }
    private fun fahrt(dark: Boolean) = shot("3-fahrmodus", dark) {
        Box(Modifier.fillMaxSize()) {
            Sketch(top, Modifier.fillMaxSize(), zoom = 2.6, approach = false)
            DriveHud("350", "m", "L 2311 Richtung Kirchzell", "nach 1,2 km links", de.scenicroutes.app.data.NavPrefs.kpis(de.scenicroutes.app.data.NavPrefs(), "14:52", 38.0, 2460.0, null, null))
        }
    }
    @Test fun planDark() = plan(true)
    @Test fun planLight() = plan(false)
    @Test fun karteDark() = karte(true)
    @Test fun karteLight() = karte(false)
    @Test fun fahrtDark() = fahrt(true)
    @Test fun fahrtLight() = fahrt(false)
}
