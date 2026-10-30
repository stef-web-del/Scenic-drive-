package de.scenicroutes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.ui.AbHierScreen
import de.scenicroutes.app.ui.ScenicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** „Runde ab hier“ mit echten Rasterdaten (aus dem Datei-Cache, wie offline). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = ScenicApp::class)
class AbHierUiTest {
    @get:Rule val rule = createComposeRule()

    private fun prepare(): ScenicApp {
        val app = ApplicationProvider.getApplicationContext<ScenicApp>()
        val src = File("../../webapp/public/grid/v1")
        val dst = File(app.filesDir, "grid/v1").apply { mkdirs() }
        src.listFiles()!!.forEach { it.copyTo(File(dst, it.name), overwrite = true) }
        return app
    }

    private fun open(dark: Boolean = false, nav: (Int) -> Unit = {}) {
        rule.setContent { ScenicTheme(dark = dark) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AbHierScreen({}, {}, nav, map = { l, m -> androidx.compose.material3.Surface(m, color = MaterialTheme.colorScheme.surfaceContainerHigh) { de.scenicroutes.app.ui.RouteShape(l, Modifier.fillMaxSize()) } }) } } }
        rule.waitUntil(60_000) { rule.onAllNodesWithText("Ab Miltenberg-Zentrum").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ab Miltenberg-Zentrum").performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("carousel").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun karussellMehrUndProfile() {
        val app = prepare(); open()
        rule.onNodeWithTag("abHierStart").assertExists()
        rule.onRoot().captureRoboImage("../docs/screenshots/9-ab-hier-light.png")
        rule.onNodeWithTag("carousel").performScrollToNode(hasTestTag("more"))
        rule.onNodeWithTag("more").performClick()
        rule.onNodeWithText("12 von", substring = true).assertExists()
        rule.onNodeWithTag("profile_geniesser").performClick()
        rule.waitUntil(10_000) { app.graph.local.profile.key == "geniesser" } // Schreiben läuft im Hintergrund
    }

    @Test fun umgekehrtGefahrenUndNavigation() {
        val app = prepare(); var navId = -1; open(dark = true) { navId = it }
        rule.onNodeWithTag("abHierList").performScrollToNode(hasTestTag("reverse"))
        rule.onNodeWithTag("startNav").performClick()
        assertTrue(navId > 0); assertTrue(app.graph.loop(navId) != null)
        rule.onNodeWithTag("reverse").performClick()
        rule.onNodeWithTag("startNav").assertIsNotEnabled()
        rule.onNodeWithTag("abHierList").performScrollToNode(hasTestTag("driven")); rule.onNodeWithTag("driven").performScrollTo().performClick()
        rule.waitUntil(10_000) { navId in app.graph.local.driven() }; val d = app.graph.local.driven(); assertTrue("navId=$navId driven=$d", navId in d)
        rule.onRoot().captureRoboImage("../docs/screenshots/9-ab-hier-umgekehrt-dark.png")
    }
}
