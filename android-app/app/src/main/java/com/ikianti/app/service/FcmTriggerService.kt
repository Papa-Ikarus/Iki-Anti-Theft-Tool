package com.ikianti.app.service

import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Empfängt "data only" FCM-Nachrichten (kein "notification"-Payload,
 * damit sie ohne sichtbare Push-Notification ankommen) und startet
 * daraufhin den CaptureForegroundService mit dem passenden Kommando.
 *
 * Erwartetes Datenformat der FCM-Nachricht:
 *   { "command": "photo" | "audio" | "location" }
 */
class FcmTriggerService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val command = message.data["command"] ?: return

        val intent = Intent(this, CaptureForegroundService::class.java).apply {
            putExtra(CaptureForegroundService.EXTRA_COMMAND, command)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    override fun onNewToken(token: String) {
        // Token hat sich geändert (z.B. nach App-Reinstall) -> aktualisieren
        Firebase.firestore.collection("devices").document("phone-1")
            .update("fcmToken", token)
    }
}
