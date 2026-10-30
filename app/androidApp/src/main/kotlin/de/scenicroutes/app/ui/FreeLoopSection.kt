package de.scenicroutes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.scenicroutes.app.graph
import de.scenicroutes.app.data.FreeLoopApi
import de.scenicroutes.app.data.FreeLoopFiles
import de.scenicroutes.shared.freeloop.FreeLoop
import de.scenicroutes.shared.freeloop.JobView
import de.scenicroutes.shared.freeloop.summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE = 6

/**
 * „Runde ab Punkt“ vom lokalen Server: Suche starten, Fortschritt zeigen, erste Vorschläge sofort,
 * je 6 weitere über „Mehr anzeigen“, Abbrechen, Navi-Datei holen. Ohne Server bleiben die fertigen Runden darüber.
 */
@Composable
fun FreeLoopSection(api: FreeLoopApi?, start: Pair<Double, Double>?, startName: String?, modifier: Modifier = Modifier, onNavigate: (Int) -> Unit = {}) {
    if (api == null || start == null) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember(start) { mutableStateOf<String?>(null) }
    var view by remember(start) { mutableStateOf<JobView?>(null) }
    var limit by remember(start) { mutableIntStateOf(PAGE) }
    var offline by remember(start) { mutableStateOf(false) }
    var navMsg by remember(start) { mutableStateOf<String?>(null) }
    val ready = remember(start) { mutableStateMapOf<Int, Int>() }   // Server-ID -> App-ID mit fertiger Navi-Datei
    val graph = ctx.graph

    // Pollen: solange gesucht wird alle 0,7 s, danach nur noch bei „Mehr anzeigen“.
    LaunchedEffect(job, limit) {
        val j = job ?: return@LaunchedEffect
        while (true) {
            val v = try { api.view(j, limit) } catch (e: CancellationException) { throw e } catch (_: Exception) { offline = true; job = null; return@LaunchedEffect }
            view = v
            if (!v.running) break
            delay(700)
        }
    }

    Card(modifier.fillMaxWidth().testTag("freeLoops")) {
        Column(Modifier.padding(Tokens.spaceLg), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
            Text("Neue Runde ab ${startName ?: "hier"}", style = MaterialTheme.typography.titleSmall)
            val v = view
            when {
                offline -> Text("Server nicht erreichbar. Die fertigen Runden oben funktionieren weiter.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("freeLoopsOffline"))
                v == null && job == null -> Text("Sucht kurvige Runden mit 35 bis 50 Minuten Fahrzeit, ohne Orte und Bundesstraßen. Anfahrt und Rückweg werden extra angezeigt.",
                    style = MaterialTheme.typography.bodySmall)
                v == null -> Text("Suche startet …", style = MaterialTheme.typography.bodySmall)
            }
            if (v != null && v.running) {
                LinearProgressIndicator(progress = { v.fraction }, modifier = Modifier.fillMaxWidth().testTag("freeLoopsProgress"))
                Text("${v.progress.phase} · ${v.progress.tried} von ${v.progress.total} Kandidaten · ${v.count} gefunden",
                    style = MaterialTheme.typography.bodySmall)
            } else if (v != null) {
                Text(when { v.status == "cancelled" -> "Abgebrochen, ${v.count} Runden gefunden."; v.error != null -> "Fehler: ${v.error}"
                    v.count == 0 -> "Keine passende Runde gefunden."; else -> "${v.count} Runden gefunden." }, style = MaterialTheme.typography.bodySmall)
            }
            v?.loops?.forEach { l -> FreeLoopCard(l, ready[l.id], onNavigate) {
                scope.launch {
                    navMsg = "Navi-Datei wird erstellt …"
                    navMsg = runCatching {
                        val id = FreeLoopFiles.appId(v.id, l.id)
                        val f = FreeLoopFiles.save(ctx, id, api.nav(v.id, l.id))
                        graph.gridLoops[id] = FreeLoopFiles.toLoop(id, l, api.geometry(v.id, l.id), startName ?: "hier")
                        ready[l.id] = id
                        "Navi-Datei gespeichert (${f.length() / 1024} KB)"
                    }.getOrElse { "Navi-Datei nicht verfügbar: ${it.message}" }
                }
            } }
            navMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("freeLoopsNav")) }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                if (job == null || (v != null && !v.running)) {
                    if (v != null && v.more) OutlinedButton(onClick = { limit += PAGE }, Modifier.testTag("freeLoopsMore")) { Text("Mehr anzeigen") }
                    if (v == null || offline) Button(onClick = {
                        offline = false; view = null; limit = PAGE
                        scope.launch { runCatching { api.start(start.first, start.second, startName ?: "Start") }.onSuccess { job = it }.onFailure { offline = true } }
                    }, Modifier.testTag("freeLoopsStart")) { Text("Runden suchen") }
                } else {
                    if (v != null && v.more) OutlinedButton(onClick = { limit += PAGE }, Modifier.testTag("freeLoopsMore")) { Text("Mehr anzeigen") }
                    TextButton(onClick = { val j = job; scope.launch { if (j != null) { api.cancel(j); view = runCatching { api.view(j, limit) }.getOrNull() ?: view; job = null } } },
                        Modifier.testTag("freeLoopsCancel")) { Text("Abbrechen") }
                }
            }
        }
    }
}

@Composable
private fun FreeLoopCard(l: FreeLoop, appId: Int?, onNavigate: (Int) -> Unit, onNav: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("freeLoop${l.rang}")) {
        Column(Modifier.padding(Tokens.spaceMd), verticalArrangement = Arrangement.spacedBy(Tokens.spaceXs)) {
            Text("${l.rang}. ${l.einstieg.ifBlank { "ab Start" }}", style = MaterialTheme.typography.labelLarge)
            Text(l.summary(), style = MaterialTheme.typography.bodyMedium)
            if (l.tags.isNotEmpty()) Text(l.tags.take(4).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text("Insgesamt etwa ${l.totalMin} min", style = MaterialTheme.typography.bodySmall)
            if (appId == null) TextButton(onClick = onNav, contentPadding = PaddingValues(Tokens.spaceNone)) { Text("Navi-Datei holen") }
            else Button(onClick = { onNavigate(appId) }, Modifier.testTag("freeNav${l.rang}")) { Text("Navigation starten") }
        }
    }
}
