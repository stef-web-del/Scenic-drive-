package de.scenicroutes.shared.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** Gemeinsame Begriffe mit der Web-App (webapp/src/lib/score.ts) – Quelle: design/tokens.json "terms". */
data class MetricDef(val key: String, val label: String, val chip: String?, val help: String, val fmt: (Double) -> String)

object ScoreDefs {
    fun de(x: Double, d: Int): String {
        val f = pow10(d).toLong(); val n = kotlin.math.round(x * f).toLong()
        val sign = if (n < 0) "-" else ""; val a = abs(n)
        return if (d == 0) "$sign$a" else "$sign${a / f}," + (a % f).toString().padStart(d, '0')
    }
    private fun pow10(d: Int) = (1..d).fold(1.0) { a, _ -> a * 10 }
    private fun pct(x: Double) = "${(x * 100).roundToInt()} %"
    val METRICS = listOf(
        MetricDef("kurv", "Kurvigkeit", "Sehr kurvig", "Mittlere Kurvigkeit entlang der Runde (Richtungswechsel je Strecke, 0–1).") { de(it, 2) },
        MetricDef("kehr", "Enge Kurven", "Viele enge Kurven", "Richtungsänderung in engen Radien, Grad je km.") { de(it, 0) + " °/km" },
        MetricDef("rhy", "Rhythmus", "Flüssiger Rhythmus", "Wie gleichmäßig die Kurvigkeit innerhalb der Abschnitte ist (1 = sehr gleichmäßig).") { de(it, 2) },
        MetricDef("rel", "Relief", "Bergig", "Geländebewegung (Höhenwechsel je km, auf 0–1 skaliert).") { de(it, 2) },
        MetricDef("hmkm", "Anstieg", null, "Aufwärts-Höhenmeter je Kilometer.") { de(it, 1) + " Hm/km" },
        MetricDef("aus", "Aussicht", "Weite Aussicht", "Anteil der Strecke mit freier Sicht in die Landschaft.") { pct(it) },
        MetricDef("fern", "Fernsicht", null, "Typische Sichtweite, wo freie Sicht besteht.") { de(it / 1000, 1) + " km" },
        MetricDef("ort", "Wenig Ortsdurchfahrt", "Wenig Ortsdurchfahrten", "Anteil der Strecke innerorts. Weniger ist besser.") { pct(it) },
        MetricDef("ortn", "Ortseinfahrten", null, "Anzahl der Ortseinfahrten auf der Runde. Weniger ist besser.") { de(it, 0) },
        MetricDef("b", "Wenig Bundesstraße", "Kaum Bundesstraße", "Anteil auf Bundesstraßen. Weniger ist besser.") { pct(it) },
        MetricDef("hl", "Highlight-Anteil", "Viele Highlights", "Anteil der Strecke auf Highlight-Abschnitten.") { pct(it) },
        MetricDef("unt", "Wenig Unterbrechung", "Wenig Stopps", "Ampeln, Stoppschilder und Ortseinfahrten je 10 km. Weniger ist besser.") { de(it, 1) + " /10 km" },
        MetricDef("dram", "Dramaturgie", "Gute Dramaturgie", "Wie gut die Highlights über die Runde verteilt sind (0–1).") { de(it, 2) },
    )
    /** Die 9 Teilwerte (Reihenfolge wie Web) */
    val BARS = listOf("kurv", "kehr", "rhy", "rel", "aus", "ort", "b", "unt", "dram")
    val CONTRIBS = listOf(
        "route" to "Strecke", "topk" to "Top-Abschnitte", "flow" to "Wenig Verbindung",
        "dram" to "Dramaturgie", "view" to "Aussicht", "interrupts" to "Unterbrechungen",
    )
    val SEG_KIND = mapOf("h" to "Highlight", "n" to "Normal", "t" to "Verbindung")
    fun metric(k: String) = METRICS.first { it.key == k }
    fun strengthLabels(st: List<String>) = st.mapNotNull { k -> METRICS.firstOrNull { it.key == k }?.chip }
    fun bestSegments(segs: List<Segment>, n: Int = 5) = segs.withIndex().filter { it.value.kind != "t" && it.value.lenM >= 1000 }.sortedByDescending { it.value.score }.take(n)
    fun maxContrib(sb: ScoreBreak) = CONTRIBS.maxOf { abs(sb.c[it.first] ?: 0.0) }

    private fun hav(a: List<Double>, b: List<Double>): Double {
        val r = kotlin.math.PI / 180; val dl = (b[1] - a[1]) * r; val dn = (b[0] - a[0]) * r
        val h = kotlin.math.sin(dl / 2).let { it * it } + kotlin.math.cos(a[1] * r) * kotlin.math.cos(b[1] * r) * kotlin.math.sin(dn / 2).let { it * it }
        return 2 * 6371000.0 * kotlin.math.asin(kotlin.math.sqrt(h))
    }
    /** Ausschnitt der Geometrie ab km0 über lenM Meter, auf Gesamtlänge totalM skaliert (wie Web sliceGeom). */
    fun sliceGeom(g: List<List<Double>>, km0: Double, lenM: Int, totalM: Double? = null): List<List<Double>> {
        if (g.size < 2) return emptyList()
        val cum = DoubleArray(g.size); for (i in 1 until g.size) cum[i] = cum[i - 1] + hav(g[i - 1], g[i])
        val f = totalM?.let { it / cum.last() } ?: 1.0; val a = km0 * 1000 / f; val b = minOf((km0 * 1000 + lenM) / f, cum.last())
        fun at(d: Double): List<Double> {
            val i = cum.indexOfFirst { it >= d }; if (i <= 0) return g[maxOf(0, i)]
            val t = (d - cum[i - 1]) / (cum[i] - cum[i - 1]).let { if (it == 0.0) 1.0 else it }
            return listOf(g[i - 1][0] + t * (g[i][0] - g[i - 1][0]), g[i - 1][1] + t * (g[i][1] - g[i - 1][1]))
        }
        return buildList { add(at(a)); for (i in g.indices) if (cum[i] > a && cum[i] < b) add(g[i]); add(at(b)) }
    }
}
