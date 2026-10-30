package de.scenicroutes.app.data

import de.scenicroutes.shared.nav.BackendConfig
import de.scenicroutes.shared.nav.GeoPoint
import de.scenicroutes.shared.nav.NavBackend
import de.scenicroutes.shared.nav.NavJson
import de.scenicroutes.shared.nav.RejoinRequest
import de.scenicroutes.shared.nav.RouteBundle
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpNavBackend(val config: BackendConfig) : NavBackend {
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    override suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching { http.newCall(Request.Builder().url(config.healthUrl).build()).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    override suspend fun rejoin(req: RejoinRequest): RouteBundle = withContext(Dispatchers.IO) {
        val body = NavJson.encode(req).toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url(config.rejoinUrl).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Backend ${r.code}")
            NavJson.decodeBundle(r.body.string())
        }
    }

    override suspend fun approach(from: GeoPoint, to: GeoPoint, heading: Double?): RouteBundle = withContext(Dispatchers.IO) {
        val body = NavJson.approachBody(from, to, heading).toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url(config.routeUrl).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Backend ${r.code}")
            NavJson.bundleFromOsrm(r.body.string())
        }
    }
}
