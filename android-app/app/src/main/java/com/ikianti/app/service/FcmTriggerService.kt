package com.ikianti.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi
import org.json.JSONArray

class FcmTriggerService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmTriggerService"

        private const val PREFS = "iki_pending_commands"
        private const val KEY_COMMAND_QUEUE = "pending_command_queue"

        private val VALID_COMMANDS = setOf(
            "photo",
            "audio",
            "location",
            "usage"
        )

        /**
         * Speichert einen gültigen FCM-Befehl dauerhaft.
         *
         * Die Queue bleibt auch erhalten, wenn der Foreground Service
         * gerade nicht läuft.
         */
        @Synchronized
        fun savePendingCommand(
            context: Context,
            command: String
        ) {
            if (command !in VALID_COMMANDS) {
                Log.w(
                    TAG,
                    "Ungültigen Befehl nicht speichern: $command"
                )
                return
            }

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val raw =
                prefs.getString(
                    KEY_COMMAND_QUEUE,
                    "[]"
                ) ?: "[]"

            try {
                val queue = JSONArray(raw)

                queue.put(command)

                prefs.edit()
                    .putString(
                        KEY_COMMAND_QUEUE,
                        queue.toString()
                    )
                    .apply()

                Log.d(
                    TAG,
                    "Befehl persistent gespeichert: $command | " +
                        "Warteschlange=${queue.length()}"
                )

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Fehler beim Speichern der Befehlswarteschlange",
                    e
                )

                /*
                 * Falls die gespeicherte Queue beschädigt ist,
                 * mit einer neuen Queue weitermachen.
                 */
                val newQueue = JSONArray()
                newQueue.put(command)

                prefs.edit()
                    .putString(
                        KEY_COMMAND_QUEUE,
                        newQueue.toString()
                    )
                    .apply()
            }
        }

        /**
         * Holt ALLE aktuell gespeicherten Befehle atomar aus der
         * persistenten Queue und leert diese anschließend.
         *
         * Dadurch bleibt die Reihenfolge erhalten:
         *
         * location -> usage -> location
         *
         * wird auch nach einem Service-Neustart genau in dieser
         * Reihenfolge verarbeitet.
         */
        @Synchronized
        fun takePendingCommands(
            context: Context
        ): List<String> {

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            val raw =
                prefs.getString(
                    KEY_COMMAND_QUEUE,
                    "[]"
                ) ?: "[]"

            try {
                val queue = JSONArray(raw)
                val commands = mutableListOf<String>()

                for (i in 0 until queue.length()) {
                    val command = queue.getString(i)

                    if (command in VALID_COMMANDS) {
                        commands.add(command)
                    } else {
                        Log.w(
                            TAG,
                            "Ungültigen gespeicherten Befehl übersprungen: $command"
                        )
                    }
                }

                /*
                 * Erst nachdem die Queue gelesen wurde,
                 * wird sie dauerhaft geleert.
                 */
                prefs.edit()
                    .remove(KEY_COMMAND_QUEUE)
                    .apply()

                Log.d(
                    TAG,
                    "Persistente Queue übernommen: " +
                        "${commands.size} Befehl(e)"
                )

                return commands

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Fehler beim Lesen der Befehlswarteschlange",
                    e
                )

                prefs.edit()
                    .remove(KEY_COMMAND_QUEUE)
                    .apply()

                return emptyList()
            }
        }

        /**
         * Nur für Notfälle/Debugging:
         * komplette persistente Queue löschen.
         */
        @Synchronized
        fun clearPendingCommands(
            context: Context
        ) {
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
                .edit()
                .remove(KEY_COMMAND_QUEUE)
                .apply()

            Log.d(
                TAG,
                "Persistente Befehlswarteschlange geleert"
            )
        }
    }

    override fun onMessageReceived(
        message: RemoteMessage
    ) {
        Log.d(
            TAG,
            "DEBUG FCM onMessageReceived aufgerufen"
        )

        Log.d(
            TAG,
            "DEBUG FCM data=${message.data}"
        )

        Log.d(
            TAG,
            "DEBUG FCM notification=${message.notification}"
        )

        val command =
            message.data["command"]
                ?: run {
                    Log.w(
                        TAG,
                        "DEBUG FCM: Kein command in message.data"
                    )
                    return
                }

        if (command !in VALID_COMMANDS) {
            Log.w(
                TAG,
                "FCM-Befehl nicht für die Zweithandy-App bestimmt: $command"
            )
            return
        }

        Log.d(
            TAG,
            "FCM-Befehl empfangen: $command"
        )

        /*
         * WICHTIG:
         *
         * Der Befehl wird zuerst persistent gespeichert.
         * Der Broadcast dient anschließend nur als Signal
         * für den Foreground Service:
         *
         * "Hole die aktuelle persistente Queue ab."
         */
        savePendingCommand(
            this,
            command
        )

        CaptureForegroundService.sendCommand(
            this,
            command
        )

        try {
            val intent =
                Intent(
                    this,
                    CaptureForegroundService::class.java
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(
                    this,
                    intent
                )
            } else {
                startService(intent)
            }

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Service-Start fehlgeschlagen: ${e.message}"
            )
        }
    }

    override fun onNewToken(
        token: String
    ) {
        val deviceId =
            DeviceManager.getDeviceId(this)

        Log.d(
            TAG,
            "FCM-Token erneuert für $deviceId"
        )

        SupabaseApi.updateFcmToken(
            deviceId,
            token
        )
    }
}
