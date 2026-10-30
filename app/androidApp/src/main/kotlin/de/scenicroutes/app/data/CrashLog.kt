package de.scenicroutes.app.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZoneId
import java.time.ZonedDateTime

/** Absturzbericht nur lokal: letzter Stacktrace in filesDir/crash/last.txt. Nichts wird gesendet. */
object CrashLog {
    fun file(ctx: Context) = File(ctx.filesDir, "crash/last.txt")
    fun install(ctx: Context, version: String) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(ctx, version, t.name, e) }
            prev?.uncaughtException(t, e)
        }
    }
    fun write(ctx: Context, version: String, thread: String, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val f = file(ctx); f.parentFile?.mkdirs()
        f.writeText("Scenic Routes $version · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
            "Zeit: ${ZonedDateTime.now(ZoneId.of("Europe/Berlin"))} · Thread: $thread\n\n$sw")
    }
    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }
}
