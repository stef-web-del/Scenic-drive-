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

    fun stats(raw: List<TrackPoint>, movingKmh: Double = 5.0, pauseS: Long = 120): TripStats {
        val pts = clean(raw)
        if (pts.size < 2) return TripStats(0.0, 0, 0, 0.0, 0.0, 0)
        var dist = 0.0; var moving = 0L; var maxV = 0.0; var pauses = 0; var still = 0L
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            if (a.seg != b.seg) continue // Pause: weder Strecke noch Fahrzeit
            val d = Geo.distanceM(a.latLon, b.latLon); val dt = (b.timeMs - a.timeMs) / 1000
            val v = b.speedMps ?: if (dt > 0) d / dt else 0.0
            dist += d
            if (v * 3.6 >= movingKmh) { moving += dt; if (still >= pauseS) pauses++; still = 0 } else still += dt
            maxV = maxOf(maxV, v)
        }
        val dur = pts.groupBy { it.seg }.values.sumOf { (it.last().timeMs - it.first().timeMs) / 1000 }
        return TripStats(dist, dur, moving, maxV * 3.6, if (moving > 0) dist / moving * 3.6 else 0.0, pauses)
    }

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
            if (p.speedMps != null || p.lonG != null || p.latG != null) {
                append("<extensions>")
                p.speedMps?.let { append("<speed>").append(it).append("</speed>") }
                p.lonG?.let { append("<sr:lonG>").append(r3(it)).append("</sr:lonG>") }
                p.latG?.let { append("<sr:latG>").append(r3(it)).append("</sr:latG>") }
                append("</extensions>")
            }
            append("</trkpt>\n")
        }
        append("</trkseg></trk>\n</gpx>\n")
    }
}
