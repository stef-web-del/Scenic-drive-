package de.scenicroutes.shared.nav

import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.model.LatLon

/**
 * Abweichungserkennung gegenüber der FESTEN Runde (nicht gegenüber der aktuellen Ferrostar-Route,
 * die nach einer Umleitung anders aussieht). Hysterese: erst nach [confirmFixes] Positionen
 * > [thresholdM] gilt man als abgewichen; zurück auf der Runde ab < [rejoinM].
 */
class OffRouteMonitor(
    private val loop: Polyline,
    private val thresholdM: Double = 50.0,
    private val rejoinM: Double = 25.0,
    private val confirmFixes: Int = 3,
    /** false = Anfahrt läuft noch: Überwachung erst scharf, wenn man die Runde erreicht hat. */
    armed: Boolean = true,
    private val maxJumpBaseM: Double = 300.0,
    private val maxSpeedMps: Double = 70.0,
    private val firstPenaltyM: Double = 60.0,
) {
    var armed = armed; private set
    enum class State { ON_LOOP, OFF_LOOP }
    var state = State.ON_LOOP; private set
    /** Fortschritt entlang der Runde in Metern (monoton, springt nicht zurück). */
    var progressM = 0.0; private set
    private var offCount = 0
    /** Zeitpunkt des letzten Fortschritts-Treffers (null = noch keiner). */
    private var lastHitMs: Long? = null
    private class Pending(val alongM: Double, val atMs: Long, val count: Int)
    private var pending: Pending? = null

    /**
     * @param nowMs Zeitstempel des Fixes. Wenn gesetzt, darf der Fortschritt höchstens
     *   [maxJumpBaseM] + [maxSpeedMps] × (Zeit seit letztem Treffer) nach vorn springen. Sonst landet ein
     *   verrauschter Fix am Start (Start = Ziel) oder an einer Stelle, wo sich die Runde selbst kreuzt,
     *   auf dem falschen Abschnitt – und die Navigation „springt“ ans Ende. Der erste Treffer bevorzugt
     *   stark den Anfang ([firstPenaltyM]), findet aber einen Einstieg mitten in der Runde.
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun update(p: LatLon, nowMs: Long? = null): Boolean {
        val first = nowMs != null && lastHitMs == null
        val pr = if (first) loop.project(p, hintAlongM = 0.0, penaltyM = firstPenaltyM) else loop.project(p, hintAlongM = progressM)
        if (!armed) { if (pr.distanceM < rejoinM && pr.alongM < 500) armed = true else return false }
        val last = lastHitMs
        val maxJump = if (nowMs == null || last == null) Double.POSITIVE_INFINITY else maxJumpBaseM + maxSpeedMps * (nowMs - last) / 1000.0
        if (pr.distanceM < rejoinM && pr.alongM >= progressM - 200 && pr.alongM <= progressM + maxJump) {
            progressM = maxOf(progressM, pr.alongM)
            if (nowMs != null) lastHitMs = nowMs
            pending = null
        } else if (nowMs != null && pr.distanceM < rejoinM && pr.alongM > progressM + maxJump) {
            // Abkürzung: weiter vorn wieder auf der Runde. Erst nach 3 aufeinanderfolgenden, plausibel vorwärts
            // laufenden Treffern übernehmen – ein Einzel-Fix an einer Kreuzung der Runde reicht nicht.
            val pd = pending
            val ok = pd != null && pr.alongM > pd.alongM && pr.alongM <= pd.alongM + maxJumpBaseM + maxSpeedMps * (nowMs - pd.atMs) / 1000.0
            val c = if (ok) pd!!.count + 1 else 1
            if (c >= 3) { progressM = pr.alongM; lastHitMs = nowMs; pending = null } else pending = Pending(pr.alongM, nowMs, c)
        } else pending = null
        val before = state
        when (state) {
            State.ON_LOOP -> { offCount = if (pr.distanceM > thresholdM) offCount + 1 else 0; if (offCount >= confirmFixes) state = State.OFF_LOOP }
            State.OFF_LOOP -> if (pr.distanceM < rejoinM) { state = State.ON_LOOP; offCount = 0 }
        }
        return before != state
    }
    fun distanceToLoop(p: LatLon) = loop.project(p, hintAlongM = progressM).distanceM
}
