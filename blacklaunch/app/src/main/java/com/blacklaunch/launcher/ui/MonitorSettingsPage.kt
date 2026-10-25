package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.settings.MonitorAccent
import com.blacklaunch.launcher.settings.MonitorLook
import com.blacklaunch.launcher.settings.MonitorTile
import com.blacklaunch.launcher.ui.theme.Oled

/** Налаштування → «Монітор системи»: як відкрити, які плитки показувати, колір, «Доступ до використання». */
@Composable
internal fun MonitorSettingsContent(
    look: MonitorLook,
    usageAccess: Boolean,
    onTile: (MonitorTile, Boolean) -> Unit,
    onAccent: (MonitorAccent) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenMonitor: () -> Unit,
) {
    Card {
        Note(stringResource(R.string.monitor_note))
        ActionOption(R.string.monitor_open, Icons.Filled.PlayArrow, onOpenMonitor)
    }
    Card {
        Text(stringResource(R.string.monitor_tiles), color = Oled.TextPrimary, fontSize = 17.sp)
        MonitorTile.entries.forEach { tile ->
            SwitchRow(stringResource(tile.title()), "", tile in look.tiles) { onTile(tile, it) }
        }
    }
    Card {
        Text(stringResource(R.string.monitor_accent), color = Oled.TextPrimary, fontSize = 17.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MonitorAccent.entries.forEach { accent -> TargetOption(accent.title(), look.accent == accent) { onAccent(accent) } }
        }
    }
    Card {
        Text(stringResource(R.string.monitor_usage_title), color = Oled.TextPrimary, fontSize = 17.sp)
        Note(stringResource(if (usageAccess) R.string.monitor_usage_on else R.string.monitor_usage_off))
        if (!usageAccess) ActionOption(R.string.monitor_usage_grant, Icons.Filled.Lock, onOpenUsageAccess)
    }
}

@StringRes
private fun MonitorTile.title(): Int = when (this) {
    MonitorTile.BATTERY -> R.string.mtile_battery
    MonitorTile.MEMORY -> R.string.mtile_memory
    MonitorTile.STORAGE -> R.string.mtile_storage
    MonitorTile.THERMAL -> R.string.mtile_thermal
    MonitorTile.NETWORK -> R.string.mtile_network
    MonitorTile.DETAILS -> R.string.mtile_details
    MonitorTile.CURRENT_GRAPH -> R.string.mtile_current_graph
    MonitorTile.MEMORY_GRAPH -> R.string.mtile_memory_graph
    MonitorTile.USAGE -> R.string.mtile_usage
}

@StringRes
private fun MonitorAccent.title(): Int = when (this) {
    MonitorAccent.BLUE -> R.string.accent_blue
    MonitorAccent.GREEN -> R.string.accent_green
    MonitorAccent.AMBER -> R.string.accent_amber
    MonitorAccent.GREY -> R.string.accent_grey
}
