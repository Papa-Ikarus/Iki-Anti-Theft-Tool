package com.ikianti.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.ktx.Firebase
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            return
        }

        val deviceId =
            DeviceManager.getDeviceId(context)

        Log.d(
            "BootReceiver",
            "Neustart erkannt – Geräteinformationen aktualisieren für $deviceId"
        )

        // Zeitpunkt des letzten Systemstarts speichern.
        SupabaseApi.updateLastBoot(deviceId)

        // FCM-Token nach einem Neustart aktualisieren.
        Firebase.messaging.token.addOnSuccessListener { token ->
            SupabaseApi.updateFcmToken(
                deviceId,
                token
            )
        }
    }
}

