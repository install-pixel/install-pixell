package com.blacklaunch.launcher.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.ContactNumber
import kotlinx.coroutines.delay

private const val COUNTDOWN_SECONDS = 3

/** Голосовий дзвінок: 3-секундний відлік, за який можна скасувати; «Дзвонити» — одразу. */
@Composable
fun CallCountdownDialog(contact: ContactNumber, onCall: () -> Unit, onCancel: () -> Unit) {
    var left by remember(contact) { mutableIntStateOf(COUNTDOWN_SECONDS) }
    val call by rememberUpdatedState(onCall)
    val haptics = LocalHaptics.current
    LaunchedEffect(contact) {
        while (left > 0) {
            delay(1_000)
            left--
        }
        call()
    }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF141414),
        title = { Text(contact.name, color = Oled.TextPrimary) },
        text = {
            Text(stringResource(R.string.voice_calling, contact.number, left), color = Oled.TextSecondary, fontSize = 16.sp)
        },
        confirmButton = {
            TextButton(onClick = { haptics.tap(); onCall() }) { Text(stringResource(R.string.call_now), color = Oled.TextPrimary) }
        },
        dismissButton = {
            TextButton(onClick = { haptics.tap(); onCancel() }) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
        },
    )
}
