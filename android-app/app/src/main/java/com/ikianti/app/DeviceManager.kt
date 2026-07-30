package com.ikianti.app

import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * Generiert beim ersten Start eine eindeutige Geräte-ID und speichert
 * sie dauerhaft in SharedPreferences.
 *
 * Format: "device-XXXXXXXX" (z.B. "device-a3f92b1c")
 * Bleibt über App-Updates hinweg erhalten, wird aber bei
 * App-Deinstallation zurückgesetzt.
 */
object DeviceManager {

    private const val TAG = "DeviceManager"
    private const val PREFS_NAME = "iki_device_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            // Neue ID generieren: "device-" + erste 8 Zeichen einer UUID
            deviceId = "device-" + UUID.randomUUID().toString().replace("-", "").take(8)
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "Neue Geräte-ID generiert: $deviceId")
        }

        return deviceId
    }
}
