package de.scenicroutes.shared.trip

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon

/**
 * Ein GPS-Fix (≈1 Hz) mit den in dieser Sekunde verdichteten Sensorwerten.
 * lonG: + = beschleunigen, − = bremsen; latG: + = Linkskurve, − = Rechtskurve (Näherung, siehe [VehicleFrame]).
 * seg: Abschnitt zwischen Pausen (nach „Fortsetzen“ +1).
 */
data class TrackPoint(
    val timeMs: Long, val lat: Double, val lon: Double, val altM: Double?, val speedMps: Double?, val accuracyM: Double?,
    val bearingDeg: Double? = null, val lonG: Double? = null, val latG: Double? = null, val yawDps: Double? = null, val gPeak: Double? = null, val seg: Int = 0,
) {
    val latLon get() = LatLon(lat, lon)
}

data class TripStats(val distanceM: Double, val durationS: Long, val movingS: Long, val maxKmh: Double, val avgMovingKmh: Double, val pauses: Int)

object TripMath {
    /** Filtert ungenaue Fixes (> maxAccuracy) und Sprünge (> 70 m/s). */
    fun clean(points: List<TrackPoint>, maxAccuracyM: Double = 30.0): List<TrackPoint> {
        val out = ArrayList<TrackPoint>()
        for (p in points) {
            if ((p.accuracyM ?: 0.0) > maxAccuracyM) continue
            val last = out.lastOrNull()
            if (last != null && last.seg == p.seg) {
                val dt = (p.timeMs - last.timeMs) / 1000.0
                if (dt <= 0) continue
                if (Geo.distanceM(last.latLon, p.latLon) / dt > 70.0) continue
            }
            out += p
        }
        return out
    }

    /**
     * Kennzahlen. 0.6.6: dt als Double (vorher ganze Sekunden: Fixes mit 999 ms Abstand zählten 0 s Fahrzeit,
     * die Strecke aber voll → Schnitt bis ~2× zu hoch). Schnitt = Strecke IN BEWEGUNG / Fahrzeit.
     * Max = Median über 3 Fixes, plausibilisiert gegen Weg/Zeit (ein einzelner Doppler-Ausreißer zählt nicht).
     */
    fun stats(raw: List<TrackPoint>, movingKmh: Double = 5.0, pauseS: Long = 120): TripStats {
        val pts = clean(raw)
        if (pts.size < 2) return TripStats(0.0, 0, 0, 0.0, 0.0, 0)
        var dist = 0.0; var movDist = 0.0; var moving = 0.0; var pauses = 0; var still = 0.0
        val v = DoubleArray(pts.size) { -1.0 }; val vPos = DoubleArray(pts.size) { -1.0 }
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            if (a.seg != b.seg) continue // Pause: weder Strecke noch Fahrzeit
            val d = Geo.distanceM(a.latLon, b.latLon); val dt = (b.timeMs - a.timeMs) / 1000.0
            val pos = if (dt > 0) d / dt else 0.0
            val vi = b.speedMps ?: pos
            v[i] = vi; vPos[i] = pos
            dist += d
            if (vi * 3.6 >= movingKmh) { moving += dt; movDist += d; if (still >= pauseS) pauses++; still = 0.0 } else still += dt
        }
        val maxV = maxSpeed(pts, v, vPos)
        val dur = pts.groupBy { it.seg }.values.sumOf { (it.last().timeMs - it.first().timeMs) / 1000 }
        return TripStats(dist, dur, moving.toLong(), maxV * 3.6, if (moving > 0) movDist / moving * 3.6 else 0.0, pauses)
    }

    /** Median über 3 aufeinanderfolgende Fixes desselben Abschnitts; höchstens Weg/Zeit-Median × 1,25 + 2 m/s. */
    internal fun maxSpeed(pts: List<TrackPoint>, v: DoubleArray, vPos: DoubleArray): Double {
        var best = 0.0
        for (i in 2 until pts.size) {
            if (pts[i - 2].seg != pts[i].seg || v[i - 1] < 0 || v[i] < 0 || v[i - 2] < 0) continue
            if (pts[i - 2].seg != pts[i - 1].seg) continue
            val m = med3(v[i - 2], v[i - 1], v[i])
            // Weg/Zeit über die drei Fixes (robuster als einzelne Abschnitte).
            val dt = (pts[i].timeMs - pts[i - 2].timeMs) / 1000.0
            val wz = if (dt > 0) (Geo.distanceM(pts[i - 2].latLon, pts[i - 1].latLon) + Geo.distanceM(pts[i - 1].latLon, pts[i].latLon)) / dt else m
            val cap = maxOf(med3(vPos[i - 2].coerceAtLeast(0.0), vPos[i - 1], vPos[i]), wz) * 1.25 + 2.0
            best = maxOf(best, minOf(m, cap))
        }
        if (best == 0.0) for (i in v.indices) if (v[i] > best && v[i] < 70) best = v[i] // < 3 Fixes: Fallback
        return best
    }
    private fun med3(a: Double, b: Double, c: Double) = maxOf(minOf(a, b), minOf(maxOf(a, b), c))

    /**
     * Gefahrene Segmente einer Runde: Runde in [binM]-Abschnitte teilen, Abschnitt gilt als gefahren,
     * wenn ein Trackpunkt näher als [toleranceM] liegt. Grundlage für Segment-Häufigkeit (Community später).
     */
    fun coverage(loop: Polyline, track: List<TrackPoint>, binM: Double = 250.0, toleranceM: Double = 40.0): BooleanArray {
        val n = maxOf(1, kotlin.math.ceil(loop.lengthM / binM).toInt())
        val bins = BooleanArray(n)
        var hint = 0.0
        for (p in clean(track)) {
            val pr = loop.project(p.latLon, hintAlongM = hint)
            if (pr.distanceM <= toleranceM) { hint = pr.alongM; bins[minOf(n - 1, (pr.alongM / binM).toInt())] = true }
        }
        return bins
    }
    fun coverageShare(bins: BooleanArray) = if (bins.isEmpty()) 0.0 else bins.count { it }.toDouble() / bins.size
}

internal fun r3(v: Double) = kotlin.math.round(v * 1000) / 1000

object Gpx {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun write(name: String, points: List<TrackPoint>, isoTime: (Long) -> String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Scenic Routes" xmlns="http://www.topografix.com/GPX/1/1" xmlns:sr="https://scenic-routes.app/gpx/1">""").append('\n')
        append("<metadata><name>").append(esc(name)).append("</name></metadata>\n<trk><name>").append(esc(name)).append("</name><trkseg>\n")
        var seg = points.firstOrNull()?.seg ?: 0
        for (p in points) {
            if (p.seg != seg) { append("</trkseg><trkseg>\n"); seg = p.seg }
            append("""<trkpt lat="${p.lat}" lon="${p.lon}">""")
            p.altM?.let { append("<ele>").append(it).append("</ele>") }
            append("<time>").append(isoTime(p.timeMs)).append("</time>")
            if (p.speedMps != null || p.lonG != null || p.latG != null || p.accuracyM != null || p.bearingDeg != null || p.yawDps != null || p.gPeak != null) {
                append("<extensions>")
                p.speedMps?.let { append("<speed>").append(it).append("</speed>") }
                p.accuracyM?.let { append("<sr:acc>").append(r3(it)).append("</sr:acc>") }
                p.bearingDeg?.let { append("<sr:bearing>").append(r3(it)).append("</sr:bearing>") }
                p.lonG?.let { append("<sr:lonG>").append(r3(it)).append("</sr:lonG>") }
                p.latG?.let { append("<sr:latG>").append(r3(it)).append("</sr:latG>") }
                p.yawDps?.let { append("<sr:yaw>").append(r3(it)).append("</sr:yaw>") }
                p.gPeak?.let { append("<sr:gPeak>").append(r3(it)).append("</sr:gPeak>") }
                append("</extensions>")
            }
            append("</trkpt>\n")
        }
        append("</trkseg></trk>\n</gpx>\n")
    }
}
