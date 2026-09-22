package com.ikianti.app

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Zentraler HTTP-Client für alle Supabase-Aufrufe.
 *
 * TODO: SUPABASE_URL und SUPABASE_ANON_KEY aus der Supabase Console eintragen:
 *   Project Settings → API → Project URL & anon/public key
 *
 * Der Anon-Key ist für INSERT gedacht (Gerät schreibt Standorte, Token).
 * Das Dashboard liest mit dem echten Auth-Token (Email-Login).
 */
object SupabaseApi {

    private const val SUPABASE_URL =
        "https://ywrhhuhadgtmdzldbawa.supabase.co"

    private const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl3cmhodWhhZGd0bWR6bGRiYXdhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUxOTc4NzQsImV4cCI6MjEwMDc3Mzg3NH0.0NjDWhRSbnUjWbH7NJ7mn9EoG8CPf9-8Ot79SguDyoU"

    private const val TAG = "SupabaseApi"

    private val client = OkHttpClient()
    private val JSON_MEDIA = "application/json".toMediaType()

    // ── Gerät registrieren / Token aktualisieren ──────────────────────────────

    fun upsertDevice(
        deviceId: String,
        fcmToken: String,
        onDone: () -> Unit
    ) {
        val body = JSONObject().apply {
            put("id", deviceId)
            put("fcm_token", fcmToken)
            put("last_seen", System.currentTimeMillis())
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/devices")
            .headers(anonHeaders())
            .header("Prefer", "resolution=merge-duplicates")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
    Log.e(TAG, "upsertDevice Netzwerk-Fehler", e)

    reportError(
        deviceId,
        "SupabaseApi",
        "UPSERT_DEVICE_NETWORK",
        e.message ?: "Netzwerkfehler"
    )

    onDone()
}

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()

                if (!response.isSuccessful) {
    Log.e(
        TAG,
        "upsertDevice HTTP-Fehler ${response.code}: $body"
    )

    reportError(
        deviceId,
        "SupabaseApi",
        "UPSERT_DEVICE_HTTP_${response.code}",
        body ?: "HTTP-Fehler ${response.code}"
    )
} else {
                    Log.d(TAG, "upsertDevice OK ${response.code}")
                }

                response.close()
                onDone()
            }
        })
    }

    fun updateFcmToken(
        deviceId: String,
        fcmToken: String
    ) {
        val body = JSONObject().apply {
            put("fcm_token", fcmToken)
            put("last_seen", System.currentTimeMillis())
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/devices?id=eq.$deviceId")
            .headers(anonHeaders())
            .patch(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "updateFcmToken Netzwerk-Fehler", e)

                reportError(
                    deviceId,
                    "SupabaseApi",
                    "UPDATE_FCM_TOKEN_NETWORK",
                    e.message ?: "Netzwerkfehler"
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()

                if (!response.isSuccessful) {
                    Log.e(
                        TAG,
                        "updateFcmToken HTTP-Fehler ${response.code}: $body"
                    )

                    reportError(
                        deviceId,
                        "SupabaseApi",
                        "UPDATE_FCM_TOKEN_HTTP_${response.code}",
                        body ?: "HTTP-Fehler ${response.code}"
                    )
                } else {
                    Log.d(TAG, "updateFcmToken OK ${response.code}")
                }

                response.close()
            }
        })
    }

    // ── Live-Status ───────────────────────────────────────────────────────────

    fun updateLastSeen(deviceId: String) {
        val body = JSONObject().apply {
            put("last_seen", System.currentTimeMillis())
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/devices?id=eq.$deviceId")
            .headers(anonHeaders())
            .patch(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
    Log.e(TAG, "updateLastSeen fehlgeschlagen", e)

    reportError(
        deviceId,
        "SupabaseApi",
        "UPDATE_LAST_SEEN_NETWORK",
        e.message ?: "Netzwerkfehler"
    )
}

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
    Log.e(
        TAG,
        "updateLastSeen HTTP-Fehler ${response.code}"
    )

    reportError(
        deviceId,
        "SupabaseApi",
        "UPDATE_LAST_SEEN_HTTP_${response.code}",
        "HTTP-Fehler ${response.code}"
    )
} else {
                    Log.d(TAG, "updateLastSeen OK")
                }

                response.close()
            }
        })
    }

    fun updateLastBoot(deviceId: String) {
        val body = JSONObject().apply {
            put("last_boot", System.currentTimeMillis())
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/devices?id=eq.$deviceId")
            .headers(anonHeaders())
            .patch(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "updateLastBoot fehlgeschlagen", e)

                reportError(
                    deviceId,
                    "SupabaseApi",
                    "UPDATE_LAST_BOOT_NETWORK",
                    e.message ?: "Netzwerkfehler"
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(
                        TAG,
                        "updateLastBoot HTTP-Fehler ${response.code}"
                    )

                    reportError(
                        deviceId,
                        "SupabaseApi",
                        "UPDATE_LAST_BOOT_HTTP_${response.code}",
                        "HTTP-Fehler ${response.code}"
                    )
                } else {
                    Log.d(TAG, "updateLastBoot OK")
                }

                response.close()
            }
        })
    }


    // ── Standort speichern ────────────────────────────────────────────────────

    fun insertLocation(
        deviceId: String,
        lat: Double,
        lng: Double,
        timestamp: Long,
        onDone: (Boolean) -> Unit
    ) {
        val body = JSONObject().apply {
            put("device_id", deviceId)
            put("lat", lat)
            put("lng", lng)
            put("timestamp", timestamp)
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/locations")
            .headers(anonHeaders())
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "insertLocation fehlgeschlagen", e)

                reportError(
                    deviceId,
                    "SupabaseApi",
                    "INSERT_LOCATION_NETWORK",
                    e.message ?: "Netzwerkfehler"
                )

                onDone(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful

                if (!success) {
                    Log.e(
                        TAG,
                        "insertLocation HTTP-Fehler ${response.code}"
                    )

                    reportError(
                        deviceId,
                        "SupabaseApi",
                        "INSERT_LOCATION_HTTP_${response.code}",
                        "HTTP-Fehler ${response.code}"
                    )
                } else {
                    Log.d(TAG, "insertLocation OK")
                    updateLastSeen(deviceId)
                }

                response.close()

                onDone(success)
            }
        })
    }

    // ── Datei in Supabase Storage hochladen ───────────────────────────────────

    fun uploadFile(
        deviceId: String,
        bucket: String,
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onDone: (Boolean) -> Unit
    ) {
        val request = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/$bucket/$path")
            .headers(anonHeaders())
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(
                    TAG,
                    "uploadFile ($bucket/$path) fehlgeschlagen",
                    e
                )

                reportError(
                    deviceId,
                    "SupabaseApi",
                    "UPLOAD_FILE_NETWORK",
                    e.message ?: "Netzwerkfehler"
                )

                onDone(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful

                    if (!success) {
                        val body = response.body?.string()

                        Log.e(
                            TAG,
                            "Upload-Fehler ${response.code}: $body"
                        )

                        reportError(
                            deviceId,
                            "SupabaseApi",
                            "UPLOAD_FILE_HTTP_${response.code}",
                            body ?: "HTTP-Fehler ${response.code}"
                        )
                    } else {
                    Log.d(
                        TAG,
                        "uploadFile OK ($bucket/$path)"
                    )
                }

                response.close()

                onDone(success)
            }
        })
    }

    // ── App-Nutzungsstatistiken hochladen (Batch) ─────────────────────────────

    fun insertUsageLogs(
        deviceId: String,
        jsonArray: String,
        onDone: (Boolean) -> Unit
    ) {
        Log.d(TAG, "insertUsageLogs() aufgerufen")
        Log.d(TAG, "JSON-Länge: ${jsonArray.length}")

        val request = Request.Builder()
            .url(
                    "$SUPABASE_URL/rest/v1/usage_logs" +
                    "?on_conflict=device_id,date,app_package"
            )
            .headers(anonHeaders())
            .header("Prefer", "resolution=merge-duplicates")
            .post(jsonArray.toRequestBody(JSON_MEDIA))
            .build()

        Log.d(TAG, "Sende UsageLogs-Request an Supabase...")

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(
                    TAG,
                    "insertUsageLogs Netzwerk-Fehler",
                    e
                )

                reportError(
                    deviceId,
                    "SupabaseApi",
                    "INSERT_USAGE_LOGS_NETWORK",
                    e.message ?: "Netzwerkfehler"
                )

                onDone(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                Log.d(TAG, "UsageLogs HTTP ${response.code}")
                Log.d(TAG, "UsageLogs Response: $responseBody")

                val success = response.isSuccessful

                if (!success) {
    Log.e(
        TAG,
        "UsageLogs-Fehler ${response.code}: $responseBody"
    )

    reportError(
        deviceId,
        "SupabaseApi",
        "INSERT_USAGE_LOGS_HTTP_${response.code}",
        responseBody ?: "HTTP-Fehler ${response.code}"
    )
} else {
                    Log.d(TAG, "UsageLogs OK")
                    updateLastSeen(deviceId)
                }

                response.close()
                onDone(success)
            }
        })
    }

    // ── Fehler an das zentrale Fehlerprotokoll melden ─────────────────────────

    private fun reportError(
        deviceId: String,
        source: String,
        errorCode: String,
        message: String
    ) {
        insertErrorLog(
            deviceId = deviceId,
            level = "ERROR",
            source = source,
            errorCode = errorCode,
            message = message
        )
    }

    // ── Fehlerprotokoll ───────────────────────────────────────────────────────

    fun insertErrorLog(
        deviceId: String,
        level: String,
        source: String,
        errorCode: String,
        message: String,
        onDone: (() -> Unit)? = null
    ) {
        val body = JSONObject().apply {
            put("device_id", deviceId)
            put("level", level)
            put("source", source)
            put("error_code", errorCode)
            put("message", message)
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/error_logs")
            .headers(anonHeaders())
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "insertErrorLog Netzwerk-Fehler", e)
                onDone?.invoke()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(
                        TAG,
                        "insertErrorLog HTTP-Fehler ${response.code}"
                    )
                } else {
                    Log.d(TAG, "insertErrorLog OK")
                }

                response.close()
                onDone?.invoke()
            }
        })
    }

    // ── Header-Helper ─────────────────────────────────────────────────────────

    private fun anonHeaders() = Headers.Builder()
        .add("apikey", SUPABASE_ANON_KEY)
        .add("Authorization", "Bearer $SUPABASE_ANON_KEY")
        .add("Content-Type", "application/json")
        .build()
}