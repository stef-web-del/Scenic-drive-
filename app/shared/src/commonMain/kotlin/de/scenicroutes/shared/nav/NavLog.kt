package de.scenicroutes.shared.nav

/** Navigationsprotokoll (navlog.jsonl): eine JSON-Zeile je Ereignis. Formatierung hier, Datei in der App. */
object NavLog {
    fun line(tMs: Long, event: String, fields: Map<String, Any?> = emptyMap()): String = buildString {
        append("{\"t\":").append(tMs).append(",\"ev\":").append(str(event))
        for ((k, v) in fields) { if (v == null) continue; append(',').append(str(k)).append(':').append(value(v)) }
        append('}')
    }
    private fun value(v: Any): String = when (v) {
        is Boolean -> v.toString()
        is Int, is Long -> v.toString()
        is Double -> if (v.isFinite()) (kotlin.math.round(v * 100) / 100).toString() else "null"
        is Float -> value(v.toDouble())
        else -> str(v.toString())
    }
    fun str(s: String) = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\""); c == '\\' -> append("\\\\"); c == '\n' -> append("\\n")
            c == '\r' -> append("\\r"); c == '\t' -> append("\\t")
            c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
        append('"')
    }
    /** Zeitstempel einer Zeile (für das Filtern auf die Fahrt-Zeitspanne). */
    fun timeOf(line: String): Long? = Regex("^\\{\"t\":(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
}
