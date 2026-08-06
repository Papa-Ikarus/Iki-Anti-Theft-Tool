package com.ikianti.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Wird über den Secret Code *#*#4545#*#* oder das Quick Settings Tile geöffnet.
 * Zeigt den aktuellen Status der App und ermöglicht das manuelle Verstecken/Zeigen.
 *
 * Im Gegensatz zu MainActivity schließt sich diese Activity NICHT automatisch.
 */
class UnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = DeviceManager.getDeviceId(this)

        // Einfaches Layout per Code – kein XML nötig
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 120, 64, 64)
        }

        val title = TextView(this).apply {
            text = "Iki Das Anti Theft Tool"
            textSize = 22f
            setPadding(0, 0, 0, 32)
        }

        val status = TextView(this).apply {
            text = "✅ Aktiv\nGeräte-ID: $deviceId"
            textSize = 15f
            setPadding(0, 0, 0, 48)
        }

        val hideBtn = Button(this).apply {
            text = "Icon verstecken"
            setOnClickListener {
                setLauncherAlias(false)
                status.text = "✅ Aktiv\nGeräte-ID: $deviceId\n\nIcon versteckt.\nCode: *#*#4545#*#*"
            }
        }

        val showBtn = Button(this).apply {
            text = "Icon anzeigen"
            setOnClickListener {
                setLauncherAlias(true)
                status.text = "✅ Aktiv\nGeräte-ID: $deviceId\n\nIcon wieder sichtbar."
            }
        }

        val closeBtn = Button(this).apply {
            text = "Schließen"
            setOnClickListener { finish() }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(hideBtn)
        layout.addView(showBtn)
        layout.addView(closeBtn)
        setContentView(layout)
    }

    private fun setLauncherAlias(enabled: Boolean) {
        val alias = ComponentName(this, "com.ikianti.app.LauncherAlias")
        packageManager.setComponentEnabledSetting(
            alias,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
