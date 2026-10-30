package de.scenicroutes.shared

import de.scenicroutes.shared.model.LoopCatalog
import de.scenicroutes.shared.nav.Curves
import de.scenicroutes.shared.nav.LoopTrack
import de.scenicroutes.shared.nav.NavJson
import de.scenicroutes.shared.nav.Polyline6
import de.scenicroutes.shared.nav.RouteBundle
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.*

/** Plausibilisierung an Katalogrunden; Werte identisch mit tools/curves/curves.py. */
class CurvesCatalogTest {
    private val assets = File("../androidApp/src/main/assets")
    private val catalog by lazy { LoopCatalog.parse(File(assets, "loops.json").readText()) }
    private fun route(id: Int) = NavJson.json.decodeFromString(RouteBundle.serializer(), File(assets, "routes/$id.json").readText()).route
    private fun curves(id: Int) = Curves.detectRoute(route(id))
    private fun ohneAusschluss(id: Int) = Curves.detect(Polyline6.decode(route(id)["geometry"]!!.jsonPrimitive.content))

    @Test fun referenzRundenVorherNachher() {
        mapOf(7241 to (68 to 7), 1547 to (73 to 11), 6105 to (65 to 8)).forEach { (id, exp) ->
            val c = ohneAusschluss(id); assertEquals(exp, c.size to c.count { it.hairpin }, "Runde $id ohne Ausschluss")
        }
        mapOf(7241 to (46 to 6), 1547 to (48 to 10), 6105 to (42 to 6)).forEach { (id, exp) ->
            val c = curves(id); assertEquals(exp, c.size to c.count { it.hairpin }, "Runde $id")
        }
    }

    @Test fun bekannteKehrenBleiben() {
        val k = curves(7241).filter { it.hairpin }.map { it.atM / 1000 }
        assertTrue(k.any { it in 4.6..4.8 }, "Wenschdorf km 4,7: $k")
        assertTrue(k.any { it in 27.3..27.5 }, "Kaltenbrunn km 27,4: $k")
        // km 27,8 ist ein Abbiegen an der Lauersmühle ("end of road", Knoten Grad 3) und entfällt
        assertTrue(k.none { it in 27.7..27.9 }, "km 27,8: $k")
    }

    @Test fun alleRundenPlausibel() {
        val ids = File(assets, "routes").list()!!.map { it.removeSuffix(".json").toInt() }.filter { id -> catalog.loops.any { it.id == id } }
        ids.forEach { id ->
            val km = catalog.loops.first { it.id == id }.navKm; val n = curves(id).size
            assertTrue(n / km in 0.2..3.0, "Runde $id: $n Kurven auf $km km")
        }
    }

    @Test fun hoehenmeterPassenZumKatalog() {
        catalog.loops.take(30).forEach { l ->
            val t = LoopTrack(l.coords, l.prof, l.profStep); val up = t.ascent(0.0, t.lengthM)!!
            assertTrue(up in l.hm * 0.5..l.hm * 1.3, "Runde ${l.id}: $up m vs. ${l.hm} m")
        }
    }
}
