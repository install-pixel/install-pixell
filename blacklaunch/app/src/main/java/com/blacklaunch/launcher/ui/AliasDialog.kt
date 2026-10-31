package com.blacklaunch.launcher.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(R.string.alias_title, app.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                aliases.sorted().forEach { alias ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(alias, color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(alias) }) {
                            HandIcon(
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
                    colors = glassFieldColors(),
                    shape = InputShape,
                )
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.alias_add), add, Tone.Confirm, enabled = text.isNotBlank()) },
        dismissButton = { DialogButton(stringResource(R.string.alias_done), onDismiss) },
    )
}
