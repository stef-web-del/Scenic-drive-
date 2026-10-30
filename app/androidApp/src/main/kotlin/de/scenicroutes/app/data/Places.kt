package de.scenicroutes.app.data

import android.content.Context
import de.scenicroutes.shared.places.PlaceIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Offline-Ortsindex (Asset places.tsv, OSM). Einmal im Hintergrund laden, danach im Speicher. */
object Places {
    private var idx: PlaceIndex? = null
    private val lock = Mutex()
    suspend fun index(ctx: Context): PlaceIndex = lock.withLock {
        idx ?: withContext(Dispatchers.IO) { PlaceIndex.parse(ctx.assets.open("places.tsv").bufferedReader().readText()) }.also { idx = it }
    }
}
