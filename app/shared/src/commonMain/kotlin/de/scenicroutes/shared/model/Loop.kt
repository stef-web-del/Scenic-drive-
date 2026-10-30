package de.scenicroutes.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Eine Scenic-Runde (aus loops.json, erzeugt von tools/build_assets.py). Koordinaten [lon, lat]. */
@Serializable
data class Loop(
    val id: Int,
    val title: String,
    val km: Double,
    val min: Int,
    val score: Double,
    val klasse: String,
    val region: String,
    val hm: Int,
    val tags: List<String> = emptyList(),
    /** Familienvertreter bei 25 % Überschneidung */
    val rep: Boolean = false,
    val family: Int = 0,
    val geom: List<List<Double>>,
    val prof: List<Int> = emptyList(),
    val profStep: Int = 100,
    val highlights: List<Highlight> = emptyList(),
    val pauses: List<Pause> = emptyList(),
    val orte: List<String> = emptyList(),
    val text: String = "",
    val navKm: Double = km,
    val steps: Int = 0,
    /** Tour-Score (Sortierung wie Web-App) */
    val tour: Double = score,
    /** Stärken-Schlüssel (z.B. "ort","dram") -> Chips, siehe ScoreDefs */
    val strengths: List<String> = emptyList(),
    val familySize: Int = 1,
    val navMatch: Double = 1.0,
    val navSha: String = "",
    val navBytes: Long = 0,
    val sb: ScoreBreak? = null,
    /** Lenkpunkte für die Google-Maps-Übergabe [lon,lat] */
    val gmaps: List<List<Double>> = emptyList(),
) {
    val coords: List<LatLon> get() = geom.map { LatLon(it[1], it[0]) }
    val highlightKm: Double get() = highlights.sumOf { it.len } / 1000.0
    val titleShort: String get() = title.substringBefore(" (")
}

@Serializable
data class Highlight(val km: Double, val len: Int, @SerialName("s") val score: Double)

@Serializable
data class Pause(@SerialName("n") val name: String, @SerialName("k") val kind: String, val km: Double, @SerialName("ll") val ll: List<Double>)

@Serializable
data class LoopCatalog(val start: List<Double>, val loops: List<Loop>, val sbm: ScoreMeta? = null, val version: Int = 1) {
    /** Sortierte Familienvertreter (eine Runde je Streckenfamilie), sonst alle. */
    fun filtered(onlyRepresentatives: Boolean, klassen: Set<String>): List<Loop> =
        loops.filter { (!onlyRepresentatives || it.rep) && (klassen.isEmpty() || it.klasse in klassen) }
            .sortedByDescending { it.tour }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): LoopCatalog = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class LatLon(val lat: Double, val lon: Double)

/** Score-Aufschlüsselung: c = Beiträge, v = Kennwerte, p = Perzentile, sg = Abschnitte [kmStart, LängeM, Score, Art h|n|t, Name, Steigung]. */
@Serializable
data class ScoreBreak(
    val c: Map<String, Double> = emptyMap(),
    val v: Map<String, Double> = emptyMap(),
    val p: Map<String, Int> = emptyMap(),
    val sg: List<kotlinx.serialization.json.JsonArray> = emptyList(),
) {
    val segments: List<Segment> get() = sg.map { a ->
        fun d(i: Int) = (a[i] as kotlinx.serialization.json.JsonPrimitive).content.toDouble()
        Segment(d(0), d(1).toInt(), d(2), (a[3] as kotlinx.serialization.json.JsonPrimitive).content, (a[4] as kotlinx.serialization.json.JsonPrimitive).content, d(5))
    }
}

data class Segment(val kmStart: Double, val lenM: Int, val score: Double, val kind: String, val name: String, val grade: Double)

@Serializable
data class ScoreMeta(val avg: Map<String, Double> = emptyMap(), val med: Map<String, Double> = emptyMap(), val c: Map<String, Double> = emptyMap(), val n: Int = 0)
