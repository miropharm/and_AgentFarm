package com.muvusoft.agentfarm.widget

import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.core.state.Link

/** The quick settings tile: lit while a farm is connected, its subtitle what waits and runs; a tap opens Now. */
class StatusTile : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val text = StatusSurfaces.current(this)
        val online = AgentFarmApp.of(this).manager.state.value.farms.any { it.link is Link.Online }
        tile.state = if (online) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = text.body
        tile.contentDescription = "${text.title}. ${text.body}"
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(StatusSurfaces.openNow(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(StatusSurfaces.openNowIntent(this))
        }
    }
}
