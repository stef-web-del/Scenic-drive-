package de.scenicroutes.shared

import de.scenicroutes.shared.freeloop.FreeLoopJson
import de.scenicroutes.shared.routes.Gpx
import de.scenicroutes.shared.routes.MyRoute
import de.scenicroutes.shared.routes.Origin
import de.scenicroutes.shared.routes.RouteBook
import de.scenicroutes.shared.routes.RouteBook.Companion.encode
import kotlin.test.*

class MyRoutesTest {
    private fun r(id: String = RouteBook.newId(), name: String = "Runde", t: Long = 1) = MyRoute(id = id, ownerId = "me", origin = Origin.SERVER, name = name,
        geometry = listOf(listOf(9.2, 49.7), listOf(9.21, 49.71), listOf(9.2, 49.7)), lengthKm = 2.6, rideMin = 40, createdMs = t, updatedMs = t)

    @Test fun uuidsAreUniqueAndWellFormed() {
        val ids = (1..200).map { RouteBook.newId() }.toSet()
        assertEquals(200, ids.size)
        assertTrue(ids.all { Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(it) })
    }

    @Test fun roundTripRenameSoftDeleteFavorite() {
        val a = r(name = "Alt")
        var b = RouteBook(ownerId = "me").upsert(a).setFavorite(a.id, true, 5)
        b = RouteBook.parse(b.encode())
        assertEquals(setOf(a.id), b.favoriteIds())
        b = b.rename(a.id, "  Neu  ", 7)
        assertEquals("Neu", b.route(a.id)!!.name); assertEquals(7, b.route(a.id)!!.updatedMs)
        b = b.rename(a.id, "   ", 8); assertEquals("Neu", b.route(a.id)!!.name)
        b = b.delete(a.id, 9)
        assertTrue(b.visible.isEmpty()); assertNull(b.route(a.id))
        assertEquals(1, b.routes.size, "gelöschte Route bleibt als Löschmarke stehen")
        assertTrue(b.routes.single().deleted); assertTrue(b.favoriteIds().isEmpty())
    }

    @Test fun mergeKeepsNewestChangeIncludingDeletes() {
        val a = r(id = "a", t = 1); val c = r(id = "c", t = 1)
        val phone = RouteBook(ownerId = "me").upsert(a).upsert(c).rename("a", "Handy", 10)
        val other = RouteBook(ownerId = "me").upsert(a).upsert(c).delete("c", 20).rename("a", "Tablet", 5)
        val m = phone.merge(other)
        assertEquals("Handy", m.route("a")!!.name)
        assertNull(m.route("c"))
        assertEquals(m, m.merge(m), "Abgleich ist idempotent")
    }

    @Test fun gpxExportImportRoundTrip() {
        val a = r(name = "Kurven & Wald <Süd>")
        val x = Gpx.export(a)
        assertTrue(x.contains("&amp;") && x.contains("&lt;Süd&gt;"))
        val t = Gpx.parse(x)
        assertEquals(a.name, t.name)
        assertEquals(a.geometry, t.points)
    }

    @Test fun gpxImportFallsBackToRouteAndSkipsJunk() {
        val x = """<gpx><rte><name><![CDATA[Spessart]]></name><rtept lon="9.1" lat="49.8"></rtept><rtept lat='49.81' lon='9.12'/><rtept lat="99" lon="9"/></rte></gpx>"""
        val t = Gpx.parse(x)
        assertEquals("Spessart", t.name); assertEquals(listOf(listOf(9.1, 49.8), listOf(9.12, 49.81)), t.points)
        assertFailsWith<IllegalArgumentException> { Gpx.parse("<gpx></gpx>") }
        assertTrue(RouteBook.lengthKm(t.points) in 1.7..1.95)
    }

    @Test fun startBodyCarriesPlanOptions() {
        assertEquals("""{"lat":49.7,"lon":9.2,"name":"A","minuten":60,"profil":"geniesser","via":{"lat":49.8,"lon":9.1}}""",
            FreeLoopJson.startBody(49.7, 9.2, "A", 60, "geniesser", 49.8 to 9.1))
        assertFalse(FreeLoopJson.startBody(49.7, 9.2, "A", 45, "sportlich", null).contains("via"))
    }
}
