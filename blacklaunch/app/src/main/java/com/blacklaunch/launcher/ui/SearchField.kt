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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.ui.theme.Oled

private val FieldShape = RoundedCornerShape(28.dp)

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    listening: Boolean,
    partialSpeech: String,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Повторюється і після повороту екрана, тому клавіатура не зникає.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = if (listening) partialSpeech else query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .clip(FieldShape)
                .background(Oled.Surface)
                .border(1.dp, if (listening) Oled.KnobListening else Oled.Glint, FieldShape)
                .focusRequester(focusRequester),
            singleLine = true,
            readOnly = listening,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
            placeholder = { Text(if (listening) "Слухаю…" else "Пошук програм", color = Oled.TextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty() && !listening) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистити", tint = Oled.TextSecondary)
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
                contentDescription = if (listening) "Зупинити голосове введення" else "Голосовий пошук",
                tint = if (listening) Color.White else Oled.Accent,
            )
        }
    }
}
