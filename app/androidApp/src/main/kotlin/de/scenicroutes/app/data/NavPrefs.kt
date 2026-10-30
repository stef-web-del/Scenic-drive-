package de.scenicroutes.app.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Kartenausrichtung im Fahrmodus. */
enum class MapOrientation(val label: String) { HEADING("Fahrtrichtung"), NORTH("Norden oben") }

/** Zoom im Fahrmodus. [delta] wird auf Ferrostars Standard-Zoom (Auto) addiert; AUTO richtet sich nach dem Tempo. */
enum class NavZoom(val label: String, val delta: Double) { NEAR("Nah", 1.0), NORMAL("Normal", 0.0), FAR("Weit", -1.0), AUTO("Auto (Tempo)", 0.0) }

/** Hell/Dunkel im Fahrmodus. AUTO = nach Sonnenstand am Start der Runde (Verhalten bis 0.6.0). */
enum class NavTheme(val label: String) { AUTO("Auto (Sonnenstand)"), LIGHT("Hell"), DARK("Dunkel") }

/**
 * Alle Navi-Einstellungen. Die Standardwerte entsprechen genau dem Verhalten von 0.6.0
 * (System-Stimme, Tempo/Tonhöhe/Lautstärke 1,0, Fahrtrichtung oben, Ferrostar-Zoom, Tag/Nacht nach Sonne,
 * Fahrleiste mit Ankunft, Rest-km und Rest-min, ohne Tempo).
 */
data class NavPrefs(
    val voiceOn: Boolean = true,
    /** Paketname der TTS-Engine, null = Systemstandard. */
    val engine: String? = null,
    /** Name der Stimme (android.speech.tts.Voice.name), null = Standardstimme der Engine für Deutsch. */
    val voiceName: String? = null,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    /** Andere Audioquellen (Musik) während einer Ansage leiser stellen. */
    val duck: Boolean = true,
    val orientation: MapOrientation = MapOrientation.HEADING,
    val zoom: NavZoom = NavZoom.NORMAL,
    val theme: NavTheme = NavTheme.AUTO,
    val showEta: Boolean = true,
    val showKm: Boolean = true,
    val showMin: Boolean = true,
    val showSpeed: Boolean = false,
    /** Fortschritt der Runde (Balken + %), nur die Runde (Anfahrt = 0 %). Seit 0.6.3 standardmäßig an (nur wenn nie selbst gesetzt). */
    val showProgress: Boolean = true,
    /** Neu in 0.6.3, standardmäßig aus: verbleibende Kurven, Höhe (DGM), gewonnene Höhenmeter, g-Werte. */
    val showCurves: Boolean = false,
    val showElevation: Boolean = false,
    val showAscent: Boolean = false,
    val showG: Boolean = false,
) {
    companion object {
        const val RATE_MIN = 0.5f; const val RATE_MAX = 2.0f
        const val PITCH_MIN = 0.5f; const val PITCH_MAX = 2.0f
        const val VOLUME_MIN = 0.1f; const val VOLUME_MAX = 1.0f

        /** Zoom-Abschlag nach Tempo für AUTO: Stadt nah, Landstraße normal, schnell weit. */
        fun autoZoomDelta(speedMps: Double?): Double = when {
            speedMps == null -> 0.0
            speedMps < 50 / 3.6 -> 0.5
            speedMps < 90 / 3.6 -> 0.0
            else -> -1.0
        }

        fun zoomDelta(z: NavZoom, speedMps: Double?) = if (z == NavZoom.AUTO) autoZoomDelta(speedMps) else z.delta

        fun dark(t: NavTheme, sunDark: Boolean) = when (t) { NavTheme.AUTO -> sunDark; NavTheme.LIGHT -> false; NavTheme.DARK -> true }

        /** Kennzahlen der Fahrleiste in fester Reihenfolge; die erste ist die große Hauptzahl. Nur echte Werte, nichts geschätzt. */
        fun kpis(p: NavPrefs, eta: String, km: Double, seconds: Double, speedKmh: Int?, progress: Float?): List<Kpi> =
            kpis(p, DriveData(eta, km, seconds, speedKmh, progress))

        fun kpis(p: NavPrefs, d: DriveData): List<Kpi> = buildList {
            if (p.showEta) add(Kpi("eta", d.eta, "", "Ankunft"))
            if (p.showMin) add(duration(d.seconds))
            if (p.showKm) add(Kpi("km", kmText(d.km), "km", "Rest"))
            if (p.showProgress && d.progress != null) add(Kpi("progress", "${(d.progress * 100).toInt()}", "%", "Runde"))
            if (p.showSpeed && d.speedKmh != null) add(Kpi("speed", "${d.speedKmh}", "km/h", "Tempo"))
            if (p.showCurves && d.curves != null) add(if ((d.hairpins ?: 0) > 0) Kpi("curves", "${d.curves}", "Kurven", "${d.hairpins} Kehren") else Kpi("curves", "${d.curves}", "", "Kurven"))
            if (p.showElevation && d.elevationM != null) add(Kpi("elevation", "${d.elevationM}", "m", "Höhe"))
            if (p.showAscent && d.ascentM != null) add(Kpi("ascent", "+${d.ascentM}", "m", "Anstieg"))
            if (p.showG && d.g != null) add(Kpi("g", gText(d.g), "g", d.gMax?.let { "max ${gText(it)}" } ?: "Querkraft"))
        }

        fun gText(g: Double): String = String.format(java.util.Locale.GERMANY, "%.1f", g)

        fun duration(seconds: Double): Kpi {
            val m = Math.round(seconds / 60).toInt().coerceAtLeast(0)
            return if (m < 60) Kpi("min", "$m", "min", "Restzeit") else Kpi("min", "${m / 60}:${"%02d".format(m % 60)}", "h", "Restzeit")
        }

        fun kmText(km: Double): String = if (km < 10) String.format(java.util.Locale.GERMANY, "%.1f", km) else "${Math.round(km)}"

        /** Anteil der gefahrenen Strecke, 0..1; null ohne Gesamtlänge. */
        fun progress(totalM: Double?, remainingM: Double): Float? =
            totalM?.takeIf { it > 0 }?.let { (1 - remainingM / it).coerceIn(0.0, 1.0).toFloat() }

        private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, def: E): E =
            getString(key, null)?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: def

        /** Liest mit Rückfall auf Standardwerte; unbekannte/fehlende Schlüssel (Upgrade von 0.6.0) = Standard. */
        fun read(p: SharedPreferences) = NavPrefs(
            voiceOn = p.getBoolean("voice", true), // Schlüssel aus 0.6.0 unverändert
            engine = p.getString("navEngine", null), voiceName = p.getString("navVoice", null),
            rate = p.getFloat("navRate", 1f).coerceIn(RATE_MIN, RATE_MAX),
            pitch = p.getFloat("navPitch", 1f).coerceIn(PITCH_MIN, PITCH_MAX),
            volume = p.getFloat("navVolume", 1f).coerceIn(VOLUME_MIN, VOLUME_MAX),
            duck = p.getBoolean("navDuck", true),
            orientation = p.enum("navOrient", MapOrientation.HEADING), zoom = p.enum("navZoom", NavZoom.NORMAL), theme = p.enum("navTheme", NavTheme.AUTO),
            showEta = p.getBoolean("barEta", true), showKm = p.getBoolean("barKm", true), showMin = p.getBoolean("barMin", true), showSpeed = p.getBoolean("barSpeed", false),
            showProgress = p.getBoolean("barProgress", true), // fehlt der Schlüssel (nie gesetzt), gilt der neue Standard
            showCurves = p.getBoolean("barCurves", false), showElevation = p.getBoolean("barElevation", false),
            showAscent = p.getBoolean("barAscent", false), showG = p.getBoolean("barG", false),
        )

        fun write(p: SharedPreferences, n: NavPrefs) = p.edit {
            putBoolean("voice", n.voiceOn); putString("navEngine", n.engine); putString("navVoice", n.voiceName)
            putFloat("navRate", n.rate); putFloat("navPitch", n.pitch); putFloat("navVolume", n.volume); putBoolean("navDuck", n.duck)
            putString("navOrient", n.orientation.name); putString("navZoom", n.zoom.name); putString("navTheme", n.theme.name)
            putBoolean("barEta", n.showEta); putBoolean("barKm", n.showKm); putBoolean("barMin", n.showMin); putBoolean("barSpeed", n.showSpeed); putBoolean("barProgress", n.showProgress)
            putBoolean("barCurves", n.showCurves); putBoolean("barElevation", n.showElevation); putBoolean("barAscent", n.showAscent); putBoolean("barG", n.showG)
        }
    }
}

/** Messwerte für die Fahrleiste; null = keine verlässlichen Daten, die Kennzahl wird dann ausgeblendet. */
data class DriveData(
    val eta: String, val km: Double, val seconds: Double, val speedKmh: Int? = null, val progress: Float? = null,
    val curves: Int? = null, val hairpins: Int? = null, val elevationM: Int? = null, val ascentM: Int? = null, val g: Double? = null, val gMax: Double? = null,
)

/** Eine Kennzahl der Fahrleiste: Wert, Einheit (darf leer sein), Beschriftung. */
data class Kpi(val key: String, val value: String, val unit: String, val label: String)

/** Luftlinie zwischen zwei Punkten in Metern (Haversine). */
fun geoDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0; val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}
