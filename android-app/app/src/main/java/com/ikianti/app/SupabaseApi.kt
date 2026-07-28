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

    // TODO: eigene Werte eintragen
    private const val SUPABASE_URL      = "https://ywrhhuhadgtmdzldbawa.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_XewJO51CVSpz2fVlHPLrbQ_Yj6OWixg"

    private const val TAG = "SupabaseApi"

    private val client = OkHttpClient()
    private val JSON_MEDIA  = "application/json".toMediaType()

    // ── Gerät registrieren / Token aktualisieren ──────────────────────────────

    fun upsertDevice(deviceId: String, fcmToken: String, onDone: () -> Unit) {
        val body = JSONObject().apply {
            put("id", deviceId)
            put("fcm_token", fcmToken)
            put("last_seen", System.currentTimeMillis())
        }.toString()

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/devices")
            .headers(anonHeaders())
            .header("Prefer", "resolution=merge-duplicates")   // upsert
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "upsertDevice fehlgeschlagen", e)
                onDone()
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                onDone()
            }
        })
    }

    fun updateFcmToken(deviceId: String, fcmToken: String) {
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
                Log.e(TAG, "updateFcmToken fehlgeschlagen", e)
            }
            override fun onResponse(call: Call, response: Response) {
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
        onDone: () -> Unit
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
                onDone()
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                onDone()
            }
        })
    }

    // ── Datei in Supabase Storage hochladen ───────────────────────────────────

    fun uploadFile(
        bucket: String,
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onDone: () -> Unit
    ) {
        val request = Request.Builder()
            .url("$SUPABASE_URL/storage/v1/object/$bucket/$path")
            .headers(anonHeaders())
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "uploadFile ($bucket/$path) fehlgeschlagen", e)
                onDone()
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Upload-Fehler ${response.code}: ${response.body?.string()}")
                }
                response.close()
                onDone()
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
