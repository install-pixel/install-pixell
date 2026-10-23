package com.blacklaunch.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.VoiceState

private val FieldShape = RoundedCornerShape(28.dp)

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    voice: VoiceState,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listening = voice != VoiceState.Idle
    val partialSpeech = (voice as? VoiceState.Listening)?.partial.orEmpty()
    // Клавіатура не відкривається сама: лише після натискання на поле.
    // Так зручніше для голосового пошуку (рішення від 23.10.2026).
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = if (listening) partialSpeech else query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .clip(FieldShape)
                .background(Oled.Surface)
                .border(1.dp, if (listening) Oled.KnobListening else Oled.Glint, FieldShape),
            singleLine = true,
            readOnly = listening,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            placeholder = {
                val hint = when (voice) {
                    VoiceState.Idle -> R.string.search_hint
                    VoiceState.Preparing -> R.string.preparing
                    is VoiceState.Listening -> R.string.listening
                }
                Text(stringResource(hint), color = Oled.TextSecondary)
            },
            trailingIcon = {
                if (query.isNotEmpty() && !listening) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear), tint = Oled.TextSecondary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Oled.TextPrimary,
                unfocusedTextColor = Oled.TextPrimary,
                cursorColor = Oled.Accent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(10.dp))
        MicButton(listening, onMicClick)
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (listening) Oled.KnobListening else Oled.Knob)
            .border(1.dp, Oled.Glint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.padding(4.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = stringResource(if (listening) R.string.voice_stop else R.string.voice_search),
                tint = if (listening) Color.White else Oled.Accent,
            )
        }
    }
}
