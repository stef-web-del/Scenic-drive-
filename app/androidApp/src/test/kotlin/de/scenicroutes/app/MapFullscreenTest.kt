package de.scenicroutes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.scenicroutes.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Vollbild-Karte (Stefan 10:21): Knopf, Vollbild, Schließen per X und System-Zurück, Touchziele, Screenshots. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = ScenicApp::class)
class MapFullscreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = "../docs/screenshots/ui-audit-0.6.4/nachher-2"
    private val top get() = catalog.loops.filter { it.rep }.maxBy { it.tour }
    private val modes = mutableListOf<Boolean>()

    private fun detail(dark: Boolean) = rule.setContent {
        ScenicTheme(dark = dark) {
            LoopDetailScreen(top, {}, {}, sbm = catalog.sbm, start = catalog.start, map = { m, _, _ ->
                val full = LocalMapMode.current.fullscreen; modes += full
                Box(m.background(MaterialTheme.colorScheme.surfaceContainer)) { RouteShape(top, Modifier.fillMaxSize().padding(if (full) Tokens.sizeIconHero else Tokens.spaceXxl)) }
            }, favorite = false)
        }
    }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    @Test fun vollbildOeffnenUndSchliessen() {
        detail(dark = true)
        val btn = rule.onNodeWithContentDescription("Karte im Vollbild")
        btn.assertIsDisplayed().assertWidthIsAtLeast(Tokens.touchMin).assertHeightIsAtLeast(Tokens.touchMin)
        captureScreenRoboImage("$dir/karte-vorschau-knopf-dark.png")
        btn.performClick(); rule.waitForIdle()
        rule.onNodeWithTag("mapFullscreenDialog").assertIsDisplayed()
        assertTrue("Karte bekommt Vollbild-Modus", modes.contains(true))
        rule.onNodeWithContentDescription("Vollbild schließen").assertWidthIsAtLeast(Tokens.touchMin).assertHeightIsAtLeast(Tokens.touchMin)
        rule.onNodeWithContentDescription("Mein Standort").assertWidthIsAtLeast(Tokens.touchMin)
        captureScreenRoboImage("$dir/karte-vollbild-dark.png")
        // Schließen per X
        rule.onNodeWithContentDescription("Vollbild schließen").performClick(); rule.waitForIdle()
        rule.onAllNodesWithTag("mapFullscreenDialog").assertCountEquals(0)
        // Schließen per System-Zurück: nur das Vollbild schließt, die Detailseite bleibt
        rule.onNodeWithContentDescription("Karte im Vollbild").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("mapFullscreenDialog").assertIsDisplayed()
        rule.onNodeWithTag("mapFullscreenDialog").performTouchInput { } // Fokus im Dialog
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithTag("mapFullscreenDialog").assertCountEquals(0)
        rule.onNodeWithTag("detailTitle").assertExists()
    }

    @Test fun vollbildHell() {
        detail(dark = false)
        captureScreenRoboImage("$dir/karte-vorschau-knopf-light.png")
        rule.onNodeWithContentDescription("Karte im Vollbild").performClick(); rule.waitForIdle()
        captureScreenRoboImage("$dir/karte-vollbild-light.png")
        assertEquals(1, rule.onAllNodesWithTag("mapFullscreenDialog").fetchSemanticsNodes().size)
    }

    @Test fun komponenteAuchOhneDetailseite() {
        var full = false
        rule.setContent { ScenicTheme(dark = true) { ExpandableMap(Modifier.size(300.dp)) { m -> full = full || LocalMapMode.current.fullscreen; Box(m) } } }
        rule.onNodeWithTag("mapFullscreen").performClick(); rule.waitForIdle()
        assertTrue(full)
    }
}
