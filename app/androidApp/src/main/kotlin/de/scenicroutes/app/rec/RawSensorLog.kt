package de.scenicroutes.app.rec

import android.content.Context
import java.io.File

/** 50-Hz-Rohdaten (nur mit Schalter in den Einstellungen): filesDir/raw/JJJJ-MM-TT.csv, 3 Tage aufbewahrt. */
object RawSensorLog {
    const val HEADER = "t_ms,sensor,x,y,z"
    private const val KEEP_DAYS = 3
    private var out: java.io.BufferedWriter? = null
    private var outDay: String? = null

    fun dir(ctx: Context) = File(ctx.filesDir, "raw")

    /** Vom Sensor-Thread aufgerufen. */
    @Synchronized fun write(ctx: Context, tMs: Long, type: Char, x: Float, y: Float, z: Float) {
        val day = de.scenicroutes.app.nav.NavLogFile.day(tMs)
        if (day != outDay) {
            close(); outDay = day
            val d = dir(ctx).apply { mkdirs() }
            d.listFiles()?.filter { it.lastModified() < tMs - KEEP_DAYS * 86_400_000L }?.forEach { it.delete() }
            out = runCatching { java.io.BufferedWriter(java.io.FileWriter(File(d, "$day.csv"), true), 64 * 1024) }.getOrNull()
        }
        runCatching { out?.write("$tMs,$type,$x,$y,$z\n") }
    }

    @Synchronized fun close() { runCatching { out?.close() }; out = null; outDay = null }

    fun lines(ctx: Context, fromMs: Long, toMs: Long): List<String> {
        close() // gepufferte Zeilen schreiben
        return (dir(ctx).listFiles()?.sortedBy { it.name } ?: emptyList()).flatMap { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).filter { l -> l.substringBefore(',').toLongOrNull()?.let { it in fromMs..toMs } == true }
        }
    }
}
