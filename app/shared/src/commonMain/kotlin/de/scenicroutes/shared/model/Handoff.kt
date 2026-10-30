package de.scenicroutes.shared.model

/** Losfahren-Übergabe + GPX – gleiche Logik wie webapp/src/lib/nav.ts bzw. data.ts. */
object Handoff {
    const val GOOGLE_MAX_WP = 9
    private fun r6(x: Double) = (kotlin.math.round(x * 1e6) / 1e6).toString().removeSuffix(".0")
    private fun c(p: List<Double>) = "${r6(p[1])},${r6(p[0])}"
    private fun enc(s: String) = s.replace("%", "%25").replace(",", "%2C").replace("|", "%7C").replace(" ", "%20")
    /** Google Maps URLs (api=1): Ziel = Start der Runde, Ursprung = aktueller Standort, max. 9 Wegpunkte. */
    fun googleUrl(loop: Loop, start: List<Double> = loop.geom.first()): String {
        val wp = loop.gmaps.take(GOOGLE_MAX_WP)
        val dest = start
        return "https://www.google.com/maps/dir/?api=1&destination=${enc(c(dest))}" +
            (if (wp.isNotEmpty()) "&waypoints=" + enc(wp.joinToString("|") { c(it) }) else "") + "&travelmode=driving&dir_action=navigate"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun gpx(l: Loop): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="Scenic Routes Miltenberg" xmlns="http://www.topografix.com/GPX/1/1">""")
        append("<metadata><name>").append(esc(l.title)).append("</name><desc>").append(esc(l.text)).append("</desc>")
        append("""<copyright author="OpenStreetMap-Mitwirkende"><license>https://opendatacommons.org/licenses/odbl/</license></copyright></metadata>""")
        l.pauses.forEach { p -> append("""<wpt lat="${p.ll[1]}" lon="${p.ll[0]}"><name>""").append(esc(p.name)).append("</name></wpt>") }
        if (l.gmaps.isNotEmpty()) {
            val rp = listOf(l.geom.first()) + l.gmaps + listOf(l.geom.last())
            append("<rte><name>Runde ${l.id} (Lenkpunkte)</name>")
            rp.forEachIndexed { i, p -> append("""<rtept lat="${p[1]}" lon="${p[0]}"><name>${if (i == 0) "Start" else if (i == rp.lastIndex) "Ziel" else "Lenkpunkt $i"}</name></rtept>""") }
            append("</rte>")
        }
        append("<trk><name>Runde ${l.id}</name><trkseg>")
        l.geom.forEach { append("""<trkpt lat="${it[1]}" lon="${it[0]}"/>""") }
        append("</trkseg></trk></gpx>")
    }
}
