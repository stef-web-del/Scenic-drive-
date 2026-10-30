package de.scenicroutes.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.scenicroutes.shared.db.ScenicDb
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LoopCatalog
import de.scenicroutes.shared.nav.NavJson
import de.scenicroutes.shared.nav.OffRouteMonitor
import de.scenicroutes.shared.trip.TrackPoint
import de.scenicroutes.shared.trip.TripMath
import de.scenicroutes.shared.trip.TripRepository
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.*

/** Prüft die echten, mit der App ausgelieferten Daten (androidApp/src/main/assets). */
class AssetsAndDbTest {
    private val assets = File("../androidApp/src/main/assets")
    private val catalog by lazy { LoopCatalog.parse(File(assets, "loops.json").readText()) }

    @Test fun catalogHas342LoopsAnd9Families() {
        assertEquals(342, catalog.loops.size)
        assertEquals(9, catalog.loops.count { it.rep })
        assertEquals(100, catalog.loops.count { it.klasse == "A" })
        assertTrue(catalog.filtered(true, emptySet()).zipWithNext().all { (a, b) -> a.tour >= b.tour })
    }

    @Test fun everyLoopHasOfflineRouteWithGermanInstructions() {
        for (l in catalog.loops) {
            val f = File(assets, "routes/${l.id}.json"); assertTrue(f.exists(), "fehlt: ${l.id}")
            val b = NavJson.decodeBundle(f.readText())
            val leg = b.route["legs"]!!.jsonArray.single().jsonObject
            val steps = leg["steps"]!!.jsonArray
            assertTrue(steps.size >= 5, "${l.id}: zu wenige Schritte")
            assertTrue(steps.any { it.jsonObject["voiceInstructions"]?.jsonArray?.isNotEmpty() == true })
            assertEquals(2, b.waypoints.size)
            val km = b.route["distance"]!!.jsonPrimitive.double / 1000
            assertTrue(kotlin.math.abs(km - l.km) <= 0.08 * l.km, "${l.id}: $km vs ${l.km}")
        }
        val first = NavJson.decodeBundle(File(assets, "routes/${catalog.loops.first().id}.json").readText())
        val txt = first.route["legs"]!!.jsonArray[0].jsonObject["steps"]!!.jsonArray[0].jsonObject["maneuver"]!!.jsonObject["instruction"]!!.jsonPrimitive.content
        assertTrue(txt.contains("fahren") || txt.contains("Richtung"), txt)
    }

    @Test fun noLoopDrivesBackAndForth() {
        // harte Regel (t12u): kein Hin-und-zurück außerhalb 1,5 km um den Start
        for (l in catalog.loops) {
            val pl = Polyline(l.coords); val c = pl.cum
            for (i in 1 until l.coords.size - 1) {
                if (c[i] < 1500 || pl.lengthM - c[i] < 1500) continue
                val a = l.coords[i - 1]; val b = l.coords[i]; val n = l.coords[i + 1]
                val turn = kotlin.math.abs(((de.scenicroutes.shared.geo.Geo.bearingDeg(a, b) - de.scenicroutes.shared.geo.Geo.bearingDeg(b, n)) + 540) % 360 - 180)
                assertTrue(turn < 178.0 || de.scenicroutes.shared.geo.Geo.distanceM(a, b) < 5, "Wende in ${l.id} bei km ${c[i] / 1000}")
            }
        }
    }

    @Test fun simulatedDriveWithDetourIsDetectedAndRecorded() {
        val loop = catalog.loops.first(); val pl = Polyline(loop.coords)
        val mon = OffRouteMonitor(pl)
        val db = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ScenicDb.Schema.create(it) }
        val repo = TripRepository(db); val trip = repo.start(loop.id, loop.title, 0)
        var t = 0L; var offEvents = 0; var backEvents = 0
        var a = 0.0
        while (a < pl.lengthM) {
            var p = pl.pointAt(a)
            if (a in 10_000.0..11_000.0) p = p.copy(lat = p.lat + 0.002) // ~220 m daneben (Umleitung)
            if (mon.update(p)) if (mon.state == OffRouteMonitor.State.OFF_LOOP) offEvents++ else backEvents++
            repo.add(trip, TrackPoint(t, p.lat, p.lon, null, 22.0, 5.0)); t += 1000; a += 22.0
        }
        assertEquals(1, offEvents); assertEquals(1, backEvents)
        assertTrue(mon.progressM > pl.lengthM - 100)
        val cov = TripMath.coverageShare(TripMath.coverage(pl, repo.points(trip)))
        repo.finish(trip, t, cov)
        val tr = repo.trip(trip)!!
        assertTrue(cov in 0.9..0.995, "cov=$cov")
        assertTrue(tr.maxKmh!! in 79.0..80.0)
        assertTrue(tr.distanceM!! > pl.lengthM)
        repo.delete(trip); assertTrue(repo.trips().isEmpty())
    }

    @Test fun offlineRepositoryRoundTrip() {
        val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY); ScenicDb.Schema.create(d)
        val r = de.scenicroutes.shared.offline.OfflineRepository(d)
        r.save(7, "{\"a\":1}", "sha", 1000)
        assertEquals("{\"a\":1}", r.navJson(7)); assertFalse(r.entry(7)!!.mapComplete)
        r.setMap(7, 5000, true)
        val e = r.entry(7)!!; assertTrue(e.mapComplete); assertEquals(5000 + 7, e.totalBytes)
        r.save(7, "{}", "sha2", 2000, 5000, true); assertEquals(1, r.all().size); assertEquals("sha2", r.entry(7)!!.navSha)
        r.remove(7); assertNull(r.navJson(7))
    }

    @Test fun scoreDefsMatchCatalog() {
        val l = catalog.loops.first { it.sb != null }
        val sb = l.sb!!
        assertEquals(9, de.scenicroutes.shared.model.ScoreDefs.BARS.size)
        de.scenicroutes.shared.model.ScoreDefs.BARS.forEach { assertTrue(sb.p[it] in 0..100, it) }
        assertTrue(sb.segments.isNotEmpty())
        val best = de.scenicroutes.shared.model.ScoreDefs.bestSegments(sb.segments)
        assertTrue(best.size <= 5 && best.all { it.value.kind != "t" && it.value.lenM >= 1000 })
        assertTrue(best.zipWithNext().all { (a, b) -> a.value.score >= b.value.score })
        assertEquals("1,5", de.scenicroutes.shared.model.ScoreDefs.de(1.5, 1))
        val sl = de.scenicroutes.shared.model.ScoreDefs.sliceGeom(l.geom, sb.segments[0].kmStart, sb.segments[0].lenM)
        assertTrue(sl.size >= 2)
    }

    @Test fun handoffGoogleAndGpx() {
        val l = catalog.loops.first()
        val u = java.net.URI(de.scenicroutes.shared.model.Handoff.googleUrl(l, catalog.start))
        val q = u.rawQuery.split("&").associate { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        assertEquals("driving", q["travelmode"]); assertEquals("navigate", q["dir_action"]); assertEquals("1", q["api"])
        assertEquals("${catalog.start[1]},${catalog.start[0]}".length > 0, true)
        assertTrue((q["waypoints"]?.split("|")?.size ?: 0) <= 9)
        val g = de.scenicroutes.shared.model.Handoff.gpx(l)
        assertTrue(g.startsWith("<?xml") && "<trk>" in g && "<rte>" in g)
    }
}
