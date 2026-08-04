package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.ikianti.app.DeviceManager
import com.ikianti.app.SupabaseApi

class LocationCapture(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun fetchAndUpload(onDone: () -> Unit) {
        LocationServices.getFusedLocationProviderClient(context)
            .lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    SupabaseApi.insertLocation(
                        deviceId  = DeviceManager.getDeviceId(context),
                        lat       = location.latitude,
                        lng       = location.longitude,
                        timestamp = System.currentTimeMillis(),
                        onDone    = onDone
                    )
                } else {
                    Log.w("LocationCapture", "Kein Standort verfügbar")
                    onDone()
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationCapture", "Standort-Fehler", e)
                onDone()
            }
    }
}
