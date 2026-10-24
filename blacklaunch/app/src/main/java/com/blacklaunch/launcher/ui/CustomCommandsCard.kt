package com.blacklaunch.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.ui.theme.Oled

/** «Мої команди»: своя фраза → кілька звичайних команд по черзі. */
@Composable
internal fun CustomCommandsCard(
    commands: List<CustomCommand>,
    onSave: (CustomCommand) -> Unit,
    onDelete: (String) -> Unit,
) {
    // null — діалог закрито; порожня команда — нова.
    var editing by remember { mutableStateOf<CustomCommand?>(null) }
    val haptics = LocalHaptics.current
    Card {
        Text(stringResource(R.string.custom_commands_title), color = Oled.TextPrimary, fontSize = 17.sp)
        Note(stringResource(R.string.custom_commands_note))
        commands.forEach { command ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { haptics.tap(); editing = command }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Text(command.phrase, color = Oled.TextPrimary, fontSize = 16.sp)
                Text(
                    command.steps.joinToString(" → "),
                    color = Oled.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ActionOption(R.string.custom_command_add, Icons.Filled.Add) { editing = CustomCommand("", "", emptyList()) }
    }
    editing?.let { command ->
        CustomCommandDialog(
            command = command,
            onSave = { onSave(it); editing = null },
            onDelete = if (command.id.isEmpty()) null else ({ onDelete(command.id); editing = null }),
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CustomCommandDialog(
    command: CustomCommand,
    onSave: (CustomCommand) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var phrase by remember { mutableStateOf(command.phrase) }
    var steps by remember { mutableStateOf(command.steps.joinToString("\n")) }
    val stepList = CustomCommand.clean(steps.lines())
    val canSave = phrase.isNotBlank() && stepList.isNotEmpty()
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Oled.TextPrimary,
        unfocusedTextColor = Oled.TextPrimary,
        cursorColor = Oled.Accent,
        focusedBorderColor = Color(0xFF5A5A5A),
        unfocusedBorderColor = Color(0xFF3A3A3A),
        focusedLabelColor = Oled.TextSecondary,
        unfocusedLabelColor = Oled.TextSecondary,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(stringResource(R.string.custom_commands_title), color = Oled.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.custom_command_phrase)) },
                    placeholder = { Text(stringResource(R.string.custom_command_phrase_hint), color = Oled.TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    minLines = 4,
                    label = { Text(stringResource(R.string.custom_command_steps)) },
                    placeholder = { Text(stringResource(R.string.custom_command_steps_hint), color = Oled.TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                )
                Note(stringResource(R.string.custom_command_steps_note))
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.custom_command_delete), color = Oled.TextSecondary) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = command.id.ifEmpty { CustomCommand.create(phrase, stepList).id }
                    onSave(CustomCommand(id, phrase.trim(), stepList))
                },
                enabled = canSave,
            ) { Text(stringResource(R.string.custom_command_save), color = if (canSave) Oled.TextPrimary else Oled.TextSecondary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
        },
    )
}
