package de.scenicroutes.shared.freeloop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Antworten des lokalen Servers „Runde ab Punkt“ (server/server.py). Unbekannte Felder werden ignoriert. */
@Serializable data class JobCreated(val id: String, val cached: Boolean = false)

@Serializable data class JobProgress(val phase: String = "", val tried: Int = 0, val total: Int = 0, val valid: Int = 0,
                                     @SerialName("elapsed_s") val elapsedS: Double = 0.0)

@Serializable data class FreeLoop(
    val id: Int, val rang: Int = 0,
    @SerialName("min") val loopMin: Int,
    val km: Double, val hm: Int = 0,
    @SerialName("anfahrt_min") val approachMin: Double = 0.0, @SerialName("anfahrt_km") val approachKm: Double = 0.0,
    @SerialName("rueckweg_min") val returnMin: Double = 0.0, @SerialName("rueckweg_km") val returnKm: Double = 0.0,
    val einstieg: String = "", @SerialName("gesamt_min") val totalMin: Int = 0,
    val tags: List<String> = emptyList(), val text: String = "",
    @SerialName("max_ueberschneidung") val maxOverlap: Double = 0.0, val ersetzt: Int? = null, @SerialName("via_m") val viaM: Int? = null,
)

@Serializable data class JobView(
    val id: String, val status: String, val final: Boolean = false, val progress: JobProgress = JobProgress(),
    @SerialName("first_s") val firstS: Double? = null, val count: Int = 0, val offset: Int = 0, val more: Boolean = false,
    val loops: List<FreeLoop> = emptyList(), val error: String? = null,
) {
    val running get() = status == "queued" || status == "running"
    /** 0..1 für die Fortschrittsanzeige; während der Suche nach versuchten Kandidaten. */
    val fraction: Float get() = if (!running) 1f else if (progress.total <= 0) 0f else (progress.tried.toFloat() / progress.total).coerceIn(0f, 1f)
}

object FreeLoopJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    fun view(s: String): JobView = json.decodeFromString(JobView.serializer(), s)
    fun created(s: String): JobCreated = json.decodeFromString(JobCreated.serializer(), s)
    fun startBody(lat: Double, lon: Double, name: String): String =
        """{"lat":$lat,"lon":$lon,"name":${kotlinx.serialization.json.JsonPrimitive(name)}}"""
    /** Mit Wunschzeit (Minuten reine Runde), Profil und optionalem Zwischenziel (Runde führt höchstens 2 km daran vorbei). */
    fun startBody(lat: Double, lon: Double, name: String, minutes: Int, profile: String, via: Pair<Double, Double>?): String =
        """{"lat":$lat,"lon":$lon,"name":${kotlinx.serialization.json.JsonPrimitive(name)},"minuten":$minutes,"profil":${kotlinx.serialization.json.JsonPrimitive(profile)}""" +
            (via?.let { ""","via":{"lat":${it.first},"lon":${it.second}}""" } ?: "") + "}"
}

/** Text einer Karte: reine Runde, Anfahrt und Rückweg getrennt. */
fun FreeLoop.summary(): String {
    val a = if (approachMin < 1.0) "Start direkt" else "Anfahrt ${approachMin.toInt()} min (${"".fmt(approachKm)} km)"
    val r = if (returnMin < 1.0) "" else " · Rückweg ${returnMin.toInt()} min (${"".fmt(returnKm)} km)"
    return "Runde $loopMin min, ${"%.0f".fmt(km)} km, $hm Hm · $a$r"
}
private fun String.fmt(v: Double): String = kotlin.math.round(v).toLong().toString()
