package de.scenicroutes.shared.geo

import de.scenicroutes.shared.model.LatLon
import kotlin.math.*

object Geo {
    private const val R = 6_371_000.0
    fun distanceM(a: LatLon, b: LatLon): Double {
        val p1 = a.lat.toRad(); val p2 = b.lat.toRad()
        val dp = p2 - p1; val dl = (b.lon - a.lon).toRad()
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * R * asin(min(1.0, sqrt(h)))
    }
    fun cumulative(line: List<LatLon>): DoubleArray {
        val c = DoubleArray(line.size)
        for (i in 1 until line.size) c[i] = c[i - 1] + distanceM(line[i - 1], line[i])
        return c
    }
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val p1 = a.lat.toRad(); val p2 = b.lat.toRad(); val dl = (b.lon - a.lon).toRad()
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (atan2(y, x).toDeg() + 360) % 360
    }
    private fun Double.toRad() = this * PI / 180
    private fun Double.toDeg() = this * 180 / PI
}

/** Projektion eines Punkts auf eine Linie (lokal eben, für Abstände < einige km genau genug). */
data class Projection(val distanceM: Double, val alongM: Double, val segment: Int, val point: LatLon)

class Polyline(val points: List<LatLon>) {
    val cum: DoubleArray = Geo.cumulative(points)
    val lengthM: Double get() = if (cum.isEmpty()) 0.0 else cum.last()

    /**
     * Nächste Projektion. Mit [hintAlongM] wird der Fortschritt bevorzugt (wichtig bei Runden,
     * die an Start/Ziel denselben Ort zweimal passieren): Kandidaten innerhalb [windowM] um den
     * Hinweis gewinnen bei Gleichstand von ±30 m.
     */
    fun project(p: LatLon, hintAlongM: Double? = null, windowM: Double = 3000.0, penaltyM: Double = 30.0): Projection {
        var best: Projection? = null
        val kx = 111_320.0 * cos(p.lat * PI / 180); val ky = 110_540.0
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * ky
            val bx = (b.lon - p.lon) * kx; val by = (b.lat - p.lat) * ky
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            val along = cum[i] + t * (cum[i + 1] - cum[i])
            val penalty = if (hintAlongM != null && abs(along - hintAlongM) > windowM) penaltyM else 0.0
            val cand = Projection(d, along, i, LatLon(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)))
            if (best == null || d + penalty < best.distanceM + (if (hintAlongM != null && abs(best.alongM - hintAlongM) > windowM) penaltyM else 0.0)) best = cand
        }
        return best ?: Projection(0.0, 0.0, 0, p)
    }

    fun pointAt(alongM: Double): LatLon {
        if (points.size < 2) return points.first()
        val a = alongM.coerceIn(0.0, lengthM)
        var i = 0
        while (i < cum.size - 2 && cum[i + 1] < a) i++
        val seg = cum[i + 1] - cum[i]
        val t = if (seg == 0.0) 0.0 else (a - cum[i]) / seg
        val p = points[i]; val q = points[i + 1]
        return LatLon(p.lat + t * (q.lat - p.lat), p.lon + t * (q.lon - p.lon))
    }
}
