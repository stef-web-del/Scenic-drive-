package de.scenicroutes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.scenicroutes.app.data.SaveState
import de.scenicroutes.app.data.UpdateState
import de.scenicroutes.app.ui.UpdateDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.scenicroutes.app.nav.NavigationRoute
import de.scenicroutes.app.ui.LoopDetailScreen
import de.scenicroutes.app.ui.LoopListScreen
import de.scenicroutes.app.ui.ScenicTheme
import de.scenicroutes.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { ScenicTheme { ScenicNavHost() } }
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); handle(intent) }
    private fun handle(i: android.content.Intent?) {
        if (i?.action == de.scenicroutes.app.rec.RecordingService.ACTION_ASK_STOP) { graph.recorder.requestStop(); i.action = null }
        if (i?.getStringExtra("open") == "record") { graph.openRecord.value = true; i.removeExtra("open") }
    }
}

@Composable
fun ScenicNavHost() {
    val nav = rememberNavController()
    val graph = LocalContext.current.graph
    val catalog by graph.catalogFlow.collectAsState()
    val saves by graph.saver.states.collectAsState()
    val upd by graph.updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { graph.updater.check(force = false) }
    val ctx = LocalContext.current
    // Bestätigungs-Intent des PackageInstallers aus der Vordergrund-Activity starten (Android 14+ BAL-Schutz).
    LaunchedEffect(upd) {
        (upd as? UpdateState.Confirm)?.let { c -> graph.updater.confirmLaunched(runCatching { ctx.startActivity(c.intent) }.isSuccess) }
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) graph.updater.onResume() }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    UpdateDialog(upd, onUpdate = {
        val v = (upd as? UpdateState.Available)?.v ?: (upd as? UpdateState.Failed)?.v
        if (v != null) graph.updater.start(v)
    }, onDismiss = { graph.updater.dismiss() },
        onAllow = { runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName))) } },
        onBrowser = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(de.scenicroutes.app.data.Updater.BROWSER_URL))) } })
    RideGlobalUi(onOpenRide = { nav.navigate("ride/$it") }, onOpenRecord = { nav.navigate("record") { launchSingleTop = true } })
    NavHost(nav, startDestination = "list") {
        composable("list") {
            val rec by graph.recorder.state.collectAsState()
            de.scenicroutes.app.ui.MainShell(catalog, saves.filterValues { it is SaveState.Saved }.keys, rec,
                onOpen = { nav.navigate("detail/$it") }, onNavigate = { nav.navigate("nav/$it") }, onRide = { nav.navigate("ride/$it") },
                onRecord = { nav.navigate("record") }, onSettings = { nav.navigate("settings") })
        }
        composable("detail/{id}", listOf(navArgument("id") { type = NavType.IntType })) { e ->
            val loop = graph.loop(e.arguments!!.getInt("id")) ?: return@composable
            val grid = loop.id !in catalog.loops.map { it.id }
            val book by graph.routes.book.collectAsState()
            val mine = book.visible.firstOrNull { it.appLoopId == loop.id || de.scenicroutes.shared.routes.RouteBook.localAppId(it.id) == loop.id }
            val parent = mine?.parentId ?: if (grid) "raster:${loop.id}" else "katalog:${loop.id}"
            val fav = mine?.let { it.id in book.favoriteIds() } ?: book.byParent(parent)?.let { it.id in book.favoriteIds() } ?: false
            LoopDetailScreen(loop, onBack = { nav.popBackStack() }, onStart = { nav.navigate("nav/${loop.id}") },
                favorite = fav, canNavigate = mine?.origin != de.scenicroutes.shared.routes.Origin.IMPORT,
                onFavorite = { if (mine != null) graph.routes.setFavorite(mine.id, !fav) else graph.routes.toggleFavorite(loop, de.scenicroutes.shared.routes.Origin.KATALOG, parent) },
                sbm = catalog.sbm, start = if (grid) loop.geom.first() else catalog.start, family = if (grid) emptyList() else catalog.loops.filter { it.family == loop.family }.sortedByDescending { it.tour },
                onOpen = { nav.navigate("detail/$it") }, offline = saves[loop.id] ?: SaveState.None,
                onSave = { graph.saver.save(loop) }, onRemove = { graph.saver.remove(loop.id) })
        }
        composable("nav/{id}", listOf(navArgument("id") { type = NavType.IntType })) { e ->
            val loop = graph.loop(e.arguments!!.getInt("id")) ?: return@composable
            NavigationRoute(loop, onExit = { nav.popBackStack() })
        }
        composable("abhier") { de.scenicroutes.app.ui.AbHierScreen(onBack = { nav.popBackStack() }, onDetail = { nav.navigate("detail/$it") }, onNavigate = { nav.navigate("nav/$it") }) }
        composable("trips") { de.scenicroutes.app.ui.RidesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("ride/$it") }, onRecord = { nav.navigate("record") }) }
        composable("record") { de.scenicroutes.app.rec.RecordScreen(onBack = { nav.popBackStack() }, onRides = { nav.navigate("trips") }) }
        composable("ride/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
            de.scenicroutes.app.ui.RideDetailScreen(e.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onOpenLoop = { nav.navigate("detail/$it") })
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

/** App-weit: „Fahrt speichern?“ und „Unterbrochene Fahrt gefunden“ – egal auf welchem Bildschirm. */
@Composable
fun RideGlobalUi(onOpenRide: (Long) -> Unit, onOpenRecord: () -> Unit) {
    val ctx = LocalContext.current; val g = ctx.graph; val rec = g.recorder
    val ask by rec.ask.collectAsState()
    val state by rec.state.collectAsState()
    val open by g.openRecord.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(open) { if (open) { g.openRecord.value = false; onOpenRecord() } }
    // Nach App-Start: Dienst wieder anbinden bzw. unterbrochene Fahrt anbieten
    var orphan by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<de.scenicroutes.shared.db.Trip?>(null) }
    LaunchedEffect(Unit) {
        if (rec.recording) de.scenicroutes.app.rec.RecordingService.ensureRunning(ctx)
        else orphan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { rec.unfinished().maxByOrNull { it.startedMs } }.getOrNull() }
    }
    ask?.let { a ->
        de.scenicroutes.app.rec.SaveRideSheet(a,
            onSave = { scope.launch { val id = a.tripId; kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { rec.save(a) }; onOpenRide(id) } },
            onContinue = if (state?.tripId == a.tripId) ({ rec.resume(); de.scenicroutes.app.rec.RecordingService.ensureRunning(ctx) }) else null,
            onDiscard = { scope.launch(kotlinx.coroutines.Dispatchers.IO) { rec.discard(a) } })
    }
    orphan?.takeIf { state == null }?.let { t ->
        de.scenicroutes.app.rec.RecoveryDialog(t.title, t.startedMs,
            onResume = { orphan = null; scope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { rec.resumeFrom(t) }; de.scenicroutes.app.rec.RecordingService.ensureRunning(ctx); onOpenRecord() } },
            onSave = { orphan = null; scope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { rec.save(rec.askFor(t.id, t.title, t.loopId?.toInt())) }; onOpenRide(t.id) } },
            onDiscard = { orphan = null; scope.launch(kotlinx.coroutines.Dispatchers.IO) { rec.discard(rec.askFor(t.id, t.title, t.loopId?.toInt())) } })
    }
}
