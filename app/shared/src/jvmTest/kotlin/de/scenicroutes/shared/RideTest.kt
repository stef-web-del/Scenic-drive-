package de.scenicroutes.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.scenicroutes.shared.db.ScenicDb
import de.scenicroutes.shared.trip.Csv
import de.scenicroutes.shared.trip.Gpx
import de.scenicroutes.shared.trip.RideMath
import de.scenicroutes.shared.trip.TrackPoint
import de.scenicroutes.shared.trip.TripRepository
import de.scenicroutes.shared.trip.VehicleFrame
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RideTest {
    /** Kreisfahrt: Radius 100 m, 20 m/s → Quer-g = v²/r/g ≈ 0,41 g (links herum). */
    private fun circle(n: Int, v: Double = 20.0, r: Double = 100.0, seg: Int = 0, t0: Long = 0): List<TrackPoint> = (0 until n).map { i ->
        val a = v / r * i; val lat = 49.7 + r * sin(a) / 111_200; val lon = 9.2 + (r - r * cos(a)) / (111_200 * cos(49.7 * PI / 180))
        TrackPoint(t0 + i * 1000L, lat, lon, 150.0 + i * 0.5, v, 4.0, seg = seg)
    }

    @Test fun querbeschleunigungAusGpsUndKurven() {
        val s = RideMath.summary(circle(60))
        assertTrue(s.maxLatG in 0.35..0.48, "latG=${s.maxLatG}")
        assertTrue(s.curves >= 1)
        assertTrue(s.ascentM in 25.0..31.0, "up=${s.ascentM}")
        assertEquals(0.0, s.descentM)
    }

    @Test fun pauseZaehltNichtAlsStreckeOderFahrzeit() {
        val a = circle(30); val b = circle(30, seg = 1, t0 = 600_000).map { it.copy(lat = it.lat + 0.05) }
        val s = RideMath.summary(a + b)
        assertTrue(s.distanceM < 1300, "dist=${s.distanceM}")
        assertTrue(s.durationS in 55..60, "dur=${s.durationS}")
        assertTrue(Gpx.write("x", a + b) { "t" }.contains("</trkseg><trkseg>"))
    }

    @Test fun csvExport() {
        val c = Csv.write(listOf(TrackPoint(0, 49.7, 9.2, 130.0, 10.0, 3.0, 90.0, 0.123456, -0.2, 5.0, 0.3, 2))) { "1970-01-01T00:00:00Z" }
        val l = c.lines(); assertEquals(Csv.HEADER, l[0])
        assertEquals("1970-01-01T00:00:00Z,49.7,9.2,130.0,36.0,3.0,90.0,0.123,-0.2,5.0,0.3,2", l[1])
    }

    /** Handy liegt gekippt: Quer-g kommt trotzdem aus Gyro·Schwerkraft × Tempo. */
    @Test fun fahrzeugachsenNaeherung() {
        val f = VehicleFrame(); val up = doubleArrayOf(0.0, 0.6, 0.8)
        var t = 0L; var last: VehicleFrame.Second? = null
        repeat(20) {
            repeat(50) { t += 20_000_000; f.onAccel(up[0] * 9.81, up[1] * 9.81, up[2] * 9.81, t); f.onGyro(0.0, 0.6 * 0.2, 0.8 * 0.2) }
            last = f.flush(20.0, 0.0)
        }
        val lat = last!!.latG!!; assertTrue(lat in 0.38..0.43, "lat=$lat")
    }

    @Test fun migrationVonM4UndDatenmenge() {
        val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        d.execute(null, "CREATE TABLE Trip (id INTEGER PRIMARY KEY AUTOINCREMENT, loopId INTEGER, title TEXT NOT NULL, startedMs INTEGER NOT NULL, endedMs INTEGER, distanceM REAL, maxKmh REAL, avgKmh REAL, loopCoverage REAL, shared INTEGER NOT NULL DEFAULT 0)", 0)
        d.execute(null, "CREATE TABLE TrackPoint (tripId INTEGER NOT NULL, timeMs INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, altM REAL, speedMps REAL, accuracyM REAL)", 0)
        d.execute(null, "INSERT INTO Trip(loopId,title,startedMs,endedMs) VALUES (1,'alt',5,9)", 0)
        d.execute(null, "INSERT INTO Trip(loopId,title,startedMs) VALUES (1,'abgebrochen',7)", 0)
        ScenicDb.Schema.migrate(d, 1, 3)
        val r = TripRepository(d)
        assertEquals(listOf("alt"), r.trips().map { it.title }); assertEquals(32, r.trips()[0].uuid!!.length)
        assertEquals(listOf("abgebrochen"), r.unfinished().map { it.title })

        val file = File.createTempFile("ride", ".db"); file.delete()
        val fd = JdbcSqliteDriver("jdbc:sqlite:" + file.path); ScenicDb.Schema.create(fd)
        val fr = TripRepository(fd); val id = fr.start(null, "Stunde", 0)
        assertEquals(36, fr.unfinished()[0].uuid!!.length)
        val before = file.length()
        circle(3600).forEach { fr.add(id, it.copy(bearingDeg = 123.4, lonG = 0.0312, latG = -0.2141, yawDps = 4.21, gPeak = 0.3321)) }
        val kb = (file.length() - before) / 1024
        println("DATENMENGE_PRO_STUNDE_KB=$kb")
        assertTrue(kb in 100..1500, "kb=$kb")
        fr.delete(id, 1); assertTrue(fr.trips().isEmpty()); assertEquals(0, fr.pointCount(id))
    }
}
