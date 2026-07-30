package com.ikianti.app.service

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

class FcmTriggerService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val command = message.data["command"] ?: return
        Log.d("FcmTriggerService", "Befehl empfangen: $command")

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
        val deviceId = DeviceManager.getDeviceId(this)
        Log.d("FcmTriggerService", "FCM-Token erneuert für $deviceId")
        SupabaseApi.updateFcmToken(deviceId, token)
    }
}
