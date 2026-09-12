package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

class LocationTracking(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracking"
        private const val INTERVAL_MS = 60_000L
        private const val MIN_DISTANCE_METERS = 15f
    }

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private var lastUploadedLocation: Location? = null

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                for (location in result.locations) {

                    if (!location.hasAccuracy()) {
                        Log.d(
                            TAG,
                            "Standort ohne Genauigkeitswert verworfen"
                        )
                        continue
                    }

                    val previous = lastUploadedLocation

                    if (previous != null) {

                        val distance = previous.distanceTo(location)

                        if (distance < MIN_DISTANCE_METERS) {
                            Log.d(
                                TAG,
                                "Standort verworfen: nur ${"%.1f".format(distance)} m Bewegung"
                            )
                            continue
                        }
                    }

                    uploadLocation(location)
                }
            }
        }

    @SuppressLint("MissingPermission")
    fun start() {

        Log.d(
            TAG,
            "Standort-Tracking starten: " +
                "Intervall=${INTERVAL_MS}ms, " +
                "Mindestbewegung=${MIN_DISTANCE_METERS}m"
        )

        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(INTERVAL_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
                .build()

        client.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
            .addOnSuccessListener {
                Log.d(
                    TAG,
                    "Standort-Tracking erfolgreich gestartet"
                )
            }
            .addOnFailureListener { e ->
                Log.e(
                    TAG,
                    "Standort-Tracking konnte nicht gestartet werden",
                    e
                )
            }
    }

    fun stop() {

        client.removeLocationUpdates(locationCallback)

        Log.d(
            TAG,
            "Standort-Tracking gestoppt"
        )

        lastUploadedLocation = null
    }

    private fun uploadLocation(location: Location) {

        val deviceId = DeviceManager.getDeviceId(context)

        Log.d(
            TAG,
            "Neuer Standort: " +
                "${location.latitude}, " +
                "${location.longitude} | " +
                "accuracy=${location.accuracy}m"
        )

        SupabaseApi.insertLocation(
            deviceId = deviceId,
            lat = location.latitude,
            lng = location.longitude,
            timestamp = System.currentTimeMillis(),
            onDone = {

                lastUploadedLocation = Location(location)

                Log.d(
                    TAG,
                    "Tracking-Standort erfolgreich hochgeladen"
                )
            }
        )
    }
}
