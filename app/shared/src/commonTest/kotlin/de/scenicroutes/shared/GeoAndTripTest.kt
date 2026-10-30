package de.scenicroutes.shared

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.nav.OffRouteMonitor
import de.scenicroutes.shared.trip.Gpx
import de.scenicroutes.shared.trip.TrackPoint
import de.scenicroutes.shared.trip.TripMath
import kotlin.test.*

class GeoAndTripTest {
    // ~1,11 km lange Nord-Süd-Linie bei Miltenberg, dann nach Osten
    private val line = Polyline(listOf(LatLon(49.70, 9.22), LatLon(49.71, 9.22), LatLon(49.71, 9.24)))

    @Test fun haversineMiltenberg() {
        val d = Geo.distanceM(LatLon(49.70, 9.22), LatLon(49.71, 9.22))
        assertTrue(d in 1100.0..1125.0, "d=$d")
    }

    @Test fun projectionAlongAndDistance() {
        val p = line.project(LatLon(49.705, 9.2207))
        assertTrue(p.distanceM in 40.0..60.0, "dist=${p.distanceM}")
        assertTrue(p.alongM in 540.0..575.0, "along=${p.alongM}")
        assertEquals(0, p.segment)
    }

    @Test fun pointAtRoundTrip() {
        val q = line.pointAt(1500.0)
        assertTrue(line.project(q).distanceM < 1.0)
        assertTrue(kotlin.math.abs(line.project(q).alongM - 1500.0) < 2.0)
    }

    @Test fun offRouteNeedsConfirmationAndRejoins() {
        val m = OffRouteMonitor(line, thresholdM = 50.0, rejoinM = 25.0, confirmFixes = 3)
        m.update(LatLon(49.702, 9.22)); assertEquals(OffRouteMonitor.State.ON_LOOP, m.state)
        // ein Ausreißer reicht nicht
        m.update(LatLon(49.703, 9.2215)); assertEquals(OffRouteMonitor.State.ON_LOOP, m.state)
        m.update(LatLon(49.703, 9.2203))
        repeat(3) { m.update(LatLon(49.704 + it * 0.0005, 9.2220)) }
        assertEquals(OffRouteMonitor.State.OFF_LOOP, m.state)
        m.update(LatLon(49.706, 9.22001)); assertEquals(OffRouteMonitor.State.ON_LOOP, m.state)
        assertTrue(m.progressM > 600)
    }

    @Test fun statsSpeedAndPauses() {
        // 10 min mit 20 m/s nach Norden, 3 min Stand, 1 min weiter
        val pts = ArrayList<TrackPoint>(); var t = 0L; var lat = 49.70
        repeat(600) { pts += TrackPoint(t, lat, 9.22, null, 20.0, 5.0); t += 1000; lat += 20.0 / 111_200 }
        repeat(180) { pts += TrackPoint(t, lat, 9.22, null, 0.0, 5.0); t += 1000 }
        repeat(60) { pts += TrackPoint(t, lat, 9.22, null, 20.0, 5.0); t += 1000; lat += 20.0 / 111_200 }
        val s = TripMath.stats(pts)
        assertTrue(s.distanceM in 13_000.0..13_500.0, "dist=${s.distanceM}")
        assertEquals(72.0, s.maxKmh, 0.01)
        assertTrue(s.avgMovingKmh in 70.0..74.0, "avg=${s.avgMovingKmh}")
        assertEquals(1, s.pauses)
    }

    @Test fun cleanDropsInaccurateAndJumps() {
        val pts = listOf(
            TrackPoint(0, 49.70, 9.22, null, null, 5.0),
            TrackPoint(1000, 49.80, 9.22, null, null, 5.0), // 11 km in 1 s
            TrackPoint(2000, 49.7001, 9.22, null, null, 80.0), // ungenau
            TrackPoint(3000, 49.7002, 9.22, null, null, 5.0),
        )
        assertEquals(2, TripMath.clean(pts).size)
    }

    @Test fun coverageCountsDrivenBins() {
        val track = (0..50).map { TrackPoint(it * 1000L, 49.70 + it * 0.0001, 9.22, null, 15.0, 5.0) } // erste ~560 m
        val bins = TripMath.coverage(line, track, binM = 250.0)
        assertEquals(true, bins[0]); assertEquals(true, bins[1]); assertEquals(false, bins.last())
        assertTrue(TripMath.coverageShare(bins) in 0.15..0.35)
    }

    @Test fun gpxIsWellFormed() {
        val g = Gpx.write("Runde <A&B>", listOf(TrackPoint(0, 49.7, 9.22, 130.0, 12.3, 4.0)), { "1970-01-01T00:00:00Z" })
        assertTrue(g.contains("""<trkpt lat="49.7" lon="9.22"><ele>130.0</ele>"""))
        assertTrue(g.contains("Runde &lt;A&amp;B&gt;"))
        assertTrue(g.trim().endsWith("</gpx>"))
    }
}
