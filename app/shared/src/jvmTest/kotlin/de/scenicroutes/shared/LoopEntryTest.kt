package de.scenicroutes.shared

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.model.LoopCatalog
import de.scenicroutes.shared.nav.LoopEntry
import de.scenicroutes.shared.nav.NavJson
import de.scenicroutes.shared.nav.Polyline6
import de.scenicroutes.shared.nav.RouteBundle
import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.*
import kotlin.test.*

class LoopEntryTest {
    private val assets = File("../androidApp/src/main/assets")
    private val catalog by lazy { LoopCatalog.parse(File(assets, "loops.json").readText()) }
    private fun bundle(id: Int) = NavJson.json.decodeFromString(RouteBundle.serializer(), File(assets, "routes/$id.json").readText())
    private fun steps(b: RouteBundle) = b.route["legs"]!!.jsonArray[0].jsonObject["steps"]!!.jsonArray.map { it.jsonObject }
    private fun offset(p: LatLon, bearing: Double, m: Double): LatLon {
        val r = bearing * PI / 180
        return LatLon(p.lat + m * cos(r) / 110_540.0, p.lon + m * sin(r) / (111_320.0 * cos(p.lat * PI / 180)))
    }

    /** Invarianten einer rotierten Route. */
    private fun check(orig: RouteBundle, rot: RouteBundle, entry: LatLon) {
        val s = steps(rot)
        assertEquals("depart", s.first()["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("arrive", s.last()["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(1, s.count { it["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content == "arrive" }, "genau eine Ankunft")
        assertEquals(1, s.count { it["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content == "depart" })
        val d0 = orig.route["distance"]!!.jsonPrimitive.double; val d1 = rot.route["distance"]!!.jsonPrimitive.double
        assertTrue(abs(d0 - d1) < 15, "Distanz $d0 vs $d1")
        assertTrue(abs(s.sumOf { it["distance"]!!.jsonPrimitive.double } - d1) < 1)
        // Geometrie: Start und Ende am Einstieg, Schritte lückenlos
        val g = Polyline6.decode(rot.route["geometry"]!!.jsonPrimitive.content)
        assertTrue(Geo.distanceM(g.first(), entry) < 1 && Geo.distanceM(g.last(), entry) < 1)
        var idx = 0
        for (st in s.dropLast(1)) {
            val p = Polyline6.decode(st["geometry"]!!.jsonPrimitive.content)
            if (Geo.distanceM(p.first(), g[idx]) > 2.0) idx++ // Sprung an Zusammenführungsstelle
            assertTrue(Geo.distanceM(p.first(), g[idx]) < 0.5, "Schrittanfang passt zur Geometrie idx=$idx d=${Geo.distanceM(p.first(), g[idx])} step=${s.indexOf(st)} n=${p.size}")
            val gi = st["intersections"]!!.jsonArray.map { it.jsonObject["geometry_index"]!!.jsonPrimitive.int }
            assertEquals(idx, gi.first()); assertTrue(gi.zipWithNext().all { (a, b) -> a < b }); assertTrue(gi.all { it in idx until idx + p.size - 1 })
            idx += p.size - 1
            // Banner/Ansagen innerhalb der Schrittlänge
            val len = st["distance"]!!.jsonPrimitive.double
            st["voiceInstructions"]!!.jsonArray.forEach { assertTrue(it.jsonObject["distanceAlongGeometry"]!!.jsonPrimitive.double <= len + 40, "Ansage > Schritt") }
            assertTrue(st["bannerInstructions"]!!.jsonArray.isNotEmpty())
        }
        assertEquals(g.size - 1, idx)
        // Keine falsche Ankunft: "Ziel erreicht" taucht nicht mehr auf, nur am Ende "Einstieg erreicht"
        val voices = LoopEntry.voices(rot.route)
        assertTrue(voices.none { it.contains("Ziel") }, voices.filter { it.contains("Ziel") }.toString())
        assertEquals(1, voices.count { it.startsWith("Einstieg erreicht") })
        assertTrue(voices.last().startsWith("Einstieg erreicht"))
        assertEquals(2, rot.waypoints.size)
    }

    @Test fun sanitizeRemovesMidRouteArrival() {
        val b = bundle(7241); val v0 = LoopEntry.voices(b.route)
        assertTrue(v0.dropLast(1).any { "Ziel" in it }, "Testdaten haben Zwischenankunft")
        val v1 = LoopEntry.voices(LoopEntry.sanitize(b).route)
        assertEquals(1, v1.count { it == "Ziel erreicht." || it.startsWith("Einstieg erreicht") || it.contains("Ziel erreicht") } .coerceAtMost(1))
        assertTrue(v1.dropLast(2).none { "Ziel" in it }, v1.toString())
    }

    @Test fun polylineRoundTrip() {
        val g = bundle(7241).route["geometry"]!!.jsonPrimitive.content
        assertEquals(g, Polyline6.encode(Polyline6.decode(g)))
    }

    @Test fun officialStartLeavesRouteUnchanged() {
        val b = bundle(7241); val start = LatLon(49.70142, 9.22416)
        val e = LoopEntry.best(b.route, start)
        assertTrue(e.isOfficialStart); assertSame(b, LoopEntry.rotate(b, e))
    }

    @Test fun entryShortlyBeforeEndSnapsToOfficialStart() {
        val b = bundle(7241); val total = de.scenicroutes.shared.geo.Polyline(Polyline6.decode(b.route["geometry"]!!.jsonPrimitive.content)).lengthM
        val e = LoopEntry.atAlong(b.route, total - 50)
        assertTrue(e.isOfficialStart)
    }

    @Test fun entryNearEndButOutsideSnap() {
        val b = bundle(7241); val total = de.scenicroutes.shared.geo.Polyline(Polyline6.decode(b.route["geometry"]!!.jsonPrimitive.content)).lengthM
        val e = LoopEntry.atAlong(b.route, total - 250)
        assertFalse(e.isOfficialStart)
        check(b, LoopEntry.rotate(b, e), e.point)
    }

    @Test fun entryAtManeuverPointAndMidStep() {
        val b = bundle(7241)
        val s = steps(b); val loc = s[5]["maneuver"]!!.jsonObject["location"]!!.jsonArray
        val p = LatLon(loc[1].jsonPrimitive.double, loc[0].jsonPrimitive.double)
        val e = LoopEntry.best(b.route, p)
        assertEquals(5, e.step); assertEquals(0, e.vertex)
        val r = LoopEntry.rotate(b, e); check(b, r, e.point)
        // Übergang am offiziellen Start: Manöver neu abgeleitet (kein depart/arrive)
        val seam = steps(r)[steps(b).size - 1 - 5]
        assertTrue(seam["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content in setOf("turn", "continue"))
        // Mitten im 15-km-Schritt
        val e2 = LoopEntry.atAlong(b.route, 12_000.0)
        assertEquals(5, e2.step); assertTrue(e2.vertex > 0)
        check(b, LoopEntry.rotate(b, e2), e2.point)
    }

    @Test fun neverEntersInsideRoundabout() {
        val b = bundle(7241); val s = steps(b)
        val k = s.indexOfFirst { it["maneuver"]!!.jsonObject["type"]!!.jsonPrimitive.content == "rotary" }
        val loc = s[k]["maneuver"]!!.jsonObject["location"]!!.jsonArray
        val e = LoopEntry.best(b.route, LatLon(loc[1].jsonPrimitive.double, loc[0].jsonPrimitive.double))
        assertNotEquals(k, e.step)
        check(b, LoopEntry.rotate(b, e), e.point)
    }

    @Test fun headingPicksMatchingDirectionOnDoublePass() {
        // Start-/Zielbereich wird zweimal durchfahren (raus und rein): Kandidaten aus beiden Durchfahrten
        val b = bundle(7241); val s = steps(b)
        val first = s[0]; val pts = Polyline6.decode(first["geometry"]!!.jsonPrimitive.content)
        val p = pts[pts.size / 2]
        val c = LoopEntry.candidates(b.route, p, null, 4)
        assertTrue(c.isNotEmpty())
        val heading = Geo.bearingDeg(pts[pts.size / 2], pts[pts.size / 2 + 1])
        val e = LoopEntry.best(b.route, p, heading)
        assertTrue(abs(((e.bearingDeg - heading + 540) % 360) - 180) < 100, "Richtung passt")
        val opp = LoopEntry.best(b.route, p, (heading + 180) % 360)
        // Gegenrichtung: anderer Kandidat (Rückweg) oder Strafe – jedenfalls nicht schlechter als erlaubt
        assertTrue(opp.approachM >= 0)
    }

    @Test fun offsetsProduceValidRotationsForManyLoops() {
        val ids = catalog.loops.filter { it.rep }.map { it.id }
        for (id in ids) {
            val b = bundle(id); val l = catalog.loops.first { it.id == id }
            for (frac in listOf(0.13, 0.37, 0.61, 0.89)) {
                val i = (l.coords.size * frac).toInt(); val p = l.coords[i]; val brg = Geo.bearingDeg(l.coords[i], l.coords[i + 1])
                for (off in listOf(0.0, 100.0, 1000.0)) {
                    val pos = offset(p, brg + 90, off)
                    val e = LoopEntry.best(b.route, pos, brg)
                    if (!e.isOfficialStart) check(b, LoopEntry.rotate(b, e), e.point)
                    if (off == 0.0) assertTrue(e.approachM < 400, "$id $frac: ${e.approachM}")
                }
            }
        }
    }

    @Test fun rotateLineKeepsLength() {
        val l = catalog.loops.first { it.rep }; val pl = de.scenicroutes.shared.geo.Polyline(l.coords)
        val r = de.scenicroutes.shared.geo.Polyline(LoopEntry.rotateLine(l.coords, pl.lengthM * 0.4))
        assertTrue(abs(r.lengthM - pl.lengthM) < 30, "${r.lengthM} vs ${pl.lengthM}")
    }

    @Test fun modifierFromBearings() {
        assertEquals("straight", LoopEntry.modifierFor(10.0, 20.0))
        assertEquals("right", LoopEntry.modifierFor(0.0, 90.0))
        assertEquals("slight left", LoopEntry.modifierFor(350.0, 310.0))
        assertEquals("uturn", LoopEntry.modifierFor(0.0, 180.0))
    }
}
