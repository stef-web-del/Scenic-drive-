package de.scenicroutes.shared.places

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceIndexTest {
    private val rows = listOf(
        listOf("Miltenberg", "town", "49.70176", "9.25597", ""),
        listOf("Bürgstadt", "village", "49.71586", "9.27138", ""),
        listOf("Klingenberg am Main", "town", "49.78241", "9.18278", ""),
        listOf("Breitendiel", "village", "49.69152", "9.22073", ""),
        listOf("Mühlberg", "locality", "49.70000", "9.30000", "Bürgstadt"),
        listOf("Mühlberg", "hamlet", "49.80000", "9.10000", "Klingenberg am Main"),
        listOf("Sankt Martin", "locality", "49.75000", "9.20000", "Miltenberg"),
        listOf("Großheubach", "village", "49.72800", "9.22300", ""),
        listOf("kaputte zeile"),
    )
    private val idx = PlaceIndex.parse(rows.joinToString("\n") { it.joinToString("\t") })
    private fun first(q: String) = idx.search(q).firstOrNull()?.place?.name

    @Test fun parseSkipsBrokenLines() = assertEquals(8, idx.places.size)
    @Test fun exactAndPrefix() { assertEquals("Miltenberg", first("Miltenberg")); assertEquals("Miltenberg", first("milt")) }
    @Test fun umlautsWithoutUmlauts() { assertEquals("Bürgstadt", first("Burgstadt")); assertEquals("Bürgstadt", first("Buergstadt")); assertEquals("Großheubach", first("grossheubach")) }
    @Test fun typos() {
        assertEquals("Miltenberg", first("Miltenbreg"))          // vertauscht
        assertEquals("Klingenberg am Main", first("Klingenburg")) // ein Buchstabe falsch
        assertEquals("Breitendiel", first("Brietendiel"))
        assertEquals("Großheubach", first("Grosheubch"))          // zwei Fehler bei langem Wort
    }
    @Test fun wordInsideName() = assertEquals("Klingenberg am Main", first("am main"))
    @Test fun saintAbbreviation() = assertEquals("Sankt Martin", first("St. Martin"))
    @Test fun shortOrEmptyQuery() { assertTrue(idx.search("").isEmpty()); assertTrue(idx.search("m").isEmpty()) }
    @Test fun noFuzzyNoiseForShortQueries() = assertTrue(idx.search("xyz").isEmpty())
    @Test fun importanceThenDistance() {
        val h = idx.search("Mühlberg", nearLat = 49.79, nearLon = 9.11)
        assertEquals(2, h.size); assertEquals("hamlet", h[0].place.kind)   // Weiler vor Flur
        assertEquals("Mühlberg (Weiler bei Klingenberg am Main)", idx.search("Muhlberg")[0].place.label)
    }
    @Test fun osaCountsTransposition() { assertEquals(1, PlaceIndex.osa("ab", "ba")); assertEquals(3, PlaceIndex.osa("kitten", "sitting")) }
}
