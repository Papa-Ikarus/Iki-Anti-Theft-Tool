package com.ikianti.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

/**
 * Einmalige Einrichtung: Permissions anfordern und Geräte-ID
 * (FCM-Token) in Firestore registrieren, damit das Dashboard
 * weiß, an welches Gerät es Trigger schicken kann.
 *
 * TODO: google-services.json aus der Firebase Console hier
 * unter android-app/app/ ablegen (siehe docs/SETUP.md).
 */
class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMissingPermissions()
        registerDeviceToken()
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }

    private fun registerDeviceToken() {
        Firebase.messaging.token.addOnSuccessListener { token ->
            // TODO: deviceId sinnvoll wählen (z.B. Android ID) statt "phone-1"
            Firebase.firestore.collection("devices").document("phone-1")
                .set(mapOf("fcmToken" to token, "lastSeen" to System.currentTimeMillis()))

            findViewById<TextView>(R.id.tokenText)?.text = "Registriert."
        }
    }
}
