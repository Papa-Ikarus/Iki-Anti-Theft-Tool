package com.ikianti.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

/**
 * Einmalige Setup-Activity.
 *
 * Ablauf:
 *   1. Permissions anfordern
 *   2. FCM-Token in Firestore registrieren
 *   3. Launcher-Icon (LauncherAlias) deaktivieren → App verschwindet
 *      aus der App-Schublade
 *   4. Activity beendet sich selbst
 *
 * Nach dem Setup läuft nur noch der FcmTriggerService im Hintergrund.
 * Die App ist danach über die App-Schublade nicht mehr sichtbar,
 * aber weiterhin aktiv.
 *
 * Erneut öffnen (z.B. für Updates): per ADB:
 *   adb shell am start -n com.ikianti.app/.MainActivity
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PERMISSIONS = 1001
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kein Layout nötig – Activity ist nur für Setup da

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            finishSetup()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            finishSetup()
        }
    }

    private fun finishSetup() {
        registerFcmToken {
            hideLauncherIcon()
            finish() // Activity beendet sich, kein Rückweg über Back-Button
        }
    }

    private fun registerFcmToken(onDone: () -> Unit) {
        Firebase.messaging.token.addOnSuccessListener { token ->
            Firebase.firestore.collection("devices").document("phone-1")
                .set(mapOf(
                    "fcmToken" to token,
                    "lastSeen" to System.currentTimeMillis()
                ))
                .addOnSuccessListener {
                    Log.d(TAG, "Gerät erfolgreich registriert")
                    onDone()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Registrierung fehlgeschlagen", e)
                    Toast.makeText(this, "Firebase-Verbindung prüfen!", Toast.LENGTH_LONG).show()
                    onDone() // trotzdem weitermachen
                }
        }.addOnFailureListener { e ->
            Log.e(TAG, "FCM-Token konnte nicht abgerufen werden", e)
            onDone()
        }
    }

    /**
     * Deaktiviert den LauncherAlias → App-Icon verschwindet aus der
     * App-Schublade. Die App selbst läuft weiter, nur das Icon ist weg.
     */
    private fun hideLauncherIcon() {
        val alias = ComponentName(this, "com.ikianti.app.LauncherAlias")
        packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        Log.d(TAG, "Launcher-Icon deaktiviert")
    }
}
