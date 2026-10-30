package de.scenicroutes.app.rec

import de.scenicroutes.shared.db.Trip
import de.scenicroutes.shared.geo.Geo
import de.scenicroutes.shared.geo.Polyline
import de.scenicroutes.shared.trip.RideMath
import de.scenicroutes.shared.trip.RideSummary
import de.scenicroutes.shared.trip.TrackPoint
import de.scenicroutes.shared.trip.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Laufende Aufzeichnung (für Statuszeile, Benachrichtigung, Live-Ansicht). */
data class RecState(
    val tripId: Long, val loopId: Int?, val title: String, val startMs: Long,
    /** Fahrzeit vor dem aktuellen Abschnitt (ms). */ val doneMs: Long, val segStartMs: Long?,
    val distM: Double, val seg: Int, val g: Double? = null, val kmh: Double? = null, val fixes: Int = 0,
) {
    val paused get() = segStartMs == null
    fun elapsedMs(now: Long) = doneMs + (segStartMs?.let { now - it } ?: 0)
}

/** Frage „Fahrt speichern?“ (nach Stopp, Navigationsende oder -abbruch). */
data class SaveAsk(val tripId: Long, val title: String, val summary: RideSummary, val loopShare: Double?, val loopTitle: String?) {
    val short get() = RideMath.isShort(summary)
    val willMarkDriven get() = (loopShare ?: 0.0) >= RideMath.DRIVEN_SHARE
}

/**
 * Kern der Fahrtaufzeichnung, ohne Android-Abhängigkeiten (testbar).
 * Jeder Fix wird sofort in die DB geschrieben – ein Absturz verliert höchstens den letzten Punkt.
 */
class RideRecorder(
    private val repo: TripRepository,
    private val loopGeom: (Int) -> Pair<Polyline, String>?,
    private val markDriven: (Int, Boolean) -> Unit,
    private val placeName: (Double, Double) -> String? = { _, _ -> null },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<RecState?>(null); val state = _state.asStateFlow()
    private val _ask = MutableStateFlow<SaveAsk?>(null); val ask = _ask.asStateFlow()
    private var last: TrackPoint? = null
    private var reversed = false

    val recording get() = _state.value != null

    /** Runde wurde während der Fahrt in Gegenrichtung neu gestartet (0.6.6): „gefahren“ gilt dann für die Gegenrichtung. */
    @Synchronized fun setReversed(r: Boolean) { reversed = r }
    @Synchronized fun start(loopId: Int?, title: String, reversed: Boolean = false): Long {
        _state.value?.let { return it.tripId }
        val t = now(); this.reversed = reversed; last = null
        val id = repo.start(loopId, title, t, reversed)
        _state.value = RecState(id, loopId, title, t, 0, t, 0.0, 0)
        return id
    }

    /** Unterbrochene Fahrt (Absturz/Kill) fortsetzen: neuer Abschnitt, bisherige Strecke bleibt. */
    @Synchronized fun resumeFrom(trip: Trip) {
        if (_state.value != null) return
        val pts = repo.points(trip.id); val s = RideMath.summary(pts)
        reversed = trip.reversed != 0L; last = null
        val t = now(); repo.setState(trip.id, "rec", t)
        _state.value = RecState(trip.id, trip.loopId?.toInt(), trip.title, trip.startedMs, s.durationS * 1000, t, s.distanceM, (pts.maxOfOrNull { it.seg } ?: -1) + 1, fixes = pts.size)
    }

    @Synchronized fun onFix(p: TrackPoint) {
        val s = _state.value ?: return; if (s.paused) return
        val pt = p.copy(seg = s.seg)
        repo.add(s.tripId, pt)
        val l = last
        val good = (p.accuracyM ?: 0.0) <= 50
        val d = if (l != null && good && l.seg == pt.seg) Geo.distanceM(l.latLon, pt.latLon).takeIf { it < 500 } ?: 0.0 else 0.0
        if (good) last = pt
        _state.value = s.copy(distM = s.distM + d, g = p.latG?.let { la -> kotlin.math.hypot(la, p.lonG ?: 0.0) }, kmh = p.speedMps?.times(3.6), fixes = s.fixes + 1)
    }

    @Synchronized fun pause() {
        val s = _state.value ?: return; if (s.paused) return
        val t = now(); repo.setState(s.tripId, "paused", t)
        _state.value = s.copy(doneMs = s.elapsedMs(t), segStartMs = null)
    }

    @Synchronized fun resume() {
        val s = _state.value ?: return; if (!s.paused) return
        val t = now(); repo.setState(s.tripId, "rec", t); last = null
        _state.value = s.copy(segStartMs = t, seg = s.seg + 1); _ask.value = null
    }

    /** Stopp: Aufzeichnung pausiert, Frage „Fahrt speichern?“ erscheint. */
    @Synchronized fun requestStop(): SaveAsk? {
        val s = _state.value ?: return null
        pause()
        return askFor(s.tripId, s.title, s.loopId).also { _ask.value = it }
    }

    /** Für eine unterbrochene Fahrt aus der DB (Wiederherstellung). */
    fun askFor(tripId: Long, title: String, loopId: Int?): SaveAsk {
        val pts = repo.points(tripId)
        val lg = loopId?.let(loopGeom)
        return SaveAsk(tripId, title, RideMath.summary(pts), lg?.let { RideMath.loopShare(it.first, pts) }, lg?.second)
    }

    /** Speichern: Kennzahlen berechnen; ab 80 % der Runde automatisch „Schon gefahren“. */
    @Synchronized fun save(a: SaveAsk = _ask.value ?: requestStop()!!): RideSummary {
        val loopId = repo.trip(a.tripId)?.loopId?.toInt()
        if (loopId == null && a.title.startsWith(FREE)) {
            repo.points(a.tripId).firstOrNull()?.let { p -> runCatching { placeName(p.lat, p.lon) }.getOrNull()?.let { repo.rename(a.tripId, "Fahrt ab $it", now()) } }
        }
        val sum = repo.finish(a.tripId, now(), a.loopShare)
        if (loopId != null && a.willMarkDriven) markDriven(loopId, reversed)
        clear(a.tripId); return sum
    }

    @Synchronized fun discard(a: SaveAsk? = _ask.value) {
        val id = a?.tripId ?: _state.value?.tripId ?: return
        repo.delete(id, now()); clear(id)
    }

    private fun clear(id: Long) {
        if (_state.value?.tripId == id) _state.value = null
        if (_ask.value?.tripId == id) _ask.value = null
        last = null
    }

    fun unfinished(): List<Trip> = if (_state.value == null) repo.unfinished() else repo.unfinished().filter { it.id != _state.value?.tripId }

    companion object { const val FREE = "Freie Fahrt" }
}
