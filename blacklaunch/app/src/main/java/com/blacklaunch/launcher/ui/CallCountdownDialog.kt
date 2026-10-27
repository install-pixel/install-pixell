package com.blacklaunch.launcher.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    LaunchedEffect(contact) {
        while (left > 0) {
            delay(1_000)
            left--
        }
        call()
    }
    GlassDialog(
        onDismissRequest = onCancel,
        title = { DialogTitle(contact.name) },
        text = {
            Text(stringResource(R.string.voice_calling, contact.number, left), color = Oled.TextSecondary, fontSize = 16.sp)
        },
        confirmButton = { DialogButton(stringResource(R.string.call_now), onCall, Tone.Confirm) },
        dismissButton = { DialogButton(stringResource(R.string.cancel), onCancel) },
    )
}
