package com.blacklaunch.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.ui.theme.Oled

/** Власні назви програми для пошуку й голосу («як я її називаю»). */
@Composable
fun AliasDialog(
    app: AppInfo,
    aliases: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) onAdd(text.trim())
        text = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(stringResource(R.string.alias_title, app.label), color = Oled.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                aliases.sorted().forEach { alias ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(alias, color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(alias) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.alias_remove, alias),
                                tint = Oled.TextSecondary,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.alias_hint), color = Oled.TextSecondary) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Oled.TextPrimary,
                        unfocusedTextColor = Oled.TextPrimary,
                        cursorColor = Oled.Accent,
                        focusedBorderColor = Color(0xFF5A5A5A),
                        unfocusedBorderColor = Color(0xFF3A3A3A),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = add, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.alias_add), color = Oled.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.alias_done), color = Oled.TextSecondary) }
        },
    )
}
