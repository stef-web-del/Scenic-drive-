package de.scenicroutes.app

import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import de.scenicroutes.app.rec.RideRecorder
import de.scenicroutes.app.ui.AbHierScreen
import de.scenicroutes.app.ui.ScenicTheme
import de.scenicroutes.shared.grid.Profile
import de.scenicroutes.shared.trip.TrackPoint
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Update per OTA auf ein Gerät, dessen Datenbank aus 0.2.0–0.3.1 stammt (Schema 2 = Trip, TrackPoint, OfflineLoop;
 * LocalProfile fehlt, weil 0.4.0 die Migration 1.sqm umgeschrieben hat, statt eine neue anzulegen).
 * Genau so sah Stefans Pixel 9 aus (Absturz „no such table: LocalProfile“ in 0.4.1 und 0.5.0).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = ScenicApp::class)
class UpgradeFrom041Test {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<ScenicApp>()

    /** Schema wie von 0.2.0-m2 … 0.3.1-m3 angelegt (aus den veröffentlichten APKs ausgelesen). */
    private fun seedOld(version: Int) {
        val f = app.getDatabasePath("scenic.db"); f.parentFile?.mkdirs(); f.delete()
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL("CREATE TABLE Trip (id INTEGER PRIMARY KEY AUTOINCREMENT, loopId INTEGER, title TEXT NOT NULL, startedMs INTEGER NOT NULL, endedMs INTEGER, distanceM REAL, maxKmh REAL, avgKmh REAL, loopCoverage REAL, shared INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE TrackPoint (tripId INTEGER NOT NULL, timeMs INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, altM REAL, speedMps REAL, accuracyM REAL)")
            db.execSQL("CREATE INDEX tp_trip ON TrackPoint(tripId, timeMs)")
            db.execSQL("CREATE TABLE OfflineLoop (loopId INTEGER NOT NULL PRIMARY KEY, navJson TEXT NOT NULL, navSha TEXT NOT NULL, savedMs INTEGER NOT NULL, mapBytes INTEGER NOT NULL DEFAULT 0, mapComplete INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("INSERT INTO Trip(loopId, title, startedMs, endedMs, distanceM) VALUES (103, 'Alte Fahrt', 1750000000000, 1750003600000, 42000)")
            db.execSQL("INSERT INTO OfflineLoop VALUES (103, '{}', 'abc', 1750000000000, 0, 0)")
            fun sqm(n: Int) = File("../shared/src/commonMain/sqldelight/de/scenicroutes/shared/db/$n.sqm").readText()
                .lines().filterNot { it.trim().startsWith("--") }.joinToString("\n").split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
            if (version >= 3) sqm(2) // Stand nach 0.5.0: 2.sqm lief, LocalProfile fehlt trotzdem
            if (version >= 4) sqm(3) // Stand 0.5.1 (Schema 4)
            db.version = version
        }
        // frischen Graph auf die alte Datenbank (wie App-Start nach dem Update)
        ScenicApp::class.java.getDeclaredField("graph").apply { isAccessible = true }.set(app, AppGraph(app))
    }

    private fun fullUse() {
        val g = app.graph
        assertEquals(Profile.SPORTLICH, g.local.profile)
        g.local.profile = Profile.entries.last(); assertEquals(Profile.entries.last(), g.local.profile)
        g.local.toggleDriven(7, false); assertTrue(7 in g.local.driven())
        g.local.report(7, "sperrung", "Test")
        assertEquals("Alte Fahrt", g.trips.trips().single().title) // alte Daten bleiben
        assertNotNull(g.offlineRepo.all().singleOrNull { it.loopId == 103 })
        val r = g.recorder
        val id = r.start(null, RideRecorder.FREE + " · Upgrade")
        var t = 1_760_000_000_000; repeat(40) { i -> t += 1000; r.onFix(TrackPoint(t, 49.70 + i * 0.0002, 9.25, 150.0, 20.0, 4.0)) }
        r.save(r.requestStop()!!)
        assertEquals("done", g.trips.trips().single { it.id == id }.state)
    }

    @Test fun datenbankAus030Schema2() { seedOld(2); fullUse() }
    @Test fun datenbankNach050Schema3() { seedOld(3); fullUse() }
    @Test fun datenbankAus051Schema4() { seedOld(4); fullUse() }

    /** Defensiv: selbst wenn eine Tabelle fehlt, wirft LocalStore nicht, sondern liefert Standardwerte. */
    @Test fun localStoreOhneTabellenStuerztNichtAb() {
        val d = app.androidx_driver("kaputt.db")
        d.execute(null, "DROP TABLE LocalProfile", 0); d.execute(null, "DROP TABLE DrivenLoop", 0); d.execute(null, "DROP TABLE LoopReport", 0)
        val s = de.scenicroutes.app.data.LocalStore(d)
        assertEquals(Profile.SPORTLICH, s.profile); s.profile = Profile.entries.last()
        assertEquals(emptySet<Int>(), s.driven()); s.toggleDriven(1, false); s.markDriven(1, false); assertFalse(s.report(1, "x", "y"))
    }
    private fun android.content.Context.androidx_driver(name: String) = de.scenicroutes.shared.createDriver(this, name)

    @Test fun rundeAbHierNachUpdate() {
        seedOld(3)
        val src = File("../../webapp/public/grid/v1"); val dst = File(app.filesDir, "grid/v1").apply { mkdirs() }
        src.listFiles()!!.forEach { it.copyTo(File(dst, it.name), overwrite = true) }
        rule.setContent { ScenicTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AbHierScreen({}, {}, {}, map = { _, m -> Box(m) }) } } }
        rule.waitUntil(60_000) { rule.onAllNodesWithText("Ab Miltenberg-Zentrum").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ab Miltenberg-Zentrum").performClick()
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("carousel").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("profile_geniesser").performClick()
        rule.waitUntil(10_000) { app.graph.local.profile.key == "geniesser" }
        // Aufzeichnung auf derselben (reparierten) Datenbank
        val r = app.graph.recorder; val id = r.start(103, "Upgrade-Runde")
        var t = 1_760_000_000_000; repeat(20) { i -> t += 1000; r.onFix(TrackPoint(t, 49.70 + i * 0.0002, 9.25, 150.0, 20.0, 4.0)) }
        r.save(r.requestStop()!!); assertEquals("done", app.graph.trips.trips().single { it.id == id }.state)
    }
}
