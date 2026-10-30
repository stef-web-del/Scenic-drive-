package de.scenicroutes.app.data

import android.content.Context
import de.scenicroutes.shared.model.LoopCatalog
import de.scenicroutes.shared.nav.NavJson
import de.scenicroutes.shared.nav.RouteBundle
import de.scenicroutes.shared.offline.OfflineRepository
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Daten-Endpunkte (Vercel Blob, öffentlich, CDN + gzip). */
object Endpoints {
    const val DATA = "https://wqofepnfttvlzsvq.public.blob.vercel-storage.com/nav/v1"
    const val CATALOG = "$DATA/catalog.json"
    fun nav(id: Int) = "$DATA/$id.json"
    const val GRID = "https://wqofepnfttvlzsvq.public.blob.vercel-storage.com/grid/v1"
    const val VERSION = "https://scenic-routes-app-download.vercel.app/version.json"
}

fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

val http: OkHttpClient by lazy { OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }

suspend fun httpGet(url: String): ByteArray = withContext(Dispatchers.IO) {
    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) error("HTTP ${r.code} für $url"); r.body.bytes()
    }
}

enum class RouteSource(val label: String) { SAVED("offline gespeichert"), REMOTE("aus dem Netz"), BUNDLED("in der App enthalten") }

/** Katalog + vorberechnete Navigation. Reihenfolge Navigation: gespeichert (SQLDelight) → Vercel → in der APK. */
class LoopData(private val ctx: Context, private val offline: () -> OfflineRepository) {
    private val cacheFile = File(ctx.filesDir, "catalog.json")

    /** Sofort verfügbar: zuletzt geladener Katalog aus dem Netz, sonst der mitgelieferte. */
    fun initialCatalog(): LoopCatalog = runCatching { LoopCatalog.parse(cacheFile.readText()) }.getOrNull()
        ?: LoopCatalog.parse(ctx.assets.open("loops.json").bufferedReader().readText())

    /** Lädt den aktuellen Katalog von Vercel; null bei Fehler (App bleibt beim vorhandenen). */
    suspend fun refreshCatalog(): LoopCatalog? = runCatching {
        val s = httpGet(Endpoints.CATALOG).decodeToString()
        LoopCatalog.parse(s).also { withContext(Dispatchers.IO) { cacheFile.writeText(s) } }
    }.getOrNull()

    fun bundled(id: Int): String? = runCatching { ctx.assets.open("routes/$id.json").bufferedReader().readText() }.getOrNull()

    suspend fun fetchNav(id: Int, expectSha: String?): String {
        val b = httpGet(Endpoints.nav(id))
        if (!expectSha.isNullOrEmpty() && sha256(b) != expectSha) error("Prüfsumme der Navigation stimmt nicht")
        return b.decodeToString()
    }

    /** Navi-Dateien freier Runden (Server „Runde ab Punkt“), auf dem Gerät unter files/freie_runden/ gespeichert. */
    fun freeNavFile(id: Int) = java.io.File(ctx.filesDir, "freie_runden/loop_$id.json")

    suspend fun route(id: Int, sha: String?): Pair<RouteBundle, RouteSource> {
        withContext(Dispatchers.IO) { freeNavFile(id).takeIf { it.exists() }?.readText() }?.let { return NavJson.decodeBundle(it) to RouteSource.SAVED }
        withContext(Dispatchers.IO) { offline().navJson(id) }?.let { return NavJson.decodeBundle(it) to RouteSource.SAVED }
        runCatching { fetchNav(id, sha) }.getOrNull()?.let { return NavJson.decodeBundle(it) to RouteSource.REMOTE }
        val b = bundled(id) ?: error("Keine Navigation für Runde $id verfügbar")
        return NavJson.decodeBundle(b) to RouteSource.BUNDLED
    }
}
