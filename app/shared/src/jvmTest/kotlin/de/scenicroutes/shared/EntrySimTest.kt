package de.scenicroutes.shared

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.model.LoopCatalog
import de.scenicroutes.shared.nav.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.math.*
import kotlin.test.*

/**
 * Einstiegs-Simulation: 5 Runden x 4 Positionen x 4 Abstände (0 / 100 m / 1 km / 10 km neben der Runde).
 * Mit SIM_BACKEND=http://127.0.0.1:8002 zusätzlich Anfahrt per Valhalla + nahtloses Zusammenfügen.
 * Schreibt docs/entry-sim.md.
 */
class EntrySimTest {
    private val assets = File("../androidApp/src/main/assets")
    private val backend = System.getenv("SIM_BACKEND")
    private fun bundle(id: Int) = NavJson.json.decodeFromString(RouteBundle.serializer(), File(assets, "routes/$id.json").readText())
    private fun off(p: LatLon, b: Double, m: Double) = LatLon(p.lat + m * cos(b * PI / 180) / 110_540.0, p.lon + m * sin(b * PI / 180) / (111_320.0 * cos(p.lat * PI / 180)))
    private fun type(o: JsonObject) = o["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content
    private fun steps(r: JsonObject) = r["legs"]!!.jsonArray[0].jsonObject["steps"]!!.jsonArray.map { it.jsonObject }

    @Test fun simulate() {
        val cat = LoopCatalog.parse(File(assets, "loops.json").readText())
        val loops = cat.filtered(true, emptySet()).take(5)
        val http = HttpClient.newHttpClient()
        val rows = StringBuilder()
        var n = 0; var ok = 0
        for (l in loops) {
            val b = LoopEntry.sanitize(bundle(l.id)); val c = l.coords
            for (frac in listOf(0.0, 0.25, 0.5, 0.8)) {
                val i = (frac * (c.size - 2)).toInt(); val p = c[i]; val brg = Geo.bearingDeg(c[i], c[i + 1])
                for (d in listOf(0.0, 100.0, 1000.0, 10_000.0)) {
                    n++
                    val pos = off(p, brg + 90, d)
                    val e = LoopEntry.best(b.route, pos, if (d == 0.0) brg else null)
                    val rot = LoopEntry.rotate(b, e)
                    val st = steps(rot.route)
                    val arrivals = st.count { type(it) == "arrive" }
                    val falseArr = LoopEntry.voices(rot.route).dropLast(2).count { "Ziel" in it || "Einstieg erreicht" in it }
                    val km = rot.route["distance"]!!.jsonPrimitive.double / 1000
                    val mode = if (e.approachM <= LoopEntry.DIRECT_START_M) "Direktstart" else "Einstiegsdialog"
                    var appr = "-"
                    if (backend != null && e.approachM > LoopEntry.DIRECT_START_M) {
                        val body = NavJson.approachBody(GeoPoint(pos.lat, pos.lon), GeoPoint(e.point.lat, e.point.lon), null)
                        val r = runCatching { http.send(HttpRequest.newBuilder(URI("$backend/route")).POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString()) }.getOrNull()
                        appr = if (r == null || r.statusCode() != 200) "kein Weg (${r?.statusCode()})" else {
                            val a = NavJson.bundleFromOsrm(r.body()); val m = LoopEntry.prepend(a, rot)
                            val ms = steps(m.route)
                            assertEquals(1, ms.count { type(it) == "arrive" }); assertEquals(1, ms.count { type(it) == "depart" })
                            "%.1f km Anfahrt, %d Schritte gesamt".format(a.route["distance"]!!.jsonPrimitive.double / 1000, ms.size)
                        }
                    }
                    val good = arrivals == 1 && falseArr == 0 && abs(km - b.route["distance"]!!.jsonPrimitive.double / 1000) < 0.02
                    if (good) ok++
                    val where = if (e.isOfficialStart) "offiz. Start" else "Schritt ${e.step}" + (if (e.vertex > 0) "/Knoten ${e.vertex}" else "")
                    rows.append("| ${l.id} | ${(frac * 100).toInt()} %% | ${d.toInt()} m | %.0f m | $where | ${e.roadName.ifEmpty { "-" }} | $mode | %.1f km | ${st.size} | ${if (good) "ok" else "FEHLER"} | $appr |\n".format(e.approachM, km))
                }
            }
        }
        val md = "# Einstiegs-Simulation (M3)\n\n" +
            "Erzeugt von `shared/src/jvmTest/.../EntrySimTest.kt` (`SIM_BACKEND=http://127.0.0.1:8002 ./gradlew :shared:jvmTest --tests '*EntrySim*'`).\n" +
            "5 Top-Runden, Startposition bei 0/25/50/80 % der Runde, jeweils 0 m, 100 m, 1 km und 10 km quer zur Fahrtrichtung versetzt.\n" +
            "Geprüft: genau ein depart und ein arrive, keine Ankunftsansage am Übergang (OBI), Rundenlänge unverändert.\n" +
            "Anfahrt: Valhalla `/route` zum Einstieg, danach mit `LoopEntry.prepend` nahtlos in die Runde.\n\n" +
            "Ergebnis: **$ok / $n** Fälle bestanden.\n\n" +
            "| Runde | Position | Versatz | Luftlinie zum Einstieg | Einstieg | Straße | UI | Rundenlänge | Schritte | Prüfung | Anfahrt (Server) |\n|---|---|---|---|---|---|---|---|---|---|---|\n" + rows
        File("../docs/entry-sim.md").writeText(md)
        assertEquals(n, ok)
    }
}
