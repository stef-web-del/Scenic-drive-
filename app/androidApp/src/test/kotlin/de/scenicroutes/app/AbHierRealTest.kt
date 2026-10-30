package de.scenicroutes.app

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.data.CrashLog
import de.scenicroutes.app.ui.AbHierScreen
import de.scenicroutes.app.ui.LoopListScreen
import de.scenicroutes.app.ui.ScenicTheme
import de.scenicroutes.app.ui.SettingsScreen
import de.scenicroutes.app.ui.SettingsPage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Fehler aus 0.4.0-m5: Einstieg sichtbar, Standort mit Berechtigung ohne Absturz, Fehlerbericht. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi", application = ScenicApp::class)
class AbHierRealTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<ScenicApp>()

    @Test fun einstiegAufKleinemHandySichtbar() {
        var opened = false
        rule.setContent { ScenicTheme { LoopListScreen(app.graph.catalog, {}, {}, {}, onAbHier = { opened = true }) } }
        rule.waitForIdle(); rule.onRoot().captureRoboImage("../docs/screenshots/10-einstieg-fab.png")
        rule.onNodeWithTag("abHier", useUnmergedTree = false).assertIsDisplayed().performClick()
        rule.onNodeWithText("Runde ab hier", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(opened)
    }

    @Test fun standortFreigegebenKeinAbsturz() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val dst = File(app.filesDir, "grid/v1").apply { mkdirs() }
        File("../../webapp/public/grid/v1").listFiles()!!.forEach { it.copyTo(File(dst, it.name), overwrite = true) }
        rule.setContent { ScenicTheme { AbHierScreen({}, {}, {}, map = { _, m -> Box(m) }) } }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Standort nicht verfügbar", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ab Miltenberg-Zentrum").performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("abHierMap").fetchSemanticsNodes().isNotEmpty() }
        // Suchfeld und Kartenknopf schieben das Karussell nach unten; Lazy-Listen bauen nur Sichtbares.
        rule.onNodeWithTag("abHierList").performScrollToNode(hasTestTag("carousel"))
        rule.onNodeWithTag("carousel").assertExists()
    }

    @Test fun startpunktPerOrtssuche() {
        val dst = File(app.filesDir, "grid/v1").apply { mkdirs() }
        File("../../webapp/public/grid/v1").listFiles()!!.forEach { it.copyTo(File(dst, it.name), overwrite = true) }
        rule.setContent { ScenicTheme { AbHierScreen({}, {}, {}, map = { _, m -> Box(m) }) } }
        rule.onNodeWithTag("placeSearch").performTextInput("Burgstadt")   // ohne Umlaut
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("placeHit").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("../docs/screenshots/11-ortssuche.png")
        rule.onAllNodesWithTag("placeHit")[0].performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Gewählt: Bürgstadt").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("abHierStart").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("../docs/screenshots/12-start-buergstadt.png")
    }

    @Test fun fehlerberichtAnzeigen() {
        CrashLog.write(app, "test", "main", IllegalStateException("Testabsturz"))
        rule.setContent { ScenicTheme { SettingsScreen({}, start = SettingsPage.ABOUT) } }
        rule.onNodeWithTag("crashReport").performClick()
        rule.onNodeWithText("Testabsturz", substring = true).assertExists()
        rule.onNodeWithTag("crashCopy").performClick()
    }
}
