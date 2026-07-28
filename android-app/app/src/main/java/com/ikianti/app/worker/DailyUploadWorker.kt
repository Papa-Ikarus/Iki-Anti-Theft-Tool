package com.ikianti.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ikianti.app.capture.LocationCapture
import com.ikianti.app.capture.UsageStatsCapture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * WorkManager Worker, der täglich einmal ausgeführt wird und:
 *   1. Aktuellen Standort erfasst und hochlädt
 *   2. App-Nutzungsstatistiken der letzten 24h hochlädt
 *
 * WorkManager ist der empfohlene Android-Weg für periodische Hintergrundarbeit –
 * er respektiert Doze-Mode und läuft auch nach Neustarts weiter.
 */
class DailyUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "DailyUploadWorker"
        const val WORK_NAME = "iki_daily_upload"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Täglicher Upload startet")

        // 1. Standort hochladen
        runCapture { done ->
            LocationCapture(applicationContext).fetchAndUpload(done)
        }

        // 2. App-Nutzungsstatistiken hochladen
        runCapture { done ->
            UsageStatsCapture(applicationContext).collectAndUpload(done)
        }

        Log.d(TAG, "Täglicher Upload abgeschlossen")
        return Result.success()
    }

    // Callback-basierte Capture-Klassen in Coroutine einbetten
    private suspend fun runCapture(block: (onDone: () -> Unit) -> Unit) {
        suspendCancellableCoroutine { cont ->
            block { if (cont.isActive) cont.resume(Unit) }
        }
    }
}
