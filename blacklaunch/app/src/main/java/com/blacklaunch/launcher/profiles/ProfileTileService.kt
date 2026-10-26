package com.blacklaunch.launcher.profiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.blacklaunch.launcher.BlackLaunchApp
import com.blacklaunch.launcher.MainActivity
import com.blacklaunch.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val app = application as BlackLaunchApp
            val next = all.quickNeighbour(active?.id, 1) { app.profileDisplay.of(it).quickSwitch } ?: return@launch
            if (app.focus.switchGate(next.id) == FocusGate.Allowed) {
                repository.setActive(next.id)
            } else {
                // «Зосередження»: відлік чи «ще 23 хв» показує Black Glass — плитка не обходить його.
                withContext(Dispatchers.Main) { openBlackGlass() }
            }
        }
    }

    private fun openBlackGlass() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_SWITCH_NEXT, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun show(profile: LauncherProfile?) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_profile)
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.tile_label)
            tile.subtitle = profile?.name?.let { name ->
                // Фокус-таймер: скільки ще лишилось — видно й зі шторки.
                val session = (application as BlackLaunchApp).focus.session.value
                if (FocusRules.running(session, System.currentTimeMillis())) {
                    getString(R.string.focus_tile, name, FocusRules.minutesLeft(session!!.endsAt - System.currentTimeMillis()))
                } else {
                    name
                }
            }
        } else {
            tile.label = profile?.name ?: getString(R.string.tile_label)
        }
        tile.updateTile()
    }
}
