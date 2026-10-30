package de.scenicroutes.shared

import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.nav.Curves
import de.scenicroutes.shared.nav.GMeter
import de.scenicroutes.shared.nav.LoopTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import de.scenicroutes.shared.nav.Polyline6
import kotlinx.serialization.json.*
import kotlin.test.*

class DriveStatsTest {
    private val lat0 = 49.7; private val lon0 = 9.2
    private val k = cos(lat0 * PI / 180) * 111_320.0
    private fun p(x: Double, y: Double) = LatLon(lat0 + y / 110_540.0, lon0 + x / k)
    private fun straight(len: Double, x0: Double = 0.0) = (0..(len / 20).toInt()).map { p(x0 + it * 20.0, 0.0) }
    /** Bogen mit Radius r und Winkel deg, linksherum, ab (x0, 0) in +x-Richtung. */
    private fun arc(x0: Double, r: Double, deg: Double, sign: Int = 1) = (1..(deg / 3).toInt()).map { i ->
        val a = i * 3 * PI / 180; p(x0 + r * sin(a), sign * r * (1 - cos(a))) }

    // Abbiegen an Kreuzungen vs. echte Kehre
    private val ecke = straight(500.0) + (1..25).map { p(500.0, it * 20.0) }
    private val kehre = straight(500.0) + arc(500.0, 30.0, 180.0)

    @Test fun abbiegenAnKreuzungZaehltNicht() {
        assertEquals(1, Curves.detect(ecke).size)
        assertEquals(0, Curves.detect(ecke, listOf(Curves.Span(500.0, 500.0))).size)
        assertEquals(1, Curves.detect(ecke, listOf(Curves.Span(560.0, 560.0))).size, "Manöver weiter als 25 m entfernt")
    }

    @Test fun echteKehreBleibtTrotzManoeverInDerNaehe() {
        val c = Curves.detect(kehre, listOf(Curves.Span(200.0, 200.0), Curves.Span(700.0, 700.0)))
        assertEquals(1, c.size); assertTrue(c[0].hairpin)
    }

    @Test fun kreisverkehrGanzAusgeschlossen() {
        val c = Curves.detect(kehre, listOf(Curves.Span(400.0, 700.0)))
        assertEquals(0, c.size)
    }

    private fun step(type: String, pts: List<LatLon>, bearings: Int) = buildJsonObject {
        put("geometry", Polyline6.encode(pts)); put("maneuver", buildJsonObject { put("type", type) })
        put("intersections", buildJsonArray { add(buildJsonObject { put("bearings", JsonArray(List(bearings) { JsonPrimitive(0) })) }) })
    }

    @Test fun manoeverAusRoutenJson() {
        val a = straight(200.0); val b = straight(200.0, 200.0); val c = straight(200.0, 400.0); val d = straight(200.0, 600.0); val e = straight(200.0, 800.0)
        val route = buildJsonObject {
            put("geometry", Polyline6.encode(straight(1000.0)))
            put("legs", buildJsonArray { add(buildJsonObject { put("steps", buildJsonArray {
                add(step("depart", a, 1)); add(step("new name", b, 2)); add(step("new name", c, 3))
                add(step("roundabout", d, 4)); add(step("turn", e, 3)); add(step("arrive", listOf(e.last()), 1))
            }) }) })
        }
        val sp = Curves.exclusions(route).map { it.fromM.toInt() to it.toM.toInt() }
        assertEquals(listOf(400 to 400, 600 to 800, 800 to 800), sp.map { (f, t) -> (f + 5) / 10 * 10 to (t + 5) / 10 * 10 })
    }

    @Test fun geradeHatKeineKurven() = assertEquals(0, Curves.detect(straight(2000.0)).size)

    @Test fun kehreWirdErkannt() {
        val c = Curves.detect(straight(500.0) + arc(500.0, 30.0, 180.0))
        assertEquals(1, c.size); assertTrue(c[0].hairpin); assertTrue(c[0].atM in 450.0..560.0)
    }

    @Test fun weiterBogenIstKeineKurve() {
        // Radius 400 m über 40° = gestreckte Landstraße, keine Kurve
        assertEquals(0, Curves.detect(straight(300.0) + arc(300.0, 400.0, 40.0)).size)
    }

    @Test fun normaleKurveKeineKehre() {
        val c = Curves.detect(straight(300.0) + arc(300.0, 60.0, 90.0))
        assertEquals(1, c.size); assertFalse(c[0].hairpin)
    }

    @Test fun kurzerKnickUnterSchwelleZaehltNicht() = assertEquals(0, Curves.detect(straight(300.0) + arc(300.0, 60.0, 21.0)).size)

    @Test fun verbleibendeKurven() {
        val c = Curves.detect(straight(300.0) + arc(300.0, 30.0, 180.0))
        assertEquals(1, Curves.remaining(c, 0.0)); assertEquals(0, Curves.remaining(c, 1_000.0))
    }

    private val loop = straight(1000.0)
    private val track = LoopTrack(loop, listOf(100, 110, 120, 115, 130, 140, 150, 145, 160, 170, 180), 100)

    @Test fun lageAufDerRundeUndAnfahrt() {
        assertEquals(500.0, track.locate(p(500.0, 10.0))!!, 1.0)
        assertNull(track.locate(p(500.0, 300.0))) // Anfahrt abseits der Runde
        assertEquals(0f, track.progress(null)); assertEquals(0.5f, track.progress(500.0), 0.01f)
        assertEquals(1000.0, track.lengthM, 2.0)
    }

    @Test fun suchfensterVerhindertSpruenge() = assertNull(track.locate(p(900.0, 0.0), lastM = 100.0, windowM = 300.0))

    @Test fun hoeheUndHoehenmeterGeglaettet() {
        assertEquals(105.0, track.elevationAt(0.0)!!, 0.1) // Rand: Mittel aus 100 und 110
        val up = track.ascent(0.0, 1000.0)!!
        assertTrue(up in 60.0..80.0, "$up") // Rohdaten: 85 m inkl. Rauschen, geglättet weniger
        assertEquals(0.0, track.ascent(500.0, 400.0))
    }

    @Test fun ohneProfilKeineHoehe() {
        val t = LoopTrack(loop, emptyList(), 100)
        assertNull(t.elevationAt(10.0)); assertNull(t.ascent(0.0, 500.0)); assertFalse(t.hasElevation)
    }

    @Test fun gMeterBrauchtKalibrierung() {
        val g = GMeter(calSamples = 5)
        g.onSample(1.0, 0.0, 9.8, standing = false); assertNull(g.current)
        repeat(5) { g.onSample(0.0, 0.0, 9.80665, standing = true) }
        assertTrue(g.calibrated)
        repeat(60) { g.onSample(4.903, 0.0, 9.80665, standing = false) } // 0,5 g quer
        assertEquals(0.5, g.current!!, 0.01); assertEquals(0.5, g.max!!, 0.01)
    }

    @Test fun gMeterSchraegeHalterung() {
        val g = GMeter(calSamples = 3); val s = sin(PI / 4) * 9.80665
        repeat(3) { g.onSample(0.0, s, s, standing = true) } // Handy um 45° gekippt
        repeat(60) { g.onSample(2.0, s, s, standing = false) }
        assertEquals(2.0 / 9.80665, g.current!!, 0.01)
    }
}
