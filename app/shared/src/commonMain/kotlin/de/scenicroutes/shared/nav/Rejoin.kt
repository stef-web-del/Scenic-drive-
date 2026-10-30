package de.scenicroutes.shared.nav

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon

/** Ein Routing-Dienst, der Valhalla-/route-JSON (format=osrm) beantwortet: lokal (on-device) oder ein Server. */
interface NavRouter {
    val label: String
    suspend fun route(bodyJson: String): String
}

/** Fehler ohne Rohtext – die App zeigt nur [userMessage]. */
class RoutingUnavailable(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

/** Probiert die Router der Reihe nach (lokal zuerst, Server optional). */
class RouterChain(private val routers: List<NavRouter>) {
    val isEmpty get() = routers.isEmpty()
    val labels get() = routers.map { it.label }
    suspend fun <T> first(block: suspend (NavRouter) -> T): Pair<T, String> {
        var last: Throwable? = null
        for (r in routers) {
            try { return block(r) to r.label } catch (e: Throwable) { if (e is kotlin.coroutines.cancellation.CancellationException) throw e; last = e }
        }
        throw RoutingUnavailable(if (routers.isEmpty()) "Offline-Routing nicht installiert" else "Keine Route gefunden", last)
    }
}

/**
 * Wiedereinstieg in die laufende Runde, Port der navserver-Logik: Kandidaten ab Fortschritt +300 m
 * (bei Sperrung erst 400 m dahinter), je Kandidat eine Anfahrt (exclude_locations = Sperrungen),
 * Kosten = Fahrzeit + 90 s je ausgelassenem km. Ergebnis: Anfahrt + Rest der aktiven Route bis zum Ziel.
 */
object Rejoin {
    data class Result(val bundle: RouteBundle, val rejoinAlongM: Double, val skippedM: Double, val via: String)

    fun body(from: LatLon, to: LatLon, heading: Double?, exclude: List<GeoPoint>): String {
        val h = heading?.let { ",\"heading\":${it.toInt()},\"heading_tolerance\":60" } ?: ""
        val ex = if (exclude.isEmpty()) "" else ",\"exclude_locations\":[" + exclude.joinToString(",") { "{\"lat\":${it.lat},\"lon\":${it.lon}}" } + "]"
        return """{"costing":"auto","format":"osrm","banner_instructions":true,"voice_instructions":true,"language":"de-DE","units":"kilometers","locations":[{"lat":${from.lat},"lon":${from.lon},"type":"break"$h},{"lat":${to.lat},"lon":${to.lon},"type":"break"}]$ex}"""
    }

    fun targets(line: Polyline, alongM: Double, blocked: List<GeoPoint>): List<Double> {
        var minA = alongM + 300
        for (b in blocked) {
            val a = line.project(LatLon(b.lat, b.lon)).alongM
            if (a >= alongM - 500) minA = maxOf(minA, a + 400)
        }
        val end = line.lengthM - 50
        return listOf(minA, minA + 1500, minA + 3500, minA + 7000).map { minOf(it, end) }.distinct()
    }

    suspend fun plan(active: RouteBundle, line: Polyline, pos: LatLon, alongM: Double, heading: Double?, blocked: List<GeoPoint>, chain: RouterChain): Result {
        val (res, via) = chain.first { r ->
            var best: Result? = null; var bestCost = Double.MAX_VALUE; var err: Throwable? = null
            for (t in targets(line, alongM, blocked)) {
                val e0 = LoopEntry.atAlong(active.route, t)
                val toEnd = e0.isOfficialStart && t > 1000  // Einstieg läge hinter dem letzten Manöver: direkt zum Ziel
                val e = e0; val target = if (toEnd) line.points.last() else e.point
                val ap = try { NavJson.bundleFromOsrm(r.route(body(pos, target, heading, blocked))) } catch (x: Throwable) {
                    if (x is kotlin.coroutines.cancellation.CancellationException) throw x; err = x; continue }
                val dur = ap.route["duration"].toString().toDouble()
                val skipped = maxOf(0.0, (if (toEnd) line.lengthM else e.alongM) - alongM)
                val cost = dur + 0.09 * skipped
                if (cost < bestCost) {
                    bestCost = cost
                    best = if (toEnd) Result(ap, line.lengthM, skipped, r.label)
                    else Result(LoopEntry.prepend(ap, if (e.isOfficialStart) active else LoopEntry.tail(active, e)), e.alongM, skipped, r.label)
                }
            }
            best ?: throw (err ?: RoutingUnavailable("Kein Wiedereinstieg gefunden"))
        }
        return res.copy(via = via)
    }

    /** Anfahrt von [from] zum Einstieg [entry] und nahtlos in [loop]. */
    suspend fun approach(loop: RouteBundle, from: LatLon, to: LatLon, heading: Double?, chain: RouterChain): Pair<RouteBundle, String> =
        chain.first { r -> NavJson.bundleFromOsrm(r.route(body(from, to, heading, emptyList()))) }

    @Suppress("unused") private fun d(a: LatLon, b: LatLon) = Geo.distanceM(a, b)
}
