package de.scenicroutes.shared.nav

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.model.LatLon

/**
 * Erkennt Fahren gegen die Rundenrichtung (0.6.6): Kurs weicht nahe der Runde über mehrere Fixes
 * und mindestens [minDistM] Strecke um > [diffDeg] von der Rundenrichtung ab.
 * Löst einmal aus; erst nach [rearmM] Fahrt in richtiger Richtung wieder scharf.
 */
class WrongWayDetector(
    private val diffDeg: Double = 120.0,
    private val minFixes: Int = 3,
    private val minDistM: Double = 150.0,
    private val nearM: Double = 40.0,
    private val minSpeedMps: Double = 3.0,
    private val rearmM: Double = 300.0,
) {
    private var count = 0
    private var dist = 0.0
    private var last: LatLon? = null
    private var okDist = 0.0
    var fired = false; private set

    /** @param loopBearing Rundenrichtung an der projizierten Stelle; @param distToLoop Abstand zur Runde.
     *  @return true genau beim Auslösen. */
    fun update(p: LatLon, headingDeg: Double?, speedMps: Double?, loopBearing: Double, distToLoop: Double): Boolean {
        val prev = last; last = p
        val step = prev?.let { Geo.distanceM(it, p) } ?: 0.0
        if (headingDeg == null || (speedMps ?: 0.0) < minSpeedMps || distToLoop > nearM) return false // keine Aussage
        if (headingDiff(headingDeg, loopBearing) > diffDeg) {
            count++; dist += step; okDist = 0.0
            if (!fired && count >= minFixes && dist >= minDistM) { fired = true; return true }
        } else {
            count = 0; dist = 0.0
            if (fired) { okDist += step; if (okDist >= rearmM) { fired = false; okDist = 0.0 } }
        }
        return false
    }
    fun reset() { count = 0; dist = 0.0; okDist = 0.0; fired = false; last = null }
}
