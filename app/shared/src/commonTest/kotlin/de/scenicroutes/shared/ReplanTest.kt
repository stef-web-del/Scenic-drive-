package de.scenicroutes.shared

import de.scenicroutes.shared.nav.OffRoutePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fehler 0.6.4: Neuberechnung nur ein einziges Mal beim Verlassen der Runde, in der Anfahrt gar nicht. */
class ReplanTest {
    @Test fun failedRejoinIsRetried() {
        val p = OffRoutePolicy(minReplanMs = 20_000)
        assertEquals(OffRoutePolicy.Action.Plan, p.leftLoop(0))
        assertFalse(p.retryDue(10_000))
        assertTrue(p.retryDue(20_000))
        assertFalse(p.retryDue(25_000))
        assertTrue(p.retryDue(40_000))
    }

    @Test fun noRetryOnceRouteActive() {
        val p = OffRoutePolicy()
        p.leftLoop(0); p.planSucceeded()
        assertFalse(p.retryDue(60_000))
        assertEquals(OffRoutePolicy.Action.Plan, p.leftRejoinRoute(30_000))
        assertTrue(p.retryDue(50_000))
    }

    @Test fun noRetryWhenPausedOrBack() {
        val p = OffRoutePolicy()
        p.leftLoop(0); p.pause()
        assertFalse(p.retryDue(100_000))
        p.backOnLoop()
        assertFalse(p.retryDue(200_000))
    }

    @Test fun approachReplanRateLimited() {
        val p = OffRoutePolicy(minReplanMs = 20_000)
        assertTrue(p.approachReplanDue(0))
        assertFalse(p.approachReplanDue(5_000))
        assertTrue(p.approachReplanDue(20_000))
    }
}
