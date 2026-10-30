package de.scenicroutes.app.rec

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.scenicroutes.app.MainActivity
import de.scenicroutes.app.graph
import de.scenicroutes.shared.trip.TrackPoint
import de.scenicroutes.shared.trip.VehicleFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vordergrunddienst (Typ location): GPS 1 Hz + Beschleunigung/Gyro (~50 Hz, je Sekunde verdichtet).
 * Läuft weiter bei App-Wechsel und Bildschirm aus; nach Kill startet Android ihn neu (START_STICKY) und die Fahrt läuft weiter.
 */
class RecordingService : LifecycleService() {
    companion object {
        const val ACTION_START = "start"; const val ACTION_PAUSE = "pause"; const val ACTION_RESUME = "resume"; const val ACTION_STOP = "stop"
        const val ACTION_ASK_STOP = "de.scenicroutes.ASK_STOP"
        private const val CH = "rec"; private const val NID = 42

        /** Aufzeichnung starten (Dienst + Rekorder). Rückgabe: Trip-ID. */
        fun start(ctx: Context, loopId: Int?, title: String, reversed: Boolean = false): Long {
            val id = ctx.graph.recorder.start(loopId, title, reversed)
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingService::class.java).setAction(ACTION_START)) }
            return id
        }
        fun ensureRunning(ctx: Context) { if (ctx.graph.recorder.recording) runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingService::class.java).setAction(ACTION_START)) } }
    }

    private val rec get() = graph.recorder
    private var running = false
    private var simJob: Job? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val frame = VehicleFrame()
    private var sensorThread: HandlerThread? = null
    private var lastSpeed: Double? = null; private var lastT = 0L

    private val cb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { r.locations.forEach { l ->
            val v = if (l.hasSpeed()) l.speed.toDouble() else null
            val dvdt = if (v != null && lastSpeed != null && l.time > lastT) (v - lastSpeed!!) / ((l.time - lastT) / 1000.0) else null
            lastSpeed = v; lastT = l.time
            val s = synchronized(frame) { frame.flush(v, dvdt?.takeIf { it in -15.0..15.0 }) }
            fix(TrackPoint(l.time, l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else null, v, if (l.hasAccuracy()) l.accuracy.toDouble() else null,
                if (l.hasBearing()) l.bearing.toDouble() else null, s.lonG, s.latG, s.yawDps, s.gPeak))
        } }
    }
    private val sensors = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) = synchronized(frame) {
            val v = e.values
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) frame.onAccel(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), e.timestamp)
            else frame.onGyro(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun fix(p: TrackPoint) { lifecycleScope.launch(Dispatchers.IO) { runCatching { rec.onFix(p) } } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundNow()
        when (intent?.action) {
            ACTION_PAUSE -> rec.pause()
            ACTION_RESUME -> rec.resume()
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            null -> runCatching { // Neustart durch Android nach Kill: unterbrochene Fahrt fortsetzen
                if (!rec.recording) rec.unfinished().maxByOrNull { it.startedMs }?.let { rec.resumeFrom(it) }
            }
        }
        if (!rec.recording) { stopEverything(); return START_NOT_STICKY }
        if (!running) begin()
        return START_STICKY
    }

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Fahrt-Aufzeichnung", NotificationManager.IMPORTANCE_LOW))
        runCatching { ServiceCompat.startForeground(this, NID, notification(rec.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) }
            .onFailure { stopSelf() }
    }

    @SuppressLint("MissingPermission")
    private fun begin() {
        running = true
        if (graph.simulating.value) {
            simJob = lifecycleScope.launch { graph.navLocations.collect { fix(it) } } // Simulation: Ferrostar-Positionen
        } else {
            runCatching { fused.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).setMinUpdateIntervalMillis(1000).build(), cb, Looper.getMainLooper()) }
            val sm = getSystemService(SensorManager::class.java)
            val th = HandlerThread("rec-sensors").apply { start() }; sensorThread = th
            val h = Handler(th.looper)
            listOfNotNull(sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE))
                .forEach { sm.registerListener(sensors, it, 20_000, h) } // 50 Hz
        }
        // Benachrichtigung aktuell halten (Zeit läuft per Chronometer, km/Pause bei Änderung)
        lifecycleScope.launch {
            rec.state.map { s -> s?.let { Triple((it.distM / 100).toInt(), it.paused, it.tripId) } }.distinctUntilChanged().collect { k ->
                if (k == null) stopEverything() else getSystemService(NotificationManager::class.java).notify(NID, notification(rec.state.value))
            }
        }
    }

    private fun stopEverything() {
        runCatching { fused.removeLocationUpdates(cb) }
        runCatching { getSystemService(SensorManager::class.java)?.unregisterListener(sensors) }
        sensorThread?.quitSafely(); sensorThread = null
        simJob?.cancel(); running = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { runCatching { fused.removeLocationUpdates(cb) }; runCatching { getSystemService(SensorManager::class.java)?.unregisterListener(sensors) }; sensorThread?.quitSafely(); super.onDestroy() }

    private fun notification(s: RecState?): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open", "record"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT).setContentIntent(open).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (s == null) return b.setContentTitle("Fahrt-Aufzeichnung").build()
        val km = "%.1f km".format(java.util.Locale.GERMANY, s.distM / 1000)
        b.setContentTitle(if (s.paused) "Aufzeichnung pausiert · $km" else "Fahrt wird aufgezeichnet · $km").setContentText(s.title)
        if (!s.paused) b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - s.elapsedMs(System.currentTimeMillis())).setShowWhen(true)
        val pr = Intent(this, RecordingService::class.java).setAction(if (s.paused) ACTION_RESUME else ACTION_PAUSE)
        b.addAction(0, if (s.paused) "Fortsetzen" else "Pause", PendingIntent.getService(this, 1, pr, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        // Stopp öffnet die App mit der Frage „Fahrt speichern?“ – nichts geht versehentlich verloren
        b.addAction(0, "Stopp", PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).setAction(ACTION_ASK_STOP).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
        return b.build()
    }
}
