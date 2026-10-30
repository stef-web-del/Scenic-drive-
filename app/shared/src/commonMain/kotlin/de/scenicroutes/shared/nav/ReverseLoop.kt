package de.scenicroutes.shared.nav

import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon

/**
 * „Runde in Gegenrichtung neu starten“ (0.6.6, nach Falscherrichtungs-Erkennung): die Runde ab der
 * aktuellen Stelle rückwärts einmal herum. Die Route rechnet der Router (Valhalla) über Durchgangspunkte.
 */
object ReverseLoop {
    /** Rundenlinie ab [alongM] rückwärts bis zum Anfang, dann vom Ende rückwärts zurück zu [alongM]. */
    fun line(loop: Polyline, alongM: Double): List<LatLon> {
        val pts = loop.points; if (pts.size < 2) return pts
        val a = alongM.coerceIn(0.0, loop.lengthM)
        val here = loop.pointAt(a)
        var k = 0; while (k < pts.size - 1 && loop.cum[k + 1] <= a) k++ // pts[0..k] liegen vor a
        val back = pts.subList(0, k + 1).reversed()
        val closed = pts.first() == pts.last() || Geo2.near(pts.first(), pts.last())
        val tail = pts.subList(k + 1, pts.size).reversed().let { if (closed && it.isNotEmpty()) it.drop(1) else it }
        return (listOf(here) + back + tail + here).zipWithNext().filter { (x, y) -> x != y }.map { it.first } + here
    }

    /** Valhalla-Body: Start (break) + ≤ [maxVia] Durchgangspunkte (through) + Ziel (break). */
    fun body(rev: List<LatLon>, maxVia: Int = 18): String {
        val l = Polyline(rev); val n = maxVia + 1
        val locs = (0..n).map { i ->
            val p = if (i == n) rev.last() else l.pointAt(l.lengthM * i / n)
            val type = if (i == 0 || i == n) "break" else "through"
            "{\"lat\":${p.lat},\"lon\":${p.lon},\"type\":\"$type\"}"
        }
        return """{"costing":"auto","format":"osrm","banner_instructions":true,"voice_instructions":true,"language":"de-DE","units":"kilometers","locations":[${locs.joinToString(",")}]}"""
    }

    private object Geo2 { fun near(a: LatLon, b: LatLon) = de.scenicroutes.shared.geo.Geo.distanceM(a, b) < 5.0 }
}
