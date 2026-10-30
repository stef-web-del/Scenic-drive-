package de.scenicroutes.shared

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.nav.*
import de.scenicroutes.shared.trip.*
import kotlin.math.abs
import kotlin.test.*

/** Tests zu den 0.6.6-Fixes (Schnitt/Max, Ansagen, Rückwärts, g-Werte, Protokoll). */
class Fixes066Test {
    private val mPerDeg = 111_195.0
    /** Fahrt nach Norden mit [v] m/s, Fixes alle [dtMs]. */
    private fun drive(n: Int, v: Double, dtMs: Long = 1000, speed: (Int) -> Double = { v }) =
        (0 until n).map { i -> TrackPoint(i * dtMs, 49.70 + i * v * dtMs / 1000.0 / mPerDeg, 9.22, null, speed(i), 5.0) }

    // ---- 1) Schnitt / Max ----
    @Test fun avgNotInflatedBySubSecondIntervals() {
        // 999-ms-Abstände: früher dt = 0 s Fahrzeit bei voller Strecke → Schnitt viel zu hoch.
        val s = TripMath.stats(drive(120, 20.0, dtMs = 999))
        assertEquals(72.0, s.avgMovingKmh, 1.5)
    }

    @Test fun standingDistanceDoesNotCountForAverage() {
        val move = drive(60, 20.0)
        val last = move.last()
        // 60 s Stand mit GPS-Zittern (~1 m, Tempo 0)
        val still = (1..60).map { i -> last.copy(timeMs = last.timeMs + i * 1000, lat = last.lat + (if (i % 2 == 0) 1 else -1) * 1.0 / mPerDeg, speedMps = 0.0) }
        val s = TripMath.stats(move + still)
        assertEquals(72.0, s.avgMovingKmh, 1.5)
    }

    @Test fun maxIgnoresSingleDopplerSpike() {
        val s = TripMath.stats(drive(60, 20.0) { if (it == 30) 60.0 else 20.0 })
        assertTrue(s.maxKmh < 80, "max=${s.maxKmh}")
        assertTrue(s.maxKmh > 70, "max=${s.maxKmh}")
    }

    @Test fun maxCappedByDistanceOverTime() {
        // Doppler meldet dauerhaft 40 m/s, gefahren werden 20 m/s → Max ≤ 20 × 1,25 + 2 m/s
        val s = TripMath.stats(drive(30, 20.0) { 40.0 })
        assertTrue(s.maxKmh <= (20 * 1.25 + 2) * 3.6 + 0.5, "max=${s.maxKmh}")
    }

    @Test fun gpxHasExtendedFields() {
        val g = Gpx.write("x", listOf(TrackPoint(0, 49.7, 9.22, 100.0, 10.0, 4.0, 90.0, 0.1, 0.2, 3.0, 0.4)), { "t" })
        listOf("<sr:acc>4.0", "<sr:bearing>90.0", "<sr:yaw>3.0", "<sr:gPeak>0.4", "<sr:lonG>0.1", "<sr:latG>0.2").forEach { assertTrue(it in g, it) }
    }

    // ---- 2c) Ansagen ----
    @Test fun batchSpeaksOnlyLastWithFlush() {
        val g = SpeechGate()
        g.offer("a", "1", 0, 0)
        g.beginBatch()
        for (i in 1..20) assertNull(g.offer("s$i", "id$i", i, 1000L + i))
        val s = g.endBatch()!!
        assertEquals("s20", s.text); assertTrue(s.flush); assertEquals(19, g.dropped)
    }

    @Test fun stepChangeFlushesSameStepQueues() {
        val g = SpeechGate()
        assertFalse(g.offer("in 500 m rechts", "1", 3, 0)!!.flush)
        assertFalse(g.offer("jetzt rechts", "2", 3, 10_000)!!.flush)
        assertTrue(g.offer("links", "3", 4, 20_000)!!.flush)
        assertTrue(g.offer("schnell", "4", 4, 20_500)!!.flush) // schnelle Folge
    }

    @Test fun pendingExpires() {
        val g = SpeechGate()
        g.hold(SpeechGate.Say("x", "1", false, 0)); assertNull(g.takePending(6_000))
        g.hold(SpeechGate.Say("y", "2", false, 0)); assertEquals("y", g.takePending(4_000)?.text)
    }

    // ---- 2a) OffRouteMonitor ----
    private val line = Polyline((0..100).map { LatLon(49.70 + it * 100 / mPerDeg, 9.22) }) // 10 km nach Norden

    @Test fun windowCappedAt2km() {
        val m = OffRouteMonitor(line)
        m.update(line.pointAt(0.0), 0); m.update(line.pointAt(20.0), 1000)
        // 10 min ohne Treffer: früher Fenster 300 + 70 × 600 = 42 km → Einzel-Fix bei 6 km wäre übernommen worden
        m.update(line.pointAt(6000.0), 601_000)
        assertTrue(m.progressM < 100, "progress=${m.progressM}")
    }

    @Test fun noProgressAgainstDirection() {
        val m = OffRouteMonitor(line)
        m.update(line.pointAt(0.0), 0)
        var t = 1000L
        for (a in listOf(100.0, 300.0, 500.0)) { m.update(line.pointAt(a), t, 0.0); t += 10_000 }
        val p = m.progressM
        assertTrue(p >= 490)
        // Rückwärts weiter vorne auftauchen (z. B. Runde andersherum) zählt nicht
        for (a in listOf(1500.0, 1450.0, 1400.0, 1350.0)) { m.update(line.pointAt(a), t, 180.0); t += 3000 }
        assertEquals(p, m.progressM)
        assertEquals(180.0, headingDiff(0.0, 180.0)); assertEquals(20.0, headingDiff(350.0, 10.0))
    }

    // ---- 2b) Falsche Richtung ----
    @Test fun wrongWayFiresAfterDistanceAndRearms() {
        val w = WrongWayDetector()
        var fired = 0
        for (i in 0..12) if (w.update(line.pointAt(2000.0 - i * 20), 180.0, 20.0, 0.0, 5.0)) fired++
        assertEquals(1, fired)
        for (i in 0..20) w.update(line.pointAt(1700.0 + i * 20), 0.0, 20.0, 0.0, 5.0)
        assertFalse(w.fired)
    }

    @Test fun wrongWayIgnoresShortAndSlow() {
        val w = WrongWayDetector()
        for (i in 0..5) assertFalse(w.update(line.pointAt(2000.0 - i * 20), 180.0, 20.0, 0.0, 5.0)) // nur 100 m
        val w2 = WrongWayDetector()
        for (i in 0..30) assertFalse(w2.update(line.pointAt(2000.0 - i * 20), 180.0, 1.0, 0.0, 5.0)) // Rangieren
    }

    @Test fun reverseLoopLine() {
        val sq = Polyline(listOf(LatLon(49.70, 9.22), LatLon(49.71, 9.22), LatLon(49.71, 9.24), LatLon(49.70, 9.24), LatLon(49.70, 9.22)))
        val at = 500.0
        val rev = ReverseLoop.line(sq, at)
        val rl = Polyline(rev)
        assertEquals(sq.lengthM, rl.lengthM, 5.0)
        assertTrue(Geo.distanceM(rev.first(), sq.pointAt(at)) < 1)
        assertTrue(Geo.distanceM(rev.last(), sq.pointAt(at)) < 1)
        // erste Bewegung Richtung Süden (zurück zum Start)
        assertTrue(headingDiff(Geo.bearingDeg(rev[0], rev[1]), 180.0) < 5)
        val body = ReverseLoop.body(rev)
        assertEquals(20, Regex("\"lat\"").findAll(body).count())
        assertEquals(18, Regex("through").findAll(body).count())
    }

    // ---- 4) g-Werte ----
    @Test fun sustainedMaxNeedsTwoFixes() {
        assertEquals(0.3, RideMath.sustainedMax(doubleArrayOf(0.1, 0.9, 0.1, 0.3, 0.35, 0.1)) { true }, 1e-9)
        val h = GHold(); listOf(0.2, 1.2, 0.2, 0.4, 0.5).forEach { h.add(it) }; assertEquals(0.4, h.max, 1e-9)
    }

    @Test fun frameGivesNoLonBeforeCalibration() {
        val f = VehicleFrame()
        for (i in 0 until 50) f.onAccel(0.0, 0.0, 9.81, i * 20_000_000L)
        val s = f.flush(10.0, 2.0)
        assertNull(s.lonG); assertFalse(s.calibrated)
    }

    @Test fun summaryDropsImplausibleAndUncalibratedLon() {
        // Sensor meldet 0,8 g Bremsen, GPS zeigt konstantes Tempo → unplausibel, zählt nicht
        val pts = drive(60, 20.0).mapIndexed { i, p -> p.copy(lonG = if (i in 20..22) -0.8 else 0.01, latG = 0.05) }
        val s = RideMath.summary(pts)
        assertTrue(s.maxBrakeG < 0.1, "brake=${s.maxBrakeG}")
        // Alt-Daten ohne Kalibrierung: lonG == GPS-dv/dt → kein Längs-Max
        val old = drive(60, 20.0) { 20.0 + (if (it in 20..24) -(it - 19) * 3.0 else 0.0) }.let { ps ->
            val gl = RideMath.gpsLonG(ps); ps.mapIndexed { i, p -> p.copy(lonG = gl[i], latG = 0.0) } }
        val so = RideMath.summary(old)
        assertFalse(so.lonCalibrated); assertEquals(0.0, so.maxBrakeG)
    }

    @Test fun p99() { assertEquals(98.0, RideMath.p99((0..100).map { it.toDouble() }), 1.0) }

    // ---- Protokoll ----
    @Test fun navLogLine() {
        val l = NavLog.line(123, "speech", mapOf("text" to "Bitte \"wenden\"\n", "step" to 3, "x" to null, "d" to 1.234))
        assertEquals("{\"t\":123,\"ev\":\"speech\",\"text\":\"Bitte \\\"wenden\\\"\\n\",\"step\":3,\"d\":1.23}", l)
        assertEquals(123L, NavLog.timeOf(l))
    }
}
