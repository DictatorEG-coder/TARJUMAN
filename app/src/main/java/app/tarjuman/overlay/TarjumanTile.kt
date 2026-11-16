package app.tarjuman.overlay

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.tarjuman.MainActivity

/** Quick Settings tile: stops the bubble if running, otherwise opens the app to start it. */
class TarjumanTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (TarjumanService.running) {
            TarjumanService.stop(this)
            refresh()
            return
        }
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("autostart", true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (TarjumanService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
