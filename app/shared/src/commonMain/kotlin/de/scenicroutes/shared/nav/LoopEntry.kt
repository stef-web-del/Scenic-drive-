package de.scenicroutes.shared.nav

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Google-Polyline mit Präzision 6 (OSRM/Valhalla polyline6). */
object Polyline6 {
    fun decode(s: String): List<LatLon> {
        val out = ArrayList<LatLon>(); var i = 0; var lat = 0L; var lon = 0L
        fun next(): Long { var r = 0L; var sh = 0; var b: Int; do { b = s[i++].code - 63; r = r or ((b and 0x1f).toLong() shl sh); sh += 5 } while (b >= 0x20); return if (r and 1L != 0L) (r shr 1).inv() else r shr 1 }
        while (i < s.length) { lat += next(); lon += next(); out += LatLon(lat / 1e6, lon / 1e6) }
        return out
    }
    fun encode(pts: List<LatLon>): String {
        val sb = StringBuilder(); var pl = 0L; var po = 0L
        fun put(v: Long) { var x = if (v < 0) (v shl 1).inv() else v shl 1; while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar()); x = x shr 5 }; sb.append((x + 63).toInt().toChar()) }
        for (p in pts) { val la = (p.lat * 1e6).roundToLong(); val lo = (p.lon * 1e6).roundToLong(); put(la - pl); put(lo - po); pl = la; po = lo }
        return sb.toString()
    }
}

/**
 * Einstiegspunkt in die Runde: Schritt [step] der vorberechneten Route, Stützpunkt [vertex] in dessen Geometrie
 * (0 = Manöverpunkt am Schrittanfang). [approachM] = Luftlinie von der Position zum Einstieg.
 */
data class LoopEntryPoint(
    val step: Int, val vertex: Int, val point: LatLon, val alongM: Double,
    val approachM: Double, val offsetM: Double, val bearingDeg: Double, val roadName: String,
    /** true = [point] liegt zwischen den Stützpunkten vertex-1 und vertex und wird dort eingefügt. */
    val inserted: Boolean = false,
) {
    val isOfficialStart get() = step == 0 && vertex == 0
}

/**
 * Flexibler Einstieg: Einstiegspunkt wählen und die vorberechnete OSRM-Route daran rotieren
 * (Einstieg → Rundenende, Rundenanfang → Einstieg). Vollständig offline, nur JSON-Umbau.
 */
object LoopEntry {
    /** Bis zu dieser Luftlinie startet die Navigation direkt am Einstieg. */
    const val DIRECT_START_M = 150.0
    private const val SNAP_AHEAD_M = 80.0
    private const val SNAP_BEHIND_M = 30.0
    private const val JUNCTION_CLEAR_M = 40.0
    private val ROUND = setOf("roundabout", "rotary", "roundabout turn")

    private class St(val obj: JsonObject, val pts: List<LatLon>, val cum: DoubleArray, val start: Double) {
        val type get() = obj["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content
        val dist get() = obj["distance"]!!.jsonPrimitive.double
        val len get() = cum.last()
    }

    private fun steps(route: JsonObject): List<St> {
        val raw = route["legs"]!!.jsonArray.flatMap { it.jsonObject["steps"]!!.jsonArray.map { s -> s.jsonObject } }
        var acc = 0.0; var last: LatLon? = null
        return raw.map { s ->
            val p = Polyline6.decode(s["geometry"]!!.jsonPrimitive.content); val c = Geo.cumulative(p)
            // Sprung an Zusammenführungsstellen mitzählen (wie appendGeom), damit Streckenpositionen übereinstimmen
            last?.let { l -> val gap = Geo.distanceM(l, p[0]); if (gap > 2.0) acc += gap }
            St(s, p, c, acc).also { acc += c.last(); last = p.last() }
        }
    }

    private fun stepOk(st: St) = st.type !in ROUND && st.len >= JUNCTION_CLEAR_M

    /** Entry am Schrittanfang (vertex 0) bzw. am Stützpunkt; Schrittende-Index n-1 (arrive) = offizieller Start. */
    private fun entry(st: List<St>, i0: Int, v0: Int, from: LatLon, offset: Double): LoopEntryPoint {
        var i = i0; var v = v0
        // Kreisverkehr / sehr kurze Schritte: zum nächsten sinnvollen Manöverpunkt weiter
        while (i < st.size - 1 && !stepOk(st[i])) { i++; v = 0 }
        if (i >= st.size - 1) { i = 0; v = 0 }
        val s = st[i]; val p = s.pts[v]
        val b = if (v + 1 < s.pts.size) Geo.bearingDeg(p, s.pts[v + 1]) else 0.0
        val name = s.obj["name"]?.jsonPrimitive?.content.orEmpty().ifEmpty { s.obj["ref"]?.jsonPrimitive?.content.orEmpty() }
        return LoopEntryPoint(i, v, p, s.start + s.cum[v], Geo.distanceM(from, p), offset, b, name)
    }

    /** Alle sinnvollen Einstiegskandidaten (je Durchfahrt ein lokales Minimum), bester zuerst. */
    fun candidates(route: JsonObject, pos: LatLon, headingDeg: Double? = null, max: Int = 4): List<LoopEntryPoint> {
        val st = steps(route)
        val all = st.flatMapIndexed { i, s -> s.pts.dropLast(1).indices.map { v -> Triple(i, v, s) } }
        val pts = ArrayList<LatLon>(); st.forEach { s -> appendGeom(pts, s.pts) }
        val line = Polyline(pts)
        // Projektion je Segment, lokale Minima in einem 300-m-Fenster
        data class Pr(val d: Double, val along: Double)
        val prs = (0 until pts.size - 1).map { seg ->
            val pl = Polyline(listOf(pts[seg], pts[seg + 1])); val pr = pl.project(pos)
            Pr(pr.distanceM, line.cum[seg] + pr.alongM)
        }
        val mins = prs.indices.filter { k ->
            val d = prs[k].d
            prs.indices.none { j -> j != k && abs(prs[j].along - prs[k].along) < 300 && (prs[j].d < d || (prs[j].d == d && j < k)) }
        }.sortedBy { prs[it].d }.take(max * 2)
        val out = mins.map { k -> snap(st, prs[k].along, pos, prs[k].d) }
        fun score(e: LoopEntryPoint): Double {
            var sc = e.approachM
            if (headingDeg != null) { val dd = abs(((e.bearingDeg - headingDeg + 540) % 360) - 180); if (dd > 100) sc += 2000 }
            return sc
        }
        return out.distinctBy { it.step to it.vertex }.sortedWith(compareBy<LoopEntryPoint>({ score(it) }, { it.alongM })).take(max)
            .also { require(all.isNotEmpty()) }
    }

    fun best(route: JsonObject, pos: LatLon, headingDeg: Double? = null) = candidates(route, pos, headingDeg, 1).first()

    /** Einstieg an einer Streckenposition (für manuelle Wahl / Tests). */
    fun atAlong(route: JsonObject, alongM: Double, from: LatLon? = null): LoopEntryPoint {
        val st = steps(route); val s = st.first { alongM < it.start + it.len || it === st.last() }
        val p = Polyline(s.pts).pointAt(alongM - s.start)
        return snap(st, alongM, from ?: p, 0.0)
    }

    private fun snap(st: List<St>, along: Double, pos: LatLon, offset: Double): LoopEntryPoint {
        // 1) Manöverpunkt in der Nähe? (kurz voraus oder knapp dahinter)
        for ((i, s) in st.withIndex()) {
            val a = s.start
            if (along - a in -SNAP_AHEAD_M..SNAP_BEHIND_M) return entry(st, if (i == st.size - 1) 0 else i, 0, pos, offset)
        }
        val i = st.indexOfLast { it.start <= along }.coerceAtLeast(0)
        val s = st[i]; val rel = along - s.start
        if (!stepOk(s)) return entry(st, i + 1, 0, pos, offset)
        // 2) nächster Stützpunkt voraus, nicht in Kreuzungsnähe
        var v = s.cum.indexOfFirst { it >= rel }.let { if (it < 0) s.pts.size - 1 else it }
        if (s.len - s.cum[v] < JUNCTION_CLEAR_M || v >= s.pts.size - 1) return entry(st, i + 1, 0, pos, offset)
        if (s.cum[v] < JUNCTION_CLEAR_M) return entry(st, i, 0, pos, offset)
        if (s.cum[v] - rel > 25.0 && v > 0 && rel - s.start * 0 > JUNCTION_CLEAR_M) {
            // weit bis zum nächsten Knoten (lange Gerade): Projektionspunkt als Einstieg einfügen
            val a = s.pts[v - 1]; val b = s.pts[v]; val seg = s.cum[v] - s.cum[v - 1]
            val t = if (seg <= 0) 0.0 else (rel - s.cum[v - 1]) / seg
            val p = LatLon(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
            val name = nameOf(s.obj)
            return LoopEntryPoint(i, v, p, along, Geo.distanceM(pos, p), offset, Geo.bearingDeg(p, b), name, inserted = true)
        }
        return entry(st, i, v, pos, offset)
    }

    // ---------- Rotation ----------

    private fun num(x: Double) = JsonPrimitive((x * 10).roundToInt() / 10.0)
    private fun JsonObject.with(vararg kv: Pair<String, JsonElement>) = JsonObject(this + kv)
    private fun JsonObject.d(k: String) = this[k]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private val MOD_DE = mapOf(
        "right" to "Rechts", "left" to "Links", "slight right" to "Leicht rechts", "slight left" to "Leicht links",
        "sharp right" to "Scharf rechts", "sharp left" to "Scharf links", "uturn" to "Wenden", "straight" to "Geradeaus",
    )

    /** Manöver-Modifier aus der Kursänderung (positiv = rechts). */
    fun modifierFor(bearingBefore: Double, bearingAfter: Double): String {
        val d = ((bearingAfter - bearingBefore + 540) % 360) - 180; val a = abs(d); val side = if (d >= 0) "right" else "left"
        return when { a < 20 -> "straight"; a < 60 -> "slight $side"; a < 140 -> side; a < 170 -> "sharp $side"; else -> "uturn" }
    }

    private fun nameOf(s: JsonObject) = s["name"]?.jsonPrimitive?.content.orEmpty().ifEmpty { s["ref"]?.jsonPrimitive?.content.orEmpty() }

    private fun banner(dist: Double, type: String, modifier: String?, text: String) = buildJsonObject {
        put("distanceAlongGeometry", num(dist))
        put("primary", buildJsonObject {
            put("text", text); put("type", type); modifier?.let { put("modifier", it) }
            put("components", buildJsonArray { add(buildJsonObject { put("text", text); put("type", "text") }) })
        })
    }
    private fun voice(dist: Double, text: String) = buildJsonObject {
        put("distanceAlongGeometry", num(dist)); put("announcement", text)
        put("ssmlAnnouncement", "<speak><prosody rate=\"1.08\"><amazon:effect name=\"drc\">$text</amazon:effect></prosody></speak>")
    }

    /** Ansagen für einen Schritt der Länge [len], der auf das Manöver [m] des Folgeschritts zuführt. */
    private fun approachTo(len: Double, next: JsonObject): Pair<JsonArray, JsonArray> {
        val m = next["maneuver"]!!.jsonObject; val type = m["type"]!!.jsonPrimitive.content
        val mod = m["modifier"]?.jsonPrimitive?.content; val name = nameOf(next)
        val act = if (type == "arrive") "Einstieg erreicht. Runde beendet." else m["instruction"]!!.jsonPrimitive.content
        val pre = if (type == "arrive") "In 500 Metern erreichen Sie den Einstieg, die Runde ist dann beendet." else "In 500 Metern: ${act.removeSuffix(".")}."
        val voices = buildJsonArray {
            if (len > 700) add(voice(500.0, pre))
            add(voice(min(len, if (type == "arrive") 60.0 else 120.0), act))
        }
        val text = if (type == "arrive") "Einstieg (Rundenende)" else name.ifEmpty { act.removeSuffix(".") }
        return buildJsonArray { add(banner(len, type, mod, text)) } to voices
    }

    private fun seamManeuver(old0: JsonObject, arrive: JsonObject): JsonObject {
        val m0 = old0["maneuver"]!!.jsonObject; val ma = arrive["maneuver"]!!.jsonObject
        val bb = ma.d("bearing_before"); val ba = m0.d("bearing_after")
        val mod = modifierFor(bb, ba); val name = nameOf(old0)
        val (type, instr) = when (mod) {
            "straight" -> "continue" to (if (name.isEmpty()) "Geradeaus weiterfahren." else "Geradeaus weiter auf $name.")
            "uturn" -> "turn" to (if (name.isEmpty()) "Wenden." else "Wenden auf $name.")
            else -> "turn" to (if (name.isEmpty()) "${MOD_DE[mod]} abbiegen." else "${MOD_DE[mod]} abbiegen auf $name.")
        }
        val mm = buildJsonObject {
            put("type", type); put("modifier", mod); put("instruction", instr)
            put("bearing_before", JsonPrimitive(bb.roundToInt())); put("bearing_after", JsonPrimitive(ba.roundToInt()))
            put("location", m0["location"]!!)
        }
        return old0.with("maneuver" to mm)
    }

    private class Part(val obj: JsonObject, val pts: List<LatLon>, val src: Int, val kind: Kind)
    private enum class Kind { KEEP, ENTRY, SEAM, LAST }

    private fun splitStep(s: St, v: Int): Pair<JsonObject, JsonObject> {
        val f = s.cum[v] / s.len
        fun scaled(o: JsonObject, k: Double) = o.with("distance" to num(o.d("distance") * k), "duration" to num(o.d("duration") * k), "weight" to num(o.d("weight") * k))
        val p1 = scaled(s.obj, f).with("geometry" to JsonPrimitive(Polyline6.encode(s.pts.subList(0, v + 1))))
        val p2 = scaled(s.obj, 1 - f).with("geometry" to JsonPrimitive(Polyline6.encode(s.pts.subList(v, s.pts.size))))
        return p1 to p2
    }

    private fun intersectionsFor(obj: JsonObject, pts: List<LatLon>, base: Int, origin: JsonArray?, firstBearing: Double): JsonArray {
        val sel = (origin ?: JsonArray(emptyList())).map { it.jsonObject }.mapNotNull { x ->
            val l = x["location"]!!.jsonArray; val p = LatLon(l[1].jsonPrimitive.double, l[0].jsonPrimitive.double)
            val j = pts.indices.minBy { Geo.distanceM(pts[it], p) }
            if (Geo.distanceM(pts[j], p) > 3.0 || j == pts.size - 1 && pts.size > 1) null else x.with("geometry_index" to JsonPrimitive(base + j)) to j
        }
        return buildJsonArray {
            if (sel.none { it.second == 0 }) add(buildJsonObject {
                put("location", buildJsonArray { add(JsonPrimitive(pts[0].lon)); add(JsonPrimitive(pts[0].lat)) })
                put("bearings", buildJsonArray { add(JsonPrimitive(firstBearing.roundToInt())) })
                put("entry", buildJsonArray { add(JsonPrimitive(true)) }); put("out", 0); put("geometry_index", base)
            })
            sel.forEach { add(it.first) }
        }
    }

    /**
     * Rotiert [bundle] am [entry]. Ergebnis: depart am Einstieg, Runde bis Rundenende, Übergang am offiziellen
     * Start (neu abgeleitetes Manöver, keine Ankunft), weiter bis zum Einstieg, dort arrive.
     */
    fun rotate(bundle: RouteBundle, entry: LoopEntryPoint): RouteBundle = cut(bundle, entry, wrap = true)

    /** Rest der Route ab [entry] bis zum ursprünglichen Ziel (Wiedereinstieg nach Abweichung/Sperrung). */
    fun tail(bundle: RouteBundle, entry: LoopEntryPoint): RouteBundle = cut(bundle, entry, wrap = false)

    private fun cut(bundle: RouteBundle, entry: LoopEntryPoint, wrap: Boolean): RouteBundle {
        if (wrap && entry.isOfficialStart) return bundle
        val route = bundle.route; val st0 = steps(route); val n = st0.size
        val k = entry.step; val v = entry.vertex
        require(k in 0 until n - 1) { "Einstieg außerhalb der Route" }
        val st = if (!entry.inserted) st0 else st0.toMutableList().also { l ->
            val o = l[k]; val np = o.pts.toMutableList().apply { add(v, entry.point) }
            l[k] = St(o.obj, np, Geo.cumulative(np), o.start)
        }
        val arrive = st[n - 1].obj
        val parts = ArrayList<Part>()
        val (p1, p2) = if (v > 0) splitStep(st[k], v) else null to st[k].obj
        parts += Part(p2, if (v > 0) st[k].pts.subList(v, st[k].pts.size) else st[k].pts, k, Kind.ENTRY)
        for (i in k + 1 until n - 1) parts += Part(st[i].obj, st[i].pts, i, Kind.KEEP)
        if (wrap) {
            val tail = ArrayList<Part>()
            for (i in 0 until k) tail += Part(st[i].obj, st[i].pts, i, Kind.KEEP)
            if (p1 != null) tail += Part(p1, st[k].pts.subList(0, v + 1), k, Kind.LAST)
            tail[0] = Part(seamManeuver(tail[0].obj, arrive), tail[0].pts, tail[0].src, Kind.SEAM)
            parts += tail
        }
        // Depart am Einstieg
        val e0 = parts[0]; val name0 = nameOf(e0.obj)
        val departTxt = if (name0.isEmpty()) "Der Runde folgen." else "Auf $name0 der Runde folgen."
        val dm = buildJsonObject {
            put("type", "depart"); put("instruction", departTxt)
            put("bearing_before", 0); put("bearing_after", JsonPrimitive(entry.bearingDeg.roundToInt()))
            put("location", buildJsonArray { add(JsonPrimitive(entry.point.lon)); add(JsonPrimitive(entry.point.lat)) })
        }
        val last = parts.last()
        val endB = if (last.pts.size >= 2) Geo.bearingDeg(last.pts[last.pts.size - 2], last.pts.last()) else 0.0
        val endPt = if (wrap) entry.point else last.pts.last()
        val newArrive = if (!wrap) arrive else arrive.with(
            "maneuver" to buildJsonObject {
                put("type", "arrive"); put("instruction", "Einstieg erreicht. Runde beendet.")
                put("bearing_before", JsonPrimitive(endB.roundToInt())); put("bearing_after", 0)
                put("location", buildJsonArray { add(JsonPrimitive(entry.point.lon)); add(JsonPrimitive(entry.point.lat)) })
            },
            "geometry" to JsonPrimitive(Polyline6.encode(listOf(entry.point, entry.point))),
            "bannerInstructions" to JsonArray(emptyList()), "voiceInstructions" to JsonArray(emptyList()),
        )
        // Ansagen neu zuordnen, Indizes neu rechnen
        val out = ArrayList<JsonObject>(); var base = 0
        val geom = ArrayList<LatLon>()
        for ((idx, p) in parts.withIndex()) {
            if (geom.isNotEmpty() && Geo.distanceM(geom.last(), p.pts[0]) > 2.0) base += 1
            val len = Geo.cumulative(p.pts).last()
            val next = if (idx + 1 < parts.size) parts[idx + 1] else null
            var o = p.obj
            if (idx == 0) o = o.with("maneuver" to dm)
            val (bn, vc) = when {
                next == null && wrap -> approachTo(len, newArrive)
                next != null && next.kind == Kind.SEAM -> approachTo(len, next.obj)
                p.kind == Kind.ENTRY -> {
                    // Ansagen des Originalschritts, die in den Reststück passen; vorne Depart-Ansage
                    val ob = (p.obj["bannerInstructions"] as? JsonArray).orEmpty().map { it.jsonObject }.filter { it.d("distanceAlongGeometry") <= len + 1 }
                    val bns = if (ob.isEmpty()) (p.obj["bannerInstructions"] as? JsonArray).orEmpty().map { it.jsonObject }.take(1) else ob
                    val b2 = bns.mapIndexed { i, b -> if (i == 0) b.with("distanceAlongGeometry" to num(len)) else b }
                    val ov = (p.obj["voiceInstructions"] as? JsonArray).orEmpty().map { it.jsonObject }.filter { it.d("distanceAlongGeometry") <= len - 30 }
                    JsonArray(b2) to JsonArray(listOf(voice(len, departTxt)) + ov)
                }
                next != null && hasFalseArrival(p.obj) -> approachTo(len, next.obj)
                else -> ((p.obj["bannerInstructions"] as? JsonArray) ?: JsonArray(emptyList())) to ((p.obj["voiceInstructions"] as? JsonArray) ?: JsonArray(emptyList()))
            }
            val firstB = if (p.pts.size >= 2) Geo.bearingDeg(p.pts[0], p.pts[1]) else 0.0
            o = o.with("bannerInstructions" to bn, "voiceInstructions" to vc,
                "intersections" to intersectionsFor(o, p.pts, base, st[p.src].obj["intersections"] as? JsonArray, firstB))
            out += o
            appendGeom(geom, p.pts)
            base += p.pts.size - 1
        }
        out += newArrive.with("intersections" to buildJsonArray { add(buildJsonObject {
            put("location", buildJsonArray { add(JsonPrimitive(endPt.lon)); add(JsonPrimitive(endPt.lat)) })
            put("bearings", buildJsonArray { add(JsonPrimitive(((endB + 180) % 360).roundToInt())) }); put("entry", buildJsonArray { add(JsonPrimitive(true)) }); put("in", 0); put("geometry_index", base)
        }) })
        val leg0 = route["legs"]!!.jsonArray[0].jsonObject
        val dist = out.sumOf { it.d("distance") }; val dur = out.sumOf { it.d("duration") }; val w = out.sumOf { it.d("weight") }
        val leg = leg0.with("steps" to JsonArray(out), "distance" to num(dist), "duration" to num(dur), "weight" to num(w))
            .let { l -> JsonObject(l - "annotation" - "via_waypoints") .with("via_waypoints" to JsonArray(emptyList())) }
        val newRoute = route.with("legs" to JsonArray(listOf(leg)), "geometry" to JsonPrimitive(Polyline6.encode(geom)),
            "distance" to num(dist), "duration" to num(dur), "weight" to num(w))
        val wp = buildJsonObject {
            put("name", entry.roadName.ifEmpty { "Einstieg" }); put("distance", 0)
            put("location", buildJsonArray { add(JsonPrimitive(entry.point.lon)); add(JsonPrimitive(entry.point.lat)) })
        }
        return bundle.copy(route = newRoute, waypoints = if (wrap) listOf(wp, wp) else listOf(wp, bundle.waypoints.last()))
    }

    private fun hasFalseArrival(o: JsonObject) =
        (o["bannerInstructions"] as? JsonArray).orEmpty().any { it.jsonObject["primary"]?.jsonObject?.get("type")?.jsonPrimitive?.content == "arrive" } ||
            (o["voiceInstructions"] as? JsonArray).orEmpty().any { "Ziel" in it.jsonObject["announcement"]!!.jsonPrimitive.content }

    /**
     * Entfernt Zwischen-Ankünfte (an Zusammenführungsstellen der Vorberechnung stand teils „Ziel erreicht“ mitten
     * in der Runde). Nur der letzte Schritt vor arrive behält die Ankunftsansage.
     */
    fun sanitize(bundle: RouteBundle): RouteBundle {
        val st = steps(bundle.route); val n = st.size
        if ((0 until n - 2).none { hasFalseArrival(st[it].obj) }) return bundle
        val out = st.mapIndexed { i, s ->
            if (i < n - 2 && hasFalseArrival(s.obj)) { val (b, v) = approachTo(s.len, st[i + 1].obj); s.obj.with("bannerInstructions" to b, "voiceInstructions" to v) } else s.obj
        }
        val leg = route0(bundle).with("steps" to JsonArray(out))
        return bundle.copy(route = bundle.route.with("legs" to JsonArray(listOf(leg))))
    }
    private fun route0(b: RouteBundle) = b.route["legs"]!!.jsonArray[0].jsonObject

    /** Schrittgeometrien verketten; Lücken (Zusammenführungsstellen der Vorberechnung) bleiben als Sprung erhalten. */
    private fun appendGeom(g: MutableList<LatLon>, p: List<LatLon>) {
        if (g.isEmpty() || Geo.distanceM(g.last(), p[0]) > 2.0) g += p else g += p.drop(1)
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    /**
     * Anfahrt (Valhalla, Ziel = Einstieg) nahtlos vor die rotierte Runde setzen: eine durchgehende Route,
     * am Einstieg keine Ankunft, sondern das abgeleitete Abbiegemanöver auf die Runde.
     */
    fun prepend(approach: RouteBundle, loop: RouteBundle): RouteBundle {
        val a = steps(approach.route); val l = steps(loop.route)
        if (a.size < 2) return loop
        val arriveA = a.last().obj
        val first = l.first().obj; val fm = first["maneuver"]!!.jsonObject
        val bb = arriveA["maneuver"]!!.jsonObject.d("bearing_before"); val ba = fm.d("bearing_after")
        val mod = modifierFor(bb, ba); val name = nameOf(first)
        val instr = when (mod) { "straight" -> if (name.isEmpty()) "Geradeaus auf die Runde." else "Geradeaus weiter auf $name, Beginn der Runde."
            else -> "${MOD_DE[mod]} abbiegen" + (if (name.isEmpty()) ", Beginn der Runde." else " auf $name, Beginn der Runde.") }
        val joined = first.with("maneuver" to fm.with("type" to JsonPrimitive(if (mod == "straight") "continue" else "turn"), "modifier" to JsonPrimitive(mod), "instruction" to JsonPrimitive(instr), "bearing_before" to JsonPrimitive(bb.roundToInt())))
        val out = ArrayList<JsonObject>(); val geom = ArrayList<LatLon>(); var base = 0
        val all = a.dropLast(1).map { it.obj to it.pts } + listOf(joined to l[0].pts) + l.drop(1).map { it.obj to it.pts }
        for ((i, pair) in all.withIndex()) {
            var (o, pts) = pair
            if (geom.isNotEmpty() && Geo.distanceM(geom.last(), pts[0]) > 2.0) base += 1
            if (i == a.size - 2) { val (b, v) = approachTo(Geo.cumulative(pts).last(), joined); o = o.with("bannerInstructions" to b, "voiceInstructions" to v) }
            val origBase = (o["intersections"] as? JsonArray).orEmpty().firstOrNull()?.jsonObject?.get("geometry_index")?.jsonPrimitive?.int ?: 0
            o = o.with("intersections" to JsonArray((o["intersections"] as? JsonArray).orEmpty().map { x -> val xo = x.jsonObject; xo.with("geometry_index" to JsonPrimitive(base + (xo["geometry_index"]!!.jsonPrimitive.int - origBase))) }))
            out += o; appendGeom(geom, pts)
            if (pts.size > 1) base += pts.size - 1
        }
        val dist = out.sumOf { it.d("distance") }; val dur = out.sumOf { it.d("duration") }
        val leg = route0(loop).with("steps" to JsonArray(out), "distance" to num(dist), "duration" to num(dur))
        val route = loop.route.with("legs" to JsonArray(listOf(leg)), "geometry" to JsonPrimitive(Polyline6.encode(geom)), "distance" to num(dist), "duration" to num(dur))
        return loop.copy(route = route, waypoints = listOf(approach.waypoints.first(), loop.waypoints.last()))
    }

    /** Geometrie der Runde ab [alongM] rotiert (für Abweichungsmonitor nach Einstieg). */
    fun rotateLine(points: List<LatLon>, alongM: Double): List<LatLon> {
        val pl = Polyline(points); if (alongM <= 0.5 || alongM >= pl.lengthM - 0.5) return points
        val pr = pl.project(pl.pointAt(alongM), hintAlongM = alongM, windowM = 50.0); val i = pr.segment
        return listOf(pr.point) + points.subList(i + 1, points.size) + points.subList(1, i + 1) + pr.point
    }

    /** Alle Schritt-Manöver (Typ, Text) – für Tests/Simulation. */
    fun maneuvers(route: JsonObject) = steps(route).map { it.type to (it.obj["maneuver"]!!.jsonObject["instruction"]?.jsonPrimitive?.content ?: "") }
    fun voices(route: JsonObject) = steps(route).flatMap { s -> (s.obj["voiceInstructions"] as? JsonArray).orEmpty().map { it.jsonObject["announcement"]!!.jsonPrimitive.content } }
}
