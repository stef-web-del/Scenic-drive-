package de.scenicroutes.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.data.FreeLoopApi
import de.scenicroutes.app.data.HttpFreeLoopApi
import de.scenicroutes.app.ui.FreeLoopSection
import de.scenicroutes.app.ui.ScenicTheme
import de.scenicroutes.shared.freeloop.FreeLoopJson
import de.scenicroutes.shared.freeloop.JobView
import java.io.File
import java.io.IOException
import java.net.Socket
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** „Runde ab Punkt“: Fortschritt, erste Vorschläge sofort, Mehr anzeigen, Abbrechen, Rückfall ohne Server. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi", application = ScenicApp::class)
class FreeLoopUiTest {
    @get:Rule val rule = createComposeRule()
    private val done = FreeLoopJson.view(File("../shared/src/jvmTest/resources/freeloop_job.json").readText())

    /** Liefert erst „läuft, 1 Runde“, dann das echte Ergebnis. */
    private class Fake(val done: JobView) : FreeLoopApi {
        var polls = 0; var cancelled = false; var lastLimit = 0
        override suspend fun start(lat: Double, lon: Double, name: String) = "job1"
        override suspend fun view(job: String, limit: Int): JobView {
            polls++; lastLimit = limit
            if (cancelled) return done.copy(status = "cancelled", loops = done.loops.take(1), count = 1, more = false)
            return if (polls < 3) done.copy(status = "running", loops = done.loops.take(1), count = 1,
                progress = done.progress.copy(phase = "Kandidaten prüfen", tried = 300, total = 6600)) else done
        }
        override suspend fun cancel(job: String) { cancelled = true }
        override suspend fun nav(job: String, loop: Int) = throw IOException("nicht im Test")
        override suspend fun geometry(job: String, loop: Int) = throw IOException("nicht im Test")
    }

    private var navigated: Int? = null
    private fun show(api: FreeLoopApi?) = rule.setContent { ScenicTheme { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        FreeLoopSection(api, 49.7067 to 9.2641, "Bürgstadt", onNavigate = { navigated = it }) } } }

    @Test fun ersterVorschlagSofortDannMehr() {
        val f = Fake(done); show(f)
        rule.onNodeWithTag("freeLoopsStart").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("freeLoopsProgress").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("freeLoop1").assertExists()
        rule.onRoot().captureRoboImage("../docs/screenshots/13-runde-ab-punkt-suche.png")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("12 Runden gefunden.").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("Navi-Datei holen").fetchSemanticsNodes().size == 6)
        rule.onNodeWithTag("freeLoopsMore").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntil(5_000) { f.lastLimit == 12 }
    }

    @Test fun abbrechen() {
        val f = Fake(done); show(f)
        rule.onNodeWithTag("freeLoopsStart").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("freeLoopsCancel").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("freeLoopsCancel").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Abgebrochen, 1 Runden gefunden.").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(f.cancelled)
    }

    @Test fun ohneServerRueckfallAufKatalog() {
        show(HttpFreeLoopApi("http://127.0.0.1:1"))   // nichts lauscht dort
        rule.onNodeWithTag("freeLoopsStart").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("freeLoopsOffline").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun keinServerEingestelltKeineKarte() {
        show(null); rule.onNodeWithTag("freeLoops").assertDoesNotExist()
    }

    /** Gegen den echten lokalen Server, falls er läuft (127.0.0.1:8765). */
    @Test fun echterServerMitNaviDatei() {
        assumeTrue(runCatching { Socket("127.0.0.1", 8765).close() }.isSuccess)
        show(HttpFreeLoopApi("http://127.0.0.1:8765"))
        rule.onNodeWithTag("freeLoopsStart").performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithText("Runden gefunden.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("../docs/screenshots/14-runde-ab-punkt-ergebnis.png")
        rule.onAllNodesWithText("Navi-Datei holen")[0].performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Navi-Datei gespeichert", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Wie bei Katalogrunden: direkt aus der gespeicherten Datei navigieren
        rule.onNodeWithTag("freeNav1").performScrollTo().assertExists()
        rule.onRoot().captureRoboImage("../docs/screenshots/15-runde-ab-punkt-navigation.png")
        rule.onNodeWithTag("freeNav1").performClick()
        val id = navigated!!
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<ScenicApp>()
        val loop = app.graph.loop(id)!!
        assertTrue(loop.geom.size > 100 && loop.klasse == "frei")
        val (bundle, src) = kotlinx.coroutines.runBlocking { app.graph.data.route(id, null) }
        assertTrue(src == de.scenicroutes.app.data.RouteSource.SAVED && bundle.toString().isNotEmpty())
    }
}
