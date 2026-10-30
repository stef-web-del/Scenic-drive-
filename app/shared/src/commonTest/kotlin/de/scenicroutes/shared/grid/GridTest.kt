package de.scenicroutes.shared.grid

import de.scenicroutes.shared.model.Highlight
import de.scenicroutes.shared.model.Pause
import kotlin.math.abs
import kotlin.test.*

class GridTest {
    private val idx = GridIndex(1, listOf(49.703, 9.2575), 12.0, 5.0, 45.0, listOf(GridPoint(0, "Bürgstadt", 49.7067, 9.2641, 20), GridPoint(1, "leer", 49.70, 9.25, 0), GridPoint(2, "Süd", 49.62, 9.25, 20)))
    private fun loop(id: Int, kl: String = "A") = GLoop(id, "R$id", 40.0, 45, .3, .2, kl, 500, g = listOf(listOf(9.2 + id * 0.05, 49.7), listOf(9.21 + id * 0.05, 49.71)),
        prof = listOf(1, 2, 3), hl = listOf(Highlight(10.0, 2000, .4)), pausen = listOf(Pause("P", "cafe", 5.0, listOf(9.0, 49.0))), gurl = "https://www.google.com/maps/dir/?api=1&destination=1,1&waypoints=1,2%7C3,4&travelmode=driving")
    private val data = PointData(0, "x", 45.0, 9, mapOf("sportlich" to (1..14).map { RankEntry(it, it, 1 - it / 20.0) }), (1..14).associate { it.toString() to loop(it, if (it == 3) "C" else "A") })

    @Test fun naechsterPunkt() {
        val n = Grid.nearest(idx, 49.701, 9.251)!!; assertEquals(0, n.point.pid); assertTrue(n.approachMin >= 1)
        assertTrue(Grid.inArea(idx, 49.7, 9.26)); assertFalse(Grid.inArea(idx, 50.1, 8.6))
    }
    @Test fun umkehren() {
        val r = loop(1).reversed(); assertEquals(listOf(3, 2, 1), r.prof); assertEquals(28.0, r.hl[0].km); assertEquals(35.0, r.pausen[0].km)
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=1,1&waypoints=3,4%7C1,2&travelmode=driving", r.gurl)
        assertEquals(loop(1).g.first(), r.g.last())
    }
    @Test fun seitenUndFilter() {
        var p = Grid.shown(data, Profile.SPORTLICH, Grid.Filters(), emptySet(), emptyList(), 1)
        assertEquals(6, p.list.size); assertTrue(p.more); assertEquals(13, p.total)
        p = Grid.shown(data, Profile.SPORTLICH, Grid.Filters(relaxQuality = true), emptySet(), emptyList(), 3); assertEquals(14, p.total); assertFalse(p.more)
        p = Grid.shown(data, Profile.SPORTLICH, Grid.Filters(hideDriven = true, relaxQuality = true), setOf(1), emptyList(), 3); assertTrue(p.list.none { it.l.id == 1 })
        p = Grid.shown(data, Profile.SPORTLICH, Grid.Filters(relaxQuality = true, lessOverlap = true), setOf(1), listOf(data.loops["2"]!!.g), 3); assertTrue(p.list.none { it.l.id == 2 })
        assertEquals(0, Grid.shown(data, Profile.GENIESSER, Grid.Filters(), emptySet(), emptyList(), 1).total)
    }
    @Test fun sonnenuntergang() {
        // Referenz (Python astral): Miltenberg 29.10.2026 Sonnenuntergang 16:05:31 UTC
        val ms = Grid.sunsetUtcMs(2026, 10, 29, 49.70, 9.26)!!; val ref = 1793289931000L
        assertTrue(abs(ms - ref) < 4 * 60_000, "Abweichung ${(ms - ref) / 60000} min")
    }
    @Test fun zuLoop() { val l = loop(5).copy(sha = "ab").toLoop(); assertEquals(5, l.id); assertEquals("ab", l.navSha); assertEquals("A", l.klasse) }
}
