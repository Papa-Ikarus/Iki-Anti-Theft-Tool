package com.ikianti.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.ktx.Firebase
import com.ikianti.app.SupabaseApi

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        Log.d("BootReceiver", "Neustart erkannt – FCM-Token wird aktualisiert")

        Firebase.messaging.token.addOnSuccessListener { token ->
            SupabaseApi.updateFcmToken("phone-1", token)
        }
    }
}
