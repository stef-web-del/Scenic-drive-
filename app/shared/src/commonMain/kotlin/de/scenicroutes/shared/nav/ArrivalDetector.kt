package de.scenicroutes.shared.nav

/**
 * Robuste Ankunft am Rundenende, auch wenn der Fortschritt im Wald nie ganz bis zum Ziel kommt.
 *
 * Angekommen ist man, wenn alle drei Bedingungen zusammen gelten:
 *  - der Fix liegt höchstens [radiusM] vom Ziel entfernt,
 *  - mindestens [minFraction] des Fortschritts auf der Route sind erreicht,
 *  - man steht seit mindestens [dwellMs]: Mittel der ersten und der letzten drei Fixes im Fenster liegen
 *    weniger als [stillM] auseinander. Bei 54 km/h wären es rund 150 m, GPS-Rauschen im Stand bleibt darunter.
 *
 * Verlässt man den Zielbereich oder fällt der Fortschritt unter die Schwelle, beginnt die Wartezeit neu.
 * Gleiche Regel wie tools/navsim (Arrival).
 */
class ArrivalDetector(
    private val radiusM: Double = 50.0,
    private val minFraction: Double = 0.95,
    private val dwellMs: Long = 10_000,
    private val stillM: Double = 20.0,
) {
    private class Fix(val x: Double, val y: Double, val t: Long)
    private val win = ArrayDeque<Fix>()

    /** [eastM]/[northM]: Lage relativ zum Ziel in Metern (beliebiger lokaler Ursprung genügt für das Stehen). */
    fun update(distToEndM: Double, progressFraction: Double, eastM: Double, northM: Double, tMs: Long): Boolean {
        if (distToEndM > radiusM || progressFraction < minFraction) { win.clear(); return false }
        win.addLast(Fix(eastM, northM, tMs))
        while (win.size > 3 && tMs - win[1].t >= dwellMs) win.removeFirst()
        if (win.size < 6 || tMs - win.first().t < dwellMs) return false
        val a = win.take(3); val b = win.takeLast(3)
        val dx = a.sumOf { it.x } / 3 - b.sumOf { it.x } / 3; val dy = a.sumOf { it.y } / 3 - b.sumOf { it.y } / 3
        return kotlin.math.sqrt(dx * dx + dy * dy) < stillM
    }

    fun reset() = win.clear()
}
