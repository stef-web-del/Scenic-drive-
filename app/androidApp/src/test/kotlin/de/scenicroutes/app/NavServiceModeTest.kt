package de.scenicroutes.app

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.verticalScroll
import androidx.test.core.app.ApplicationProvider
import de.scenicroutes.app.data.NavPrefs
import de.scenicroutes.app.rec.RecordingService
import de.scenicroutes.app.rec.ServiceMode
import de.scenicroutes.app.ui.MapSettings
import de.scenicroutes.app.ui.ScenicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 0.6.5: Vordergrunddienst-Modus „nur Navigation“ und Einstellung „Bildschirm anlassen“. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScenicApp::class)
class NavServiceModeTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<ScenicApp>()

    @Test fun modeLogic() {
        assertEquals(ServiceMode.NONE, ServiceMode.of(recording = false, navigating = false))
        assertEquals(ServiceMode.NAV, ServiceMode.of(recording = false, navigating = true))
        assertEquals(ServiceMode.REC, ServiceMode.of(recording = true, navigating = true)) // kein Doppeldienst
        assertEquals(ServiceMode.REC, ServiceMode.of(recording = true, navigating = false))
    }

    @Test fun navOnlyStartsForegroundAndSavesNothing() {
        app.graph.navSession.value = "Testrunde"
        val c = Robolectric.buildService(RecordingService::class.java, Intent(app, RecordingService::class.java).setAction(RecordingService.ACTION_NAV)).create().startCommand(0, 1)
        val sh = shadowOf(c.get())
        assertFalse(sh.isStoppedBySelf)
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("nav", n.channelId)
        assertEquals("Navigation aktiv", n.extras.getCharSequence("android.title").toString())
        assertEquals("Beenden", n.actions.single().title.toString())
        assertFalse(app.graph.recorder.recording)
        assertTrue(app.graph.trips.unfinished().isEmpty())
        // Navigation endet → Dienst beendet sich
        app.graph.navSession.value = null
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(sh.isStoppedBySelf)
    }

    @Test fun endActionStopsNavigation() {
        app.graph.navSession.value = "Testrunde"
        val c = Robolectric.buildService(RecordingService::class.java, Intent(app, RecordingService::class.java).setAction(RecordingService.ACTION_NAV)).create().startCommand(0, 1)
        c.withIntent(Intent(app, RecordingService::class.java).setAction(RecordingService.ACTION_NAV_END)).startCommand(0, 2)
        assertNull(app.graph.navSession.value)
        assertTrue(shadowOf(c.get()).isStoppedBySelf)
    }

    @Test fun nothingToDoStopsAtOnce() {
        val c = Robolectric.buildService(RecordingService::class.java, Intent(app, RecordingService::class.java).setAction(RecordingService.ACTION_NAV)).create().startCommand(0, 1)
        assertTrue(shadowOf(c.get()).isStoppedBySelf)
    }

    @Test fun keepScreenOnPref() {
        assertTrue(NavPrefs().keepScreenOn)
        assertTrue(NavPrefs.keepAwake(NavPrefs(), driving = true))
        assertFalse(NavPrefs.keepAwake(NavPrefs(), driving = false))
        assertFalse(NavPrefs.keepAwake(NavPrefs(keepScreenOn = false), driving = true))
        val s = app.graph.settings
        rule.setContent { ScenicTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { MapSettings(s) } } }
        rule.onNodeWithTag("navKeepScreenOn").performClick()
        rule.waitForIdle()
        assertFalse(s.nav.keepScreenOn)
        assertFalse(NavPrefs.read(app.getSharedPreferences("x", 0).also { NavPrefs.write(it, s.nav) }).keepScreenOn)
    }
}
