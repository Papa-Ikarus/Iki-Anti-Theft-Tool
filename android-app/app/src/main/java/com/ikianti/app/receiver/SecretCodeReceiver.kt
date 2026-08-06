package com.ikianti.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ikianti.app.UnlockActivity

/**
 * Lauscht auf den geheimen Wählcode *#*#4545#*#*
 *
 * Auf Android 10+ darf ein BroadcastReceiver keine Activity direkt starten.
 * Daher: kurze Notification anzeigen → Nutzer tippt drauf → App öffnet sich.
 *
 * Die Notification verschwindet automatisch nach dem Antippen.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecretCodeReceiver"
        private const val CHANNEL_ID = "iki_unlock_channel"
        private const val NOTIFICATION_ID = 99
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SECRET_CODE") return
        Log.d(TAG, "Geheimer Code erkannt")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Kanal anlegen
        val channel = NotificationChannel(
            CHANNEL_ID, "Entsperren", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Systemdienst öffnen"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        // PendingIntent → öffnet MainActivity
        val openIntent = Intent(context, UnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Systemdienst")
            .setContentText("Tippen zum Öffnen")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)   // verschwindet nach Antippen
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}
