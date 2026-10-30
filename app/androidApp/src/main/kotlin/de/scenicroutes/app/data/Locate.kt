package de.scenicroutes.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Standort einmalig: Play-Dienste wenn vorhanden, sonst System-LocationManager. Wirft nie, liefert null. */
object Locate {
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Pair<Double, Double>? = runCatching {
        val gms = runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS }.getOrDefault(false)
        val l = if (gms) {
            val f = LocationServices.getFusedLocationProviderClient(ctx)
            withTimeoutOrNull(12_000) { suspendCancellableCoroutine<android.location.Location?> { c -> f.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { if (c.isActive) c.resume(it) }.addOnFailureListener { if (c.isActive) c.resume(null) } } }
                ?: withTimeoutOrNull(3_000) { suspendCancellableCoroutine<android.location.Location?> { c -> f.lastLocation.addOnSuccessListener { if (c.isActive) c.resume(it) }.addOnFailureListener { if (c.isActive) c.resume(null) } } }
        } else null
        (l ?: systemLast(ctx))?.let { it.latitude to it.longitude }
    }.getOrElse { systemLastSafe(ctx) }

    @SuppressLint("MissingPermission")
    private fun systemLast(ctx: Context) = (ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.let { lm ->
        lm.getProviders(true).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time } }
    private fun systemLastSafe(ctx: Context) = runCatching { systemLast(ctx)?.let { it.latitude to it.longitude } }.getOrNull()
}
