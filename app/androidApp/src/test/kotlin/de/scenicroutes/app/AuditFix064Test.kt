package de.scenicroutes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.scenicroutes.app.nav.DriveBackGuard
import de.scenicroutes.app.ui.AbHierScreen
import de.scenicroutes.app.ui.ScenicTheme
import de.scenicroutes.app.ui.Tokens
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Stefans Entscheidungen zum UI-Audit 0.6.4: A10 (Zurück im Fahrmodus), A11 (Rahmen dunkel), A12 (200 % Schrift). */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = ScenicApp::class)
class AuditFix064Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = "../docs/screenshots/ui-audit-0.6.4/nachher-2"
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    @Test fun zurueckImFahrmodusFragtNach() {
        var stops = 0
        rule.setContent { ScenicTheme(dark = true) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { DriveBackGuard(active = true, onStop = { stops++ }) } } }
        back()
        rule.onNodeWithText("Navigation beenden?").assertIsDisplayed()
        rule.onNodeWithTag("stopNavConfirm").assertHeightIsAtLeast(Tokens.touchDrive)
        rule.onNodeWithTag("stopNavCancel").assertHeightIsAtLeast(Tokens.touchDrive)
        captureScreenRoboImage("$dir/a10-dialog-navigation-beenden-dark.png")
        // Weiterfahren: Dialog zu, Navigation läuft weiter
        rule.onNodeWithText("Weiterfahren").performClick()
        rule.onAllNodesWithText("Navigation beenden?").assertCountEquals(0); assertEquals(0, stops)
        // Zurück im offenen Dialog = Abbrechen
        back(); back()
        rule.onAllNodesWithText("Navigation beenden?").assertCountEquals(0); assertEquals(0, stops)
        // Beenden: genau einmal stopAll
        back(); rule.onNodeWithText("Beenden").performClick()
        assertEquals(1, stops)
    }

    @Test fun dialogHell() {
        rule.setContent { ScenicTheme(dark = false) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { DriveBackGuard(active = true, onStop = {}) } } }
        back(); rule.onNodeWithText("Navigation beenden?").assertIsDisplayed()
        captureScreenRoboImage("$dir/a10-dialog-navigation-beenden-light.png")
    }

    @Test fun ausserhalbFahrmodusNormalesZurueck() {
        var stops = 0
        rule.setContent { ScenicTheme { DriveBackGuard(active = false, onStop = { stops++ }) } }
        rule.runOnUiThread { assertTrue(!rule.activity.onBackPressedDispatcher.hasEnabledCallbacks()) }
        rule.onAllNodesWithText("Navigation beenden?").assertCountEquals(0); assertEquals(0, stops)
    }

    @Test fun rahmenDunkel() {
        rule.setContent { ScenicTheme(dark = true) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceLg)) {
                Text("Rahmen auf Hintergrund #1A1A2E", color = MaterialTheme.colorScheme.onSurface)
                OutlinedCard(Modifier.fillMaxWidth()) { Text("OutlinedCard (outlineVariant/border)", Modifier.padding(Tokens.spaceLg)) }
                HorizontalDivider()
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
                        Text("Auf Fläche #16213E")
                        OutlinedCard(Modifier.fillMaxWidth()) { Text("OutlinedCard", Modifier.padding(Tokens.spaceLg)) }
                        HorizontalDivider()
                        OutlinedButton(onClick = {}) { Text("OutlinedButton") }
                        OutlinedTextField("Bürgstadt", {}, label = { Text("Eingabefeld (outline, funktional)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } }
        rule.waitForIdle(); rule.onRoot().captureRoboImage("$dir/a11-rahmen-dunkel.png")
    }

    @Test fun karussellBei200ProzentSchrift() {
        val app = ApplicationProvider.getApplicationContext<ScenicApp>()
        val dst = File(app.filesDir, "grid/v1").apply { mkdirs() }
        File("../../webapp/public/grid/v1").listFiles()!!.forEach { it.copyTo(File(dst, it.name), overwrite = true) }
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 2f)) {
                ScenicTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AbHierScreen({}, {}, {}, map = { l, m -> Surface(m, color = MaterialTheme.colorScheme.surfaceContainerHigh) { de.scenicroutes.app.ui.RouteShape(l, Modifier.fillMaxSize()) } })
                } }
            }
        }
        rule.waitUntil(60_000) { rule.onAllNodesWithText("Ab Miltenberg-Zentrum").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ab Miltenberg-Zentrum").performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("abHierList").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(60_000) { runCatching { rule.onNodeWithTag("abHierList").performScrollToNode(hasTestTag("carousel")) }.isSuccess }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("$dir/a12-karussell-schrift-200.png")
        // Karte wächst mit dem Text statt ihn abzuschneiden: Mindesthöhe 170 dp, bei 200 % höher.
        val card = rule.onAllNodes(SemanticsMatcher("grid card") { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("grid_") == true })[0]
        card.assertHeightIsAtLeast(Tokens.sizeCardMinH)
        rule.onNodeWithTag("carousel").performScrollToNode(hasTestTag("more"))
        rule.waitForIdle(); rule.onRoot().captureRoboImage("$dir/a12-karussell-schrift-200-mehr.png")
    }
}
