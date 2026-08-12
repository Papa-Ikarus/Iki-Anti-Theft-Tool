package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

class LocationCapture(private val context: Context) {

    companion object {
        private const val TAG = "LocationCapture"
    }

    @SuppressLint("MissingPermission")
    fun fetchAndUpload(onDone: () -> Unit) {
        val client = LocationServices.getFusedLocationProviderClient(context)

        // Zuerst lastLocation versuchen
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.d(TAG, "lastLocation: ${location.latitude}, ${location.longitude}")
                uploadLocation(location, onDone)
            } else {
                // lastLocation null → frischen Standort anfordern
                Log.d(TAG, "lastLocation null – frischen Standort anfordern")
                requestFreshLocation(client, onDone)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "lastLocation Fehler", e)
            requestFreshLocation(client, onDone)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(
        client: FusedLocationProviderClient,
        onDone: () -> Unit
    ) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                client.removeLocationUpdates(this)
                val location = result.lastLocation
                if (location != null) {
                    Log.d(TAG, "Frischer Standort: ${location.latitude}, ${location.longitude}")
                    uploadLocation(location, onDone)
                } else {
                    Log.w(TAG, "Auch frischer Standort null")
                    onDone()
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun uploadLocation(location: Location, onDone: () -> Unit) {
        val deviceId = DeviceManager.getDeviceId(context)
        SupabaseApi.insertLocation(
            deviceId = deviceId,
            lat = location.latitude,
            lng = location.longitude,
            timestamp = System.currentTimeMillis(),
            onDone = onDone
        )
    }
}
