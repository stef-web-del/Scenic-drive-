package de.scenicroutes.shared

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.model.LoopCatalog
import de.scenicroutes.shared.nav.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.*
import kotlin.test.*

/**
 * Offline-Szenarien. Router-Reihenfolge wie in der App: lokal (SIM_LOCAL = dieselben Auto-Kacheln wie das
 * Offline-Paket, per pyvalhalla) -> optionaler Server. Ohne SIM_LOCAL laufen nur die Fehlerpfad-Tests.
 * Schreibt docs/offline-sim.md.
 */
class OfflineRoutingTest {
    private val assets = File("../androidApp/src/main/assets")
    private val local = System.getenv("SIM_LOCAL")
    private fun bundle(id: Int) = LoopEntry.sanitize(NavJson.json.decodeFromString(RouteBundle.serializer(), File(assets, "routes/$id.json").readText()))
    private fun off(p: LatLon, b: Double, m: Double) = LatLon(p.lat + m * cos(b * PI / 180) / 110_540.0, p.lon + m * sin(b * PI / 180) / (111_320.0 * cos(p.lat * PI / 180)))
    private fun steps(r: JsonObject) = r["legs"]!!.jsonArray[0].jsonObject["steps"]!!.jsonArray.map { it.jsonObject }
    private fun type(o: JsonObject) = o["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content

    class HttpRouter(private val base: String, override val label: String) : NavRouter {
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        override suspend fun route(bodyJson: String): String {
            val r = http.send(HttpRequest.newBuilder(URI("$base/route")).timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.ofString(bodyJson)).header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString())
            if (r.statusCode() != 200) error("HTTP ${r.statusCode()}")
            return r.body()
        }
    }
    private val dead = HttpRouter("http://127.0.0.1:9", "Server (nicht erreichbar)")

    @Test fun noRouterGivesFriendlyMessage() = runBlocking {
        val b = bundle(LoopCatalog.parse(File(assets, "loops.json").readText()).loops.first().id)
        val line = Polyline(Polyline6.decode(b.route["geometry"]!!.jsonPrimitive.content))
        val e = assertFailsWith<RoutingUnavailable> { Rejoin.plan(b, line, line.pointAt(500.0), 400.0, null, emptyList(), RouterChain(emptyList())) }
        assertEquals("Offline-Routing nicht installiert", e.userMessage)
        val e2 = assertFailsWith<RoutingUnavailable> { Rejoin.plan(b, line, line.pointAt(500.0), 400.0, null, emptyList(), RouterChain(listOf(dead))) }
        assertEquals("Keine Route gefunden", e2.userMessage)
        assertFalse("Connection" in e2.userMessage || "Exception" in e2.userMessage)
    }

    @Test fun scenarios() = runBlocking {
        val loc = local ?: return@runBlocking
        val chain = RouterChain(listOf(HttpRouter(loc, "lokal"), dead))
        val loops = LoopCatalog.parse(File(assets, "loops.json").readText()).filtered(true, emptySet()).take(5)
        val rows = StringBuilder(); var n = 0; var ok = 0
        fun row(id: Int, name: String, good: Boolean, info: String) { n++; if (good) ok++; rows.append("| $id | $name | ${if (good) "✓" else "✗"} | $info |\n") }
        for (l in loops) {
            val b = bundle(l.id); val pts = Polyline6.decode(b.route["geometry"]!!.jsonPrimitive.content); val line = Polyline(pts)
            val c = l.coords; val i = (0.3 * (c.size - 2)).toInt(); val brg = Geo.bearingDeg(c[i], c[i + 1])
            // Einstieg 1 km / 10 km neben der Runde
            for (d in listOf(1000.0, 10_000.0)) {
                val pos = off(c[i], brg + 90, d)
                val e = LoopEntry.best(b.route, pos)
                val t0 = System.nanoTime()
                val r = runCatching { Rejoin.approach(b, pos, e.point, null, chain) }
                val ms = (System.nanoTime() - t0) / 1_000_000
                r.onSuccess { (a, via) ->
                    val full = LoopEntry.prepend(a, LoopEntry.rotate(b, e)); val st = steps(full.route)
                    val good = st.count { type(it) == "arrive" } == 1 && st.count { type(it) == "depart" } == 1 && via == "lokal"
                    row(l.id, "Einstieg ${(d / 1000).toInt()} km daneben", good, "Anfahrt %.1f km via %s, %d ms".format(a.route["distance"]!!.jsonPrimitive.double / 1000, via, ms))
                }.onFailure { row(l.id, "Einstieg ${(d / 1000).toInt()} km daneben", false, (it as? RoutingUnavailable)?.userMessage ?: "Fehler") }
            }
            // 400 m Abweichung
            val along = line.project(c[i]).alongM
            val pos = off(c[i], brg + 90, 400.0)
            runCatching { Rejoin.plan(b, line, pos, along, null, emptyList(), chain) }.onSuccess { res ->
                val st = steps(res.bundle.route)
                val endD = Geo.distanceM(Polyline6.decode(res.bundle.route["geometry"]!!.jsonPrimitive.content).last(), pts.last())
                row(l.id, "400 m Abweichung", st.count { type(it) == "arrive" } == 1 && endD < 30 && res.via == "lokal",
                    "Wiedereinstieg bei km %.1f, %.1f km ausgelassen, Ziel = Rundenziel (%.0f m)".format(res.rejoinAlongM / 1000, res.skippedM / 1000, endD))
            }.onFailure { row(l.id, "400 m Abweichung", false, (it as? RoutingUnavailable)?.userMessage ?: "Fehler") }
            // Sperrung 150 m voraus
            val a2 = line.lengthM * 0.4; val here = line.pointAt(a2); val blk = line.pointAt(a2 + 150)
            val hdg = Geo.bearingDeg(here, line.pointAt(a2 + 20))
            runCatching { Rejoin.plan(b, line, here, a2, hdg, listOf(GeoPoint(blk.lat, blk.lon)), chain) }.onSuccess { res ->
                val g = Polyline6.decode(res.bundle.route["geometry"]!!.jsonPrimitive.content)
                val cut = Polyline(g).project(line.pointAt(res.rejoinAlongM)).alongM
                val gl = Polyline(g); val pre = g.filterIndexed { k, _ -> gl.cum[k] <= cut }
                val near = pre.minOf { Geo.distanceM(it, blk) }
                row(l.id, "Sperrung 150 m voraus", near > 10 && res.rejoinAlongM >= a2 + 150 + 350 && res.via == "lokal",
                    "Umleitung meidet Sperrpunkt (min. %.0f m), Wiedereinstieg %.1f km dahinter".format(near, (res.rejoinAlongM - a2 - 150) / 1000))
            }.onFailure { row(l.id, "Sperrung 150 m voraus", false, (it as? RoutingUnavailable)?.userMessage ?: "Fehler") }
        }
        File("../docs/offline-sim.md").writeText("# Offline-Simulation (lokales Routing, Auto-Kacheln)\n\nRouter-Reihenfolge: lokal → Server (hier absichtlich nicht erreichbar).\n\n**$ok/$n bestanden**\n\n| Runde | Szenario | ok | Ergebnis |\n|---|---|---|---|\n$rows")
        assertTrue(ok >= n - 2, "nur $ok/$n")
    }
}
