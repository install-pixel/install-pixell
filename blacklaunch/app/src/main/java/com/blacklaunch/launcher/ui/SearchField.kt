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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
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
    onFocusChange: (Boolean) -> Unit = {},
    /** Enter на клавіатурі — виконати найнижчий результат (лише явною дією). */
    onSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listening = voice != VoiceState.Idle
    val haptics = LocalHaptics.current
    val sheet = LocalSheet.current
    val partialSpeech = (voice as? VoiceState.Listening)?.partial.orEmpty()
    // Клавіатура не відкривається сама: лише після натискання на поле.
    // Так зручніше для голосового пошуку (рішення від 23.10.2026).
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = if (listening) partialSpeech else query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (sheet) {
                        Modifier.pencilFrame(28.dp, seed = 11, ink = if (listening) Oled.Listening else SheetInk).clip(FieldShape)
                    } else {
                        Modifier.clip(FieldShape).background(Oled.Surface).border(1.dp, if (listening) Oled.KnobListening else Oled.Glint, FieldShape)
                    },
                )
                .onFocusChanged { onFocusChange(it.isFocused) },
            singleLine = true,
            readOnly = listening,
            // «Аркуш»: у шрифту від руки дуже високий рядок — курсор виходив на півполя. Обрізаємо рядок до самих
            // літер, і курсор стає звичайної висоти.
            textStyle = if (sheet) {
                androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    fontFamily = HandFont,
                    lineHeight = 22.sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                )
            } else {
                androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
            },
            placeholder = {
                val hint = when (voice) {
                    VoiceState.Idle -> R.string.search_hint
                    VoiceState.Preparing -> R.string.preparing
                    is VoiceState.Listening -> R.string.listening
                }
                // «Аркуш»: «Слухаю…» — вишневим, як рамка мікрофона.
                // Підказка трохи правіше курсора — він стоїть перед першою літерою, а не на ній.
                Text(
                    stringResource(hint),
                    color = if (sheet && listening) Oled.Listening else Oled.TextSecondary,
                    modifier = Modifier.padding(start = 3.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty() && !listening) {
                    if (sheet) {
                        TextButton(onClick = { haptics.tap(); onClear() }) { Text(stringResource(R.string.clear), color = Oled.TextSecondary) }
                    } else {
                        IconButton(onClick = { haptics.tap(); onClear() }) {
                            HandIcon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear), tint = Oled.TextSecondary)
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Oled.TextPrimary,
                unfocusedTextColor = Oled.TextPrimary,
                cursorColor = if (sheet) Oled.TextSecondary else Oled.Accent,
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
    val haptics = LocalHaptics.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .then(
                if (LocalSheet.current) {
                    Modifier.pencilFrame(28.dp, seed = 13, ink = if (listening) Oled.Listening else SheetInk).clip(CircleShape)
                } else {
                    Modifier.clip(CircleShape).background(if (listening) Oled.KnobListening else Oled.Knob).border(1.dp, Oled.Glint, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = { haptics.tap(); onClick() }, modifier = Modifier.padding(4.dp)) {
            HandGlyph(
                Glyph.MIC,
                contentDescription = stringResource(if (listening) R.string.voice_stop else R.string.voice_search),
                tint = if (listening) (if (LocalSheet.current) Oled.Listening else Color.White) else Oled.Accent,
            )
        }
    }
}
