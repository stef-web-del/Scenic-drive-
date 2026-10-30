package de.scenicroutes.shared.offline

import app.cash.sqldelight.db.SqlDriver
import de.scenicroutes.shared.db.ScenicDb

data class OfflineEntry(val loopId: Int, val navSha: String, val savedMs: Long, val mapBytes: Long, val mapComplete: Boolean, val navBytes: Long) {
    val totalBytes get() = mapBytes + navBytes
}

/** Offline gespeicherte Runden (Navigation als OSRM-JSON in SQLDelight). */
class OfflineRepository(driver: SqlDriver) {
    private val q = ScenicDb(driver).offlineLoopQueries
    fun save(loopId: Int, navJson: String, navSha: String, nowMs: Long, mapBytes: Long = 0, mapComplete: Boolean = false) =
        q.upsert(loopId.toLong(), navJson, navSha, nowMs, mapBytes, if (mapComplete) 1 else 0)
    fun setMap(loopId: Int, bytes: Long, complete: Boolean) = q.setMap(bytes, if (complete) 1 else 0, loopId.toLong())
    fun navJson(loopId: Int): String? = q.byId(loopId.toLong()).executeAsOneOrNull()?.navJson
    fun entry(loopId: Int): OfflineEntry? = all().firstOrNull { it.loopId == loopId }
    fun all(): List<OfflineEntry> = q.all { id, sha, ms, mb, mc, nb -> OfflineEntry(id.toInt(), sha, ms, mb, mc == 1L, nb ?: 0) }.executeAsList()
    fun remove(loopId: Int) = q.remove(loopId.toLong())
}
