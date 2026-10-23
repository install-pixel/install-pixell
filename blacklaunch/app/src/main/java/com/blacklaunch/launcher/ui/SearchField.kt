package com.blacklaunch.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import com.blacklaunch.launcher.R

private val ListeningColor = Color(0xFFE53935)

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

    OutlinedTextField(
        value = if (listening) partialSpeech else query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        readOnly = listening,
        placeholder = { Text(if (listening) "Слухаю…" else "Пошук програм", color = Color.Gray) },
        trailingIcon = {
            Row {
                if (query.isNotEmpty() && !listening) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистити", tint = Color.Gray)
                    }
                }
                IconButton(onClick = onMicClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = if (listening) "Зупинити голосове введення" else "Голосовий пошук",
                        tint = if (listening) ListeningColor else Color.Gray,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedBorderColor = if (listening) ListeningColor else Color.DarkGray,
            unfocusedBorderColor = Color.DarkGray,
            focusedContainerColor = Color.Black,
            unfocusedContainerColor = Color.Black,
        ),
    )
}
