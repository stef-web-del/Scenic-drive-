package de.scenicroutes.app.data

import android.content.Context
import androidx.core.content.edit
import de.scenicroutes.app.BuildConfig

class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("scenic", Context.MODE_PRIVATE)
    var backendUrl: String
        get() = p.getString("backend", null) ?: BuildConfig.DEFAULT_BACKEND
        set(v) = p.edit { putString("backend", v.trim()) }
    /** Leer = kein eigener Server: Navigation läuft rein offline, Rückweg/Sperrung nicht verfügbar. */
    val hasBackend get() = backendUrl.isNotBlank()
    /** Server für „Runde ab Punkt“ (nur lokal). Leer = aus, dann gibt es nur Katalog und Raster. */
    var freeLoopUrl: String
        get() = p.getString("freeLoop", null) ?: BuildConfig.FREE_LOOP_URL
        set(v) = p.edit { putString("freeLoop", v.trim()) }
    var lastUpdateCheck: Long
        get() = p.getLong("updCheck", 0)
        set(v) = p.edit { putLong("updCheck", v) }
    private val _nav = kotlinx.coroutines.flow.MutableStateFlow(NavPrefs.read(p))
    /** Navi-Einstellungen als Zustand: Einstellungsseite und Fahrleiste (Stumm-Taste) bleiben so synchron. */
    val navFlow: kotlinx.coroutines.flow.StateFlow<NavPrefs> get() = _nav
    var nav: NavPrefs
        get() = _nav.value
        set(v) { NavPrefs.write(p, v); _nav.value = v }
    fun updateNav(f: (NavPrefs) -> NavPrefs) { nav = f(nav) }
    var voice: Boolean
        get() = nav.voiceOn
        set(v) = updateNav { it.copy(voiceOn = v) }
    /** Rohdaten 50 Hz (Beschleunigung/Gyro) für den Fehlerbericht mitschreiben. Standard aus (Speicher). */
    var rawSensors: Boolean
        get() = p.getBoolean("rawSensors", false)
        set(v) = p.edit { putBoolean("rawSensors", v) }
    var record: Boolean
        get() = p.getBoolean("record", true)
        set(v) = p.edit { putBoolean("record", v) }
}
