package com.ikianti.app.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.ikianti.app.MainActivity

/**
 * Lauscht auf den geheimen Wählcode *#*#4545#*#*
 * Wenn der Code gewählt wird:
 *   1. LauncherAlias wieder aktivieren (falls deaktiviert)
 *   2. MainActivity direkt starten
 *
 * Funktioniert auf allen Android-Versionen ohne Root.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecretCodeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SECRET_CODE") return

        Log.d(TAG, "Geheimer Code erkannt – App wird geöffnet")

        // LauncherAlias wieder aktivieren
        val alias = ComponentName(context, "com.ikianti.app.LauncherAlias")
        context.packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // MainActivity direkt starten
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launch)
    }
}
