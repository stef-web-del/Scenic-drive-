package de.scenicroutes.shared.grid

import de.scenicroutes.shared.model.Highlight
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.model.Pause
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.*

/** „Runde ab hier“: vorberechnetes Startraster (grid/v1, erzeugt von scenic/grid_build.py). Koordinaten [lon, lat]. */
enum class Profile(val key: String, val label: String, val hint: String) {
    SPORTLICH("sportlich", "Sportlich", "Kurven, Rhythmus, Relief"),
    GENIESSER("geniesser", "Genießer", "Aussicht, Landschaft, wenig Orte"),
    ENTDECKER("entdecker", "Entdecker", "viele Orte, Höhenmeter, Einkehr");
    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: SPORTLICH }
}

@Serializable data class GridPoint(val pid: Int, val name: String, val lat: Double, val lon: Double, val n: Int = 0, val valid: Int? = null)
@Serializable data class GridIndex(val v: Int, val center: List<Double>, val radius_km: Double, val spacing_km: Double, val minutes: Double, val points: List<GridPoint>)
@Serializable data class RankEntry(val id: Int, val rank: Int, val score: Double, val maxov: Double = 0.0, val why: List<String>? = null)
@Serializable data class GLoop(
    val id: Int, val ti: String, val km: Double, val min: Int, val t: Double, val s: Double, val kl: String, val hm: Int, val rg: String = "",
    val tags: List<String> = emptyList(), val g: List<List<Double>>, val prof: List<Int> = emptyList(), val pstep: Int = 500,
    val hl: List<Highlight> = emptyList(), val orte: List<String> = emptyList(), val pausen: List<Pause> = emptyList(),
    val nav: Boolean = false, val navMatch: Double = 1.0, val gurl: String? = null, val sha: String = "",
) {
    /** Für Detailansicht und In-App-Navigation (gleiches Modell wie die Katalogrunden). */
    fun toLoop() = Loop(id = id, title = ti, km = km, min = min, score = s, klasse = kl, region = rg, hm = hm, tags = tags, geom = g,
        prof = prof, profStep = pstep, highlights = hl, pauses = pausen, orte = orte, tour = t, navMatch = navMatch, navSha = sha)
    fun reversed(): GLoop {
        fun flip(k: Double, lenM: Int = 0) = ((km - k - lenM / 1000.0) * 10).roundToInt() / 10.0
        return copy(g = g.reversed(), prof = prof.reversed(), hl = hl.map { it.copy(km = flip(it.km, it.len)) }.sortedBy { it.km },
            pausen = pausen.map { it.copy(km = flip(it.km)) }.sortedBy { it.km }, orte = orte.reversed(), gurl = gurl?.let(::reverseGoogleUrl))
    }
}
@Serializable data class PointData(val pid: Int, val name: String, val minutes: Double, val valid: Int, val profiles: Map<String, List<RankEntry>>, val loops: Map<String, GLoop>)

object Grid {
    const val PAGE = 6
    val STRICT = setOf("A+", "A", "B")
    val REPORT_KINDS = listOf("Baustelle / gesperrt", "Straßenzustand schlecht", "Zu viel Verkehr", "Gefährliche Stelle", "Sonstiges")
    private val json = Json { ignoreUnknownKeys = true }
    fun parseIndex(s: String): GridIndex = json.decodeFromString(GridIndex.serializer(), s)
    fun parsePoint(s: String): PointData = json.decodeFromString(PointData.serializer(), s)

    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = PI / 180; val a = sin((lat2 - lat1) * r / 2).pow(2) + cos(lat1 * r) * cos(lat2 * r) * sin((lon2 - lon1) * r / 2).pow(2)
        return 2 * 6371 * asin(sqrt(a))
    }
    data class Near(val point: GridPoint, val km: Double, val approachMin: Int)
    /** Nächster Rasterpunkt mit Runden; Anfahrt grob: Luftlinie × 1,35 bei 50 km/h. */
    fun nearest(idx: GridIndex, lat: Double, lon: Double): Near? = idx.points.filter { it.n > 0 }.minByOrNull { km(lat, lon, it.lat, it.lon) }
        ?.let { p -> val d = km(lat, lon, p.lat, p.lon); Near(p, d, max(1, (d * 1.35 / 50 * 60).roundToInt())) }
    fun inArea(idx: GridIndex, lat: Double, lon: Double) = km(lat, lon, idx.center[0], idx.center[1]) <= idx.radius_km + idx.spacing_km

    data class Filters(val hideDriven: Boolean = false, val relaxQuality: Boolean = false, val lessOverlap: Boolean = false)
    data class Shown(val e: RankEntry, val l: GLoop, val driven: Boolean, val ovKnown: Double)
    data class Page(val list: List<Shown>, val total: Int, val more: Boolean)
    fun shown(d: PointData, p: Profile, f: Filters, driven: Set<Int>, known: List<List<List<Double>>>, pages: Int): Page {
        val kc = if (f.lessOverlap) known.map(::cells) else emptyList()
        val all = (d.profiles[p.key] ?: emptyList()).mapNotNull { e ->
            val l = d.loops[e.id.toString()] ?: return@mapNotNull null
            val dr = e.id in driven
            if (f.hideDriven && dr) return@mapNotNull null
            if (!f.relaxQuality && l.kl !in STRICT) return@mapNotNull null
            var ov = 0.0
            if (kc.isNotEmpty() && !dr) { val c = cells(l.g); ov = kc.maxOf { overlap(c, it) }; if (ov > 0.5) return@mapNotNull null }
            Shown(e, l, dr, ov)
        }
        return Page(all.take(pages * PAGE), all.size, all.size > pages * PAGE)
    }
    /** ~100-m-Zellen entlang der Linie (wie Web). */
    fun cells(g: List<List<Double>>, size: Double = 0.0012): Set<Long> {
        val s = HashSet<Long>()
        for (i in 1 until g.size) {
            val a = g[i - 1]; val b = g[i]; val n = max(1, ceil(hypot(b[0] - a[0], b[1] - a[1]) / (size / 2)).toInt())
            for (k in 0..n) { val x = a[0] + (b[0] - a[0]) * k / n; val y = a[1] + (b[1] - a[1]) * k / n; s.add((x / size / 1.5).roundToLong() * 1_000_000L + (y / size).roundToLong()) }
        }
        return s
    }
    fun overlap(a: Set<Long>, b: Set<Long>) = a.count { it in b }.toDouble() / max(1, min(a.size, b.size))

    /** Sonnenuntergang (NOAA-Näherung) in Epoch-ms (UTC) für Kalendertag year/month(1-12)/day am Ort. */
    fun sunsetUtcMs(year: Int, month: Int, day: Int, lat: Double, lon: Double): Long? {
        val dim = intArrayOf(31, if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val n = (0 until month - 1).sumOf { dim[it] } + day
        val g = 2 * PI / 365 * (n - 1 + 0.5); val rad = PI / 180
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val c = cos(90.833 * rad) / (cos(lat * rad) * cos(decl)) - tan(lat * rad) * tan(decl)
        if (c < -1 || c > 1) return null
        val utcMin = 720 - 4 * (lon - acos(c) / rad) - eq
        return daysFromCivil(year, month, day) * 86_400_000L + (utcMin * 60_000).roundToLong()
    }
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0; val era = (if (y >= 0) y else y - 399) / 400; val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1; val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }
}

fun reverseGoogleUrl(u: String): String {
    val i = u.indexOf("waypoints="); if (i < 0) return u
    val end = u.indexOf('&', i).let { if (it < 0) u.length else it }
    val v = u.substring(i + 10, end); val sep = if (v.contains("%7C")) "%7C" else "|"
    return u.substring(0, i + 10) + v.split(sep).reversed().joinToString(sep) + u.substring(end)
}
