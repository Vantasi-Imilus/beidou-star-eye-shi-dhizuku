package org.beidou.stareye.feature.yaoguang

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * 快捷设置磁贴：默认作为「自动冻结」开关，也可在设置中改为其他动作（与雹一致）。
 */
class YaoguangTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val action = YaoguangStore.tileAction(this)
        if (action == YaoguangShortcuts.ACTION_AUTO_FREEZE) {
            val enabled = !YaoguangStore.autoFreeze(this)
            YaoguangStore.setAutoFreeze(this, enabled)
            YaoguangAutoFreezeService.setEnabled(this, enabled)
            refresh()
            return
        }
        val intent = YaoguangShortcuts.intentFor(this, action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val action = YaoguangStore.tileAction(this)
        tile.label = "瑶光·${YaoguangShortcuts.label(action)}"
        tile.state =
            if (action == YaoguangShortcuts.ACTION_AUTO_FREEZE && YaoguangStore.autoFreeze(this)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
        tile.updateTile()
    }
}
