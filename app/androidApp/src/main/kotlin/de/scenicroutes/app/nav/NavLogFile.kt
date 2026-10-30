package de.scenicroutes.app.nav

import android.content.Context
import de.scenicroutes.shared.nav.NavLog
import java.io.File
import java.util.concurrent.Executors

/** Navigationsprotokoll: filesDir/navlog/JJJJ-MM-TT.jsonl, Schreiben im Hintergrund. Nur lokal, nur für den Fehlerbericht. */
object NavLogFile {
    private val io = Executors.newSingleThreadExecutor()
    private const val KEEP_DAYS = 14

    fun dir(ctx: Context) = File(ctx.filesDir, "navlog")

    fun log(ctx: Context, event: String, fields: Map<String, Any?> = emptyMap()) {
        val t = System.currentTimeMillis(); val line = NavLog.line(t, event, fields)
        val app = ctx.applicationContext
        io.execute {
            runCatching {
                val d = dir(app).apply { mkdirs() }
                File(d, day(t) + ".jsonl").appendText(line + "\n")
                d.listFiles()?.filter { it.lastModified() < t - KEEP_DAYS * 86_400_000L }?.forEach { it.delete() }
            }
        }
    }

    /** Zeilen im Zeitraum [fromMs, toMs]. */
    fun lines(ctx: Context, fromMs: Long, toMs: Long): List<String> =
        (dir(ctx).listFiles()?.sortedBy { it.name } ?: emptyList()).flatMap { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).filter { l -> NavLog.timeOf(l)?.let { it in fromMs..toMs } == true }
        }

    fun day(t: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(t))
}
