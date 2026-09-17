package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi
import java.util.concurrent.atomic.AtomicBoolean

class LocationCapture(private val context: Context) {

    companion object {
        private const val TAG = "LocationCapture"
        private const val CURRENT_LOCATION_TIMEOUT_MS = 20_000L
    }

    @SuppressLint("MissingPermission")
        fun fetchAndUpload(onDone: (Boolean) -> Unit) {

        val client =
            LocationServices.getFusedLocationProviderClient(context)

        client.lastLocation
            .addOnSuccessListener { location ->

                if (location != null) {
                    Log.d(
                        TAG,
                        "lastLocation: ${location.latitude}, ${location.longitude}"
                    )

                    uploadLocation(location, onDone)

                } else {
                    Log.d(
                        TAG,
                        "lastLocation null – getCurrentLocation wird verwendet"
                    )

                    requestCurrentLocation(client, onDone)
                }
            }
            .addOnFailureListener { e ->

                Log.e(TAG, "lastLocation Fehler", e)

                requestCurrentLocation(client, onDone)
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(
        client: FusedLocationProviderClient,
        onDone: (Boolean) -> Unit
    ) {    

        val cancellationTokenSource =
            CancellationTokenSource()

        val handler =
            Handler(Looper.getMainLooper())

        val finished =
            AtomicBoolean(false)

        fun finishOnce(success: Boolean) {
            if (finished.compareAndSet(false, true)) {
                handler.removeCallbacksAndMessages(null)
                cancellationTokenSource.cancel()
                onDone(success)
            }
        }

        val timeoutRunnable = Runnable {
            Log.w(
                TAG,
                "getCurrentLocation Timeout nach ${CURRENT_LOCATION_TIMEOUT_MS} ms"
            )

            finishOnce(false)
        }

        handler.postDelayed(
            timeoutRunnable,
            CURRENT_LOCATION_TIMEOUT_MS
        )

        Log.d(TAG, "Fordere aktuellen Standort an")

        client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        )
            .addOnSuccessListener { location ->

                if (finished.get()) {
                    Log.w(
                        TAG,
                        "getCurrentLocation Ergebnis kam nach Timeout"
                    )
                    return@addOnSuccessListener
                }

                if (location == null) {
                    Log.w(
                        TAG,
                        "getCurrentLocation liefert null"
                    )

                    finishOnce(false)
                    return@addOnSuccessListener
                }

                Log.d(
                    TAG,
                    "getCurrentLocation: ${location.latitude}, ${location.longitude}"
                )

                handler.removeCallbacks(timeoutRunnable)

                uploadLocation(
                    location
                ) {
                    finishOnce(true)
                }
            }
            .addOnFailureListener { e ->

                Log.e(
                    TAG,
                    "getCurrentLocation Fehler",
                    e
                )

                finishOnce(false)
            }
    }

    private fun uploadLocation(
        location: Location,
        onDone: (Boolean) -> Unit
    ) {

        val deviceId =
            DeviceManager.getDeviceId(context)

        SupabaseApi.insertLocation(
            deviceId = deviceId,
            lat = location.latitude,
            lng = location.longitude,
            timestamp = System.currentTimeMillis(),
            onDone = onDone
        )
    }
}