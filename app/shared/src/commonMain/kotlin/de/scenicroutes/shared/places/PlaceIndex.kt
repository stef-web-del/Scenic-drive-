package de.scenicroutes.shared.places

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/** Ein Ort aus dem Offline-Ortsindex (OSM place=*, Asset places.tsv, erzeugt mit tools/build_places.py). */
data class Place(val name: String, val kind: String, val lat: Double, val lon: Double, val near: String = "") {
    /** Anzeige: „Breitendiel“ oder „Mühlberg (Flur bei Bürgstadt)“ */
    val label: String get() = if (near.isEmpty()) name else "$name (${KIND_LABEL[kind] ?: "Ort"} bei $near)"
    companion object {
        val KIND_LABEL = mapOf("city" to "Stadt", "town" to "Stadt", "municipality" to "Gemeinde", "village" to "Dorf", "suburb" to "Stadtteil",
            "quarter" to "Viertel", "neighbourhood" to "Viertel", "hamlet" to "Weiler", "isolated_dwelling" to "Einzelhof", "farm" to "Hof",
            "locality" to "Flur", "island" to "Insel", "islet" to "Insel")
        /** kleinere Zahl = wichtiger (bei gleicher Trefferqualität zuerst) */
        val KIND_RANK = listOf("city", "town", "municipality", "village", "suburb", "quarter", "hamlet", "neighbourhood",
            "isolated_dwelling", "farm", "locality", "island", "islet").withIndex().associate { it.value to it.index }
    }
}

data class PlaceHit(val place: Place, val quality: Int, val distKm: Double?)

/**
 * Offline-Ortssuche mit Tippfehler-Toleranz. Ohne Netz, ohne Server.
 * Normalisierung: Kleinschreibung, ä/ö/ü/ß und ae/oe/ue gleich behandelt, Satzzeichen ignoriert.
 * Trefferqualität (kleiner = besser): 0 genau, 1 Anfang des Namens, 2 Anfang eines Wortes, 3 enthalten,
 * 4..6 ähnlich (1–2 Tippfehler inkl. vertauschter Buchstaben, je nach Länge der Eingabe).
 */
class PlaceIndex(val places: List<Place>) {
    private val norm = places.map { normalize(it.name) }
    private val tokens = norm.map { it.split(' ').filter(String::isNotEmpty) }

    fun search(query: String, limit: Int = 8, nearLat: Double? = null, nearLon: Double? = null): List<PlaceHit> {
        val q = normalize(query); if (q.length < 2) return emptyList()
        val qt = q.split(' ').filter(String::isNotEmpty)
        val hits = ArrayList<PlaceHit>()
        for (i in places.indices) {
            val ql = quality(q, qt, norm[i], tokens[i]) ?: continue
            val p = places[i]
            hits += PlaceHit(p, ql, if (nearLat != null && nearLon != null) km(nearLat, nearLon, p.lat, p.lon) else null)
        }
        // Weit entfernte Fluren/Weiler-Splitter (ab Einzelgehöft) nach hinten, auch ohne Standort: Bezug ist dann Miltenberg.
        val rl = nearLat ?: CENTER_LAT; val rn = nearLon ?: CENTER_LON
        fun farMinor(h: PlaceHit) = (Place.KIND_RANK[h.place.kind] ?: 99) >= MINOR_RANK && km(rl, rn, h.place.lat, h.place.lon) > FAR_KM
        return hits.sortedWith(compareBy<PlaceHit>({ farMinor(it) }, { it.quality }, { Place.KIND_RANK[it.place.kind] ?: 99 }, { it.distKm ?: 0.0 }, { it.place.name.length }, { it.place.name }))
            .distinctBy { Triple(it.place.name, it.place.kind, it.place.near) }.take(limit)
    }

    private fun quality(q: String, qt: List<String>, n: String, nt: List<String>): Int? {
        if (n == q) return 0
        if (n.startsWith(q)) return 1
        if (qt.size > 1) {   // mehrere Wörter: jedes muss zu einem Wort des Namens passen
            var worst = 0
            for (w in qt) { worst = maxOf(worst, nt.minOfOrNull { tokenQuality(w, it) } ?: return null); if (worst > 6) return null }
            return maxOf(worst, 2)
        }
        if (nt.any { it.startsWith(q) }) return 2
        if (q.length >= 3 && n.contains(q)) return 3
        val best = nt.minOfOrNull { tokenQuality(q, it) } ?: 99
        val whole = fuzzyPrefix(q, n)
        val r = min(best, whole ?: 99)
        return if (r <= 6) maxOf(r, 4) else null
    }

    private fun tokenQuality(q: String, t: String): Int = when {
        t == q -> 0
        t.startsWith(q) -> 2
        else -> fuzzyPrefix(q, t) ?: 99
    }

    /** Ähnlichkeit von q mit dem Anfang (oder ganz) von t: 4 + Zahl der Fehler, null wenn zu viele. */
    private fun fuzzyPrefix(q: String, t: String): Int? {
        val maxErr = when { q.length < 4 -> 0; q.length <= 6 -> 1; else -> 2 }
        if (maxErr == 0) return null
        var best = Int.MAX_VALUE
        for (len in (q.length - maxErr)..(q.length + maxErr)) {
            if (len < 1 || len > t.length) continue
            best = min(best, osa(q, t.substring(0, len)))
        }
        if (abs(t.length - q.length) <= maxErr) best = min(best, osa(q, t))
        return if (best <= maxErr) 4 + best else null
    }

    companion object {
        const val CENTER_LAT = 49.7018; const val CENTER_LON = 9.2560; const val FAR_KM = 20.0
        val MINOR_RANK = Place.KIND_RANK.getValue("isolated_dwelling")
        fun parse(tsv: String): PlaceIndex = PlaceIndex(tsv.lineSequence().mapNotNull { line ->
            val f = line.split('\t'); if (f.size < 4) return@mapNotNull null
            val lat = f[2].toDoubleOrNull() ?: return@mapNotNull null; val lon = f[3].toDoubleOrNull() ?: return@mapNotNull null
            Place(f[0], f[1], lat, lon, f.getOrElse(4) { "" })
        }.toList())

        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s.lowercase()) when (c) {
                'ä', 'á', 'à', 'â' -> sb.append('a'); 'ö', 'ó', 'ò', 'ô' -> sb.append('o'); 'ü', 'ú', 'ù', 'û' -> sb.append('u')
                'é', 'è', 'ê' -> sb.append('e'); 'ß' -> sb.append("ss")
                else -> sb.append(if (c.isLetterOrDigit()) c else ' ')
            }
            return sb.toString().replace("ae", "a").replace("oe", "o").replace("ue", "u")
                .replace(Regex("\\bst\\b"), "sankt").replace(Regex(" +"), " ").trim()
        }

        /** Optimal String Alignment (Levenshtein + Vertauschung benachbarter Buchstaben) */
        fun osa(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                val c = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
            }
            return d[a.length][b.length]
        }

        fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val x = (lon2 - lon1) * 111.32 * cos((lat1 + lat2) / 2 * kotlin.math.PI / 180); val y = (lat2 - lat1) * 110.54
            return sqrt(x * x + y * y)
        }
    }
}
