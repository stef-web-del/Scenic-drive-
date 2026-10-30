package de.scenicroutes.shared.trip

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import de.scenicroutes.shared.db.ScenicDb
import de.scenicroutes.shared.db.Trip
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Lokales Fahrtenbuch (SQLDelight, gemeinsam für Android/iOS). Alles bleibt auf dem Gerät.
 * Jeder Punkt wird sofort geschrieben (übersteht Absturz); state = rec | paused | done.
 */
class TripRepository(driver: SqlDriver, private val newUuid: () -> String = { randomUuid() }) {
    private val db = ScenicDb(driver)
    private val q = db.tripQueries

    fun start(loopId: Int?, title: String, nowMs: Long, reversed: Boolean = false): Long = db.transactionWithResult {
        q.insertTrip(loopId?.toLong(), title, nowMs, newUuid(), nowMs, if (reversed) 1 else 0); q.lastId().executeAsOne()
    }
    fun add(tripId: Long, p: TrackPoint) = q.insertPoint(tripId, p.timeMs, p.lat, p.lon, p.altM, p.speedMps, p.accuracyM, p.bearingDeg, p.lonG, p.latG, p.yawDps, p.gPeak, p.seg.toLong())
    fun setState(tripId: Long, state: String, nowMs: Long) = q.setState(state, nowMs, tripId)
    fun finish(tripId: Long, nowMs: Long, loopCoverage: Double?): RideSummary {
        val s = RideMath.summary(points(tripId))
        q.finishTrip(nowMs, s.distanceM, s.maxKmh, s.avgKmh, loopCoverage, s.durationS, s.movingS, s.ascentM, s.descentM, s.maxLonG, s.maxLatG, s.curves.toLong(), nowMs, tripId)
        return s
    }
    fun rename(tripId: Long, title: String, nowMs: Long) = q.rename(title.trim().take(80), nowMs, tripId)
    fun points(tripId: Long): List<TrackPoint> = q.pointsForTrip(tripId) { _, t, la, lo, alt, v, acc, b, lg, qg, y, gp, seg ->
        TrackPoint(t, la, lo, alt, v, acc, b, lg, qg, y, gp, seg.toInt()) }.executeAsList()
    fun pointCount(tripId: Long): Long = q.pointCount(tripId).executeAsOne()
    fun trips(): List<Trip> = q.allTrips().executeAsList()
    fun tripsFlow(ctx: CoroutineDispatcher): Flow<List<Trip>> = q.allTrips().asFlow().mapToList(ctx)
    fun unfinished(): List<Trip> = q.unfinished().executeAsList()
    fun trip(id: Long): Trip? = q.tripById(id).executeAsOneOrNull()
    /** Löschen = Löschflag (sync-fähig) + Punkte entfernen (Speicher). */
    fun delete(id: Long, nowMs: Long = 0) = db.transaction { q.deletePoints(id); q.markDeleted(nowMs, id) }
}

internal fun randomUuid(): String {
    val r = kotlin.random.Random.Default; val b = ByteArray(16).also { r.nextBytes(it) }
    b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte(); b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
    val h = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
}
