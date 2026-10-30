package de.scenicroutes.shared.trip

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Kennzahlen einer Fahrt (alles lokal berechnet, keine Bestzeiten/Ranglisten). */
data class RideSummary(
    val distanceM: Double, val durationS: Long, val movingS: Long, val avgKmh: Double, val maxKmh: Double,
    val ascentM: Double, val descentM: Double, val maxLonAccG: Double, val maxBrakeG: Double, val maxLatG: Double,
    val curves: Int, val points: Int, val hasSensors: Boolean,
) {
    val maxLonG get() = max(maxLonAccG, maxBrakeG)
}

/** Ein Punkt für Diagramme: Strecke (m), Zeit seit Start (s), Werte. */
data class RideSample(val idx: Int, val distM: Double, val tS: Double, val kmh: Double, val altM: Double?, val lonG: Double, val latG: Double)

object RideMath {
    const val G = 9.80665
    /** „Schon gefahren“ automatisch ab diesem Anteil der Runde. */
    const val DRIVEN_SHARE = 0.8
    const val SHORT_S = 120L
    const val SHORT_M = 500.0

    fun isShort(s: RideSummary) = s.durationS < SHORT_S || s.distanceM < SHORT_M

    /** Höhenmeter mit Glättung (gleitendes Mittel über 5 Fixes) und 3-m-Hysterese gegen GPS-Rauschen. */
    fun climb(alts: List<Double>, hysteresisM: Double = 3.0): Pair<Double, Double> {
        if (alts.size < 2) return 0.0 to 0.0
        val sm = alts.indices.map { i -> val a = maxOf(0, i - 2); val b = minOf(alts.lastIndex, i + 2); alts.subList(a, b + 1).average() }
        var up = 0.0; var down = 0.0; var ref = sm[0]
        for (h in sm) { val d = h - ref; if (d >= hysteresisM) { up += d; ref = h } else if (d <= -hysteresisM) { down -= d; ref = h } }
        return up to down
    }

    /** Querbeschleunigung aus GPS allein (Kursänderung × Geschwindigkeit), falls keine Sensorwerte. */
    fun gpsLatG(pts: List<TrackPoint>): DoubleArray {
        val out = DoubleArray(pts.size)
        for (i in 1 until pts.size - 1) {
            val a = pts[i - 1]; val b = pts[i]; val c = pts[i + 1]
            if (a.seg != c.seg) continue
            val dt = (c.timeMs - a.timeMs) / 1000.0; if (dt <= 0) continue
            val h1 = Geo.bearingDeg(a.latLon, b.latLon); val h2 = Geo.bearingDeg(b.latLon, c.latLon)
            if (Geo.distanceM(a.latLon, b.latLon) < 2 || Geo.distanceM(b.latLon, c.latLon) < 2) continue
            var dh = h2 - h1; while (dh > 180) dh -= 360; while (dh < -180) dh += 360
            val v = b.speedMps ?: (Geo.distanceM(a.latLon, c.latLon) / dt)
            out[i] = (-dh * kotlin.math.PI / 180 / (dt / 2)) * v / G // links = +
        }
        return out
    }

    fun gpsLonG(pts: List<TrackPoint>): DoubleArray {
        val out = DoubleArray(pts.size)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]; if (a.seg != b.seg) continue
            val dt = (b.timeMs - a.timeMs) / 1000.0; val va = a.speedMps; val vb = b.speedMps
            if (dt in 0.2..5.0 && va != null && vb != null) out[i] = (vb - va) / dt / G
        }
        return out
    }

    /** Kurven = Episoden mit |Quer-g| ≥ 0,2 g (Ende < 0,1 g) bei > 20 km/h, mindestens 2 s. */
    fun curves(lat: DoubleArray, pts: List<TrackPoint>): Int {
        var n = 0; var on = false; var since = 0L
        for (i in pts.indices) {
            val v = (pts[i].speedMps ?: 0.0) * 3.6; val g = abs(lat[i])
            if (!on && g >= 0.2 && v > 20) { on = true; since = pts[i].timeMs }
            else if (on && (g < 0.1 || v <= 20)) { if (pts[i].timeMs - since >= 2000) n++; on = false }
        }
        return n
    }

    fun samples(raw: List<TrackPoint>): List<RideSample> {
        val pts = TripMath.clean(raw); if (pts.isEmpty()) return emptyList()
        val glat = gpsLatG(pts); val glon = gpsLonG(pts)
        var d = 0.0; val t0 = pts[0].timeMs
        return pts.mapIndexed { i, p ->
            if (i > 0 && pts[i - 1].seg == p.seg) d += Geo.distanceM(pts[i - 1].latLon, p.latLon)
            RideSample(i, d, (p.timeMs - t0) / 1000.0, (p.speedMps ?: 0.0) * 3.6, p.altM, p.lonG ?: glon[i], p.latG ?: glat[i])
        }
    }

    fun summary(raw: List<TrackPoint>): RideSummary {
        val pts = TripMath.clean(raw); val s = TripMath.stats(raw); val sm = samples(raw)
        val (up, down) = climb(pts.mapNotNull { it.altM })
        val lat = DoubleArray(sm.size) { sm[it].latG }
        val moving = sm.filter { it.kmh >= 5 }
        return RideSummary(
            s.distanceM, s.durationS, s.movingS, s.avgMovingKmh, s.maxKmh, up, down,
            moving.maxOfOrNull { it.lonG }?.coerceAtLeast(0.0) ?: 0.0, moving.maxOfOrNull { -it.lonG }?.coerceAtLeast(0.0) ?: 0.0,
            moving.maxOfOrNull { abs(it.latG) } ?: 0.0, curves(lat, pts), raw.size, raw.any { it.latG != null },
        )
    }

    /** Anteil der Runde, der gefahren wurde (250-m-Abschnitte, 40 m Toleranz). */
    fun loopShare(loop: Polyline, pts: List<TrackPoint>) = TripMath.coverageShare(TripMath.coverage(loop, pts))
}

object Csv {
    const val HEADER = "time_utc,lat,lon,alt_m,speed_kmh,accuracy_m,bearing_deg,lon_g,lat_g,yaw_dps,g_peak,segment"
    private fun n(v: Double?, k: Int = 2) = v?.let { val f = when (k) { 6 -> 1e6; 3 -> 1e3; 1 -> 10.0; else -> 100.0 }; (kotlin.math.round(it * f) / f).toString() } ?: ""
    fun write(points: List<TrackPoint>, isoTime: (Long) -> String): String = buildString {
        append(HEADER).append('\n')
        for (p in points) {
            append(isoTime(p.timeMs)).append(',').append(n(p.lat, 6)).append(',').append(n(p.lon, 6)).append(',').append(n(p.altM, 1)).append(',')
                .append(n(p.speedMps?.times(3.6), 1)).append(',').append(n(p.accuracyM, 1)).append(',').append(n(p.bearingDeg, 1)).append(',')
                .append(n(p.lonG, 3)).append(',').append(n(p.latG, 3)).append(',').append(n(p.yawDps, 1)).append(',').append(n(p.gPeak, 3)).append(',').append(p.seg).append('\n')
        }
    }
}

/**
 * Näherung der Fahrzeugachsen aus Handy-Sensoren, unabhängig davon, wie das Handy liegt:
 *  - Schwerkraft = Tiefpass (τ ≈ 2 s) des Beschleunigungssensors → „unten“.
 *  - Drehrate um die Hochachse = Gyro · Schwerkraftrichtung → Quer-g = v · ω / g (braucht nur „unten“ + GPS-Tempo).
 *  - Längsachse wird gelernt: Wenn GPS deutlich beschleunigen/bremsen zeigt, zeigt die horizontale Beschleunigung nach vorn/hinten.
 *    Bis genug gelernt ist (≥ 5 Ereignisse), kommt Längs-g aus der GPS-Geschwindigkeitsänderung.
 * Ehrlich: Das ist eine Schätzung. Rutscht das Handy, wird neu gelernt (Schwerkraft folgt sofort, Längsachse nach einigen Ereignissen).
 */
class VehicleFrame {
    private val grav = DoubleArray(3); private var gInit = false
    private val fwd = DoubleArray(3); var fwdEvents = 0; private set
    private var sumLin = DoubleArray(3); private var sumYaw = 0.0; private var nS = 0; private var peak = 0.0
    private var lastAccT = 0L

    /** Beschleunigungssensor (m/s², inkl. Schwerkraft), Zeit in ns. */
    fun onAccel(x: Double, y: Double, z: Double, tNs: Long) {
        val dt = if (lastAccT == 0L) 0.02 else ((tNs - lastAccT) / 1e9).coerceIn(0.001, 0.2); lastAccT = tNs
        if (!gInit) { grav[0] = x; grav[1] = y; grav[2] = z; gInit = true }
        val a = dt / (2.0 + dt)
        grav[0] += a * (x - grav[0]); grav[1] += a * (y - grav[1]); grav[2] += a * (z - grav[2])
        val lin = horiz(doubleArrayOf(x - grav[0], y - grav[1], z - grav[2]))
        for (i in 0..2) sumLin[i] += lin[i]
        peak = max(peak, norm(lin) / RideMath.G); nS++
    }

    /** Gyroskop (rad/s). */
    fun onGyro(x: Double, y: Double, z: Double) {
        if (!gInit) return
        val u = unit(grav); sumYaw += x * u[0] + y * u[1] + z * u[2]; yawN++
    }
    private var yawN = 0

    data class Second(val lonG: Double?, val latG: Double?, val yawDps: Double?, val gPeak: Double?, val calibrated: Boolean)

    /** Bei jedem GPS-Fix: Werte dieser Sekunde verdichten. dvdt = GPS-Beschleunigung (m/s²) oder null. */
    fun flush(speedMps: Double?, dvdt: Double?): Second {
        if (nS == 0) return Second(null, null, null, null, false)
        val lin = DoubleArray(3) { sumLin[it] / nS }
        val yaw = if (yawN > 0) sumYaw / yawN else null
        if (dvdt != null && abs(dvdt) > 0.6 && (speedMps ?: 0.0) > 3) {
            val s = if (dvdt > 0) 1.0 else -1.0; val n = norm(lin)
            if (n > 0.3) { for (i in 0..2) fwd[i] = fwd[i] * 0.8 + 0.2 * s * lin[i] / n; fwdEvents++ }
        }
        val cal = fwdEvents >= 5 && norm(horiz(fwd)) > 0.1
        val lon = if (cal) { val f = unit(horiz(fwd)); (lin[0] * f[0] + lin[1] * f[1] + lin[2] * f[2]) / RideMath.G } else dvdt?.div(RideMath.G)
        // Vorzeichen der Drehung: Android-Gyro positiv = gegen den Uhrzeigersinn um die Achse; Schwerkraftvektor zeigt bei ruhendem Handy „nach oben“ (Reaktionskraft).
        val lat = if (yaw != null && speedMps != null && speedMps > 3) speedMps * yaw / RideMath.G else if (yaw != null) 0.0 else null
        val out = Second(lon, lat, yaw?.let { it * 180 / kotlin.math.PI }, peak, cal)
        sumLin = DoubleArray(3); sumYaw = 0.0; yawN = 0; nS = 0; peak = 0.0
        return out
    }

    private fun horiz(v: DoubleArray): DoubleArray { val u = unit(grav); val d = v[0] * u[0] + v[1] * u[1] + v[2] * u[2]; return DoubleArray(3) { v[it] - d * u[it] } }
    private fun norm(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    private fun unit(v: DoubleArray): DoubleArray { val n = norm(v).takeIf { it > 1e-6 } ?: 1.0; return DoubleArray(3) { v[it] / n } }
}
