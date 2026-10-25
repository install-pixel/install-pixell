package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.search.AppSearchEngine
import com.blacklaunch.launcher.ui.theme.Oled
import com.blacklaunch.launcher.voice.VoiceCommand
import com.blacklaunch.launcher.voice.VoiceCommandParser

/**
 * «Мої команди»: своя фраза → кроки по черзі. Кроки збираються з готових блоків
 * (програма, дія, режим); кожен зберігається звичайною командою мовою телефону,
 * тож її видно й можна виправити текстом.
 */
@Composable
internal fun CustomCommandsCard(
    commands: List<CustomCommand>,
    apps: List<AppInfo>,
    profiles: List<String>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onSave: (CustomCommand) -> Unit,
    onDelete: (String) -> Unit,
) {
    // null — список команд; порожня команда — нова.
    var editing by remember { mutableStateOf<CustomCommand?>(null) }
    val haptics = LocalHaptics.current
    val current = editing
    if (current != null) {
        CommandEditor(
            command = current,
            apps = apps,
            profiles = profiles,
            iconFor = iconFor,
            onSave = { onSave(it); editing = null },
            onDelete = if (current.id.isEmpty()) null else ({ onDelete(current.id); editing = null }),
            onCancel = { editing = null },
        )
        return
    }
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
}

private enum class StepPicker { MENU, APP, ACTION, PROFILE, TEXT }

@Composable
private fun CommandEditor(
    command: CustomCommand,
    apps: List<AppInfo>,
    profiles: List<String>,
    iconFor: suspend (AppInfo) -> ImageBitmap?,
    onSave: (CustomCommand) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var phrase by remember { mutableStateOf(command.phrase) }
    var steps by remember { mutableStateOf(command.steps) }
    var picker by remember { mutableStateOf<StepPicker?>(null) }
    val canSave = phrase.isNotBlank() && steps.isNotEmpty()
    val addStep: (String) -> Unit = { step -> steps = CustomCommand.clean(steps + step); picker = null }
    Card {
        Text(stringResource(R.string.custom_commands_title), color = Oled.TextPrimary, fontSize = 17.sp)
        OutlinedTextField(
            value = phrase,
            onValueChange = { phrase = it },
            singleLine = true,
            label = { Text(stringResource(R.string.custom_command_phrase)) },
            placeholder = { Text(stringResource(R.string.custom_command_phrase_hint), color = Oled.TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Text(stringResource(R.string.custom_command_steps), color = Oled.TextSecondary, fontSize = 13.sp)
        if (steps.isEmpty()) Note(stringResource(R.string.custom_steps_empty))
        steps.forEachIndexed { index, step ->
            StepRow(
                number = index + 1,
                step = step,
                onUp = if (index > 0) ({ steps = steps.swap(index, index - 1) }) else null,
                onDown = if (index < steps.lastIndex) ({ steps = steps.swap(index, index + 1) }) else null,
                onRemove = { steps = steps.filterIndexed { i, _ -> i != index } },
            )
        }
        ActionOption(R.string.custom_step_add, Icons.Filled.Add) { picker = StepPicker.MENU }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.custom_command_delete), color = Oled.TextSecondary) }
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
            TextButton(
                onClick = {
                    val id = command.id.ifEmpty { CustomCommand.create(phrase, steps).id }
                    onSave(CustomCommand(id, phrase.trim(), steps))
                },
                enabled = canSave,
            ) { Text(stringResource(R.string.custom_command_save), color = if (canSave) Oled.TextPrimary else Oled.TextSecondary) }
        }
    }
    when (picker) {
        StepPicker.MENU -> StepMenu(onPick = { picker = it }, onDismiss = { picker = null })
        StepPicker.APP -> AppStepDialog(apps, iconFor, onPick = addStep, onDismiss = { picker = null })
        StepPicker.ACTION -> ActionStepDialog(onPick = addStep, onDismiss = { picker = null })
        StepPicker.PROFILE -> ProfileStepDialog(profiles, onPick = addStep, onDismiss = { picker = null })
        StepPicker.TEXT -> TextStepDialog(onPick = addStep, onDismiss = { picker = null })
        null -> Unit
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

/** Крок: номер, значок за видом (програма / режим / дія / текст), текст і кнопки порядку. */
@Composable
private fun StepRow(number: Int, step: String, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onRemove: () -> Unit) {
    val icon = when (VoiceCommandParser.parse(step)) {
        is VoiceCommand.Open, VoiceCommand.OpenLast -> Icons.Filled.PlayArrow
        is VoiceCommand.SwitchProfile -> Icons.Filled.AccountCircle
        is VoiceCommand.Action -> Icons.Filled.Settings
        null -> Icons.Filled.Edit
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$number", color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.width(18.dp))
        Icon(icon, contentDescription = null, tint = Oled.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(step, color = Oled.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        SmallButton(Icons.Filled.KeyboardArrowUp, R.string.custom_step_up, onUp)
        SmallButton(Icons.Filled.KeyboardArrowDown, R.string.custom_step_down, onDown)
        SmallButton(Icons.Filled.Close, R.string.custom_step_remove, onRemove)
    }
}

@Composable
private fun SmallButton(icon: ImageVector, @StringRes description: Int, onClick: (() -> Unit)?) {
    val haptics = LocalHaptics.current
    IconButton(onClick = { haptics.tap(); onClick?.invoke() }, enabled = onClick != null, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = stringResource(description),
            tint = if (onClick != null) Oled.TextSecondary else Oled.TextSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun StepMenu(onPick: (StepPicker) -> Unit, onDismiss: () -> Unit) {
    PickerDialog(R.string.custom_step_add, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionOption(R.string.custom_step_app, Icons.Filled.PlayArrow) { onPick(StepPicker.APP) }
            ActionOption(R.string.custom_step_action, Icons.Filled.Settings) { onPick(StepPicker.ACTION) }
            ActionOption(R.string.custom_step_profile, Icons.Filled.AccountCircle) { onPick(StepPicker.PROFILE) }
            ActionOption(R.string.custom_step_text, Icons.Filled.Edit) { onPick(StepPicker.TEXT) }
        }
    }
}

@Composable
private fun AppStepDialog(apps: List<AppInfo>, iconFor: suspend (AppInfo) -> ImageBitmap?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val engine = remember { AppSearchEngine() }
    val index = remember(apps) { engine.index(apps) }
    val shown = if (query.isBlank()) apps else index.search(query)
    val template = stringResource(R.string.step_open)
    val haptics = LocalHaptics.current
    PickerDialog(R.string.custom_step_app, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.custom_step_search), color = Oled.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(shown, key = { it.key }) { app ->
                    val icon by produceState<ImageBitmap?>(null, app.key) { value = iconFor(app) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { haptics.tap(); onPick(template.format(app.label)) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) } }
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = Oled.TextPrimary, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

/** Дії без параметрів — одразу кроком; яскравість, таймер, будильник — із вибором значення. */
@Composable
private fun ActionStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var param by remember { mutableStateOf<Int?>(null) }
    val simple = listOf(
        R.string.step_flashlight_on, R.string.step_flashlight_off,
        R.string.step_play, R.string.step_pause, R.string.step_next,
        R.string.step_volume_up, R.string.step_volume_down, R.string.step_mute,
        R.string.step_wifi, R.string.step_bluetooth, R.string.step_mobile_data, R.string.step_time,
    ).map { stringResource(it) }
    when (param) {
        R.string.step_brightness -> BrightnessDialog(onPick, onDismiss)
        R.string.step_timer -> TimerDialog(onPick, onDismiss)
        R.string.step_alarm -> AlarmDialog(onPick, onDismiss)
        else -> PickerDialog(R.string.custom_step_action, onDismiss) {
            LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ActionOption(R.string.custom_action_brightness, Icons.Filled.Settings) { param = R.string.step_brightness } }
                item { ActionOption(R.string.custom_action_timer, Icons.Filled.Settings) { param = R.string.step_timer } }
                item { ActionOption(R.string.custom_action_alarm, Icons.Filled.Settings) { param = R.string.step_alarm } }
                items(simple) { text -> ActionOption(text.replaceFirstChar { it.uppercase() }, Icons.Filled.Settings) { onPick(text) } }
            }
        }
    }
}

@Composable
private fun BrightnessDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var percent by remember { mutableStateOf(50f) }
    val template = stringResource(R.string.step_brightness)
    PickerDialog(R.string.custom_action_brightness, onDismiss, confirm = { onPick(template.format(percent.toInt())) }) {
        Column {
            Text(stringResource(R.string.custom_step_percent, percent.toInt()), color = Oled.TextPrimary, fontSize = 16.sp)
            Slider(
                value = percent,
                onValueChange = { percent = it },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Oled.TextPrimary, activeTrackColor = Oled.TextPrimary),
            )
        }
    }
}

@Composable
private fun TimerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("10") }
    val minutes = text.toIntOrNull()?.takeIf { it in 1..999 }
    val template = stringResource(R.string.step_timer)
    PickerDialog(R.string.custom_action_timer, onDismiss, confirm = minutes?.let { { onPick(template.format(it)) } }) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(3) },
            singleLine = true,
            label = { Text(stringResource(R.string.custom_step_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
    }
}

@Composable
private fun AlarmDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var hour by remember { mutableStateOf("7") }
    var minute by remember { mutableStateOf("00") }
    val h = hour.toIntOrNull()?.takeIf { it in 0..23 }
    val m = minute.toIntOrNull()?.takeIf { it in 0..59 }
    val template = stringResource(R.string.step_alarm)
    PickerDialog(R.string.custom_action_alarm, onDismiss, confirm = if (h != null && m != null) ({ onPick(template.format(h, m)) }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hour,
                onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                singleLine = true,
                label = { Text(stringResource(R.string.custom_step_hour)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
            Text(" : ", color = Oled.TextPrimary, fontSize = 20.sp)
            OutlinedTextField(
                value = minute,
                onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                singleLine = true,
                label = { Text(stringResource(R.string.custom_step_minute)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
            )
        }
    }
}

@Composable
private fun ProfileStepDialog(profiles: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val template = stringResource(R.string.step_profile)
    PickerDialog(R.string.custom_step_profile, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profiles.forEach { name -> ActionOption(name, Icons.Filled.AccountCircle) { onPick(template.format(name)) } }
        }
    }
}

@Composable
private fun TextStepDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    PickerDialog(R.string.custom_step_text, onDismiss, confirm = if (text.isNotBlank()) ({ onPick(text.trim()) }) else null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.custom_command_steps_hint), color = Oled.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Note(stringResource(R.string.custom_command_steps_note))
        }
    }
}

/** Темний діалог вибору; [confirm] = null — без кнопки «Додати» (вибір одним дотиком). */
@Composable
private fun PickerDialog(@StringRes title: Int, onDismiss: () -> Unit, confirm: (() -> Unit)? = null, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(stringResource(title), color = Oled.TextPrimary) },
        text = content,
        confirmButton = {
            if (confirm != null) {
                TextButton(onClick = confirm) { Text(stringResource(R.string.custom_step_add_confirm), color = Oled.TextPrimary) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
        },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Oled.TextPrimary,
    unfocusedTextColor = Oled.TextPrimary,
    cursorColor = Oled.Accent,
    focusedBorderColor = Color(0xFF5A5A5A),
    unfocusedBorderColor = Color(0xFF3A3A3A),
    focusedLabelColor = Oled.TextSecondary,
    unfocusedLabelColor = Oled.TextSecondary,
)
