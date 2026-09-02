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

/**
 * Empfängt FCM-Befehle.
 *
 * Strategie für Android 12+:
 * 1. Befehl in SharedPreferences speichern (Fallback falls Service tot)
 * 2. Broadcast senden (sofort wenn persistenter FGS läuft)
 * 3. Service starten/neustarten (verarbeitet gespeicherten Befehl in onCreate)
 */
class FcmTriggerService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmTriggerService"
        private const val PREFS = "iki_pending_commands"
        private const val KEY_COMMAND = "pending_command"

        fun savePendingCommand(context: Context, command: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_COMMAND, command).apply()
        }

        fun getPendingCommand(context: Context): String? {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_COMMAND, null)
        }

        fun clearPendingCommand(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_COMMAND).apply()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
    Log.d(TAG, "DEBUG FCM onMessageReceived aufgerufen")
    Log.d(TAG, "DEBUG FCM data=${message.data}")
    Log.d(TAG, "DEBUG FCM notification=${message.notification}")

    val command = message.data["command"] ?: run {
        Log.w(TAG, "DEBUG FCM: Kein command in message.data")
        return
    }

    Log.d(TAG, "FCM-Befehl empfangen: $command")

        // 1. Befehl speichern (Fallback)
        savePendingCommand(this, command)

        // 2. Broadcast an laufenden Service
        CaptureForegroundService.sendCommand(this, command)

        // 3. Service starten falls er nicht läuft
        // (bei Android 12+ ohne Kamera/Mikro – aber der Service holt
        //  den gespeicherten Befehl aus SharedPreferences)
        try {
            val intent = Intent(this, CaptureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Service-Start fehlgeschlagen (erwartet auf Android 12+): ${e.message}")
        }
    }

    override fun onNewToken(token: String) {
        val deviceId = DeviceManager.getDeviceId(this)
        Log.d(TAG, "FCM-Token erneuert für $deviceId")
        SupabaseApi.updateFcmToken(deviceId, token)
    }
}
