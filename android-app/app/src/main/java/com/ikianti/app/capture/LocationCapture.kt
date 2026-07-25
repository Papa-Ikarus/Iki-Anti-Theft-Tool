package com.ikianti.app.capture

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LocationCapture(private val context: Context) {

    @SuppressLint("MissingPermission") // Permission wird in MainActivity abgefragt
    fun fetchAndUpload(onDone: () -> Unit) {
        LocationServices.getFusedLocationProviderClient(context)
            .lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    Firebase.firestore.collection("devices").document("phone-1")
                        .collection("locations")
                        .add(
                            mapOf(
                                "lat" to location.latitude,
                                "lng" to location.longitude,
                                "timestamp" to System.currentTimeMillis()
                            )
                        )
                }
                onDone()
            }
            .addOnFailureListener { onDone() }
    }
}
