package cloud.codella.vikunja.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import cloud.codella.vikunja.MainActivity

/**
 * System Quick Settings Tile providing one-tap task capture from notification shade or lock screen.
 * Compliant with Android 14+ (API 34) background activity launch restrictions using [startActivityAndCollapse(PendingIntent)].
 */
@RequiresApi(Build.VERSION_CODES.N)
class VikunjaQuickAddTileService : TileService() {

    companion object {
        private const val TAG = "QuickAddTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Vikunja Quick Add"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Tap to add task"
            }
            updateTile()
        }
        Log.d(TAG, "Quick Settings tile is listening")
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Quick Settings tile stopped listening")
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Quick Settings tile clicked")

        val targetIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_QUICK_ADD, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @SuppressLint("StartActivityAndCollapseDeprecated")
            @Suppress("DEPRECATION")
            startActivityAndCollapse(targetIntent)
        }
    }
}
