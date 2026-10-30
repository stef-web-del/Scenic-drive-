package de.scenicroutes.shared

import de.scenicroutes.shared.places.PlaceIndex
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Echter Ortsindex aus dem App-Asset: bekannte Orte, Tippfehler, Laufzeit. */
class PlaceAssetTest {
    private val idx by lazy {
        val f = listOf("../androidApp/src/main/assets/places.tsv", "androidApp/src/main/assets/places.tsv").map(::File).first { it.exists() }
        PlaceIndex.parse(f.readText())
    }
    @Test fun loadsAndFinds() {
        assertTrue(idx.places.size > 5000, "zu wenige Orte: ${idx.places.size}")
        for ((q, want) in listOf("Miltenberg" to "Miltenberg", "Burgstadt" to "Bürgstadt", "Klingenburg" to "Klingenberg am Main", "Amorbch" to "Amorbach", "Obernburg" to "Obernburg am Main"))
            assertEquals(want, idx.search(q, nearLat = 49.70, nearLon = 9.25).first().place.name, "Suche „$q“")
    }
    @Test fun farFieldNamesRankLastWithoutLocation() {
        assertEquals("Bürgstadt", idx.search("Burgstadt").first().place.name)
        // keine weit entfernte Flur vor einem nahen Treffer gleicher Art
        val hits = idx.search("Burgstadt", limit = 6)
        val firstFar = hits.indexOfFirst { (de.scenicroutes.shared.places.Place.KIND_RANK[it.place.kind] ?: 0) >= PlaceIndex.MINOR_RANK &&
            PlaceIndex.km(PlaceIndex.CENTER_LAT, PlaceIndex.CENTER_LON, it.place.lat, it.place.lon) > PlaceIndex.FAR_KM }
        if (firstFar >= 0) assertTrue(hits.drop(firstFar).all { (de.scenicroutes.shared.places.Place.KIND_RANK[it.place.kind] ?: 0) >= PlaceIndex.MINOR_RANK &&
            PlaceIndex.km(PlaceIndex.CENTER_LAT, PlaceIndex.CENTER_LON, it.place.lat, it.place.lon) > PlaceIndex.FAR_KM }, hits.joinToString { it.place.label })
        println("Burgstadt ohne Standort: " + hits.joinToString { it.place.label })
    }
    @Test fun fastEnough() {
        idx.search("warm"); val t = System.nanoTime(); repeat(20) { idx.search("Klingenburg") }
        val ms = (System.nanoTime() - t) / 20 / 1e6; println("Ortssuche: ${"%.1f".format(ms)} ms je Anfrage, ${idx.places.size} Orte")
        assertTrue(ms < 150, "zu langsam: $ms ms")
    }
}
