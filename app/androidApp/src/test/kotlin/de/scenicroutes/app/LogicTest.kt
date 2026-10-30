package de.scenicroutes.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.scenicroutes.app.data.AppVersion
import de.scenicroutes.app.data.Settings
import de.scenicroutes.app.data.UpdateState
import de.scenicroutes.app.data.Updater
import de.scenicroutes.app.data.sha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LogicTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private val v = """{"versionCode":3,"versionName":"0.3.0","apkUrl":"https://x/a.apk","sha256":"ab","changes":["a"],"minSupported":3,"extra":1}"""

    @Test fun parsesVersionJsonAndEvaluates() {
        val u = Updater(ctx, Settings(ctx), currentCode = 2)
        val a = u.parse(v)
        assertEquals(3, a.versionCode); assertEquals(listOf("a"), a.changes)
        val e = u.evaluate(a); assertTrue(e is UpdateState.Available && e.required)
        assertEquals(UpdateState.UpToDate, Updater(ctx, Settings(ctx), currentCode = 3).evaluate(a))
        assertFalse((u.evaluate(a.copy(minSupported = 0)) as UpdateState.Available).required)
    }

    @Test fun backendEmptyMeansNone() {
        val s = Settings(ctx); s.backendUrl = ""; assertFalse(s.hasBackend)
        s.backendUrl = "http://h:8002"; assertTrue(s.hasBackend)
    }

    @Test fun sha() = assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256(ByteArray(0)))

    @Test fun bundledRoutesMatchCatalogSha() {
        val c = catalog
        c.loops.take(25).forEach { l ->
            val f = java.io.File("src/main/assets/routes/${l.id}.json")
            assertTrue("route ${l.id}", f.exists()); assertEquals(l.navSha, sha256(f.readBytes()))
        }
    }

    @Test fun updaterInstallFlow() = kotlinx.coroutines.runBlocking {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val v = de.scenicroutes.app.data.AppVersion(4, "0.3.1-m3", "https://x/a.apk", "00")
        var allowed = false
        val u = Updater(ctx, Settings(ctx), currentCode = 3, canInstall = { allowed })
        u.installVerified(v, java.io.File.createTempFile("upd", ".apk"))
        assertTrue(u.state.value is UpdateState.NeedsPermission)
        u.onResume(); assertTrue(u.state.value is UpdateState.NeedsPermission)  // noch nicht erlaubt
        u.forceState(UpdateState.Installing(v))
        val confirm = android.content.Intent("android.content.pm.action.CONFIRM_INSTALL")
        u.onInstallStatus(android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION, null, confirm)
        assertEquals(confirm, (u.state.value as UpdateState.Confirm).intent)
        u.confirmLaunched(true); assertTrue(u.state.value is UpdateState.Installing)
        u.onInstallStatus(android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED, null, null)
        assertEquals("Installation abgebrochen", (u.state.value as UpdateState.Failed).msg)
        u.forceState(UpdateState.Confirm(v, confirm)); u.confirmLaunched(false); assertTrue(u.state.value is UpdateState.Failed)
        u.forceState(UpdateState.Installing(v)); u.onInstallStatus(android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION, null, null); assertTrue(u.state.value is UpdateState.Failed)
        u.forceState(UpdateState.Installing(v)); u.onInstallStatus(android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT, null, null); assertTrue(u.state.value is UpdateState.Failed)
        u.forceState(UpdateState.Installing(v)); u.dismiss(); assertEquals(UpdateState.Idle, u.state.value)
    }
}

class DriveDistanceTest {
    @org.junit.Test fun stufen() {
        org.junit.Assert.assertEquals("40" to "m", de.scenicroutes.app.ui.driveDistance(47.0))
        org.junit.Assert.assertEquals("350" to "m", de.scenicroutes.app.ui.driveDistance(372.0))
        org.junit.Assert.assertEquals("1,2" to "km", de.scenicroutes.app.ui.driveDistance(1240.0))
    }
}
