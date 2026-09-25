package com.ikianti.owner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class OwnerMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["command"] != "DAILY_REPORT") return
        val device = message.data["device_id"] ?: return
        val date = message.data["date"] ?: return
        if (!device.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            !date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "daily_reports", "Tagesberichte", NotificationManager.IMPORTANCE_DEFAULT
        ))
        val key = "$device:$date"
        val prefs = getSharedPreferences("delivered_reports", MODE_PRIVATE)
        // Gleiche FCM-Zustellung erneut empfangen: keine zweite Meldung, auch nach Neustart.
        if (prefs.getBoolean(key, false)) return
        val intent = Intent(this, MainActivity::class.java).apply {
            data = Uri.Builder().scheme("iki-owner").authority("report")
                .appendPath(device).appendPath(date).build()
            putExtra("device_id", device)
            putExtra("date", date)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "daily_reports")
            .setSmallIcon(R.drawable.ic_report)
            .setContentTitle("Tagesbericht · $date")
            .setContentText("Bericht für $device öffnen")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try {
            manager.notify(key, 1, notification)
            prefs.edit().apply {
                if (prefs.all.size > 100) clear()
                putBoolean(key, true)
            }.apply()
        } catch (_: SecurityException) {
            // Berechtigung kann zwischen Prüfung und Anzeige entzogen werden.
        }
    }

    override fun onNewToken(token: String) {
        // Registrierung erfolgt beim nächsten Öffnen mit der Dashboard-Anmeldung.
        // Keine Zugangsdaten und keine Geräte-Registrierung auf dem Besitzer-Handy.
    }
}
