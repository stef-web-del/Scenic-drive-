package de.scenicroutes.app

import androidx.test.core.app.ApplicationProvider
import de.scenicroutes.app.data.FreeLoopFiles
import de.scenicroutes.app.data.LoopData
import de.scenicroutes.app.data.RouteRepo
import de.scenicroutes.app.data.RouteSource
import de.scenicroutes.shared.routes.Origin
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import org.junit.Assert.assertEquals


/**
 * Neustart der App = neue RouteRepo-/LoopData-Instanz auf denselben Dateien.
 * Raster-Kopien behalten navSha und Geometrie, freie Runden behalten ihre Navi-Datei.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ScenicApp::class)
class RestartPersistenceTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun rasterKopieNachNeustartGleich() {
        val raster = catalog.loops.first { it.navSha.isNotEmpty() }.let { it.copy(id = 700_000_123, title = "Rasterrunde Test") }
        val kept = RouteRepo(ctx, { "geraet-1" }).keep(raster, Origin.SERVER, "raster:700000123")
        val after = RouteRepo(ctx, { "geraet-1" })   // „Neustart“
        val r = after.book.value.routes.firstOrNull { it.id == kept.id }!!
        val loop = after.loopOf(r)
        assertEquals(raster.id, loop.id); assertEquals(raster.navSha, loop.navSha); assertEquals(raster.geom, loop.geom)
        assertEquals(raster.navKm, loop.navKm, 1e-9)
    }

    @Test fun freieRundeNavigiertNachNeustart() = runBlocking {
        val json = File("src/main/assets/routes/101.json").readText()
        val id = FreeLoopFiles.appId("job-xyz", 2)
        FreeLoopFiles.save(ctx, id, json)
        val loop = catalog.loops.first().copy(id = id, title = "Neue Runde ab Bürgstadt (Nord)", klasse = "frei")
        val kept = RouteRepo(ctx, { "geraet-1" }).keep(loop, Origin.SERVER, "frei:job-xyz:2")
        val after = RouteRepo(ctx, { "geraet-1" })
        val back = after.loopOf(after.book.value.routes.first { it.id == kept.id })
        assertEquals(id, back.id)
        val (_, src) = LoopData(ctx) { error("nicht gebraucht") }.route(back.id, back.navSha)
        assertEquals(RouteSource.SAVED, src)
    }
}
