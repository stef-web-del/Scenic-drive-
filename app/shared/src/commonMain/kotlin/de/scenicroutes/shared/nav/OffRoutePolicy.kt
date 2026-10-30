package de.scenicroutes.shared.nav

/**
 * Was tun, wenn man (absichtlich) von der Runde abweicht? Reine Logik, ohne Android – testbar.
 *
 * - Erste Abweichung: still einen Rückweg VORWÄRTS zur Runde rechnen (Rejoin, Offline-Valhalla).
 * - Folgt man dem Rückweg nicht (verlässt man auch ihn), gilt das als „ignoriert“; bis [askAfter]-mal
 *   wird still neu gerechnet (frühestens alle [minReplanMs]).
 * - Danach fragt die App einmal: „Runde hier fortsetzen“ / „Navigation pausieren“ / „Beenden“ –
 *   statt endlos Wendeanweisungen anzusagen.
 * - Pausiert: keine Ansagen, kein Banner; zurück auf der Runde → automatisch weiter.
 */
class OffRoutePolicy(private val askAfter: Int = 2, private val minReplanMs: Long = 20_000) {
    enum class Mode { ON_LOOP, REJOINING, ASK, PAUSED }
    var mode = Mode.ON_LOOP; private set
    var ignored = 0; private set
    private var lastPlanMs = Long.MIN_VALUE / 2

    sealed interface Action { data object None : Action; data object Plan : Action; data object Ask : Action }

    /** Von der Runde abgekommen (OffRouteMonitor ON→OFF). */
    fun leftLoop(nowMs: Long): Action = when (mode) {
        Mode.PAUSED, Mode.ASK -> Action.None
        else -> { mode = Mode.REJOINING; lastPlanMs = nowMs; Action.Plan }
    }

    /** Auch den berechneten Rückweg verlassen (Ferrostar meldet Abweichung von der aktuellen Route). */
    fun leftRejoinRoute(nowMs: Long): Action {
        if (mode != Mode.REJOINING || nowMs - lastPlanMs < minReplanMs) return Action.None
        ignored++
        return if (ignored >= askAfter) { mode = Mode.ASK; Action.Ask } else { lastPlanMs = nowMs; Action.Plan }
    }

    fun backOnLoop() { mode = Mode.ON_LOOP; ignored = 0 }
    fun pause() { mode = Mode.PAUSED }
    /** „Runde hier fortsetzen“ gewählt: neuer Versuch, Zähler zurück. */
    fun continueHere(nowMs: Long) { mode = Mode.REJOINING; ignored = 0; lastPlanMs = nowMs }
    val guidanceVisible get() = mode == Mode.ON_LOOP || mode == Mode.REJOINING
}
