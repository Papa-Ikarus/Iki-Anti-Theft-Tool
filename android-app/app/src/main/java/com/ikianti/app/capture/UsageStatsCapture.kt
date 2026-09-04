package com.ikianti.app.capture

import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.util.Log
import com.ikianti.app.SupabaseApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class UsageStatsCapture(private val context: Context) {

    companion object {
        private const val TAG = "UsageStatsCapture"

        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.inputmethod.latin"
        )
    }

    fun collectAndUpload(onDone: () -> Unit) {

        try {
            val usageManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

            val now = System.currentTimeMillis()

            // -------------------------------------------------------------
            // 1. Echten gestrigen Kalendertag bestimmen
            // -------------------------------------------------------------

            val startCalendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -1)
            }

            val since = startCalendar.timeInMillis

            val endCalendar = Calendar.getInstance().apply {
                timeInMillis = since
                add(Calendar.DAY_OF_YEAR, 1)
            }

            val endOfDay = endCalendar.timeInMillis

            Log.d(
                TAG,
                "UsageStats Zeitraum: $since -> $endOfDay"
            )

            // -------------------------------------------------------------
            // 2. Datum des erfassten Tages
            // -------------------------------------------------------------

            val year = startCalendar.get(Calendar.YEAR)
            val month = startCalendar.get(Calendar.MONTH) + 1
            val day = startCalendar.get(Calendar.DAY_OF_MONTH)

            val dateString =
                "%04d-%02d-%02d".format(year, month, day)

            Log.d(
                TAG,
                "UsageStats Datum: $dateString"
            )

            // -------------------------------------------------------------
// 3. Foreground-Sessions aus UsageEvents ermitteln
// -------------------------------------------------------------

val firstUsage = mutableMapOf<String, Long>()
val lastUsage = mutableMapOf<String, Long>()
val activeSince = mutableMapOf<String, Long>()
val totalUsage = mutableMapOf<String, Long>()

val usageEvents =
    usageManager.queryEvents(since, endOfDay)

val event = UsageEvents.Event()

while (usageEvents.hasNextEvent()) {

    usageEvents.getNextEvent(event)

    val packageName =
        event.packageName ?: continue

    if (packageName in IGNORED_PACKAGES) {
        continue
    }

    val timestamp = event.timeStamp

    // Nur Events innerhalb des gestrigen Tages akzeptieren.
    if (timestamp < since || timestamp >= endOfDay) {
        continue
    }

    when (event.eventType) {

        // -----------------------------------------------------
        // App kommt in den Vordergrund
        // -----------------------------------------------------

        UsageEvents.Event.MOVE_TO_FOREGROUND,
        UsageEvents.Event.ACTIVITY_RESUMED -> {

            if (!activeSince.containsKey(packageName)) {

                activeSince[packageName] = timestamp

                if (!firstUsage.containsKey(packageName)) {
                    firstUsage[packageName] = timestamp
                }

                Log.d(
                    TAG,
                    "SESSION START | " +
                        "package=$packageName | " +
                        "timestamp=$timestamp"
                )
            }
        }

        // -----------------------------------------------------
        // App geht in den Hintergrund
        // -----------------------------------------------------

        UsageEvents.Event.MOVE_TO_BACKGROUND,
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED -> {

            val sessionStart =
                activeSince.remove(packageName)

            if (sessionStart != null) {

                val sessionEnd =
                    timestamp.coerceAtMost(endOfDay)

                val duration =
                    (sessionEnd - sessionStart).coerceAtLeast(0L)

                totalUsage[packageName] =
                    (totalUsage[packageName] ?: 0L) + duration

                lastUsage[packageName] = sessionEnd

                Log.d(
                    TAG,
                    "SESSION END | " +
                        "package=$packageName | " +
                        "start=$sessionStart | " +
                        "end=$sessionEnd | " +
                        "duration=${duration}ms"
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 4. Apps, die am Ende des Tages noch aktiv waren
// -------------------------------------------------------------

for ((packageName, sessionStart) in activeSince) {

    val sessionEnd = endOfDay

    val duration =
        (sessionEnd - sessionStart).coerceAtLeast(0L)

    totalUsage[packageName] =
        (totalUsage[packageName] ?: 0L) + duration

    lastUsage[packageName] = sessionEnd

    Log.d(
        TAG,
        "SESSION END (DAY LIMIT) | " +
            "package=$packageName | " +
            "start=$sessionStart | " +
            "end=$sessionEnd | " +
            "duration=${duration}ms"
    )
}

// -------------------------------------------------------------
// 5. Daten aggregieren
// -------------------------------------------------------------

val relevant = totalUsage
    .filter { (_, totalTime) ->
        totalTime > 0L
    }
    .filter { (packageName, _) ->
        packageName !in IGNORED_PACKAGES
    }
    .map { (packageName, totalTime) ->

        val firstTime =
            firstUsage[packageName] ?: 0L

        val lastTime =
            lastUsage[packageName] ?: 0L

        Triple(
            packageName,
            totalTime,
            Pair(firstTime, lastTime)
        )
    }
    .sortedByDescending {
        it.second
    }
    .take(30)

// -------------------------------------------------------------
// 6. Upload vorbereiten
// -------------------------------------------------------------

val deviceId =
    com.ikianti.app.DeviceManager.getDeviceId(context)

val jsonArray = JSONArray()

for ((packageName, totalTime, times) in relevant) {

    val firstTime = times.first
    val lastTime = times.second

    Log.d(
        TAG,
        "APP | $packageName | " +
            "total=${totalTime}ms | " +
            "first=$firstTime | " +
            "last=$lastTime"
    )

    val appName =
        getAppName(packageName)

    val json = JSONObject().apply {

        put("device_id", deviceId)

        put("date", dateString)

        put("app_package", packageName)

        put("app_name", appName)

        put(
            "total_time_ms",
            totalTime
        )

        if (firstTime > 0L) {
            put(
                "first_time_used",
                firstTime
            )
        } else {
            put(
                "first_time_used",
                JSONObject.NULL
            )
        }

        if (lastTime > 0L) {
            put(
                "last_used",
                lastTime
            )
        } else {
            put(
                "last_used",
                JSONObject.NULL
            )
        }
    }

    jsonArray.put(json)
}

            

            // -------------------------------------------------------------
            // 7. Keine Daten
            // -------------------------------------------------------------

            if (jsonArray.length() == 0) {

                Log.d(
                    TAG,
                    "Keine relevanten App-Nutzungsdaten"
                )

                onDone()
                return
            }

            Log.d(
                TAG,
                "Lade ${jsonArray.length()} " +
                    "zusammengefasste App-Nutzungseinträge hoch"
            )

            // -------------------------------------------------------------
            // 8. Supabase Upload
            // -------------------------------------------------------------

            SupabaseApi.insertUsageLogs(
                jsonArray.toString(),
                onDone
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "UsageStats Fehler",
                e
            )

            onDone()
        }
    }

    private fun getAppName(
        packageName: String
    ): String {

        return try {

            val packageManager =
                context.packageManager

            val applicationInfo =
                packageManager.getApplicationInfo(
                    packageName,
                    0
                )

            packageManager.getApplicationLabel(
                applicationInfo
            ).toString()

        } catch (_: Exception) {

            packageName
        }
    }
}