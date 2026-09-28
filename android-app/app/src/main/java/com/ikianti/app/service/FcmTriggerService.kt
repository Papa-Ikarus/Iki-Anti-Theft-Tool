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
import org.json.JSONObject
import java.util.UUID

class FcmTriggerService : FirebaseMessagingService() {

    data class PendingCommand(
        val id: String,
        val command: String,
        val attempts: Int
    )

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
        ): Boolean {
            if (command !in VALID_COMMANDS) {
                Log.w(
                    TAG,
                    "Ungültigen Befehl nicht speichern: $command"
                )
                return false
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

                val entry = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("command", command)
                    put("attempts", 0)
                }

                queue.put(entry)

                val saved =
                    prefs.edit()
                        .putString(
                            KEY_COMMAND_QUEUE,
                            queue.toString()
                        )
                        .commit()

                if (!saved) {
                    Log.e(
                        TAG,
                        "Persistenter Befehl konnte nicht gespeichert werden"
                    )
                    return false
                }

                Log.d(
                    TAG,
                    "Befehl persistent gespeichert: $command | " +
                        "Warteschlange=${queue.length()}"
                )

                return true

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Fehler beim Speichern der Befehlswarteschlange",
                    e
                )

                return false
            }
        }

        /**
         * Liest alle aktuell persistent gespeicherten Befehle.
         *
         * Die Einträge bleiben gespeichert, bis sie nach erfolgreicher
         * Verarbeitung oder nach Erreichen der maximalen Versuchszahl
         * gezielt anhand ihrer ID entfernt werden.
         *
         * Die gespeicherte Reihenfolge bleibt erhalten.
         */
        @Synchronized
        fun takePendingCommands(
            context: Context
            ): List<PendingCommand> {

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
                val commands = mutableListOf<PendingCommand>()

                for (i in 0 until queue.length()) {
                    val item = queue.opt(i)

                    when (item) {

                        is JSONObject -> {
                            val id = item.optString("id")
                            val command = item.optString("command")
                            val attempts = item.optInt("attempts", 0)

                            if (id.isNotBlank() && command in VALID_COMMANDS) {
                                commands.add(
                                    PendingCommand(
                                        id = id,
                                        command = command,
                                        attempts = attempts
                                    )
                                )
                            } else {
                                Log.w(
                                    TAG,
                                    "Ungültigen persistenten Befehl übersprungen"
                                )
                            }
                        }

                        is String -> {
                            /*
                             * Kompatibilität mit der bisherigen Queue.
                             * Alte String-Einträge erhalten beim Lesen eine ID.
                             */
                            if (item in VALID_COMMANDS) {
                                commands.add(
                                    PendingCommand(
                                        id = UUID.randomUUID().toString(),
                                        command = item,
                                        attempts = 0
                                    )
                                )
                            }
                        }
                    }
                }

                /*
                 * Die persistente Queue bleibt bestehen, bis ein Befehl
                 * nach seiner Verarbeitung gezielt entfernt wird.
                 *
                 * Gleichzeitig werden eventuell vorhandene alte
                 * String-Einträge dauerhaft in das neue Format migriert.
                 */
                val normalizedQueue = JSONArray()

                for (pendingCommand in commands) {
                    normalizedQueue.put(
                        JSONObject().apply {
                            put("id", pendingCommand.id)
                            put("command", pendingCommand.command)
                            put("attempts", pendingCommand.attempts)
                        }
                    )
                }

                val saved =
                    prefs.edit()
                        .putString(
                            KEY_COMMAND_QUEUE,
                            normalizedQueue.toString()
                        )
                        .commit()

                if (!saved) {
                    Log.e(
                        TAG,
                        "Normalisierte Befehlsqueue konnte nicht persistent gespeichert werden"
                    )

                    return emptyList()
                }

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



                return emptyList()
            }
        }

        @Synchronized
        fun incrementPendingCommandAttempts(
            context: Context,
            commandId: String
        ): Int {
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
                var newAttempts = -1

                for (i in 0 until queue.length()) {
                    val item = queue.optJSONObject(i)
                        ?: continue

                    if (item.optString("id") == commandId) {
                        newAttempts =
                            item.optInt("attempts", 0) + 1

                        item.put(
                            "attempts",
                            newAttempts
                        )

                        break
                    }
                }

                if (newAttempts == -1) {
                    Log.w(
                        TAG,
                        "Persistenter Befehl für Versuchszähler nicht gefunden: " +
                            "id=$commandId"
                    )

                    return -1
                }

                val saved =
                    prefs.edit()
                        .putString(
                            KEY_COMMAND_QUEUE,
                            queue.toString()
                        )
                        .commit()

                if (!saved) {
                    Log.e(
                        TAG,
                        "Versuchszähler konnte nicht persistent gespeichert werden: " +
                            "id=$commandId"
                    )

                    return -1
                }

                Log.d(
                    TAG,
                    "Versuchszähler aktualisiert: " +
                        "id=$commandId | attempts=$newAttempts"
                )

                return newAttempts

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Fehler beim Aktualisieren des Versuchszählers: " +
                        "id=$commandId",
                    e
                )

                return -1
            }
        }

        /**
         * Entfernt genau einen persistenten Befehl anhand seiner ID.
         *
         * Wird erst aufgerufen, wenn der Befehl endgültig
         * abgeschlossen wurde.
         */
        @Synchronized
        fun removePendingCommand(
            context: Context,
            commandId: String
        ): Boolean {
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
                val remainingQueue = JSONArray()

                for (i in 0 until queue.length()) {
                    val item = queue.optJSONObject(i)

                    if (item == null) {
                        continue
                    }

                    val id = item.optString("id")

                    if (id != commandId) {
                        remainingQueue.put(item)
                    }
                }

                val saved =
                    prefs.edit()
                        .putString(
                            KEY_COMMAND_QUEUE,
                            remainingQueue.toString()
                        )
                        .commit()

                if (!saved) {
                    Log.e(
                        TAG,
                        "Persistenter Befehl konnte nicht entfernt werden: " +
                            "id=$commandId"
                    )

                    return false
                }

                Log.d(
                    TAG,
                    "Persistenten Befehl entfernt: id=$commandId | " +
                        "Warteschlange=${remainingQueue.length()}"
                )
                return true



            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Fehler beim Entfernen des persistenten Befehls: id=$commandId",
                    e
                )
                return false
            }
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
        val saved =
            savePendingCommand(
                this,
                command
            )

        if (!saved) {
            Log.e(
                TAG,
                "FCM-Befehl wird nicht ausgeführt, " +
                    "weil persistentes Speichern fehlgeschlagen ist: $command"
            )
            return
        }

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
