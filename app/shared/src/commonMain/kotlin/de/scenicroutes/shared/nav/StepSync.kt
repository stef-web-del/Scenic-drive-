package de.scenicroutes.shared.nav

import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.model.LatLon

/**
 * Hält den Ferrostar-Schritt (= Abbiegebanner) mit dem tatsächlichen Fortschritt synchron.
 *
 * Ferrostar schaltet einen Schritt nur weiter, wenn ein Fix ≤ 30 m vor dem Schrittende liegt (Eintritt/Austritt).
 * Wird dieses Fenster verpasst (GPS-Lücke, Tunnel, schlechte Genauigkeit, Abkürzung, Einstieg mitten in der Runde),
 * bleibt der Schritt für immer stehen – das „eingefrorene Banner“ aus der ersten Testfahrt. Ferrostars eigenes
 * DistanceFromStep-Weiterschalten ist bei Runden ungeeignet (Start = Ziel, Selbstkreuzungen: ein Ausreißer
 * springt ans Rundenende). Deshalb: monotoner Fortschritt aus [OffRouteMonitor] auf der aktuellen Route →
 * alle Schritte, deren Ende > [slackM] hinter uns liegt, werden übersprungen.
 *
 * Gleiche Logik wie tools/navsim (steps_to_advance), dort gegen den echten Ferrostar-Kern getestet.
 */
class StepSync(stepGeometries: List<List<LatLon>>, private val slackM: Double = 40.0) {
    /** Lage jedes Schrittendes entlang der zusammengesetzten Routenlinie [points]. */
    val stepEndM: DoubleArray
    val points: List<LatLon>

    init {
        val pts = ArrayList<LatLon>(); val ends = DoubleArray(stepGeometries.size); var cum = 0.0
        stepGeometries.forEachIndexed { i, g ->
            for (c in g) {
                val last = pts.lastOrNull()
                if (last == null) pts += c else { val d = Geo.distanceM(last, c); if (d > 0.01) { cum += d; pts += c } }
            }
            ends[i] = cum
        }
        points = pts; stepEndM = ends
    }

    /**
     * Wie viele Schritte ab [currentIndex] liegen komplett hinter [progressM]? Die letzten zwei Schritte
     * (Ankunft) bleiben Ferrostar bzw. der App-Ankunftsregel überlassen.
     */
    fun stepsToAdvance(currentIndex: Int, progressM: Double): Int {
        var k = 0
        while (currentIndex + k + 2 < stepEndM.size && stepEndM[currentIndex + k] < progressM - slackM) k++
        return k
    }

    /** Rundenende erreicht (Ferrostar-Ankunft braucht sonst noch einen Fix hinter dem letzten Kreisel). */
    fun arrived(progressM: Double, toleranceM: Double = 40.0) = points.size > 1 && progressM >= stepEndM.last() - toleranceM
}
