package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi
import java.util.concurrent.Executors

class LocationTracking(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracking"
        private const val INTERVAL_MS = 60_000L
        private const val MIN_DISTANCE_METERS = 30f
        private const val MAX_ACCURACY_METERS = 25f
    }

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationExecutor =
        Executors.newSingleThreadExecutor()

    private var lastUploadedLocation: Location? = null

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(
                    TAG,
                    "GPS-VERFÜGBARKEIT: ${availability.isLocationAvailable}"
                )
            }

            override fun onLocationResult(result: LocationResult) {

                for (location in result.locations) {

                    val previous = lastUploadedLocation

                    val distance = if (previous != null) {
                        previous.distanceTo(location)
                    } else {
                        null
                    }

                    Log.d(
                        TAG,
                        "GPS-KANDIDAT: " +
                            "lat=${location.latitude}, " +
                            "lng=${location.longitude}, " +
                            "accuracy=${if (location.hasAccuracy()) location.accuracy else "unbekannt"}m, " +
                            "time=${location.time}, " +
                            "distance=${distance?.let { "%.1f".format(it) } ?: "kein vorheriger Punkt"}m"
                    )

                    if (!location.hasAccuracy()) {
                        Log.d(
                            TAG,
                            "GPS-VERWORFEN: kein Genauigkeitswert"
                        )
                        continue
                    }

                    if (location.accuracy > MAX_ACCURACY_METERS) {
                        Log.d(
                            TAG,
                            "GPS-VERWORFEN: Genauigkeit=${location.accuracy}m > ${MAX_ACCURACY_METERS}m"
                        )
                        continue
                    }

                    

                    Log.d(
                        TAG,
                        "GPS-ANGENOMMEN: " +
                            "accuracy=${location.accuracy}m, " +
                            "distance=${distance?.let { "%.1f".format(it) } ?: "kein vorheriger Punkt"}m"
                    )

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
                .setMinUpdateIntervalMillis(5_000L)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
                .build()

        client.requestLocationUpdates(
            request,
            locationExecutor,
            locationCallback
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

        locationExecutor.shutdown()
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
