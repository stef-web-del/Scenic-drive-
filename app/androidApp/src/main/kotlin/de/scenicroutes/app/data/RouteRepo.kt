package de.scenicroutes.app.data

import android.content.Context
import de.scenicroutes.shared.model.Loop
import de.scenicroutes.shared.routes.MyRoute
import de.scenicroutes.shared.routes.Origin
import de.scenicroutes.shared.routes.RouteBook
import de.scenicroutes.shared.routes.RouteBook.Companion.encode
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * „Meine Routen“ auf dem Gerät: eine JSON-Datei (files/meine_routen.json), atomar geschrieben.
 * Datenmodell ist schon für ein späteres Konto gebaut (UUID, Besitzer, Änderungszeit, Löschmarke), siehe [RouteBook].
 * Defensiv: eine kaputte Datei wird beiseitegelegt, die App startet dann mit leerem Buch.
 */
class RouteRepo(private val ctx: Context, private val owner: () -> String, private val now: () -> Long = System::currentTimeMillis) {
    private val file get() = File(ctx.filesDir, "meine_routen.json")
    private val _book = MutableStateFlow(load())
    val book: StateFlow<RouteBook> = _book.asStateFlow()

    private fun load(): RouteBook {
        val f = file
        if (!f.exists()) return RouteBook(ownerId = "")
        return runCatching { RouteBook.parse(f.readText()) }.getOrElse {
            runCatching { f.renameTo(File(ctx.filesDir, "meine_routen.kaputt.json")) }
            RouteBook(ownerId = "")
        }
    }

    @Synchronized private fun update(fn: (RouteBook) -> RouteBook): RouteBook {
        val cur = _book.value.let { if (it.ownerId.isEmpty()) it.copy(ownerId = runCatching(owner).getOrDefault("")) else it }
        val next = fn(cur)
        val tmp = File(ctx.filesDir, "meine_routen.json.tmp")
        tmp.writeText(next.encode()); if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        _book.value = next
        return next
    }

    fun rename(id: String, name: String) = update { it.rename(id, name, now()) }
    fun delete(id: String) = update { it.delete(id, now()) }
    fun setFavorite(id: String, on: Boolean) = update { it.setFavorite(id, on, now()) }
    fun isFavoriteParent(parent: String): Boolean = _book.value.byParent(parent)?.let { it.id in _book.value.favoriteIds() } ?: false

    /** Eine Runde (Katalog, Raster, frei) als eigene Kopie ablegen; gibt es sie schon, wird sie zurückgegeben. */
    fun keep(loop: Loop, origin: String, parent: String, extra: (MyRoute) -> MyRoute = { it }): MyRoute {
        _book.value.byParent(parent)?.let { return it }
        val t = now()
        val r = extra(MyRoute(id = RouteBook.newId(), ownerId = _book.value.ownerId, origin = origin, parentId = parent, name = loop.titleShort,
            geometry = loop.geom, lengthKm = loop.km, rideMin = loop.min, hm = loop.hm, text = loop.text, tags = loop.tags,
            appLoopId = loop.id, createdMs = t, updatedMs = t))
        update { it.upsert(r) }
        snapshot(r.id, loop)
        return r
    }
    /** Vollständige Runde (mit navSha, Profil, Pausen) neben dem Buch ablegen, damit Detail und Navigation nach einem Neustart gleich bleiben. */
    private val snapDir get() = File(ctx.filesDir, "meine_routen_runden")
    private fun snapshot(id: String, loop: Loop) = runCatching {
        snapDir.mkdirs(); val tmp = File(snapDir, "$id.json.tmp")
        tmp.writeText(snapJson.encodeToString(Loop.serializer(), loop)); tmp.renameTo(File(snapDir, "$id.json"))
    }
    private val snapJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }
    /** Stern in der Detailansicht: Kopie anlegen (falls nötig) und Favorit setzen bzw. entfernen. */
    fun toggleFavorite(loop: Loop, origin: String, parent: String): Boolean {
        val r = keep(loop, origin, parent)
        val on = r.id !in _book.value.favoriteIds()
        setFavorite(r.id, on); return on
    }
    fun importGpx(xml: String, fallbackName: String): MyRoute {
        val tr = de.scenicroutes.shared.routes.Gpx.parse(xml)
        val t = now(); val km = RouteBook.lengthKm(tr.points)
        val r = MyRoute(id = RouteBook.newId(), ownerId = _book.value.ownerId, origin = Origin.IMPORT, name = (tr.name ?: fallbackName).take(80),
            geometry = tr.points, lengthKm = km, rideMin = (km / 55.0 * 60).toInt().coerceAtLeast(1), createdMs = t, updatedMs = t)
        update { it.upsert(r) }
        return r
    }

    /** Für Detail und Navigation: gespeicherte Runden wieder als [Loop] (ID bleibt stabil über Neustarts). */
    fun loopOf(r: MyRoute): Loop = runCatching { snapJson.decodeFromString(Loop.serializer(), File(snapDir, "${r.id}.json").readText()) }.getOrNull()
        ?.let { it.copy(id = r.appLoopId ?: it.id, title = r.name) } ?: plainLoop(r)
    private fun plainLoop(r: MyRoute): Loop = Loop(id = r.appLoopId ?: RouteBook.localAppId(r.id), title = r.name, km = r.lengthKm, min = r.rideMin, score = 0.0,
        klasse = if (r.origin == Origin.KATALOG) "A" else "frei", region = when (r.origin) { Origin.IMPORT -> "Importiert (GPX)"; Origin.SERVER -> "Neue Runde"; else -> "Gespeichert" },
        hm = r.hm, tags = r.tags, geom = r.geometry, text = r.text)
}
