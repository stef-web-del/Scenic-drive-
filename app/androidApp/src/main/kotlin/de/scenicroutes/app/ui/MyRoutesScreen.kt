package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Share
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.scenicroutes.app.graph
import de.scenicroutes.shared.routes.Gpx
import de.scenicroutes.shared.routes.MyRoute
import de.scenicroutes.shared.routes.Origin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reiter in „Meine Routen“. */
enum class RouteTab(val label: String) { FAVORITEN("Favoriten"), GESPEICHERT("Gespeichert"), EIGENE("Eigene") }

/**
 * Tab „Meine Routen“: Favoriten, gespeicherte Vorschläge und eigene (importierte) Strecken.
 * Umbenennen, Löschen, GPX-Export und -Import. Alles liegt lokal; das Datenmodell ist für ein späteres Konto vorbereitet.
 */
@Composable
fun MyRoutesScreen(onOpen: (Int) -> Unit, onNavigate: (Int) -> Unit, snack: SnackbarHostState, importRequest: Int = 0) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val book by g.routes.book.collectAsState()
    var tab by rememberSaveable { mutableStateOf(RouteTab.FAVORITEN) }
    var renaming by remember { mutableStateOf<MyRoute?>(null) }
    var deleting by remember { mutableStateOf<MyRoute?>(null) }
    var exporting by remember { mutableStateOf<MyRoute?>(null) }
    val favs = book.favoriteIds()
    val all = book.visible
    val list = when (tab) {
        RouteTab.FAVORITEN -> all.filter { it.id in favs }
        RouteTab.GESPEICHERT -> all.filter { it.origin == Origin.KATALOG || it.origin == Origin.SERVER }
        RouteTab.EIGENE -> all.filter { it.origin == Origin.IMPORT || it.origin == Origin.GERAET }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val r = exporting; exporting = null
        if (uri != null && r != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Gpx.export(r).toByteArray()) } }.isSuccess }
            snack.showSnackbar(if (ok) "GPX gespeichert" else "GPX konnte nicht geschrieben werden")
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching {
                val xml = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                require(xml.length < 20_000_000) { "Datei zu groß" }
                g.routes.importGpx(xml, "Importierte Strecke").also { r -> g.routes.loopOf(r).let { g.gridLoops[it.id] = it } }
            } }
            r.onSuccess { tab = RouteTab.EIGENE; snack.showSnackbar("„${it.name}“ importiert") }
                .onFailure { snack.showSnackbar("Keine Strecke in der Datei gefunden") }
        }
    }
    LaunchedEffect(importRequest) { if (importRequest > 0) runCatching { import.launch(arrayOf("application/gpx+xml", "application/octet-stream", "text/xml", "application/xml", "*/*")) } }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Tokens.spaceLg, vertical = Tokens.spaceSm)) {
            RouteTab.entries.forEachIndexed { i, t ->
                SegmentedButton(selected = tab == t, onClick = { tab = t }, shape = SegmentedButtonDefaults.itemShape(i, RouteTab.entries.size), modifier = Modifier.testTag("routes_${t.name}")) {
                    Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (list.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Tokens.spaceXxxl).testTag("routesEmpty"), verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm)) {
                Icon(when (tab) { RouteTab.FAVORITEN -> Lucide.Star; RouteTab.GESPEICHERT -> Lucide.Bookmark; RouteTab.EIGENE -> Lucide.FileUp }, null, Modifier.size(Tokens.sizeIconXl), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when (tab) { RouteTab.FAVORITEN -> "Noch keine Favoriten"; RouteTab.GESPEICHERT -> "Noch nichts gespeichert"; RouteTab.EIGENE -> "Noch keine eigenen Strecken" }, style = MaterialTheme.typography.titleMedium)
                Text(when (tab) {
                    RouteTab.FAVORITEN -> "Tipp in einer Runde auf den Stern. Favoriten bleiben auch ohne Netz hier."
                    RouteTab.GESPEICHERT -> "Vorschläge aus „Planen“ landen hier, sobald du sie speicherst oder losfährst."
                    RouteTab.EIGENE -> "Importier eine GPX-Datei, zum Beispiel aus einer anderen App."
                }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (tab == RouteTab.EIGENE) OutlinedButton(onClick = { runCatching { import.launch(arrayOf("*/*")) } }) { Icon(Lucide.FolderOpen, null); Spacer(Modifier.size(Tokens.spaceSm)); Text("GPX importieren") }
            }
        } else LazyColumn(Modifier.fillMaxSize().testTag("routesList"), contentPadding = PaddingValues(Tokens.spaceLg, Tokens.spaceXs, Tokens.spaceLg, Tokens.spaceXxl), verticalArrangement = Arrangement.spacedBy(Tokens.spaceMd)) {
            items(list, key = { it.id }) { r ->
                MyRouteCard(r, r.id in favs,
                    onClick = { val l = g.routes.loopOf(r); if (g.loop(l.id) == null) g.gridLoops[l.id] = l; onOpen(l.id) },
                    onFav = { scope.launch(Dispatchers.IO) { g.routes.setFavorite(r.id, r.id !in favs) } },
                    onRename = { renaming = r }, onDelete = { deleting = r },
                    onExport = { exporting = r; runCatching { export.launch(fileName(r.name)) } },
                    onNav = if (r.origin == Origin.IMPORT) null else ({ val l = g.routes.loopOf(r); if (g.loop(l.id) == null) g.gridLoops[l.id] = l; onNavigate(l.id) }))
            }
        }
    }

    renaming?.let { r ->
        var name by remember(r.id) { mutableStateOf(r.name) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Umbenennen") },
            text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().testTag("renameField")) },
            confirmButton = { TextButton(onClick = { renaming = null; scope.launch(Dispatchers.IO) { g.routes.rename(r.id, name) } }, enabled = name.isNotBlank(), modifier = Modifier.testTag("renameOk")) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Abbrechen") } })
    }
    deleting?.let { r ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("„${r.name}“ löschen?") },
            text = { Text("Die Strecke verschwindet aus „Meine Routen“. Gefahrene Fahrten bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch(Dispatchers.IO) { g.routes.delete(r.id) } }, modifier = Modifier.testTag("deleteOk")) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } })
    }
}

private fun fileName(n: String) = n.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß _-]"), "").trim().ifBlank { "Runde" }.take(60) + ".gpx"

private fun originLabel(r: MyRoute) = when (r.origin) {
    Origin.KATALOG -> "Aus dem Katalog"; Origin.SERVER -> "Neu berechnet"; Origin.IMPORT -> "GPX-Import · nur Ansicht"; else -> "Auf dem Gerät"
}

@Composable
private fun MyRouteCard(r: MyRoute, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit, onNav: (() -> Unit)?) {
    val g = LocalContext.current.graph
    val loop = remember(r.id, r.updatedMs) { g.routes.loopOf(r) }
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("route_${r.name}")) {
        Row(Modifier.padding(Tokens.spaceMd), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Tokens.radiusLg), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(Tokens.sizeThumbSm)) { RouteShape(loop, Modifier.fillMaxSize()) }
            Column(Modifier.padding(start = Tokens.spaceMd).weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${fmtKm(r.lengthKm)} · ${fmtMin(r.rideMin)}" + (if (r.hm > 0) " · ${r.hm} Hm" else ""), style = MaterialTheme.typography.bodySmall)
                Text(originLabel(r), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onNav != null) TextButton(onClick = onNav, contentPadding = PaddingValues(Tokens.spaceNone)) { Icon(Lucide.Navigation, null, Modifier.size(Tokens.sizeIconSm)); Spacer(Modifier.size(Tokens.spaceSm)); Text("Losfahren") }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFav, modifier = Modifier.testTag("fav_${r.name}")) {
                    Icon(if (fav) Lucide.Star else Lucide.Star, if (fav) "Favorit entfernen" else "Als Favorit merken", tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("menu_${r.name}")) { Icon(Lucide.EllipsisVertical, "Mehr") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Lucide.Pencil, null) }, onClick = { menu = false; onRename() }, modifier = Modifier.testTag("rename"))
                        DropdownMenuItem(text = { Text("Als GPX exportieren") }, leadingIcon = { Icon(Lucide.Share, null) }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Lucide.Trash2, null) }, onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("delete"))
                    }
                }
            }
        }
    }
}
