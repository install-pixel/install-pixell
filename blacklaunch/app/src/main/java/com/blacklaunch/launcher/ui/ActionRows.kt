package com.blacklaunch.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.voice.MediaCommand
import com.blacklaunch.launcher.voice.SystemPanel
import com.blacklaunch.launcher.voice.VoiceAction
import com.blacklaunch.launcher.voice.VoiceActionKind
import com.blacklaunch.launcher.voice.VolumeChange

/** Назва команди в результатах пошуку — мовою телефону. */
@Composable
internal fun VoiceAction.title(): String = when (this) {
    is VoiceAction.Alarm -> stringResource(R.string.action_row_alarm, "%d:%02d".format(hour, minute))
    is VoiceAction.Timer -> stringResource(R.string.action_row_timer, timerText(seconds))
    is VoiceAction.Flashlight -> stringResource(
        when (on) {
            true -> R.string.action_row_flash_on
            false -> R.string.action_row_flash_off
            null -> R.string.action_flashlight
        },
    )
    is VoiceAction.Media -> stringResource(
        when (command) {
            MediaCommand.PLAY_PAUSE, MediaCommand.PAUSE -> R.string.action_row_pause
            MediaCommand.PLAY -> R.string.action_row_play
            MediaCommand.NEXT -> R.string.action_row_next
            MediaCommand.PREVIOUS -> R.string.action_row_previous
        },
    )
    is VoiceAction.Volume -> stringResource(
        when (change) {
            VolumeChange.UP -> R.string.action_row_louder
            VolumeChange.DOWN -> R.string.action_row_quieter
            VolumeChange.MUTE -> R.string.action_row_mute
        },
    )
    is VoiceAction.Panel -> stringResource(
        when (panel) {
            SystemPanel.WIFI -> R.string.action_row_wifi
            SystemPanel.BLUETOOTH -> R.string.action_row_bluetooth
            SystemPanel.INTERNET -> R.string.action_row_internet
        },
    )
    is VoiceAction.Navigate -> stringResource(R.string.action_row_route, destination)
    is VoiceAction.Call -> stringResource(R.string.action_row_call, name)
    is VoiceAction.Sms -> stringResource(R.string.action_row_sms, name)
    VoiceAction.Time -> stringResource(R.string.action_time)
}

/** Друга лінія: текст SMS. */
internal fun VoiceAction.subtitle(): String? = (this as? VoiceAction.Sms)?.body?.takeIf { it.isNotBlank() }

internal fun VoiceAction.icon(): ImageVector = when (kind) {
    VoiceActionKind.ALARM -> Icons.Filled.Notifications
    VoiceActionKind.FLASHLIGHT -> Icons.Filled.Star
    VoiceActionKind.MEDIA -> Icons.Filled.PlayArrow
    VoiceActionKind.PANELS -> Icons.Filled.Settings
    VoiceActionKind.NAVIGATION -> Icons.Filled.Place
    VoiceActionKind.CALLS -> Icons.Filled.Call
    VoiceActionKind.SMS -> Icons.Filled.Send
    VoiceActionKind.TIME -> Icons.Filled.DateRange
}

/** «10:00», «1:30:00». */
internal fun timerText(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
