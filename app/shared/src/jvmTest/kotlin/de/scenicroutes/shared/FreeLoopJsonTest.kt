package de.scenicroutes.shared

import de.scenicroutes.shared.freeloop.FreeLoopJson
import de.scenicroutes.shared.freeloop.summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Echte Server-Antwort (Bürgstadt, 29.10.2026) wird gelesen. */
class FreeLoopJsonTest {
    private val raw = javaClass.getResource("/freeloop_job.json")!!.readText()
    @Test fun parsesRealResponse() {
        val v = FreeLoopJson.view(raw)
        assertEquals("done", v.status); assertTrue(!v.running); assertEquals(1f, v.fraction)
        assertEquals(6, v.loops.size); assertTrue(v.more)
        v.loops.forEach { assertTrue(it.loopMin in 35..50, "${it.loopMin}") }
        assertEquals((1..6).toList(), v.loops.map { it.rang })
    }
    @Test fun summaryShowsApproachSeparately() {
        val l = FreeLoopJson.view(raw).loops.first { it.approachMin >= 1.0 }
        val s = l.summary(); assertTrue(s.startsWith("Runde ${l.loopMin} min") && "Anfahrt" in s && "Rückweg" in s, s)
    }
    @Test fun startBodyEscapes() = assertEquals("""{"lat":49.7,"lon":9.2,"name":"A \"B\""}""", FreeLoopJson.startBody(49.7, 9.2, "A \"B\""))
    @Test fun runningFraction() {
        val v = FreeLoopJson.view("""{"id":"x","status":"running","progress":{"tried":50,"total":200},"loops":[]}""")
        assertEquals(0.25f, v.fraction)
    }
}
