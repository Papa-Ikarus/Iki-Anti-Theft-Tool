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
import com.ikianti.app.SupabaseApi
import java.util.concurrent.Executors
import kotlin.math.max

class LocationTracking(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracking"

        private const val INTERVAL_MS = 60_000L
        private const val MIN_UPDATE_INTERVAL_MS = 5_000L

        private const val MAX_ACCURACY_METERS = 50f

        // Bereich, in dem GPS-Schwankungen bei Stillstand noch als Drift
        // betrachtet werden.
        private const val STILLSTAND_RADIUS_METERS = 15f

        // Ein Punkt außerhalb des Stillstandsbereichs wird zunächst
        // nur vorgemerkt und noch nicht hochgeladen.
        private const val MOVEMENT_CONFIRMATION_METERS = 15f

        // Anzahl aufeinanderfolgender Punkte, die eine Bewegung
        // bestätigen müssen.
        private const val REQUIRED_MOVEMENT_CONFIRMATIONS = 2

        // Sehr große Sprünge bleiben zusätzlich geschützt.
        private const val MAX_JUMP_METERS = 150f

        // Physikalisch unrealistische Geschwindigkeit.
        private const val MAX_SPEED_KMH = 180f
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private val locationExecutor = Executors.newSingleThreadExecutor()

    private var locationCallback: LocationCallback? = null

    /**
     * Letzter tatsächlich akzeptierter Standort.
     *
     * Dieser Wert wird synchron auf dem Location-Executor gepflegt und
     * ist deshalb unabhängig davon, wann Supabase antwortet.
     */
    private var lastAcceptedLocation: Location? = null

    /**
     * Letzter verarbeiteter Location-Zeitstempel.
     *
     * Verhindert, dass FusedLocationProvider dieselbe alte Location
     * mehrfach liefert und wir sie erneut verarbeiten/hochladen.
     */
    private var lastProcessedLocationTime: Long = 0L

    /**
     * Aktueller Stillstands-Mittelpunkt.
     *
     * Solange sich das Gerät in diesem Bereich bewegt, betrachten wir
     * die Unterschiede als GPS-Drift.
     */
    private var stillstandAnchor: Location? = null

    /**
     * Kandidat für eine echte Bewegung.
     */
    private var pendingMovement: Location? = null

    private var pendingConfirmations = 0

    fun start() {

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(context)

        if (locationCallback != null) {
            Log.d(TAG, "Location Tracking läuft bereits")
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                Log.d(
                    TAG,
                    "GPS-CALLBACK: ${result.locations.size} Standort(e) empfangen"
                )

                for (location in result.locations) {
                    processLocation(location)
                }
            }
        }

        try {
            @SuppressLint("MissingPermission")
            fusedLocationClient?.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )

            Log.d(TAG, "Location Tracking gestartet")

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Starten des Location Trackings", e)
        }
    }

    private fun processLocation(location: Location) {

        val locationTime = location.time

        Log.d(
            TAG,
            "GPS-CALLBACK-POSITION: " +
                    "lat=${location.latitude}, " +
                    "lng=${location.longitude}, " +
                    "accuracy=${location.accuracy}m, " +
                    "time=$locationTime"
        )

        // ------------------------------------------------------------
        // 1. Doppelte / veraltete Location ignorieren
        // ------------------------------------------------------------

        if (locationTime <= lastProcessedLocationTime) {

            Log.d(
                TAG,
                "GPS-DOPPELT/VERALTET: ignoriert | " +
                        "time=$locationTime <= last=$lastProcessedLocationTime"
            )

            return
        }

        lastProcessedLocationTime = locationTime

        // ------------------------------------------------------------
        // 2. Ungenaue GPS-Punkte ignorieren
        // ------------------------------------------------------------

        if (location.accuracy > MAX_ACCURACY_METERS) {

            Log.d(
                TAG,
                "GPS-UNGÜLTIG: accuracy=${location.accuracy}m > " +
                        "${MAX_ACCURACY_METERS}m"
            )

            return
        }

        // ------------------------------------------------------------
        // 3. Erster Standort
        // ------------------------------------------------------------

        if (lastAcceptedLocation == null) {

            Log.d(TAG, "GPS-ERSTER-PUNKT: Standort wird angenommen")

            acceptLocation(location)

            return
        }

        val previous = lastAcceptedLocation!!

        val distance = previous.distanceTo(location)

        val timeSeconds =
            (location.time - previous.time) / 1000.0

        val speedKmh =
            if (timeSeconds > 0) {
                (distance / timeSeconds) * 3.6
            } else {
                0.0
            }

        Log.d(
            TAG,
            "GPS-KANDIDAT: distance=${"%.1f".format(distance)}m, " +
                    "zeit=${"%.1f".format(timeSeconds)}s, " +
                    "geschwindigkeit=${"%.1f".format(speedKmh)}km/h, " +
                    "accuracy=${"%.1f".format(location.accuracy)}m"
        )

        // ------------------------------------------------------------
        // 4. Unrealistische Geschwindigkeit
        // ------------------------------------------------------------

        if (speedKmh > MAX_SPEED_KMH) {

            Log.d(
                TAG,
                "GPS-VERWORFEN: unrealistische Geschwindigkeit " +
                        "${"%.1f".format(speedKmh)}km/h"
            )

            return
        }

        // ------------------------------------------------------------
        // 5. Sehr großer GPS-Sprung
        // ------------------------------------------------------------

        if (distance > MAX_JUMP_METERS) {

            Log.d(
                TAG,
                "GPS-VERWORFEN: Sprung ${"%.1f".format(distance)}m > " +
                        "${MAX_JUMP_METERS}m"
            )

            return
        }

        // ------------------------------------------------------------
        // 6. Stillstand erkennen
        // ------------------------------------------------------------

        val anchor = stillstandAnchor ?: previous

        val distanceFromAnchor = anchor.distanceTo(location)

        if (distanceFromAnchor <= STILLSTAND_RADIUS_METERS) {

            Log.d(
                TAG,
                "GPS-STATE: STILLSTAND | " +
                        "anchor=${"%.1f".format(distanceFromAnchor)}m"
            )

            // Ein eventuell vorgemerkter Bewegungskandidat ist damit
            // nicht bestätigt worden.
            if (pendingMovement != null) {

                Log.d(
                    TAG,
                    "GPS-DRIFT: Kandidat verworfen | " +
                            "Punkt ist wieder nahe am Stillstandsanker"
                )

                pendingMovement = null
                pendingConfirmations = 0
            }

            // Im Stillstand brauchen wir nicht jede kleine GPS-Wanderung
            // an Supabase zu senden.
            Log.d(
                TAG,
                "GPS-DRIFT: ${"%.1f".format(distanceFromAnchor)}m | " +
                        "nicht hochgeladen"
            )

            return
        }

        // ------------------------------------------------------------
        // 7. Punkt liegt außerhalb des Stillstandsbereichs
        // ------------------------------------------------------------

        Log.d(
            TAG,
            "GPS-STATE: BEWEGUNG | " +
                    "Abstand zum Anchor=${"%.1f".format(distanceFromAnchor)}m"
        )

        // ------------------------------------------------------------
        // 8. Noch kein Bewegungskandidat vorhanden
        // ------------------------------------------------------------

        if (pendingMovement == null) {

            pendingMovement = location
            pendingConfirmations = 1

            Log.d(
                TAG,
                "GPS-NEUER BEWEGUNGSKANDIDAT: " +
                        "distance=${"%.1f".format(distanceFromAnchor)}m"
            )

            return
        }

        val pending = pendingMovement!!

        val distanceFromPending = pending.distanceTo(location)

        // ------------------------------------------------------------
        // 9. Nächster Punkt liegt ebenfalls in Richtung Kandidat
        // ------------------------------------------------------------

        if (distanceFromPending >= MOVEMENT_CONFIRMATION_METERS) {

            pendingConfirmations++

            Log.d(
                TAG,
                "GPS-BEWEGUNG-BESTÄTIGUNG: " +
                        "Abstand zum Kandidaten=" +
                        "${"%.1f".format(distanceFromPending)}m | " +
                        "Bestätigungen=$pendingConfirmations/" +
                        "$REQUIRED_MOVEMENT_CONFIRMATIONS"
            )

            if (pendingConfirmations >= REQUIRED_MOVEMENT_CONFIRMATIONS) {

                Log.d(
                    TAG,
                    "GPS-NEUER BEWEGUNGSPUNKT: Bewegung bestätigt"
                )

                acceptLocation(location)

                pendingMovement = null
                pendingConfirmations = 0
            }

            return
        }

        // ------------------------------------------------------------
        // 10. Kandidat wurde nicht bestätigt
        // ------------------------------------------------------------

        Log.d(
            TAG,
            "GPS-DRIFT: Bewegung nicht bestätigt | " +
                    "Abstand zum Kandidaten=" +
                    "${"%.1f".format(distanceFromPending)}m"
        )
    }

    /**
     * Standort wird intern als akzeptiert gesetzt und anschließend
     * an Supabase übergeben.
     */
    private fun acceptLocation(location: Location) {

        lastAcceptedLocation = Location(location)

        // Nach bestätigter Position wird dieser Punkt zum neuen
        // Stillstandsanker.
        stillstandAnchor = Location(location)

        Log.d(
            TAG,
            "GPS-ANGENOMMEN: " +
                    "lat=${location.latitude}, " +
                    "lng=${location.longitude}, " +
                    "accuracy=${location.accuracy}m"
        )

        uploadLocation(location)
    }

    private fun uploadLocation(location: Location) {

        val deviceId = com.ikianti.app.DeviceManager.getDeviceId(context)

        if (deviceId == null) {

            Log.e(
                TAG,
                "Tracking-Standort nicht hochgeladen: " +
                        "device_id fehlt"
            )

            return
        }

                SupabaseApi.insertLocation(
                    deviceId = deviceId,
                    lat = location.latitude,
                    lng = location.longitude,
                    timestamp = location.time,
                    onDone = {

                        Log.d(
                            TAG,
                            "Tracking-Standort erfolgreich hochgeladen"
                        )
                    }
                )

    }

    fun stop() {
        fusedLocationClient?.let { client ->
            locationCallback?.let {
                client.removeLocationUpdates(it)
            }
        }

        locationCallback = null
        lastAcceptedLocation = null
        lastProcessedLocationTime = 0L
        stillstandAnchor = null
}
}