package com.ikianti.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

/**
 * Aktualisiert regelmäßig last_seen in Supabase.
 *
 * Zweck:
 * - Das Dashboard kann erkennen, ob das Gerät kürzlich aktiv war.
 * - Separat vom täglichen Upload-Worker.
 */
class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "HeartbeatWorker"
        const val WORK_NAME = "iki_heartbeat"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Heartbeat startet")

        val deviceId = DeviceManager.getDeviceId(applicationContext)

        SupabaseApi.updateLastSeen(deviceId)

        Log.d(TAG, "Heartbeat abgeschlossen")
        return Result.success()
    }
}