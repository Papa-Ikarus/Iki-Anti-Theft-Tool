package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
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

        // Sehr gute Messungen werden direkt akzeptiert.
        private const val GOOD_ACCURACY_METERS = 20f

        // Oberhalb davon wird der Standort verworfen.
        private const val MAX_ACCURACY_METERS = 50f

        // Schutz gegen offensichtliche GPS-Ausreißer.
        private const val MAX_JUMP_METERS = 150f
    }

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationExecutor =
        Executors.newSingleThreadExecutor()

    private var lastUploadedLocation: Location? = null

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationAvailability(
                availability: LocationAvailability
            ) {
                Log.d(
                    TAG,
                    "GPS-VERFÜGBARKEIT: ${availability.isLocationAvailable}"
                )
            }

            override fun onLocationResult(result: LocationResult) {

                Log.d(
                    TAG,
                    "GPS-CALLBACK: ${result.locations.size} Standort(e) empfangen"
                )

                for (location in result.locations) {

                    Log.d(
                        TAG,
                        "GPS-CALLBACK-POSITION: " +
                            "lat=${location.latitude}, " +
                            "lng=${location.longitude}, " +
                            "accuracy=${location.accuracy}m, " +
                            "time=${location.time}"
                    )

                    if (!location.hasAccuracy()) {

                        Log.d(
                            TAG,
                            "GPS-VERWORFEN: kein Genauigkeitswert"
                        )

                        continue
                    }

                    val accuracy = location.accuracy
                    val previous = lastUploadedLocation

                    val distance = if (previous != null) {
                        previous.distanceTo(location)
                    } else {
                        null
                    }

                    Log.d(
                        TAG,
                        "GPS-KANDIDAT: " +
                            "accuracy=${"%.1f".format(accuracy)}m, " +
                            "distance=${distance?.let { "%.1f".format(it) } ?: "kein vorheriger Punkt"}m"
                    )

                    // Zu ungenaue Messung.
                    if (accuracy > MAX_ACCURACY_METERS) {

                        Log.d(
                            TAG,
                            "GPS-VERWORFEN: " +
                                "Genauigkeit=${"%.1f".format(accuracy)}m > " +
                                "${MAX_ACCURACY_METERS}m"
                        )

                        continue
                    }

                    // Erster gültiger Standort.
                    if (previous == null) {

                        Log.d(
                            TAG,
                            "GPS-ANGENOMMEN: erster gültiger Standort"
                        )

                        uploadLocation(location)
                        continue
                    }

                    /*
                     * Sehr gute Messung:
                     * direkt übernehmen.
                     */
                    if (accuracy <= GOOD_ACCURACY_METERS) {

                        Log.d(
                            TAG,
                            "GPS-ANGENOMMEN: " +
                                "gute Genauigkeit=${"%.1f".format(accuracy)}m"
                        )

                        uploadLocation(location)
                        continue
                    }

                    /*
                     * Genauigkeit zwischen 20 und 50 m:
                     * Nur übernehmen, wenn der Sprung nicht offensichtlich
                     * unrealistisch groß ist.
                     */
                    if (distance != null && distance > MAX_JUMP_METERS) {

                        Log.d(
                            TAG,
                            "GPS-VERWORFEN: " +
                                "möglicher Ausreißer | " +
                                "distance=${"%.1f".format(distance)}m > " +
                                "${MAX_JUMP_METERS}m"
                        )

                        continue
                    }

                    Log.d(
                        TAG,
                        "GPS-ANGENOMMEN: " +
                            "normale Genauigkeit=${"%.1f".format(accuracy)}m"
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
                "Intervall=${INTERVAL_MS}ms"
        )

        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(5_000L)
                .setWaitForAccurateLocation(false)
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