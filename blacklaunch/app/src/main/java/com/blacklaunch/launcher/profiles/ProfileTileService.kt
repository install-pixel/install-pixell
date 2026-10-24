package com.blacklaunch.launcher.profiles

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Плитка в шторці швидких налаштувань: показує активний профіль, натискання — наступний.
 * Працює з будь-якої програми; оновлюється лише поки шторка відкрита.
 */
class ProfileTileService : TileService() {
    private var scope: CoroutineScope? = null
    private val repository get() = (application as BlackLaunchApp).profileRepository

    override fun onStartListening() {
        super.onStartListening()
        val listening = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = listening
        listening.launch {
            repository.activeProfile.collect { show(it) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        (application as BlackLaunchApp).appScope.launch {
            val (all, active) = combine(repository.profiles, repository.activeProfile) { a, b -> a to b }.first()
            all.neighbour(active?.id, 1)?.let { repository.setActive(it.id) }
        }
    }

    private fun show(profile: LauncherProfile?) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_profile)
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.tile_label)
            tile.subtitle = profile?.name
        } else {
            tile.label = profile?.name ?: getString(R.string.tile_label)
        }
        tile.updateTile()
    }
}
