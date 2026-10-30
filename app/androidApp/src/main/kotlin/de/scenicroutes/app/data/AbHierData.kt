package de.scenicroutes.app.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import de.scenicroutes.shared.db.ScenicDb
import de.scenicroutes.shared.grid.Grid
import de.scenicroutes.shared.grid.GridIndex
import de.scenicroutes.shared.grid.PointData
import de.scenicroutes.shared.grid.Profile
import java.io.File
import java.util.UUID

/** Vorberechnetes Startraster aus Vercel Blob (grid/v1), mit Datei-Cache für schlechtes Netz. */
class GridData(private val ctx: Context) {
    private val dir get() = File(ctx.filesDir, "grid/v1").apply { mkdirs() }
    private suspend fun load(name: String): String = runCatching { String(httpGet("${Endpoints.GRID}/$name")).also { File(dir, name).writeText(it) } }
        .getOrElse { e -> File(dir, name).takeIf { it.exists() }?.readText() ?: throw e }
    suspend fun index(): GridIndex = Grid.parseIndex(load("index.json"))
    suspend fun point(pid: Int): PointData = Grid.parsePoint(load("p%03d.json".format(pid)))
}

/** Lokales Profil, vorbereitet für Konten: UUID, Zeitstempel, Löschflag – später synchronisierbar.
 *  Defensiv: ein Datenbankfehler darf die App nie beenden (0.5.0: „no such table: LocalProfile“) – dann Standardwerte. */
class LocalStore(driver: SqlDriver) {
    private val q = ScenicDb(driver).localQueries
    private fun now() = System.currentTimeMillis()
    private inline fun <T> safe(fallback: T, block: () -> T): T = try { block() } catch (e: Exception) { android.util.Log.w("LocalStore", "DB-Fehler", e); fallback }
    private fun me() = q.profile().executeAsOneOrNull() ?: UUID.randomUUID().toString().let { id -> q.insertProfile(id, Profile.SPORTLICH.key, now(), now()); q.profile().executeAsOne() }
    val uuid: String get() = safe("") { me().id }
    var profile: Profile
        get() = safe(Profile.SPORTLICH) { Profile.of(me().profile) }
        set(v) { safe(Unit) { q.setProfile(v.key, now(), me().id) } }
    fun driven(): Set<Int> = safe(emptySet()) { q.drivenActive().executeAsList().map { it.loopId.toInt() }.toSet() }
    fun toggleDriven(loopId: Int, reversed: Boolean) = safe(Unit) {
        if (loopId in driven()) q.markDrivenDeleted(now(), loopId.toLong())
        else q.insertDriven(UUID.randomUUID().toString(), loopId.toLong(), if (reversed) 1 else 0, now(), now())
    }
    /** Automatisch nach gespeicherter Fahrt (≥ 80 % der Runde): setzt nur, entfernt nie. */
    fun markDriven(loopId: Int, reversed: Boolean) = safe(Unit) { if (loopId !in driven()) q.insertDriven(UUID.randomUUID().toString(), loopId.toLong(), if (reversed) 1 else 0, now(), now()) }
    fun report(loopId: Int, kind: String, text: String): Boolean = safe(false) { q.insertReport(UUID.randomUUID().toString(), loopId.toLong(), kind, text.take(500), now(), now()); true }
}
