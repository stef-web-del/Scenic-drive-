package de.scenicroutes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertEquals

/** Die vier Tabs (Planen, Entdecken, Meine Routen, Fahrten) und der Fahrmodus: Bedienung und Bildschirmfotos hell/dunkel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi", application = ScenicApp::class)
class TabsUiTest {
    @get:Rule val rule = createComposeRule()
    private var opened: Int? = null; private var settings = 0; private var record = 0

    // MapLibre rendert in Robolectric nicht; die Karten sind Platzhalter in Kartenfarbe.
    private val planMap: @androidx.compose.runtime.Composable (Pair<Double, Double>, Pair<Double, Double>?, Modifier, (Double, Double) -> Unit) -> Unit =
        { _, _, m, _ -> Box(m.background(LocalMapColors.current.land).testTag("planMapStub")) }
    private val aroundMap: @androidx.compose.runtime.Composable (de.scenicroutes.shared.model.Loop, Modifier) -> Unit =
        { _, m -> Box(m.background(LocalMapColors.current.land)) }

    private fun shell(tab: Tab, dark: Boolean = false, discover: Int = 1) = rule.setContent {
        ScenicTheme(dark = dark) {
            MainShell(catalog, emptySet(), null, { opened = it }, {}, {}, { record++ }, { settings++ }, initial = tab, planMap = planMap, aroundMap = aroundMap, discoverInitial = discover)
        }
    }
    private fun shot(name: String, dark: Boolean) { rule.waitForIdle(); rule.onRoot().captureRoboImage("../docs/screenshots/tabs/$name-${if (dark) "dark" else "light"}.png") }

    @Test fun planenLight() { shell(Tab.PLANEN); shot("1-planen", false) }
    @Test fun planenDark() { shell(Tab.PLANEN, true); shot("1-planen", true) }
    @Test fun entdeckenLight() { shell(Tab.ENTDECKEN); shot("2-entdecken-katalog", false) }
    @Test fun entdeckenDark() { shell(Tab.ENTDECKEN, true); shot("2-entdecken-katalog", true) }
    @Test fun rundUmDichLight() { shell(Tab.ENTDECKEN, discover = 0); shot("2-entdecken-rund-um-dich", false) }
    @Test fun rundUmDichDark() { shell(Tab.ENTDECKEN, true, discover = 0); shot("2-entdecken-rund-um-dich", true) }
    @Test fun routenLight() { shell(Tab.ROUTEN); shot("3-meine-routen", false) }
    @Test fun routenDark() { shell(Tab.ROUTEN, true); shot("3-meine-routen", true) }
    @Test fun fahrtenLight() { shell(Tab.FAHRTEN); shot("4-fahrten", false) }
    @Test fun fahrtenDark() { shell(Tab.FAHRTEN, true); shot("4-fahrten", true) }

    @Test fun fahrmodusLight() = drive(false)
    @Test fun fahrmodusDark() = drive(true)
    private fun drive(dark: Boolean) {
        rule.setContent { ScenicTheme(dark = dark) { Box(Modifier.fillMaxSize().background(LocalMapColors.current.land)) {
            DriveHud("350", "m", "Rechts auf die Talstraße", "links Richtung Mönchberg", de.scenicroutes.app.data.NavPrefs.kpis(de.scenicroutes.app.data.NavPrefs(), "17:42", 38.0, 3120.0, null, null))
        } } }
        shot("5-fahrmodus", dark)
    }

    @Test fun tabsWechseln() {
        shell(Tab.PLANEN)
        rule.onNodeWithText("Runde planen").assertExists()
        rule.onNodeWithTag("tab_ENTDECKEN").performClick()
        rule.onNodeWithText("${catalog.loops.size} Runden ab Miltenberg").assertExists()
        rule.onNodeWithTag("tab_ROUTEN").performClick()
        rule.onNodeWithTag("gpxImport").assertExists()
        rule.onNodeWithTag("tab_FAHRTEN").performClick()
        rule.onNodeWithTag("recordFab").assertExists().performClick()
        assertEquals(1, record)
        rule.onNodeWithTag("gpxImport").assertDoesNotExist()
        rule.onNodeWithTag("settings").performClick(); assertEquals(1, settings)
    }

    @Test fun entdeckenUmschaltenUndOeffnen() {
        shell(Tab.ENTDECKEN)
        rule.onNodeWithTag("discoverAround").performClick()
        rule.onNodeWithText("${catalog.loops.size} Runden ab Miltenberg").assertDoesNotExist()
        rule.onNodeWithTag("discoverCatalog").performClick()
        rule.onAllNodes(hasClickAction() and hasText(catalog.loops.first().titleShort, substring = true)).onFirst().performClick()
        assertEquals(true, opened != null)
    }

    @Test fun fahrmodusTippzieleGross() {
        rule.setContent { ScenicTheme { DriveHud("1,2", "km", "Hauptstraße", null, de.scenicroutes.app.data.NavPrefs.kpis(de.scenicroutes.app.data.NavPrefs(), "18:00", 10.0, 900.0, null, null)) } }
        rule.onNodeWithContentDescription("Navigation beenden").assertWidthIsAtLeast(Tokens.touchDrive).assertHeightIsAtLeast(Tokens.touchDrive)
        rule.onNodeWithTag("driveTurn").assertExists(); rule.onNodeWithText("Danach:", substring = true).assertDoesNotExist()
    }
}
