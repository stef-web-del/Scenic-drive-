package de.scenicroutes.app.data

import android.content.Context
import de.scenicroutes.shared.freeloop.FreeLoopJson
import de.scenicroutes.shared.freeloop.JobView
import de.scenicroutes.shared.nav.NavJson
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Lokaler Server „Runde ab Punkt“ (server/server.py). */
interface FreeLoopApi {
    suspend fun start(lat: Double, lon: Double, name: String): String
    /** Mit Wunschzeit, Profil und optionalem Ort, an dem die Runde vorbeiführt. Ältere Server/Fakes: nur Start. */
    suspend fun start(lat: Double, lon: Double, name: String, minutes: Int, profile: String, via: Pair<Double, Double>?): String = start(lat, lon, name)
    suspend fun view(job: String, limit: Int): JobView
    suspend fun cancel(job: String)
    /** Navi-Datei (Anfahrt + Runde + Rückweg) als Ferrostar-JSON. */
    suspend fun nav(job: String, loop: Int): String
    /** Geometrie: reine Runde, Anfahrt, Rückweg (je [lon, lat]). */
    suspend fun geometry(job: String, loop: Int): FreeGeometry
}

@kotlinx.serialization.Serializable
data class FreeGeometry(val coords: List<List<Double>>, val anfahrt: List<List<Double>> = emptyList(), val rueckweg: List<List<Double>> = emptyList()) {
    /** Ganze Fahrt ab Startpunkt, so wie sie auch in der Navi-Datei steht. */
    val full get() = anfahrt + coords + rueckweg
}

class HttpFreeLoopApi(base: String) : FreeLoopApi {
    private val base = base.trimEnd('/')
    private val http = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    private suspend fun call(r: Request): String = withContext(Dispatchers.IO) {
        http.newCall(r).execute().use { if (!it.isSuccessful) throw IOException("Server ${it.code}"); it.body.string() }
    }
    override suspend fun start(lat: Double, lon: Double, name: String) =
        FreeLoopJson.created(call(Request.Builder().url("$base/jobs").post(FreeLoopJson.startBody(lat, lon, name).toRequestBody(jsonType)).build())).id
    override suspend fun start(lat: Double, lon: Double, name: String, minutes: Int, profile: String, via: Pair<Double, Double>?) =
        FreeLoopJson.created(call(Request.Builder().url("$base/jobs").post(FreeLoopJson.startBody(lat, lon, name, minutes, profile, via).toRequestBody(jsonType)).build())).id
    override suspend fun view(job: String, limit: Int) = FreeLoopJson.view(call(Request.Builder().url("$base/jobs/$job?offset=0&limit=$limit").build()))
    override suspend fun cancel(job: String) { runCatching { call(Request.Builder().url("$base/jobs/$job").delete().build()) } }
    override suspend fun geometry(job: String, loop: Int): FreeGeometry =
        FreeLoopJson.json.decodeFromString(FreeGeometry.serializer(), call(Request.Builder().url("$base/jobs/$job/loops/$loop").build()))
    override suspend fun nav(job: String, loop: Int) = call(Request.Builder().url("$base/jobs/$job/loops/$loop/nav").post("{}".toRequestBody(jsonType)).build())
}

/**
 * Freie Runde für die Navigation vorbereiten: Navi-Datei prüfen und speichern, Runde als normales [Loop] anmelden.
 * Damit laufen Navigation, Einstieg/Anfahrt, Rückführung und Fahrtaufzeichnung genau wie bei Katalogrunden.
 */
object FreeLoopFiles {
    /** Feste App-ID je Job und Runde, außerhalb der Katalog- und Raster-IDs. */
    fun appId(job: String, loop: Int) = 900_000_000 + ((job.hashCode() * 31 + loop) and 0x7ffffff) % 99_000_000

    fun save(ctx: Context, id: Int, json: String): File {
        NavJson.decodeBundle(json) // wirft, wenn die Datei kein gültiges Routenformat hat
        val dir = File(ctx.filesDir, "freie_runden").apply { mkdirs() }
        return File(dir, "loop_$id.json").apply { writeText(json) }
    }

    fun toLoop(id: Int, l: de.scenicroutes.shared.freeloop.FreeLoop, g: FreeGeometry, startName: String) = de.scenicroutes.shared.model.Loop(
        id = id, title = "Neue Runde ab $startName (${l.einstieg})", km = l.km, min = l.loopMin, score = 0.0, klasse = "frei", region = startName,
        hm = l.hm, tags = l.tags, geom = g.full, text = l.text, navKm = l.km + l.approachKm + l.returnKm)
}
