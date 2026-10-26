package com.blacklaunch.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.VoiceState

/**
 * Голос поверх заблокованого екрана: лише чорний екран, пульсуюча крапка й почуте — ні програм, ні пошуку не видно.
 * Почуте виконується тільки після розблокування; дотик будь-де — скасувати.
 */
@Composable
internal fun LockedVoiceScreen(state: VoiceState, onCancel: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "locked-voice")
        .animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(32.dp)) {
            val listening = state is VoiceState.Listening
            Box(
                Modifier.size(56.dp).scale(if (listening) pulse else 1f).clip(CircleShape)
                    .background(if (listening) Oled.Listening else Color(0xFF3A3A3A)),
            )
            val heard = (state as? VoiceState.Listening)?.partial.orEmpty()
            Text(
                heard.ifEmpty { stringResource(if (listening) R.string.voice_locked_listening else R.string.voice_locked_wait) },
                color = Oled.TextPrimary,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.voice_locked_hint), color = Oled.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

private const val PULSE_MS = 700
