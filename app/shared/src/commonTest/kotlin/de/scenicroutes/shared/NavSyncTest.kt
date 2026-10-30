package de.scenicroutes.shared

import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon
import de.scenicroutes.shared.nav.OffRouteMonitor
import de.scenicroutes.shared.nav.OffRoutePolicy
import de.scenicroutes.shared.nav.StepSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavSyncTest {
    // Rechteck-Runde ~ 4 × 1,1 km, Start = Ziel
    private val a = LatLon(49.70, 9.22); private val b = LatLon(49.71, 9.22); private val c = LatLon(49.71, 9.235); private val d = LatLon(49.70, 9.235)
    private fun seg(p: LatLon, q: LatLon, n: Int = 20) = (0..n).map { LatLon(p.lat + (q.lat - p.lat) * it / n, p.lon + (q.lon - p.lon) * it / n) }
    private val steps = listOf(seg(a, b), seg(b, c), seg(c, d), seg(d, a), listOf(a, a))
    private val sync = StepSync(steps)
    private val line = Polyline(sync.points)

    @Test fun skipsMissedStepsAfterGap() {
        val m = OffRouteMonitor(line)
        m.update(a, 0); m.update(LatLon(49.701, 9.22), 7_000)
        // 90 s Lücke, wieder da mitten auf Schritt 2 (c→d)
        m.update(LatLon(49.705, 9.235), 97_000)
        assertEquals(2, sync.stepsToAdvance(0, m.progressM))
    }

    @Test fun noisyFixAtStartDoesNotJumpToEnd() {
        val m = OffRouteMonitor(line)
        // 20 m neben dem Start, näher am letzten Abschnitt (d→a) als am ersten
        m.update(LatLon(49.70005, 9.2203), 0)
        assertTrue(m.progressM < 500, "progress ${m.progressM}")
        assertEquals(0, sync.stepsToAdvance(0, m.progressM))
        assertTrue(!sync.arrived(m.progressM))
    }

    @Test fun shortcutIsAcceptedAfterThreeConsistentFixes() {
        val m = OffRouteMonitor(line)
        m.update(a, 0); m.update(LatLon(49.705, 9.22), 60_000)
        // Abkürzung quer rüber, dann auf c→d weiter (weit mehr als 300 m + 70 m/s voraus)
        m.update(LatLon(49.7060, 9.235), 70_000)
        assertTrue(m.progressM < 1_000, "Einzel-Fix darf nicht springen: ${m.progressM}")
        m.update(LatLon(49.7055, 9.235), 72_000); m.update(LatLon(49.7050, 9.235), 74_000)
        assertTrue(m.progressM > 2_500, "nach 3 Treffern übernommen: ${m.progressM}")
        assertEquals(2, sync.stepsToAdvance(0, m.progressM))
    }

    @Test fun arrivalAtLoopEnd() {
        val m = OffRouteMonitor(line); var t = 0L
        for (p in steps.flatten()) { m.update(p, t); t += 10_000 }
        assertTrue(sync.arrived(m.progressM))
    }

    @Test fun policyAsksAfterIgnoredRejoins() {
        val p = OffRoutePolicy(askAfter = 2, minReplanMs = 20_000)
        assertEquals(OffRoutePolicy.Action.Plan, p.leftLoop(0))
        assertEquals(OffRoutePolicy.Action.None, p.leftRejoinRoute(5_000)) // zu früh
        assertEquals(OffRoutePolicy.Action.Plan, p.leftRejoinRoute(30_000))
        assertEquals(OffRoutePolicy.Action.Ask, p.leftRejoinRoute(60_000))
        assertTrue(!p.guidanceVisible)
        p.pause(); assertEquals(OffRoutePolicy.Action.None, p.leftLoop(70_000))
        p.backOnLoop(); assertTrue(p.guidanceVisible); assertEquals(0, p.ignored)
    }
}
