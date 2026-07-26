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
 * Foreground Service, der die eigentliche Capture-Arbeit ausführt.
 *
 * Ab Android 9 müssen Kamera/Mikro/Standort im Hintergrund über einen
 * Foreground Service laufen – eine Notification ist dabei sichtbar
 * (Android-Policy, nicht umgehbar). Für Diebstahlschutz ist das okay,
 * da der Nutzer (Besitzer) der Auslöser ist.
 *
 * Timeout: Falls ein Capture-Job nach 30 Sekunden nicht fertig ist,
 * wird der Service automatisch beendet, um Akku und Ressourcen zu schonen.
 */
class CaptureForegroundService : Service() {

    companion object {
        const val EXTRA_COMMAND = "command"
        private const val CHANNEL_ID = "iki_capture_channel"
        private const val NOTIFICATION_ID = 42
        private const val TIMEOUT_MS = 30_000L
        private const val TAG = "CaptureForegroundService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Capture-Timeout – Service wird beendet")
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        // Sicherheitsnetz: nach 30 Sekunden immer aufräumen
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        val command = intent?.getStringExtra(EXTRA_COMMAND)
        Log.d(TAG, "Befehl empfangen: $command")

        val onDone = {
            mainHandler.removeCallbacks(timeoutRunnable)
            stopSelf()
        }

        when (command) {
            "photo"    -> CameraCapture(this).captureAndUpload(onDone)
            "audio"    -> AudioCapture(this).recordAndUpload(seconds = 10, onDone = onDone)
            "location" -> LocationCapture(this).fetchAndUpload(onDone)
            else       -> {
                Log.w(TAG, "Unbekannter Befehl: $command")
                onDone()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sicherheitsdienst",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Aktiv während Fernzugriff durch Gerätebesitzer"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sicherheitsdienst aktiv")
            .setContentText("Fernzugriff läuft...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
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
