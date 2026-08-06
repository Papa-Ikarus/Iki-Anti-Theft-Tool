package com.ikianti.app.service

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.ikianti.app.UnlockActivity

/**
 * Quick Settings Tile – erscheint in der Schnelleinstellungs-Leiste.
 * Langes Drücken oder Antippen öffnet die App.
 *
 * Hinzufügen: Schnelleinstellungen bearbeiten → "Systemdienst" ziehen.
 */
class IkiTileService : TileService() {

    companion object {
        private const val TAG = "IkiTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Systemdienst"
            contentDescription = "Systemdienst"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile geklickt – App wird geöffnet")

        // LauncherAlias wieder aktivieren
        val alias = ComponentName(this, "com.ikianti.app.LauncherAlias")
        packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        val intent = Intent(this, UnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }
}
