package com.ikianti.app.capture

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ikianti.app.SupabaseApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Liest die App-Nutzungsstatistiken der letzten 24h via UsageStatsManager
 * und lädt sie nach Supabase hoch.
 *
 * Voraussetzung: Permission PACKAGE_USAGE_STATS muss in den Systemeinstellungen
 * manuell erlaubt sein (Settings → Apps → Spezieller App-Zugriff → Nutzungszugriff).
 * MainActivity leitet beim Setup automatisch dorthin weiter.
 */
class UsageStatsCapture(private val context: Context) {

    companion object {
        private const val TAG = "UsageStatsCapture"

        // System-Apps die wir rausfiltern (uninteressant für den Bericht)
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher3",
            "com.android.launcher",
            "com.google.android.inputmethod.latin",
            "com.android.settings",
            "com.android.phone",
        )
    }

    fun collectAndUpload(onDone: () -> Unit) {
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: run {
            Log.e(TAG, "UsageStatsManager nicht verfügbar")
            onDone(); return
        }

        val now   = System.currentTimeMillis()
        val since = now - TimeUnit.HOURS.toMillis(24)

        val stats = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, since, now
        )

        if (stats.isNullOrEmpty()) {
            Log.w(TAG, "Keine Nutzungsdaten verfügbar – Permission erteilt?")
            onDone(); return
        }

        // Filtern: nur Apps mit tatsächlicher Nutzung, keine System-Apps
        val relevant = stats
            .filter { it.totalTimeInForeground > 0 }
            .filter { it.packageName !in IGNORED_PACKAGES }
            .sortedByDescending { it.totalTimeInForeground }
            .take(30) // max. 30 Apps pro Tag

        if (relevant.isEmpty()) {
            Log.d(TAG, "Keine relevanten Nutzungsdaten")
            onDone(); return
        }

        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(since))

        // Batch-Upload als JSON-Array (ein REST-Call für alle Apps)
        val entries = JSONArray()
        relevant.forEach { stat ->
            val appName = getAppName(stat.packageName)
            entries.put(JSONObject().apply {
                put("device_id", "phone-1")
                put("date", date)
                put("app_package", stat.packageName)
                put("app_name", appName)
                put("total_time_ms", stat.totalTimeInForeground)
                put("last_used", stat.lastTimeUsed)
            })
        }

        Log.d(TAG, "Lade ${relevant.size} App-Nutzungseinträge hoch")
        SupabaseApi.insertUsageLogs(entries.toString(), onDone)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback: Package-Name
        }
    }
}
