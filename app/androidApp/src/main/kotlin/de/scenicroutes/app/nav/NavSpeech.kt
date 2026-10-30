package de.scenicroutes.app.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import de.scenicroutes.app.data.NavPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uniffi.ferrostar.SpokenInstruction
import java.util.Locale

/**
 * Sprachausgabe der Navigation (ersetzt Ferrostars AndroidTtsObserver, der weder Stimme noch Tempo,
 * Tonhöhe oder Lautstärke kennt und die TTS-Ausgabe nicht als Navigationsansage markiert).
 * - Ausgabe als USAGE_ASSISTANCE_NAVIGATION_GUIDANCE / CONTENT_TYPE_SPEECH: Android und Auto-Bluetooth
 *   behandeln sie als Navi-Ansage (eigene Lautstärke-/Routing-Regeln).
 * - Optional Audio-Focus „GAIN_TRANSIENT_MAY_DUCK“: Musik wird während der Ansage leiser, danach wieder freigegeben.
 * - Stimme, Engine, Tempo, Tonhöhe, Lautstärke aus [NavPrefs]; Engine-Wechsel startet die TTS neu.
 */
class NavSpeech(ctx: Context, prefs: NavPrefs) : SpokenInstructionObserver {
    private val app = ctx.applicationContext
    private val am = app.getSystemService(AudioManager::class.java)
    private var prefs = prefs
    private var tts: TextToSpeech? = null
    private var engineInUse: String? = null
    @Volatile private var ready = false
    private val gate = de.scenicroutes.shared.nav.SpeechGate()
    /** Ansagen in der TTS-Warteschlange (id → Ansage), um überalterte beim Start zu verwerfen. */
    private val queued = java.util.concurrent.ConcurrentHashMap<String, de.scenicroutes.shared.nav.SpeechGate.Say>()
    /** Aktueller Ferrostar-Schritt (setzt das NavViewModel). */
    var stepIndex: () -> Int? = { null }
    /** Protokoll-Haken: (Ereignis, Felder) – setzt das NavViewModel. */
    var log: (String, Map<String, Any?>) -> Unit = { _, _ -> }
    private val _mute = MutableStateFlow(false)
    private val _ready = MutableStateFlow(false)
    /** true, sobald die TTS bereit ist (für Stimmenliste in den Einstellungen). */
    val readyState: StateFlow<Boolean> get() = _ready

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(ATTRS).setOnAudioFocusChangeListener { }.build()

    fun start() { if (tts == null) open() }

    private fun open() {
        ready = false; _ready.value = false
        engineInUse = prefs.engine
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true; configure(); _ready.value = true
                gate.takePending(System.currentTimeMillis())?.let { say(it) }
            }
        }
        tts = (if (prefs.engine != null) TextToSpeech(app, listener, prefs.engine) else TextToSpeech(app, listener)).also {
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    val q = id?.let { queued.remove(it) } ?: return
                    val now = System.currentTimeMillis()
                    if (!gate.fresh(q, now)) {
                        // Überaltert (z. B. hinter einer langen Ansage gewartet): abbrechen, neueste frische nachsprechen.
                        log("speech_drop_stale", mapOf("text" to q.text, "ageMs" to now - q.atMs))
                        val next = queued.values.filter { gate.fresh(it, now) }.maxByOrNull { it.atMs }
                        queued.clear(); tts?.stop(); next?.let { say(it.copy(flush = true)) }
                    } else log("speech_spoken", mapOf("text" to q.text, "delayMs" to now - q.atMs))
                }
                override fun onDone(id: String?) = release()
                @Deprecated("API") override fun onError(id: String?) = release()
                override fun onError(id: String?, code: Int) = release()
            })
        }
    }

    private fun configure() {
        val t = tts ?: return
        t.setAudioAttributes(ATTRS)
        t.language = Locale.GERMANY
        prefs.voiceName?.let { n -> runCatching { t.voices }.getOrNull()?.firstOrNull { it.name == n }?.let { t.voice = it } }
        t.setSpeechRate(prefs.rate); t.setPitch(prefs.pitch)
    }

    /** Neue Einstellungen übernehmen (ohne Neustart, außer die Engine wechselt). */
    fun apply(p: NavPrefs) {
        val engineChanged = p.engine != engineInUse
        prefs = p
        if (engineChanged && tts != null) { tts?.shutdown(); tts = null; open() } else if (ready) configure()
    }

    /** Deutsche Stimmen der laufenden Engine, offline zuerst. */
    fun germanVoices(): List<Voice> = runCatching { tts?.voices.orEmpty() }.getOrDefault(emptySet())
        .filter { it.locale.language == "de" }.sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))

    fun engines(): List<TextToSpeech.EngineInfo> = runCatching { tts?.engines.orEmpty() }.getOrDefault(emptyList())

    /** Direkt sprechen (Test-Ansage in den Einstellungen): ersetzt, was gerade läuft. */
    fun speak(text: String, id: String) = say(de.scenicroutes.shared.nav.SpeechGate.Say(text, id, true, System.currentTimeMillis()))

    private fun say(s: de.scenicroutes.shared.nav.SpeechGate.Say) {
        val t = tts
        if (t == null || !ready) { gate.hold(s); return }
        if (prefs.duck) am.requestAudioFocus(focus)
        val b = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.volume) }
        if (s.flush) queued.clear()
        queued[s.id] = s
        if (t.speak(s.text, if (s.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, b, s.id) != TextToSpeech.SUCCESS) { queued.remove(s.id); release() }
    }

    /** Mehrere Schritte werden gleich weitergeschaltet: nur die letzte Ansage sprechen. */
    fun beginBatch() = gate.beginBatch()
    fun endBatch() {
        val before = gate.dropped
        gate.endBatch()?.let { if (!isMuted) say(it) }
        if (gate.dropped > before) log("speech_batch_dropped", mapOf("count" to gate.dropped - before))
    }

    private fun release() { if (tts?.isSpeaking != true) am.abandonAudioFocusRequest(focus) }

    override fun onSpokenInstructionTrigger(spokenInstruction: SpokenInstruction) {
        val now = System.currentTimeMillis(); val step = stepIndex()
        log("speech_triggered", mapOf("text" to spokenInstruction.text, "step" to step))
        if (isMuted) return
        gate.offer(spokenInstruction.text, spokenInstruction.utteranceId.toString(), step, now)?.let { say(it) }
    }
    override fun stopAndClearQueue() { gate.clear(); queued.clear(); tts?.stop(); am.abandonAudioFocusRequest(focus) }
    override fun setMuted(isMuted: Boolean) { _mute.value = isMuted; if (isMuted) stopAndClearQueue() }
    override val muteState: StateFlow<Boolean> get() = _mute

    fun shutdown() { stopAndClearQueue(); tts?.shutdown(); tts = null; ready = false; _ready.value = false }

    companion object {
        val ATTRS: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        const val TEST_TEXT = "In 300 Metern rechts abbiegen auf die Hauptstraße. Das ist eine Test-Ansage."
    }
}
