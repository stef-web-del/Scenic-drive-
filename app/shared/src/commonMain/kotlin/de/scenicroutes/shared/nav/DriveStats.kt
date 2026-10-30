package de.scenicroutes.shared.nav

import de.scenicroutes.shared.model.LatLon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/** Eine erkannte Kurve: Position ab Routenanfang (m), Richtungsänderung (Grad), Kehre ja/nein. */
data class Curve(val atM: Double, val turnDeg: Double, val hairpin: Boolean)

/**
 * Kurvenzählung aus der Routengeometrie (vorab, offline). Methode (Referenz: tools/curves/curves.py):
 * 1. Geometrie lokal in Meter projizieren und alle [STEP_M] m neu abtasten.
 * 2. Richtungsänderung je Abschnitt, über 3 Abschnitte (30 m) gemittelt.
 * 3. Kurve = zusammenhängender Bereich gleicher Drehrichtung, in dem der Radius unter [RADIUS_M] liegt
 *    (Rand bis zum halben Schwellwert), mit mindestens [MIN_TURN_DEG] Gesamtdrehung. S-Kurve = 2 Kurven.
 * 4. Kehre = Kurve mit mindestens [HAIRPIN_DEG].
 * 5. Abbiegen zählt nicht: Liegt der Scheitel (Punkt stärkster Drehung) höchstens [NODE_M] m vor/nach einem
 *    Manöverpunkt der Route ([exclusions]), entfällt die Kurve. Manöverpunkte = alle Valhalla-Manöver außer
 *    depart/arrive; "new name"/"continue"/"notification" nur an Knoten mit Grad >= 3. Kreisverkehre werden über
 *    ihre ganze Länge (± [NODE_M]) ausgeschlossen. Echte Kurven/Kehren auf derselben Straße bleiben.
 */
object Curves {
    const val STEP_M = 10.0
    const val RADIUS_M = 120.0
    const val MIN_TURN_DEG = 30.0
    const val HAIRPIN_DEG = 135.0
    const val NODE_M = 25.0

    /** Ausschlussbereich in m ab Routenanfang (Punkt: from == to). */
    data class Span(val fromM: Double, val toM: Double)

    /** Kurven einer OSRM/Valhalla-Route (JSON-Objekt "route") ohne Abbiegen und Kreisverkehre. */
    fun detectRoute(route: JsonObject): List<Curve> =
        detect(Polyline6.decode(route["geometry"]!!.jsonPrimitive.content), exclusions(route))

    fun exclusions(route: JsonObject): List<Span> {
        val out = ArrayList<Span>(); var pos = 0.0; var lat0: Double? = null
        for (leg in route["legs"]?.jsonArray.orEmpty()) for (st in leg.jsonObject["steps"]?.jsonArray.orEmpty()) {
            val o = st.jsonObject
            val g = o["geometry"]?.jsonPrimitive?.content?.let { Polyline6.decode(it) }.orEmpty()
            if (lat0 == null && g.isNotEmpty()) lat0 = g[0].lat
            val len = if (g.size < 2) 0.0 else Flat.toXY(g, lat0!!).zipWithNext { a, b -> hypot(b.first - a.first, b.second - a.second) }.sum()
            val type = o["maneuver"]?.jsonObject?.get("type")?.jsonPrimitive?.content ?: ""
            val deg = (o["intersections"]?.jsonArray?.firstOrNull()?.jsonObject?.get("bearings") as? JsonArray)?.size ?: 0
            when (type) {
                "roundabout", "rotary", "roundabout turn" -> out += Span(pos, pos + len)
                "depart", "arrive" -> {}
                "new name", "continue", "notification" -> if (deg >= 3) out += Span(pos, pos)
                else -> out += Span(pos, pos)
            }
            pos += len
        }
        return out
    }

    fun detect(pts: List<LatLon>, excl: List<Span> = emptyList()): List<Curve> {
        if (pts.size < 3) return emptyList()
        val r = resample(Flat.toXY(pts))
        if (r.size < 4) return emptyList()
        val h = r.zipWithNext { a, b -> deg(atan2(b.second - a.second, b.first - a.first)) }
        val d = h.zipWithNext { a, b -> wrap(b - a) }
        val s = d.indices.map { i -> val w = d.subList(maxOf(0, i - 1), minOf(d.size, i + 2)); w.sum() / w.size }
        val thr = deg(STEP_M / RADIUS_M)
        val out = ArrayList<Curve>(); var i = 0
        while (i < s.size) {
            if (abs(s[i]) < thr) { i++; continue }
            val sg = if (s[i] > 0) 1 else -1; var j = i; var tot = 0.0
            while (j < s.size && s[j] * sg >= thr * 0.5) { tot += d[j]; j++ }
            if (abs(tot) >= MIN_TURN_DEG) {
                val mx = (i until j).maxOf { abs(s[it]) }; val k = (i until j).first { abs(s[it]) >= mx - 1e-6 }; val apex = (k + 1) * STEP_M
                if (excl.none { apex >= it.fromM - NODE_M && apex <= it.toM + NODE_M })
                    out += Curve((i + 1) * STEP_M, abs(tot), abs(tot) >= HAIRPIN_DEG)
            }
            i = j
        }
        return out
    }

    /** Verbleibende Kurven ab Position [doneM] (m ab Routenanfang). */
    fun remaining(c: List<Curve>, doneM: Double) = c.count { it.atM >= doneM }

    private fun resample(p: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val out = arrayListOf(p.first()); var need = STEP_M
        for (k in 0 until p.size - 1) {
            val (x0, y0) = p[k]; val (x1, y1) = p[k + 1]; val dd = hypot(x1 - x0, y1 - y0); var t = need
            while (t <= dd) { out += (x0 + (x1 - x0) * t / dd) to (y0 + (y1 - y0) * t / dd); t += STEP_M }
            need = t - dd
        }
        return out
    }
    private fun deg(r: Double) = r * 180 / PI
    private fun wrap(a: Double): Double { val w = ((a + 180) % 360 + 360) % 360 - 180; return if (w <= -180 + 1e-6) 180.0 else w }
}

internal object Flat {
    fun toXY(pts: List<LatLon>, lat0: Double = pts.first().lat): List<Pair<Double, Double>> {
        val k = cos(lat0 * PI / 180) * 111_320.0
        return pts.map { it.lon * k to it.lat * 110_540.0 }
    }
}

/**
 * Lage auf der festen Runde (nicht auf der Anfahrt): Projektion der Position auf die Rundengeometrie,
 * Höhe aus dem mitgelieferten DGM-Profil (loops.json „prof“, alle [profStep] m, aus DGM1/DGM10/Copernicus GLO-30).
 * Ohne Profil liefern Höhe und Höhenmeter null (KPI wird ausgeblendet).
 */
class LoopTrack(coords: List<LatLon>, private val prof: List<Int>, private val profStep: Int) {
    private val xy = if (coords.isEmpty()) emptyList() else Flat.toXY(coords)
    private val k = if (coords.isEmpty()) 1.0 else cos(coords.first().lat * PI / 180) * 111_320.0
    private val cum = DoubleArray(xy.size).also { c -> for (i in 1 until xy.size) c[i] = c[i - 1] + hypot(xy[i].first - xy[i - 1].first, xy[i].second - xy[i - 1].second) }
    val lengthM: Double get() = cum.lastOrNull() ?: 0.0
    /** Geglättetes Profil (gleitendes Mittel über 3 Stützstellen = 300 m), gegen Rasterrauschen. */
    private val smooth = prof.indices.map { i -> val w = prof.subList(maxOf(0, i - 1), minOf(prof.size, i + 2)); w.sum().toDouble() / w.size }
    val hasElevation: Boolean get() = prof.size >= 2 && profStep > 0

    /**
     * Position auf der Runde in m, oder null, wenn weiter als [maxOffM] von der Runde entfernt (z. B. Anfahrt).
     * [lastM] begrenzt die Suche auf ein Fenster davor/dahinter, damit sich kreuzende Abschnitte nicht verwechselt werden.
     */
    fun locate(p: LatLon, lastM: Double? = null, maxOffM: Double = 60.0, windowM: Double = 2_000.0): Double? {
        if (xy.size < 2) return null
        val x = p.lon * k; val y = p.lat * 110_540.0
        var best = Double.MAX_VALUE; var at = 0.0
        for (i in 0 until xy.size - 1) {
            if (lastM != null && (cum[i + 1] < lastM - 200 || cum[i] > lastM + windowM)) continue
            val (ax, ay) = xy[i]; val (bx, by) = xy[i + 1]; val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            val t = if (l2 == 0.0) 0.0 else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0.0, 1.0)
            val dd = hypot(x - (ax + t * dx), y - (ay + t * dy))
            if (dd < best) { best = dd; at = cum[i] + t * (cum[i + 1] - cum[i]) }
        }
        return if (best <= maxOffM) at else null
    }

    /** Anteil der Runde 0..1 (Anfahrt = 0). */
    fun progress(m: Double?): Float = if (m == null || lengthM <= 0) 0f else (m / lengthM).coerceIn(0.0, 1.0).toFloat()

    fun elevationAt(m: Double): Double? {
        if (!hasElevation) return null
        val f = (m / profStep).coerceIn(0.0, (smooth.size - 1).toDouble()); val i = f.toInt().coerceAtMost(smooth.size - 2)
        return smooth[i] + (smooth[i + 1] - smooth[i]) * (f - i)
    }

    /** Gewonnene Höhenmeter zwischen [fromM] und [toM] (nur Anstiege, geglättetes DGM-Profil). */
    fun ascent(fromM: Double, toM: Double): Double? {
        if (!hasElevation || toM <= fromM) return if (hasElevation) 0.0 else null
        var sum = 0.0; var m = fromM; var last = elevationAt(m)!!
        while (m < toM) { m = minOf(toM, m + profStep); val z = elevationAt(m)!!; if (z > last) sum += z - last; last = z }
        return sum
    }
}

/**
 * Querbeschleunigung aus dem Beschleunigungssensor. Kalibrierung: im Stillstand (GPS < 2 km/h) wird der
 * Schwerkraftvektor gemittelt; danach zählt nur die waagrechte Komponente (Betrag, in g).
 * Ohne Kalibrierung null. Setzt eine feste Halterung voraus.
 */
class GMeter(private val calSamples: Int = 25) {
    private var gx = 0.0; private var gy = 0.0; private var gz = 0.0; private var n = 0
    private var cal: Triple<Double, Double, Double>? = null
    var current: Double? = null; private set
    var max: Double? = null; private set
    val calibrated get() = cal != null
    private var fx = 0.0; private var fy = 0.0; private var fz = 0.0; private var hasF = false

    fun onSample(ax: Double, ay: Double, az: Double, standing: Boolean) {
        if (standing) {
            gx += ax; gy += ay; gz += az; n++
            if (n >= calSamples) { cal = Triple(gx / n, gy / n, gz / n); gx = 0.0; gy = 0.0; gz = 0.0; n = 0 }
        } else { gx = 0.0; gy = 0.0; gz = 0.0; n = 0 }
        val c = cal ?: return
        // leichte Glättung (Tiefpass), damit Schlaglöcher nicht als Spitzen zählen
        if (!hasF) { fx = ax; fy = ay; fz = az; hasF = true } else { fx += 0.2 * (ax - fx); fy += 0.2 * (ay - fy); fz += 0.2 * (az - fz) }
        val gl = kotlin.math.sqrt(c.first * c.first + c.second * c.second + c.third * c.third)
        if (gl < 1) return
        val ux = c.first / gl; val uy = c.second / gl; val uz = c.third / gl
        val dot = fx * ux + fy * uy + fz * uz
        val hx = fx - dot * ux; val hy = fy - dot * uy; val hz = fz - dot * uz
        val g = kotlin.math.sqrt(hx * hx + hy * hy + hz * hz) / G
        current = g; if (!standing) max = maxOf(max ?: 0.0, g)
    }
    companion object { const val G = 9.80665 }
}
