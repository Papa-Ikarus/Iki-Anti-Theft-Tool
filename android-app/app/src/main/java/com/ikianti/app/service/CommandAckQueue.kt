package com.ikianti.app.service

import android.content.Context
import android.util.Log
import com.ikianti.app.SupabaseApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Speichert Statusmeldungen vor dem Versand dauerhaft.
 *
 * Netzwerkfehler behalten den Eintrag für einen späteren Versuch.
 * Es wird höchstens eine Meldung gleichzeitig versendet.
 */
object CommandAckQueue {

    private const val TAG = "CommandAckQueue"
    private const val PREFS = "iki_command_acks"
    private const val KEY_QUEUE = "queue"

    private val validStatuses =
        setOf("received", "running", "success", "error", "timeout")

    private var sending = false

    @Synchronized
    fun enqueue(
        context: Context,
        deviceId: String,
        commandId: String,
        ackToken: String?,
        status: String
    ): Boolean {
        // Alte Befehle ohne Token benötigen keine Rückmeldung.
        if (ackToken == null) return true

        if (
            deviceId.isBlank() ||
            commandId.isBlank() ||
            status !in validStatuses ||
            !ackToken.matches(Regex("^[0-9a-f]{64}$"))
        ) {
            return false
        }

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return try {
            val queue = JSONArray(prefs.getString(KEY_QUEUE, "[]") ?: "[]")

            for (i in 0 until queue.length()) {
                val existing = queue.getJSONObject(i)

                if (
                    existing.optString("device_id") == deviceId &&
                    existing.optString("command_id") == commandId &&
                    existing.optString("ack_token") == ackToken &&
                    existing.optString("status") == status
                ) {
                    return true
                }
            }

            queue.put(
                JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("device_id", deviceId)
                    put("command_id", commandId)
                    put("ack_token", ackToken)
                    put("status", status)
                }
            )

            prefs.edit()
                .putString(KEY_QUEUE, queue.toString())
                .commit()
        } catch (_: Exception) {
            // Keine Inhalte oder Tokens protokollieren.
            Log.e(TAG, "Rückmeldung konnte nicht gespeichert werden")
            false
        }
    }

    @Synchronized
    fun flush(context: Context) {
        if (sending) return

        val appContext = context.applicationContext
        val prefs = appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        try {
            val queue = JSONArray(prefs.getString(KEY_QUEUE, "[]") ?: "[]")
            if (queue.length() == 0) return

            val entry = queue.getJSONObject(0)
            val entryId = entry.getString("id")
            val deviceId = entry.getString("device_id")
            val commandId = entry.getString("command_id")
            val ackToken = entry.getString("ack_token")
            val status = entry.getString("status")

            sending = true

            SupabaseApi.sendCommandAck(
                deviceId = deviceId,
                commandId = commandId,
                ackToken = ackToken,
                status = status
            ) { result ->
                finish(appContext, entryId, result)
            }
        } catch (_: Exception) {
            sending = false
            Log.e(TAG, "Rückmeldequeue konnte nicht verarbeitet werden")
        }
    }

    @Synchronized
    private fun finish(
        context: Context,
        entryId: String,
        result: SupabaseApi.CommandAckResult
    ) {
        if (result == SupabaseApi.CommandAckResult.RETRY) {
            sending = false
            // Der nächste flush-Aufruf versucht den Versand erneut.
            return
        }

        val prefs = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        try {
            // Neu lesen: Während des Versands können Einträge hinzukommen.
            val queue = JSONArray(prefs.getString(KEY_QUEUE, "[]") ?: "[]")
            val remaining = JSONArray()

            for (i in 0 until queue.length()) {
                val entry = queue.getJSONObject(i)

                if (entry.getString("id") != entryId) {
                    remaining.put(entry)
                }
            }

            val saved = prefs.edit()
                .putString(KEY_QUEUE, remaining.toString())
                .commit()

            sending = false

            if (!saved) {
                Log.e(TAG, "Bestätigte Rückmeldung konnte nicht entfernt werden")
                return
            }

            if (result == SupabaseApi.CommandAckResult.REJECTED) {
                Log.w(TAG, "Rückmeldung endgültig vom Endpunkt abgelehnt")
            }

            flush(context)
        } catch (_: Exception) {
            sending = false
            Log.e(TAG, "Rückmeldequeue konnte nicht aktualisiert werden")
        }
    }
}