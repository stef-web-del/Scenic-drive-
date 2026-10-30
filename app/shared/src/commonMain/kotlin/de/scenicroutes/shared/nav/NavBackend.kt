package de.scenicroutes.shared.nav

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Konfiguration des selbst gehosteten Backends (Valhalla + scenic navserver). Base-URL in den Einstellungen änderbar. */
data class BackendConfig(val baseUrl: String) {
    val rejoinUrl get() = baseUrl.trimEnd('/') + "/scenic/rejoin"
    val routeUrl get() = baseUrl.trimEnd('/') + "/route"
    val healthUrl get() = baseUrl.trimEnd('/') + "/health"
}

@Serializable data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class RejoinRequest(
    val loopId: Int,
    val lat: Double,
    val lon: Double,
    val heading: Double? = null,
    val alongM: Double? = null,
    val blocked: List<GeoPoint> = emptyList(),
)

/** route = OSRM-Route (Valhalla format=osrm), waypoints = OSRM-Waypoints – direkt für Ferrostar createRouteFromOsrm. */
@Serializable
data class RouteBundle(
    val route: JsonObject,
    val waypoints: List<JsonObject>,
    val rejoinAlongM: Double? = null,
    val skippedM: Double? = null,
)

object NavJson {
    /** Request-Body für Valhalla /route mit OSRM-Ausgabe (wie der Nav-Server). */
    fun approachBody(from: GeoPoint, to: GeoPoint, heading: Double?): String {
        val h = heading?.let { ",\"heading\":${it.toInt()},\"heading_tolerance\":60" } ?: ""
        return """{"costing":"auto","format":"osrm","banner_instructions":true,"voice_instructions":true,"language":"de-DE","units":"kilometers","locations":[{"lat":${from.lat},"lon":${from.lon},"type":"break"$h},{"lat":${to.lat},"lon":${to.lon},"type":"break"}]}"""
    }
    /** Valhalla-Antwort {routes:[…], waypoints:[…]} -> RouteBundle */
    fun bundleFromOsrm(s: String): RouteBundle {
        val o = json.parseToJsonElement(s) as JsonObject
        val r = (o["routes"] as kotlinx.serialization.json.JsonArray)[0] as JsonObject
        val w = (o["waypoints"] as kotlinx.serialization.json.JsonArray).map { it as JsonObject }
        return RouteBundle(r, w)
    }
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    fun encode(r: RejoinRequest) = json.encodeToString(RejoinRequest.serializer(), r)
    fun decodeBundle(s: String) = json.decodeFromString(RouteBundle.serializer(), s)
    fun routeBytes(b: RouteBundle) = b.route.toString().encodeToByteArray()
    fun waypointBytes(b: RouteBundle) = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()), b.waypoints).encodeToByteArray()
}

/** Plattformneutrale Schnittstelle (Android: OkHttp, iOS später: URLSession). */
interface NavBackend {
    suspend fun health(): Boolean
    suspend fun rejoin(req: RejoinRequest): RouteBundle
    /** Anfahrt von [from] zum Einstieg [to] (Valhalla /route, format=osrm, deutsch). */
    suspend fun approach(from: GeoPoint, to: GeoPoint, heading: Double? = null): RouteBundle
}
