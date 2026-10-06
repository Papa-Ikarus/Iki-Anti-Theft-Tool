package com.ikianti.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

class IkiNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "IkiNotificationListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Benachrichtigungszugriff verbunden")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Benachrichtigungszugriff getrennt")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName

        val appName = try {
            val applicationInfo =
                packageManager.getApplicationInfo(packageName, 0)

            packageManager
                .getApplicationLabel(applicationInfo)
                .toString()
        } catch (_: Exception) {
            packageName
        }

        Log.d(
            TAG,
            "NOTIFICATION | " +
                "app=$appName | " +
                "package=$packageName | " +
                "id=${sbn.id} | " +
                "key=${sbn.key} | " +
                "postTime=${sbn.postTime}"
        )

        SupabaseApi.insertNotificationEvent(
            deviceId = DeviceManager.getDeviceId(this),
            uploadToken = DeviceManager.getUploadToken(this),
            appName = appName,
            packageName = packageName,
            eventType = "posted",
            notificationId = sbn.id,
            notificationKey = sbn.key,
            eventTimestamp = System.currentTimeMillis(),
            postTimestamp = sbn.postTime
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        val packageName = sbn.packageName

        val appName = try {
            val applicationInfo =
                packageManager.getApplicationInfo(packageName, 0)

            packageManager
                .getApplicationLabel(applicationInfo)
                .toString()
        } catch (_: Exception) {
            packageName
        }

        Log.d(
            TAG,
            "REMOVED | " +
                "app=$appName | " +
                "package=$packageName | " +
                "id=${sbn.id} | " +
                "key=${sbn.key} | " +
                "postTime=${sbn.postTime}"
        )

        SupabaseApi.insertNotificationEvent(
            deviceId = DeviceManager.getDeviceId(this),
            uploadToken = DeviceManager.getUploadToken(this),
            appName = appName,
            packageName = packageName,
            eventType = "removed",
            notificationId = sbn.id,
            notificationKey = sbn.key,
            eventTimestamp = System.currentTimeMillis(),
            postTimestamp = sbn.postTime
        )
    }
}