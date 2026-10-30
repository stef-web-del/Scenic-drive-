package de.scenicroutes.shared.nav

/**
 * Entscheidet, welche Navi-Ansage wie gesprochen wird (0.6.6, Ansagen-Schwall aus der ersten 0.6.4-Fahrt).
 *
 * Vorher: jede Ansage per QUEUE_ADD hinten angehängt. Wurden viele Schritte auf einmal weitergeschaltet
 * (Fortschrittssprung, verspätete Fixes im Hintergrund), las die TTS danach ~20 Ansagen am Stück vor.
 * Jetzt:
 *  - Sammelschaltung ([beginBatch]/[endBatch]): nur die LETZTE Ansage wird gesprochen, mit Flush.
 *  - Neue Ansage für einen anderen Schritt als die vorige → Flush (die alte ist überfahren).
 *  - Schnelle Folge (< [burstMs]) → Flush, nur die neueste zählt.
 *  - Ansagen, die warten mussten (TTS noch nicht bereit), verfallen nach [maxAgeMs].
 */
class SpeechGate(private val maxAgeMs: Long = 5_000, private val burstMs: Long = 1_500) {
    data class Say(val text: String, val id: String, val flush: Boolean, val atMs: Long)

    private var batch = false
    private var held: Say? = null
    private var lastStep: Int? = null
    private var lastOfferMs: Long? = null
    private var pending: Say? = null
    /** Zahl der in Sammelschaltungen verworfenen Ansagen (für das Navigationsprotokoll). */
    var dropped = 0; private set

    fun beginBatch() { batch = true; held = null }

    /** Ende der Sammelschaltung: höchstens eine Ansage (die letzte), immer mit Flush. */
    fun endBatch(): Say? { batch = false; val h = held; held = null; return h?.copy(flush = true) }

    /** Ferrostar hat eine Ansage ausgelöst. null = (noch) nicht sprechen. */
    fun offer(text: String, id: String, stepIndex: Int?, nowMs: Long): Say? {
        val newStep = stepIndex != null && lastStep != null && stepIndex != lastStep
        val burst = lastOfferMs?.let { nowMs - it < burstMs } ?: false
        lastStep = stepIndex ?: lastStep; lastOfferMs = nowMs
        val s = Say(text, id, newStep || burst, nowMs)
        if (batch) { if (held != null) dropped++; held = s; return null }
        return s
    }

    /** TTS nicht bereit: Ansage zurückstellen (nur die letzte). */
    fun hold(s: Say) { pending = s }
    /** TTS bereit: zurückgestellte Ansage, falls noch frisch. */
    fun takePending(nowMs: Long): Say? { val p = pending; pending = null; return p?.takeIf { fresh(it, nowMs) } }
    fun fresh(s: Say, nowMs: Long) = nowMs - s.atMs <= maxAgeMs
    fun clear() { held = null; pending = null }
}
