package de.scenicroutes.app

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.nav.NavExtras
import de.scenicroutes.app.nav.NavOverlay
import de.scenicroutes.app.ui.LoopDetailScreen
import de.scenicroutes.app.ui.LoopListScreen
import de.scenicroutes.app.ui.RouteShape
import de.scenicroutes.app.ui.ScenicTheme
import de.scenicroutes.shared.model.LoopCatalog
import java.io.File
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasScrollAction
import de.scenicroutes.app.data.AppVersion
import de.scenicroutes.app.data.RouteSource
import de.scenicroutes.app.data.SaveState
import de.scenicroutes.app.data.UpdateState
import de.scenicroutes.app.ui.OfflineCard
import de.scenicroutes.app.ui.UpdateDialog
import de.scenicroutes.shared.model.ScoreDefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

val catalog: LoopCatalog by lazy { LoopCatalog.parse(File("src/main/assets/loops.json").readText()) }

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class UiTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent { ScenicTheme(dark = dark) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } } }
        rule.onRoot().captureRoboImage("../docs/screenshots/$name-${if (dark) "dark" else "light"}.png")
    }


    private val n get() = catalog.loops.size
    private val top get() = catalog.loops.filter { it.rep }.maxBy { it.tour }
    @Composable private fun detail(offline: SaveState = SaveState.None, onStart: () -> Unit = {}) =
        LoopDetailScreen(top, {}, onStart, sbm = catalog.sbm, start = catalog.start, family = catalog.loops.filter { it.family == top.family }, offline = offline, map = { m, _, _ -> RouteShape(top, m) })

    @Test fun listLight() = shot("1-liste", false) { LoopListScreen(catalog, {}, {}, {}, saved = setOf(top.id)) }
    @Test fun listDark() = shot("1-liste", true) { LoopListScreen(catalog, {}, {}, {}, saved = setOf(top.id)) }
    @Test fun detailLight() = shot("2-detail", false) { detail() }
    @Test fun detailDark() = shot("2-detail", true) { detail() }
    @Test fun whyLight() = whyShot(false)
    @Test fun whyDark() = whyShot(true)
    private fun whyShot(dark: Boolean) {
        rule.setContent { ScenicTheme(dark = dark) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { detail() } } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("bars"))
        rule.onRoot().captureRoboImage("../docs/screenshots/3-warum-${if (dark) "dark" else "light"}.png")
    }
    @Test fun navStartLight() = shot("4-navigation", false) { NavOverlay(Modifier, top, NavExtras(simulate = true, source = RouteSource.REMOTE.label), granted = false, {}, {}, {}, {}, {}, {}) }
    @Test fun navStartDark() = shot("4-navigation", true) { NavOverlay(Modifier, top, NavExtras(simulate = true, source = RouteSource.SAVED.label), granted = false, {}, {}, {}, {}, {}, {}, saved = true) }
    @Test fun offlineLight() = shot("5-offline-speichern", false) { offlineCards() }
    @Test fun offlineDark() = shot("5-offline-speichern", true) { offlineCards() }
    @Composable private fun offlineCards() = androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        OfflineCard(top, SaveState.None, {}, {}); OfflineCard(top, SaveState.Running("Karte (hell)", 0.42f, 3_400_000), {}, {}); OfflineCard(top, SaveState.Saved(9_800_000, true), {}, {})
    }
    private val upd = AppVersion(3, "0.3.0-m3", "https://example.invalid/a.apk", "00", listOf("Neue Runden", "Schnellere Karte"), 0, 52_000_000)
    @Test fun updateLight() = shot("6-update", false) { UpdateDialog(UpdateState.Available(upd, false), {}, {}) }
    @Test fun updateChecking() = shot("6-update-pruefen", false) { UpdateDialog(UpdateState.Verifying(upd), {}, {}) }
    @Test fun updatePermission() = shot("6-update-erlauben", false) { UpdateDialog(UpdateState.NeedsPermission(upd), {}, {}) }
    @Test fun updateInstalling() = shot("6-update-installieren", false) { UpdateDialog(UpdateState.Installing(upd), {}, {}) }
    @Test fun updateError() = shot("6-update-fehler", false) { UpdateDialog(UpdateState.Failed("Installation abgebrochen", upd), {}, {}) }
    @Test fun updateButtons() {
        var allow = 0; var retry = 0; var browser = 0; var cancel = 0
        var st by androidx.compose.runtime.mutableStateOf<UpdateState>(UpdateState.NeedsPermission(upd))
        rule.setContent { de.scenicroutes.app.ui.ScenicTheme { UpdateDialog(st, { retry++ }, { cancel++ }, { allow++ }, { browser++ }) } }
        rule.onNodeWithTag("updAllow").performClick(); rule.onNodeWithTag("updBrowser").performClick(); rule.onNodeWithTag("updCancel").performClick()
        st = UpdateState.Downloading(upd, 0.3f); rule.waitForIdle()
        rule.onNodeWithTag("updateNow").assertDoesNotExist(); rule.onNodeWithText("Abbrechen").performClick()
        st = UpdateState.Installing(upd); rule.waitForIdle(); rule.onNodeWithTag("updInstalling").assertExists(); rule.onNodeWithTag("updCancel").assertExists()
        st = UpdateState.Failed("x", upd); rule.waitForIdle(); rule.onNodeWithTag("updateNow").performClick(); rule.onNodeWithTag("updBrowser").performClick()
        org.junit.Assert.assertEquals(listOf(1, 1, 2, 2), listOf(allow, retry, browser, cancel))
    }
    @Test fun updateProgress() = shot("6-update-laden", true) { UpdateDialog(UpdateState.Downloading(upd, 0.6f), {}, {}) }

    @Test fun updateDialogButtons() {
        var go = 0
        rule.setContent { ScenicTheme { UpdateDialog(UpdateState.Available(upd, false), { go++ }, {}) } }
        rule.onNodeWithText("• Neue Runden").assertIsDisplayed()
        rule.onNodeWithTag("updateNow").performClick(); assertEquals(1, go)
    }

    @Test fun filterChipsFilterList() {
        rule.setContent { ScenicTheme { LoopListScreen(catalog, {}, {}, {}) } }
        rule.onNodeWithText("Runden ab Miltenberg").assertIsDisplayed()
        assertEquals(342, n)
        rule.onNodeWithText("${catalog.filtered(true, emptySet()).size} von $n").assertIsDisplayed()
        rule.onNodeWithTag("chipRep").performClick()
        rule.onNodeWithText("$n von $n").assertIsDisplayed()
        rule.onNodeWithTag("chipA").performClick()
        rule.onNodeWithText("${catalog.loops.count { it.klasse == "A" }} von $n").assertIsDisplayed()
    }

    @Test fun openDetailFromList() {
        var opened = -1
        val first = catalog.filtered(true, emptySet()).first()
        rule.setContent { ScenicTheme { LoopListScreen(catalog, onOpen = { opened = it }, {}, {}) } }
        rule.onNodeWithTag("loop_${first.id}").performClick()
        assertEquals(first.id, opened)
    }

    @Test fun detailStartButton() {
        var started = false
        rule.setContent { ScenicTheme { detail(onStart = { started = true }) } }
        rule.onNodeWithTag("strengths").assertIsDisplayed()
        rule.onNodeWithTag("startNav").performScrollTo().performClick()
        rule.onNodeWithTag("goGoogle").assertIsDisplayed()
        rule.onNodeWithText("In der App navigieren").performClick(); rule.waitForIdle()
        assertTrue(started)
    }

    @Test fun detailHasWhyWithNineBars() {
        rule.setContent { ScenicTheme { detail() } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("bars"))
        ScoreDefs.BARS.forEach { rule.onNodeWithTag("bar_$it").assertExists() }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("profile"))
        rule.onNodeWithTag("profile").assertIsDisplayed()
    }

    @Test fun offlineSaveAndRemove() {
        var saved = 0; var removed = 0
        rule.setContent { ScenicTheme { androidx.compose.foundation.layout.Column { OfflineCard(top, SaveState.None, { saved++ }, {}); OfflineCard(top, SaveState.Saved(1, true), {}, { removed++ }) } } }
        rule.onNodeWithTag("offlineSave").performClick(); rule.onNodeWithTag("offlineRemove").performClick()
        assertEquals(1, saved); assertEquals(1, removed)
    }

    @Test fun startNeedsPermissionOrSimulation() {
        rule.setContent { ScenicTheme { NavOverlay(Modifier, top, NavExtras(simulate = false), granted = false, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("go").assertIsNotEnabled()
        rule.onNodeWithText("Erlauben").assertIsDisplayed()
    }

    @Test fun noClosureButtonWithoutBackend() {
        rule.setContent { ScenicTheme { NavOverlay(Modifier, top, NavExtras(started = true, hasBackend = false), true, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("closure").assertDoesNotExist()
    }

    @Test fun closureButtonWhileNavigating() {
        var closures = 0
        rule.setContent { ScenicTheme { NavOverlay(Modifier, top, NavExtras(started = true, hasBackend = true, blockedCount = 1, message = "Umleitung: 2.3 km der Runde ausgelassen"), true, {}, {}, {}, onClosure = { closures++ }, {}, {}) } }
        rule.onNodeWithTag("closure").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Umleitung: 2.3 km der Runde ausgelassen").assertIsDisplayed()
        assertEquals(1, closures)
    }
    @Test fun offRouteCardAfterIgnoredRejoins() {
        var here = 0; var paused = 0
        rule.setContent { ScenicTheme { NavOverlay(Modifier, top, NavExtras(started = true, offLoop = true, offMode = de.scenicroutes.shared.nav.OffRoutePolicy.Mode.ASK), true, {}, {}, {}, {}, {}, {}, onContinueHere = { here++ }, onPause = { paused++ }) } }
        rule.onNodeWithTag("closure").assertDoesNotExist()
        rule.onNodeWithText("Runde hier fortsetzen").performClick()
        rule.onNodeWithText("Navigation pausieren").performClick()
        rule.waitForIdle()
        assertEquals(1, here); assertEquals(1, paused)
    }
    @Test fun offRouteShot() = shot("9-abseits", false) { NavOverlay(Modifier, top, NavExtras(started = true, offLoop = true, offMode = de.scenicroutes.shared.nav.OffRoutePolicy.Mode.ASK), true, {}, {}, {}, {}, {}, {}) }

    private fun entryUi(): de.scenicroutes.app.nav.EntryUi {
        val c = top.coords; val ep = c[c.size / 3]
        val me = de.scenicroutes.shared.model.LatLon(ep.lat + 0.018, ep.lon - 0.012)
        return de.scenicroutes.app.nav.EntryUi(ep, me, de.scenicroutes.shared.geo.Geo.distanceM(me, ep), de.scenicroutes.shared.geo.Geo.bearingDeg(me, ep), 40.0, "Neudorfer Straße", top.km / 3, top.km, false)
    }
    private fun entryExtras(phase: de.scenicroutes.app.nav.EntryPhase) = NavExtras(phase = phase, entry = entryUi(), hasBackend = true)
    @Test fun entryLight() = shot("7-einstieg", false) { NavOverlay(Modifier, top, entryExtras(de.scenicroutes.app.nav.EntryPhase.CHOOSE), granted = true, {}, {}, {}, {}, {}, {}) }
    @Test fun entryDark() = shot("7-einstieg", true) { NavOverlay(Modifier, top, entryExtras(de.scenicroutes.app.nav.EntryPhase.CHOOSE), granted = true, {}, {}, {}, {}, {}, {}) }
    @Test fun entryGuideShot() = shot("8-einstieg-ohne-navi", false) { NavOverlay(Modifier, top, entryExtras(de.scenicroutes.app.nav.EntryPhase.GUIDE), granted = true, {}, {}, {}, {}, {}, {}) }

    @Test fun entryOptionsCallActions() {
        val calls = mutableListOf<String>()
        val a = de.scenicroutes.app.nav.EntryActions(server = { calls += "server" }, google = { calls += "google" }, guide = { calls += "guide" }, official = { calls += "official" })
        rule.setContent { ScenicTheme { NavOverlay(Modifier, top, entryExtras(de.scenicroutes.app.nav.EntryPhase.CHOOSE), true, {}, {}, {}, {}, {}, {}, entry = a) } }
        rule.onNodeWithText("Anfahrt berechnen").performClick()
        rule.onNodeWithText("Anfahrt mit Google Maps").performClick()
        rule.onNodeWithText("Zum Einstieg ohne Navigation").performClick()
        rule.onNodeWithText("Am offiziellen Start beginnen").performClick()
        assertEquals(listOf("server", "google", "guide", "official"), calls)
    }

    @Test fun entryWithoutBackendHidesServer() {
        rule.setContent { ScenicTheme { NavOverlay(Modifier, top, entryExtras(de.scenicroutes.app.nav.EntryPhase.CHOOSE).copy(hasBackend = false), true, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("entryServer").assertDoesNotExist()
        rule.onNodeWithTag("entryGoogle").assertIsDisplayed()
    }
}
