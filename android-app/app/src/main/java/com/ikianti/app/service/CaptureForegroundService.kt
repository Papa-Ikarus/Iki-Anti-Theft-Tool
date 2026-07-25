package com.ikianti.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ikianti.app.capture.AudioCapture
import com.ikianti.app.capture.CameraCapture
import com.ikianti.app.capture.LocationCapture

/**
 * Muss ab Android 9 als Foreground Service laufen, sobald Kamera/Mikro
 * im Hintergrund genutzt werden -> eine Notification ist dabei laut
 * Android-Policy sichtbar und lässt sich nicht verstecken.
 */
class CaptureForegroundService : Service() {

    companion object {
        const val EXTRA_COMMAND = "command"
        private const val CHANNEL_ID = "iki_capture_channel"
        private const val NOTIFICATION_ID = 42
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        when (intent?.getStringExtra(EXTRA_COMMAND)) {
            "photo" -> CameraCapture(this).captureAndUpload { stopSelf() }
            "audio" -> AudioCapture(this).recordAndUpload(seconds = 10) { stopSelf() }
            "location" -> LocationCapture(this).fetchAndUpload { stopSelf() }
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Sicherheitsdienst", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sicherheitsdienst aktiv")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
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

    override fun onBind(intent: Intent?): IBinder? = null
}
