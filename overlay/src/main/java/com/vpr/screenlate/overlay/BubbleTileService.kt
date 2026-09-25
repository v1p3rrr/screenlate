package com.vpr.screenlate.overlay

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vpr.screenlate.overlay.settings.OverlaySettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quick Settings tile that shows or hides the bubble. */
@AndroidEntryPoint
class BubbleTileService : TileService() {

    @Inject lateinit var overlaySettings: OverlaySettingsRepository

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = scope.launch {
            overlaySettings.settings.collect { settings ->
                val tile = qsTile ?: return@collect
                val serviceEnabled = OverlayServiceStatus.isEnabled(this@BubbleTileService)
                tile.state = when {
                    !serviceEnabled -> Tile.STATE_UNAVAILABLE
                    settings.bubbleVisible -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val visible = overlaySettings.settings.first().bubbleVisible
            overlaySettings.setBubbleVisible(!visible)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
