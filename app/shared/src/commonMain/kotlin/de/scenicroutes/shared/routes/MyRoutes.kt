package de.scenicroutes.shared.routes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * „Meine Routen“, vorbereitet für Konten und Abgleich (Konzept „Neue Routenplanung“, Datenmodell):
 * jede Route und jeder Favorit hat eine UUID, einen Besitzer, Änderungszeit und ein Löschflag statt echter Löschung.
 * Bis es Konten gibt, ist der Besitzer die lokale Geräte-ID.
 */
object Origin { const val KATALOG = "katalog"; const val SERVER = "server"; const val GERAET = "geraet"; const val IMPORT = "import" }

@Serializable data class MyRoute(
    val id: String,
    val ownerId: String,
    val kind: String = "loop",            // loop | ab (A→B, später)
    val origin: String,                   // katalog | server | geraet | import
    val parentId: String? = null,         // Katalog-/Raster-ID oder Server-Job:Runde, aus der die Kopie stammt
    val name: String,
    val geometry: List<List<Double>>,     // [lon, lat], ganze Fahrt ab Start
    val waypoints: List<List<Double>> = emptyList(),
    val lengthKm: Double,
    val rideMin: Int,
    val approachMin: Int = 0,
    val hm: Int = 0,
    val profile: String? = null,
    val reversed: Boolean = false,
    val text: String = "",
    val tags: List<String> = emptyList(),
    /** Lokale App-ID, unter der die Runde für Detail und Navigation angemeldet wird (null = nur Ansicht/Export). */
    val appLoopId: Int? = null,
    val dataVersion: Int = 1,
    val createdMs: Long,
    val updatedMs: Long,
    val deleted: Boolean = false,
    val visibility: String = "private",
)

@Serializable data class Favorite(val id: String, val ownerId: String, val routeId: String, val list: String = "favoriten",
                                  val addedMs: Long, val updatedMs: Long, val deleted: Boolean = false)

@Serializable data class RouteBook(val v: Int = 1, val ownerId: String, val routes: List<MyRoute> = emptyList(), val favorites: List<Favorite> = emptyList()) {
    val visible get() = routes.filter { !it.deleted }.sortedByDescending { it.updatedMs }
    fun favoriteIds() = favorites.filter { !it.deleted }.map { it.routeId }.toSet()
    fun route(id: String) = routes.firstOrNull { it.id == id && !it.deleted }
    fun byParent(parent: String) = routes.firstOrNull { it.parentId == parent && !it.deleted }

    fun upsert(r: MyRoute) = copy(routes = routes.filter { it.id != r.id } + r)
    fun rename(id: String, name: String, now: Long) = copy(routes = routes.map { if (it.id == id) it.copy(name = name.trim().take(80).ifBlank { it.name }, updatedMs = now) else it })
    /** Weiches Löschen: bleibt mit deleted=true stehen, damit ein späterer Abgleich die Löschung kennt. Favorit fällt mit. */
    fun delete(id: String, now: Long) = copy(
        routes = routes.map { if (it.id == id) it.copy(deleted = true, updatedMs = now) else it },
        favorites = favorites.map { if (it.routeId == id && !it.deleted) it.copy(deleted = true, updatedMs = now) else it })
    fun setFavorite(routeId: String, on: Boolean, now: Long): RouteBook {
        val f = favorites.firstOrNull { it.routeId == routeId }
        return when {
            f == null && on -> copy(favorites = favorites + Favorite(newId(), ownerId, routeId, addedMs = now, updatedMs = now))
            f == null -> this
            else -> copy(favorites = favorites.map { if (it.routeId == routeId) it.copy(deleted = !on, updatedMs = now) else it })
        }
    }
    /** Abgleich zweier Stände (späteres Konto): je ID gewinnt die jüngere Änderung, Löschungen eingeschlossen. */
    fun merge(o: RouteBook) = copy(
        routes = (routes + o.routes).groupBy { it.id }.map { (_, v) -> v.maxBy { it.updatedMs } },
        favorites = (favorites + o.favorites).groupBy { it.id }.map { (_, v) -> v.maxBy { it.updatedMs } })

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
        fun parse(s: String): RouteBook = json.decodeFromString(serializer(), s)
        fun RouteBook.encode(): String = json.encodeToString(serializer(), this)
        @OptIn(ExperimentalUuidApi::class) fun newId(): String = Uuid.random().toString()
        fun lengthKm(g: List<List<Double>>): Double = g.zipWithNext { a, b -> haversine(a[1], a[0], b[1], b[0]) }.sum() / 1000
        fun haversine(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
            val r = 6_371_000.0; val dLa = (la2 - la1) * PI / 180; val dLo = (lo2 - lo1) * PI / 180
            val a = sin(dLa / 2).pow(2) + cos(la1 * PI / 180) * cos(la2 * PI / 180) * sin(dLo / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
        /** Stabile App-ID für Routen ohne eigene ID (Importe), außerhalb von Katalog-, Raster- und Server-IDs. */
        fun localAppId(routeId: String) = 800_000_000 + (routeId.hashCode() and 0x7fffffff) % 99_000_000
    }
}

/** GPX 1.1 als Track: Export für andere Navis, Import von Strecken aus anderen Apps. */
object Gpx {
    fun export(r: MyRoute): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Scenic Routes" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata><name>").append(esc(r.name)).append("</name></metadata>\n")
        append("  <trk><name>").append(esc(r.name)).append("</name>")
        if (r.text.isNotBlank()) append("<desc>").append(esc(r.text)).append("</desc>")
        append("\n    <trkseg>\n")
        r.geometry.forEach { p -> append("      <trkpt lat=\"").append(fmt(p[1])).append("\" lon=\"").append(fmt(p[0])).append("\"/>\n") }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    data class Track(val name: String?, val points: List<List<Double>>)

    /** Liest trkpt, sonst rtept, sonst wpt (lat/lon in beliebiger Reihenfolge). Wirft bei leerer oder kaputter Datei. */
    fun parse(xml: String): Track {
        val name = Regex("<name>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?\\s*</name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.let(::unesc)?.trim()?.takeIf { it.isNotEmpty() }
        for (tag in listOf("trkpt", "rtept", "wpt")) {
            val pts = Regex("<$tag\\b([^>]*)>|<$tag\\b([^>]*)/>").findAll(xml).mapNotNull { m ->
                val a = m.groupValues[1].ifEmpty { m.groupValues[2] }
                val la = attr(a, "lat"); val lo = attr(a, "lon")
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) null else listOf(lo, la)
            }.toList()
            if (pts.size >= 2) return Track(name, pts)
        }
        throw IllegalArgumentException("Keine Strecke in der GPX-Datei gefunden")
    }
    private fun attr(s: String, k: String) = Regex("\\b$k\\s*=\\s*[\"']([-+0-9.eE]+)[\"']").find(s)?.groupValues?.get(1)?.toDoubleOrNull()
    private fun fmt(v: Double): String { val x = (v * 1e6).roundToLong(); val s = if (x < 0) "-" else ""; val a = abs(x); return "$s${a / 1_000_000}.${(a % 1_000_000).toString().padStart(6, '0')}" }
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
