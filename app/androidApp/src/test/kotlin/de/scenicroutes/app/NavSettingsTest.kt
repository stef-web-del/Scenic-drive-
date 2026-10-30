package de.scenicroutes.app

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.scenicroutes.app.data.*
import de.scenicroutes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Navi-Einstellungen (0.6.1): Standardwerte = Verhalten 0.6.0, Upgrade ohne Datenverlust, Logik und Bedienung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NavPrefsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private fun prefs() = ctx.getSharedPreferences("scenic", Context.MODE_PRIVATE)

    @Test fun defaultsEqualBehaviourOf060() {
        val d = NavPrefs()
        assertTrue(d.voiceOn); assertNull(d.engine); assertNull(d.voiceName)
        assertEquals(1f, d.rate); assertEquals(1f, d.pitch); assertEquals(1f, d.volume); assertTrue(d.duck)
        assertEquals(MapOrientation.HEADING, d.orientation); assertEquals(0.0, NavPrefs.zoomDelta(d.zoom, 30.0), 0.0)
        assertEquals(NavTheme.AUTO, d.theme)
        // Fahrleiste: Ankunft groß, dazu Restzeit, Rest-km und (seit 0.6.3) Fortschritt der Runde; Tempo und neue Werte aus.
        assertEquals(listOf("eta", "min", "km", "progress"), NavPrefs.kpis(d, DriveData("17:42", 38.0, 3120.0, 80, 0.4f, 12, 1, 300, 50, 0.3, 0.5)).map { it.key })
        assertTrue(d.showProgress); assertFalse(d.showSpeed); assertFalse(d.showCurves); assertFalse(d.showElevation); assertFalse(d.showAscent); assertFalse(d.showG)
    }

    @Test fun upgradeFrom060KeepsDataAndVoiceSwitch() {
        // Stand 0.6.0: nur diese Schlüssel existieren, Ansagen waren ausgeschaltet.
        prefs().edit().clear().putBoolean("voice", false).putBoolean("record", false).putString("backend", "http://h:1").putLong("updCheck", 42).commit()
        val s = Settings(ctx)
        assertEquals(NavPrefs(voiceOn = false), s.nav)
        assertFalse(s.voice); assertFalse(s.record); assertEquals("http://h:1", s.backendUrl); assertEquals(42L, s.lastUpdateCheck)
        s.updateNav { it.copy(zoom = NavZoom.FAR) }
        assertFalse(prefs().getBoolean("voice", true)); assertFalse(s.record); assertEquals(42L, s.lastUpdateCheck)
    }

    @Test fun roundTripAllFields() {
        prefs().edit().clear().commit()
        val n = NavPrefs(false, "com.google.android.tts", "de-de-x-deb-local", 1.3f, 0.8f, 0.5f, false, MapOrientation.NORTH, NavZoom.AUTO, NavTheme.DARK, false, true, false, true, false, true, false, true, true)
        Settings(ctx).nav = n
        assertEquals(n, Settings(ctx).nav)
        Settings(ctx).updateNav { it.copy(engine = null, voiceName = null) }
        assertNull(Settings(ctx).nav.engine); assertNull(Settings(ctx).nav.voiceName)
    }

    @Test fun brokenValuesFallBack() {
        prefs().edit().clear().putString("navZoom", "RIESIG").putString("navTheme", "").putFloat("navRate", 9f).putFloat("navVolume", 0f).commit()
        val n = Settings(ctx).nav
        assertEquals(NavZoom.NORMAL, n.zoom); assertEquals(NavTheme.AUTO, n.theme)
        assertEquals(NavPrefs.RATE_MAX, n.rate); assertEquals(NavPrefs.VOLUME_MIN, n.volume)
    }

    @Test fun voiceFlagAndFlowStaySynced() {
        prefs().edit().clear().commit()
        val s = Settings(ctx)
        s.voice = false; assertFalse(s.navFlow.value.voiceOn)
        s.updateNav { it.copy(voiceOn = true) }; assertTrue(s.voice)
    }

    @Test fun zoomPerSpeed() {
        assertEquals(1.0, NavPrefs.zoomDelta(NavZoom.NEAR, null), 0.0)
        assertEquals(-1.0, NavPrefs.zoomDelta(NavZoom.FAR, 40.0), 0.0)
        assertEquals(0.0, NavPrefs.zoomDelta(NavZoom.AUTO, null), 0.0)
        assertEquals(0.5, NavPrefs.zoomDelta(NavZoom.AUTO, 30 / 3.6), 0.0)
        assertEquals(0.0, NavPrefs.zoomDelta(NavZoom.AUTO, 70 / 3.6), 0.0)
        assertEquals(-1.0, NavPrefs.zoomDelta(NavZoom.AUTO, 100 / 3.6), 0.0)
    }

    @Test fun themeChoice() {
        assertTrue(NavPrefs.dark(NavTheme.AUTO, true)); assertFalse(NavPrefs.dark(NavTheme.AUTO, false))
        assertFalse(NavPrefs.dark(NavTheme.LIGHT, true)); assertTrue(NavPrefs.dark(NavTheme.DARK, false))
    }

    @Test fun upgradeFrom061KeepsNavSettings() {
        // Stand 0.6.1: Navi-Schlüssel vorhanden, „barProgress“ gibt es noch nicht.
        prefs().edit().clear().putBoolean("voice", true).putString("navZoom", "FAR").putBoolean("barKm", false).putBoolean("barSpeed", true).putFloat("navRate", 1.4f).commit()
        val n = Settings(ctx).nav
        assertEquals(NavPrefs(zoom = NavZoom.FAR, showKm = false, showSpeed = true, rate = 1.4f), n)
        assertTrue(n.showProgress) // nie gesetzt -> neuer Standard
        assertFalse(n.showCurves || n.showElevation || n.showAscent || n.showG)
    }

    @Test fun selbstAusgeschalteterFortschrittBleibtAus() {
        prefs().edit().clear().putBoolean("barProgress", false).commit()
        assertFalse(Settings(ctx).nav.showProgress)
        Settings(ctx).updateNav { it.copy(showCurves = true) }
        assertFalse(Settings(ctx).nav.showProgress); assertTrue(Settings(ctx).nav.showCurves)
    }

    @Test fun neueKpisNurMitDaten() {
        val all = NavPrefs(showSpeed = true, showCurves = true, showElevation = true, showAscent = true, showG = true)
        val k = NavPrefs.kpis(all, DriveData("17:42", 38.4, 3120.0, 74, 0.42f, curves = 23, hairpins = 2, elevationM = 312, ascentM = 248, g = 0.31, gMax = 0.62))
        assertEquals(listOf("eta", "min", "km", "progress", "speed", "curves", "elevation", "ascent", "g"), k.map { it.key })
        assertEquals(Kpi("curves", "23", "Kurven", "2 Kehren"), k[5])
        assertEquals(Kpi("elevation", "312", "m", "Höhe"), k[6])
        assertEquals(Kpi("ascent", "+248", "m", "Anstieg"), k[7])
        assertEquals(Kpi("g", "0,3", "g", "max 0,6"), k[8])
        assertEquals(Kpi("curves", "4", "", "Kurven"), NavPrefs.kpis(all, DriveData("x", 1.0, 1.0, curves = 4, hairpins = 0))[3])
        // fehlende Daten (Anfahrt, kein Profil, nicht kalibriert): Kennzahl fehlt, kein Ersatzwert
        assertEquals(listOf("eta", "min", "km"), NavPrefs.kpis(all, DriveData("x", 1.0, 1.0)).map { it.key })
    }

    @Test fun driveBarKpis() {
        val all = NavPrefs(showProgress = true, showSpeed = true)
        val k = NavPrefs.kpis(all, "17:42", 38.4, 3120.0, 74, 0.42f)
        assertEquals(listOf("eta", "min", "km", "progress", "speed"), k.map { it.key })
        assertEquals(Kpi("eta", "17:42", "", "Ankunft"), k[0])
        assertEquals(Kpi("min", "52", "min", "Restzeit"), k[1])
        assertEquals(Kpi("km", "38", "km", "Rest"), k[2])
        assertEquals(Kpi("progress", "42", "%", "Runde"), k[3])
        assertEquals(Kpi("speed", "74", "km/h", "Tempo"), k[4])
        // Ohne Messwert kein Feld (nichts erfinden), ohne Ankunft wird die Restzeit zur Hauptzahl.
        assertEquals(listOf("min", "km"), NavPrefs.kpis(all.copy(showEta = false), "x", 1.0, 60.0, null, null).map { it.key })
        assertEquals(Kpi("min", "1:05", "h", "Restzeit"), NavPrefs.duration(3900.0))
        assertEquals("4,5", NavPrefs.kmText(4.54)); assertEquals("12", NavPrefs.kmText(12.4))
        assertTrue(NavPrefs.kpis(NavPrefs(showEta = false, showKm = false, showMin = false, showProgress = false), "x", 1.0, 1.0, 50, 0.1f).isEmpty())
    }

    @Test fun progressFromRoute() {
        assertNull(NavPrefs.progress(null, 10.0)); assertNull(NavPrefs.progress(0.0, 10.0))
        assertEquals(0.25f, NavPrefs.progress(1000.0, 750.0)!!, 0.001f)
        assertEquals(0f, NavPrefs.progress(1000.0, 1200.0)!!, 0f) // nach Umleitung länger als geplant
        assertEquals(111_195.0, geoDistanceM(0.0, 0.0, 1.0, 0.0), 50.0)
    }

    @Test fun mainPageSummaries() {
        assertEquals("An · Standardstimme", voiceSummary(NavPrefs()))
        assertEquals("Aus", voiceSummary(NavPrefs(voiceOn = false)))
        assertEquals("Fahrtrichtung · Zoom normal · Tag/Nacht auto", mapSummary(NavPrefs()))
        assertEquals("Ankunft, Restzeit, km, Fortschritt", barSummary(NavPrefs()))
        assertEquals("Nur Tasten", barSummary(NavPrefs(showEta = false, showKm = false, showMin = false, showProgress = false)))
    }

    @Test fun voiceLabels() {
        assertEquals("deb (lokal)", voiceLabel("de-de-x-deb-local"))
        assertEquals("nfh (online)", voiceLabel("de-de-x-nfh-network"))
        assertEquals("de-DE-language", voiceLabel("de-DE-language"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi", application = ScenicApp::class)
class NavSettingsUiTest {
    @get:Rule val rule = createComposeRule()
    private val g get() = ApplicationProvider.getApplicationContext<ScenicApp>().graph

    private fun page(dark: Boolean = false, start: SettingsPage? = null) { g.settings.nav = NavPrefs(); rule.setContent { ScenicTheme(dark = dark) { SettingsScreen(onBack = {}, speechEnabled = false, start = start) } } }
    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("../docs/screenshots/ui-redesign/$name.png") }

    @Test fun hauptseiteLight() { page(false); shot("einstellungen-hauptseite-light") }
    @Test fun hauptseiteDark() { page(true); shot("einstellungen-hauptseite-dark") }
    @Test fun ansagenLight() { page(false, SettingsPage.VOICE); shot("einstellungen-ansagen-light") }
    @Test fun ansagenDark() { page(true, SettingsPage.VOICE); shot("einstellungen-ansagen-dark") }
    @Test fun fahrleisteLight() { page(false, SettingsPage.BAR); shot("einstellungen-fahrleiste-light") }
    @Test fun fahrleisteDark() { page(true, SettingsPage.BAR); shot("einstellungen-fahrleiste-dark") }

    @Test fun unterseiteUndZurueck() {
        page()
        SettingsPage.entries.forEach { rule.onNodeWithTag("settings-${it.name}").performScrollTo().assertHasClickAction() }
        rule.onNodeWithText("An · Standardstimme").assertExists()
        rule.onNodeWithTag("settings-MAP").performClick()
        rule.onNodeWithTag("settingsTitle").assertTextEquals("Karte & Ansicht")
        rule.onNodeWithTag("navOrient-NORTH").performClick()
        rule.onNodeWithTag("settingsBack").performClick()
        rule.onNodeWithTag("settingsTitle").assertTextEquals("Einstellungen")
        rule.onNodeWithText("Norden oben · Zoom normal · Tag/Nacht auto").assertExists() // Untertitel folgt der Auswahl
    }

    @Test fun auswahlWirdGespeichert() {
        page(start = SettingsPage.MAP)
        rule.onNodeWithTag("navOrient-NORTH").performScrollTo().performClick()
        rule.onNodeWithTag("navZoom-AUTO").performScrollTo().performClick()
        rule.onNodeWithTag("navTheme-DARK").performScrollTo().performClick()
        rule.onNodeWithTag("settingsBack").performClick(); rule.onNodeWithTag("settings-BAR").performClick()
        rule.onNodeWithTag("barSpeed").performScrollTo().performClick()
        rule.onNodeWithTag("barKm").performScrollTo().performClick()
        rule.onNodeWithTag("barProgress").performScrollTo().performClick()
        rule.onNodeWithTag("barCurves").performScrollTo().performClick()
        rule.onNodeWithTag("barG").performScrollTo().performClick()
        rule.onNodeWithTag("settingsBack").performClick(); rule.onNodeWithTag("settings-VOICE").performClick()
        rule.onNodeWithTag("navDuck").performScrollTo().performClick()
        rule.onNodeWithTag("navVoiceOn").performScrollTo().performClick()
        rule.waitForIdle()
        val n = Settings(ApplicationProvider.getApplicationContext()).nav // frisch von der Platte
        assertEquals(MapOrientation.NORTH, n.orientation); assertEquals(NavZoom.AUTO, n.zoom); assertEquals(NavTheme.DARK, n.theme)
        assertTrue(n.showSpeed); assertFalse(n.showKm); assertFalse(n.showProgress); assertTrue(n.showCurves); assertTrue(n.showG); assertFalse(n.showElevation); assertFalse(n.duck); assertFalse(n.voiceOn)
    }

    @Test fun stimmenDialogOhneEngineZeigtHinweis() {
        page(start = SettingsPage.VOICE)
        rule.onNodeWithTag("navVoiceName").performScrollTo().performClick()
        rule.onNodeWithText("Sprachausgabe startet …").assertExists()
        assertEquals(2, rule.onAllNodesWithText("Standardstimme (Deutsch)").fetchSemanticsNodes().size) // Liste + Dialog
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi", application = ScenicApp::class)
class DriveBarMuteTest {
    @get:Rule val rule = createComposeRule()
    private val s get() = ApplicationProvider.getApplicationContext<ScenicApp>().graph.settings

    /** Wie in NavigationScreen: Taste schaltet die gespeicherte Einstellung, Anzeige folgt dem Zustand. */
    private fun bar(dark: Boolean = false) = rule.setContent { ScenicTheme(dark = dark) { Box(Modifier.fillMaxSize().background(LocalMapColors.current.land)) {
        val p by s.navFlow.collectAsState()
        DriveHud("350", "m", "Rechts auf die Talstraße", "links Richtung Mönchberg", NavPrefs.kpis(p, DriveData("17:42", 38.4, 3120.0, 74, 0.42f, curves = 23, hairpins = 2, elevationM = 312, ascentM = 248, g = 0.31, gMax = 0.62)),
            onMute = { s.updateNav { it.copy(voiceOn = !it.voiceOn) } }, muted = !p.voiceOn, progress = 0.42f)
    } } }
    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("../docs/screenshots/ui-redesign/$name.png") }
    private val ALLE = NavPrefs(showSpeed = true, showCurves = true, showElevation = true, showAscent = true, showG = true)

    @Test fun stummTasteSchaltetEinstellung() {
        s.nav = NavPrefs(); bar()
        rule.onNodeWithContentDescription("Ansage stumm").performClick()
        assertFalse(s.voice); rule.onNodeWithContentDescription("Ansagen einschalten").assertExists()
        rule.onNodeWithTag("driveMute").performClick()
        assertTrue(s.voice); rule.onNodeWithContentDescription("Ansage stumm").assertExists()
    }

    @Test fun einstellungWirktAufTaste() {
        s.nav = NavPrefs(); bar()
        s.voice = false; rule.waitForIdle()
        rule.onNodeWithContentDescription("Ansagen einschalten").assertExists()
    }

    @Test fun standardMitFortschrittOhneTempo() {
        s.nav = NavPrefs(); bar()
        listOf("eta", "min", "km", "progress").forEach { rule.onNodeWithTag("kpi-$it").assertExists() }; rule.onNodeWithTag("driveProgress").assertExists()
        listOf("speed", "curves", "elevation", "ascent", "g").forEach { rule.onNodeWithTag("kpi-$it").assertDoesNotExist() }
        listOf("driveStop", "driveMute", "driveOverview").forEach { rule.onNodeWithTag(it).assertHasClickAction() }
    }

    @Test fun felderAusblenden() {
        s.nav = NavPrefs(showEta = false, showKm = false, showSpeed = true); bar()
        rule.onNodeWithText("17:42").assertDoesNotExist()
        rule.onNodeWithTag("kpi-speed").assertExists(); rule.onNodeWithText("74").assertExists()
    }

    @Test fun alleKpis() {
        s.nav = ALLE; bar()
        listOf("eta", "min", "km", "progress", "speed", "curves", "elevation", "ascent", "g").forEach { rule.onNodeWithTag("kpi-$it").assertExists() }
        rule.onNodeWithTag("driveProgress").assertExists()
    }

    @Test fun fahrleisteStandardLight() { s.nav = NavPrefs(); bar(false); shot("fahrleiste-standard-light") }
    @Test fun fahrleisteStandardDark() { s.nav = NavPrefs(); bar(true); shot("fahrleiste-standard-dark") }
    @Test fun fahrleisteAlleLight() { s.nav = ALLE.copy(voiceOn = false); bar(false); shot("fahrleiste-alle-kpis-light") }
    @Test fun fahrleisteAlleDark() { s.nav = ALLE.copy(voiceOn = false); bar(true); shot("fahrleiste-alle-kpis-dark") }
}
