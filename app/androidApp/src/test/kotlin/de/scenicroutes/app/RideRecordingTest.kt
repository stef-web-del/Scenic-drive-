package de.scenicroutes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.rec.LiveRecording
import de.scenicroutes.app.rec.Perms
import de.scenicroutes.app.rec.RecState
import de.scenicroutes.app.rec.RecordScreen
import de.scenicroutes.app.rec.RecoveryDialog
import de.scenicroutes.app.rec.RideRecorder
import de.scenicroutes.app.rec.SaveRideContent
import de.scenicroutes.app.ui.LoopListScreen
import de.scenicroutes.app.ui.RideDetailScreen
import de.scenicroutes.app.ui.RidesScreen
import de.scenicroutes.app.ui.ScenicTheme
import de.scenicroutes.app.ui.TrackShape
import de.scenicroutes.app.ui.exportRide
import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.trip.Csv
import de.scenicroutes.shared.trip.RideMath
import de.scenicroutes.shared.trip.TrackPoint
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M4: Aufzeichnung, Pause, Speichern-Frage, Absturz-Wiederherstellung, Export, Bildschirme. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = ScenicApp::class)
class RideRecordingTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<ScenicApp>()
    private val g get() = app.graph
    private val loop: Loop get() = g.catalog.loops.filter { it.rep }.maxBy { it.tour }

    /** Fahrt entlang einer Runde (Anteil share), ~1 Hz, mit g-Werten wie vom Dienst. */
    private fun drive(rec: RideRecorder, l: Loop, share: Double = 1.0, t0: Long = 1_760_000_000_000) {
        val c = l.coords; val n = (c.size * share).toInt().coerceAtLeast(2)
        var t = t0; val pts = mutableListOf<TrackPoint>()
        for (i in 0 until n) {
            val v = 16.0 + 6 * sin(i / 9.0)
            if (i > 0) t += (Geo.distanceM(c[i - 1], c[i]) / v * 1000).toLong().coerceAtLeast(200)
            pts += TrackPoint(t, c[i].lat, c[i].lon, 150 + 60 * sin(i / 40.0), v, 4.0)
        }
        val lat = RideMath.gpsLatG(pts); val lon = RideMath.gpsLonG(pts)
        pts.forEachIndexed { i, p -> rec.onFix(p.copy(latG = lat[i], lonG = lon[i], gPeak = kotlin.math.hypot(lat[i], lon[i]))) }
    }

    @Test fun aufzeichnungPauseSpeichern() {
        val r = g.recorder
        val id = r.start(null, RideRecorder.FREE + " · Test")
        assertEquals(id, r.start(null, "zweiter Start")) // läuft schon → keine zweite Fahrt
        drive(r, loop, 0.3)
        assertTrue(g.trips.pointCount(id) > 10) // laufend in der DB, nicht erst am Ende
        val km = r.state.value!!.distM; assertTrue(km > 1000)
        r.pause(); assertTrue(r.state.value!!.paused)
        val before = g.trips.pointCount(id); r.onFix(TrackPoint(1, 49.7, 9.2, null, 1.0, 3.0)); assertEquals(before, g.trips.pointCount(id)) // Pause: nichts
        r.resume(); assertEquals(1, r.state.value!!.seg)
        val a = r.requestStop()!!
        assertTrue(r.state.value!!.paused); assertNull(a.loopShare); assertFalse(a.short)
        r.save(a)
        assertNull(r.state.value); assertNull(r.ask.value)
        val t = g.trips.trips().single { it.id == id }
        assertEquals("done", t.state); assertTrue((t.distanceM ?: 0.0) > 1000); assertNotNull(t.ascentM); assertTrue((t.curves ?: 0) >= 0)
    }

    @Test fun rundeAb80ProzentSchonGefahren() {
        val r = g.recorder
        r.start(loop.id, loop.titleShort); drive(r, loop, 0.5)
        val half = r.requestStop()!!; assertTrue(half.loopShare!! in 0.3..0.7); assertFalse(half.willMarkDriven)
        r.save(half); assertFalse(loop.id in g.local.driven())
        r.start(loop.id, loop.titleShort); drive(r, loop, 1.0)
        val full = r.requestStop()!!; assertTrue(full.willMarkDriven)
        r.save(full); assertTrue(loop.id in g.local.driven())
    }

    @Test fun verwerfenUndKurzeFahrt() {
        val r = g.recorder; val id = r.start(null, "kurz")
        r.onFix(TrackPoint(0, 49.7, 9.2, null, 2.0, 3.0)); r.onFix(TrackPoint(5000, 49.7001, 9.2, null, 2.0, 3.0))
        val a = r.requestStop()!!; assertTrue(a.short)
        r.discard(a); assertNull(r.state.value); assertTrue(g.trips.trips().none { it.id == id }); assertEquals(0, g.trips.pointCount(id))
    }

    /** Prozess wird beendet (Absturz/Kill): neuer Rekorder findet die Fahrt und setzt sie in neuem Abschnitt fort. */
    @Test fun absturzWiederherstellung() {
        val r1 = g.recorder; val id = r1.start(loop.id, loop.titleShort); drive(r1, loop, 0.4)
        val n = g.trips.pointCount(id)
        val r2 = RideRecorder(g.trips, { i -> g.loop(i)?.let { Polyline(it.coords) to it.titleShort } }, { i, rev -> g.local.markDriven(i, rev) })
        val orphan = r2.unfinished().single(); assertEquals(id, orphan.id)
        r2.resumeFrom(orphan)
        val s = r2.state.value!!; assertEquals(1, s.seg); assertTrue(s.distM > 1000); assertEquals(loop.id, s.loopId)
        r2.onFix(TrackPoint(1_770_000_000_000, loop.coords.last().lat, loop.coords.last().lon, null, 10.0, 3.0))
        assertEquals(n + 1, g.trips.pointCount(id))
        r2.save(r2.requestStop()!!); assertTrue(r2.unfinished().isEmpty())
        assertTrue(g.trips.points(id).last().seg == 1)
    }

    @Test fun exportGpxUndCsv() {
        val r = g.recorder; val id = r.start(loop.id, loop.titleShort); drive(r, loop, 0.2); r.save(r.requestStop()!!)
        val t = g.trips.trip(id)!!
        val gpx = exportRide(app, t, true).readText(); val csv = exportRide(app, t, false).readLines()
        assertTrue(gpx.startsWith("<?xml") && gpx.contains("<trkpt") && gpx.contains("sr:latG"))
        assertEquals(Csv.HEADER, csv[0]); assertEquals(g.trips.pointCount(id).toInt() + 1, csv.size)
    }

    // ---------- UI ----------

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent { ScenicTheme(dark = dark) { androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } } }
        rule.waitForIdle(); rule.onRoot().captureRoboImage("../docs/screenshots/m4-$name-${if (dark) "dark" else "light"}.png")
    }

    @Test fun saveSheetZweiterTippZumVerwerfen() {
        val r = g.recorder; r.start(null, "kurz"); r.onFix(TrackPoint(0, 49.7, 9.2, null, 2.0, 3.0))
        val a = r.requestStop()!!
        var discarded = 0; var saved = 0
        rule.setContent { ScenicTheme { SaveRideContent(a, { saved++ }, {}, { discarded++ }) } }
        rule.onNodeWithTag("shortHint").assertIsDisplayed()
        rule.onNodeWithTag("discardRide").performClick(); assertEquals(0, discarded)
        rule.onNodeWithText("Wirklich verwerfen").assertIsDisplayed()
        rule.onNodeWithTag("discardRide").performClick(); assertEquals(1, discarded)
        rule.onNodeWithTag("saveRide").performClick(); assertEquals(1, saved)
    }

    @Test fun startCheckOhneStandortGesperrt() {
        rule.setContent { ScenicTheme { RecordScreen({}, {}, permsOverride = Perms(location = false, notifications = false, battery = false)) } }
        rule.onNodeWithTag("recStart").assertIsNotEnabled()
        rule.onNodeWithTag("permLocation").assertIsDisplayed()
    }

    @Test fun shotsLight() = shots(false)
    @Test fun shotsDark() = shots(true)
    private fun shots(dark: Boolean) {
        val now = System.currentTimeMillis()
        shot("1-start-check", dark) { RecordScreen({}, {}, permsOverride = Perms(location = true, notifications = false, battery = false)) }
    }
    @Test fun liveLight() = live(false)
    @Test fun liveDark() = live(true)
    private fun live(dark: Boolean) {
        val now = System.currentTimeMillis()
        shot("2-aufzeichnung", dark) { LiveRecording(Modifier, RecState(1, null, "Freie Fahrt · Do 29. Okt., 14:05", now - 2_712_000, 2_712_000 - 60_000, now - 60_000, 38_420.0, 1, 0.31, 64.0, 2300), {}, {}, {}) }
    }
    @Test fun listeMitAufnahmeLight() = liste(false)
    @Test fun listeMitAufnahmeDark() = liste(true)
    private fun liste(dark: Boolean) {
        val now = System.currentTimeMillis()
        shot("3-liste-aufnahme", dark) { LoopListScreen(g.catalog, {}, {}, {}, recording = RecState(1, null, "Freie Fahrt", now, 754_000, now, 12_300.0, 0)) }
    }
    @Test fun listeOhneLight() = shot("0-liste-einstieg", false) { LoopListScreen(g.catalog, {}, {}, {}) }
    @Test fun listeOhneDark() = shot("0-liste-einstieg", true) { LoopListScreen(g.catalog, {}, {}, {}) }

    @Test fun sheetLight() = sheet(false)
    @Test fun sheetDark() = sheet(true)
    private fun sheet(dark: Boolean) {
        val r = g.recorder; r.start(loop.id, loop.titleShort); drive(r, loop, 0.86)
        val a = r.requestStop()!!
        shot("4-speichern", dark) { SaveRideContent(a, {}, {}, {}) }
    }
    @Test fun recoveryLight() = shot("5-unterbrochen", false) { RecoveryDialog("Freie Fahrt · Do 29. Okt., 14:05", System.currentTimeMillis() - 3_600_000, {}, {}, {}) }
    @Test fun recoveryDark() = shot("5-unterbrochen", true) { RecoveryDialog("Freie Fahrt · Do 29. Okt., 14:05", System.currentTimeMillis() - 3_600_000, {}, {}, {}) }

    private fun seed(): Long {
        val r = g.recorder
        r.start(null, "Fahrt ab Miltenberg"); drive(r, g.catalog.loops.filter { it.rep }.sortedBy { it.km }[2], 1.0, 1_761_000_000_000); r.save(r.requestStop()!!)
        val id = r.start(loop.id, loop.titleShort); drive(r, loop, 0.9); r.save(r.requestStop()!!)
        return id
    }
    @Test fun fahrtenLight() = fahrten(false)
    @Test fun fahrtenDark() = fahrten(true)
    private fun fahrten(dark: Boolean) { seed(); shot("6-meine-fahrten", dark) { RidesScreen({}, {}, {}) }; Thread.sleep(300); rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/m4-6-meine-fahrten-${if (dark) "dark" else "light"}.png") }

    @Test fun detailLight() = detail(false)
    @Test fun detailDark() = detail(true)
    private fun detail(dark: Boolean) {
        val id = seed()
        shot("7-auswertung", dark) { RideDetailScreen(id, {}, map = { d, _, _, m -> TrackShape(d.pts, m) }) }
        Thread.sleep(300); rule.waitForIdle()
        rule.onNodeWithTag("stats").assertIsDisplayed()
        rule.onRoot().captureRoboImage("../docs/screenshots/m4-7-auswertung-${if (dark) "dark" else "light"}.png")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("chartSpeed"))
        rule.onNodeWithTag("chartSpeed").performClick()
        rule.onNodeWithTag("scrubInfo").assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("gg"))
        rule.onRoot().captureRoboImage("../docs/screenshots/m4-8-diagramme-${if (dark) "dark" else "light"}.png")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("safety"))
        rule.onRoot().captureRoboImage("../docs/screenshots/m4-9-gg-hinweis-${if (dark) "dark" else "light"}.png")
    }
}
