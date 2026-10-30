package de.scenicroutes.shared

import de.scenicroutes.shared.nav.ArrivalDetector
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrivalDetectorTest {
    /** Erster Zeitpunkt (s), zu dem die Regel anschlägt, oder null. */
    private fun firstArrival(d: ArrivalDetector, fixes: List<Triple<Double, Double, Double>>, frac: Double = 0.97): Int? {
        fixes.forEachIndexed { i, (e, n, dist) -> if (d.update(dist, frac, e, n, i * 1000L)) return i }
        return null
    }

    @Test fun stehenImWaldMitRauschen() {
        val r = Random(7)
        val fixes = List(40) { val e = r.nextDouble(-1.0, 1.0) * 15; val n = r.nextDouble(-1.0, 1.0) * 15; Triple(e, n, kotlin.math.hypot(e, n)) }
        val t = firstArrival(ArrivalDetector(), fixes)
        assertTrue(t != null && t in 10..20, "Ankunft nach etwa 10 s Stand, war $t")
    }

    @Test fun durchfahrenZaehltNicht() {
        // 15 m/s quer durch den 50-m-Bereich: höchstens 7 s drin, nie 10 s Stand
        val fixes = List(8) { i -> val e = -50.0 + 15 * i; Triple(e, 0.0, kotlin.math.abs(e)) }
        assertEquals(null, firstArrival(ArrivalDetector(), fixes))
    }

    @Test fun langsamRollenZaehltNicht() {
        // 3 m/s im Zielbereich hin und her: 30 m in 10 s, also kein Stehen
        val fixes = List(30) { i -> val e = -45.0 + 3 * i; Triple(e, 0.0, kotlin.math.abs(e)) }
        assertEquals(null, firstArrival(ArrivalDetector(), fixes))
    }

    @Test fun zuWenigFortschrittZaehltNicht() {
        // Start = Ziel: am Anfang der Runde stehen (Fortschritt 1 %) darf nicht als Ankunft gelten
        val fixes = List(30) { Triple(0.0, 0.0, 5.0) }
        assertEquals(null, firstArrival(ArrivalDetector(), fixes, frac = 0.01))
        assertFalse(ArrivalDetector().update(60.0, 1.0, 0.0, 0.0, 0))
    }

    @Test fun verlassenSetztZurueck() {
        val d = ArrivalDetector()
        for (i in 0..8) d.update(10.0, 0.97, 0.0, 0.0, i * 1000L)
        assertFalse(d.update(80.0, 0.97, 0.0, 0.0, 9_000))
        for (i in 10..18) assertFalse(d.update(10.0, 0.97, 0.0, 0.0, i * 1000L), "Wartezeit beginnt neu ($i)")
        assertTrue(d.update(10.0, 0.97, 0.0, 0.0, 20_000))
    }
}
