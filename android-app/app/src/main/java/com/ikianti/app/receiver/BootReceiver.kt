package com.ikianti.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

/**
 * Wird nach jedem Geräteneustart aufgerufen.
 *
 * Nach einem Neustart kann sich der FCM-Token geändert haben.
 * Wir holen den aktuellen Token und schreiben ihn nach Firestore,
 * damit das Dashboard weiterhin Befehle senden kann.
 *
 * Kein Foreground Service nötig – FCM übernimmt das Aufwecken
 * bei eingehenden Befehlen selbst.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        Log.d(TAG, "Gerät neugestartet – FCM-Token wird aktualisiert")

        Firebase.messaging.token.addOnSuccessListener { token ->
            Firebase.firestore.collection("devices").document("phone-1")
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "lastSeen" to System.currentTimeMillis(),
                        "lastBoot" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener { Log.d(TAG, "Token nach Neustart aktualisiert") }
                .addOnFailureListener { e -> Log.e(TAG, "Token-Update fehlgeschlagen", e) }
        }
    }
}
