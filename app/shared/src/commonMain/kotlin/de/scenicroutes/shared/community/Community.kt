package de.scenicroutes.shared.community

/**
 * Community ist bewusst NICHT umgesetzt, nur die Nahtstellen (M1: Architektur mitgedacht):
 * - Fahrten bleiben lokal (Trip.shared = 0), Teilen erst nach ausdrücklicher Zustimmung.
 * - Beim Teilen werden Start/Ziel unscharf gemacht (privacyTrimM), damit die Wohnadresse nicht erkennbar ist.
 * - Sperrungsmeldungen ("Gesperrt"-Knopf) werden lokal gesammelt und können später an alle verteilt werden.
 */
data class ClosureReport(val lat: Double, val lon: Double, val timeMs: Long, val loopId: Int?)

interface CommunitySync {
    suspend fun reportClosure(r: ClosureReport) {}
    suspend fun shareTrip(tripId: Long, privacyTrimM: Double = 500.0) {}
}

/** Standard in M1: tut nichts, nichts verlässt das Gerät. */
object NoCommunity : CommunitySync
