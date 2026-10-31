package com.blacklaunch.launcher.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.automation.TriggerKind
import com.blacklaunch.launcher.commands.CommandCondition
import com.blacklaunch.launcher.commands.CommandTrigger
import com.blacklaunch.launcher.commands.ConditionKind
import com.blacklaunch.launcher.ui.theme.Oled

/** Спарений Bluetooth-пристрій для вибору в команді. */
data class PairedDevice(val name: String, val address: String)

/** Доступ до списку спарених пристроїв (потрібен дозвіл «Пристрої поблизу»). */
data class DeviceAccess(val allowed: Boolean, val list: () -> List<PairedDevice>, val request: () -> Unit)

/** «Відкривати програми одразу»: з дозволом «Поверх інших вікон» команда, що запустилась сама, відкриває програму без дотику. */
@Composable
internal fun OpenDirectlyCard(canOpenDirectly: Boolean, lastSeen: String?, onOpenOverlaySettings: () -> Unit) {
    Card {
        Text(stringResource(R.string.automation_direct_title), color = Oled.TextPrimary, fontSize = 17.sp)
        Note(stringResource(R.string.automations_note))
        Note(stringResource(if (canOpenDirectly) R.string.automation_direct_on else R.string.automation_direct_off))
        if (!canOpenDirectly) ActionOption(R.string.automation_direct_open, Icons.Filled.Settings, onOpenOverlaySettings)
        Note(stringResource(R.string.automation_live_note))
        // Перевірка: чи доходять події до Black Glass (підключили навушники — тут має з'явитися).
        Note(stringResource(R.string.automation_last_seen, lastSeen ?: stringResource(R.string.automation_last_seen_none)))
    }
}

/**
 * Нова умова «Лише якщо»: що підключено, час і дні, батарея, профіль, мережа; «навпаки» — коли НЕ виконується.
 * Усе перевіряється лише в момент запуску команди.
 */
@Composable
internal fun ConditionDialog(devices: DeviceAccess, profiles: List<String>, onPick: (CommandCondition) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(ConditionKind.BLUETOOTH_DEVICE) }
    var device by remember { mutableStateOf<PairedDevice?>(null) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(0) }
    var percent by remember { mutableStateOf(50f) }
    var chosenProfiles by remember { mutableStateOf(emptySet<String>()) }
    var negate by remember { mutableStateOf(false) }
    val fromMin = parseTime(from)
    val toMin = parseTime(to)
    val canSave = when (kind) {
        ConditionKind.BLUETOOTH_DEVICE -> device != null
        // Час: або обидві межі, або жодної (тоді — лише дні тижня).
        ConditionKind.TIME -> (fromMin != null && toMin != null && fromMin != toMin) || (from.isBlank() && to.isBlank() && days != 0)
        ConditionKind.PROFILE -> chosenProfiles.isNotEmpty()
        else -> true
    }
    PickDialog(
        title = R.string.condition_add,
        onDismiss = onDismiss,
        confirm = if (canSave) ({
            onPick(
                CommandCondition(
                    kind,
                    deviceAddress = device?.address.takeIf { kind == ConditionKind.BLUETOOTH_DEVICE },
                    deviceName = device?.name.takeIf { kind == ConditionKind.BLUETOOTH_DEVICE },
                    from = fromMin.takeIf { kind == ConditionKind.TIME },
                    to = toMin.takeIf { kind == ConditionKind.TIME },
                    days = if (kind == ConditionKind.TIME) days else 0,
                    percent = percent.roundToInt().takeIf { kind == ConditionKind.BATTERY_ABOVE || kind == ConditionKind.BATTERY_BELOW },
                    // У порядку списку режимів; кілька — будь-який з них.
                    profile = profiles.filter { it in chosenProfiles }.joinToString(CommandCondition.PROFILE_SEPARATOR)
                        .takeIf { kind == ConditionKind.PROFILE && it.isNotEmpty() },
                    negate = negate,
                ),
            )
        }) else null,
    ) {
        Note(stringResource(R.string.condition_dialog_note))
        ConditionKind.entries.forEach { option -> Choice(stringResource(option.label()), selected = kind == option) { kind = option } }
        when (kind) {
            ConditionKind.BLUETOOTH_DEVICE -> DevicePicker(devices, device) { device = it }
            ConditionKind.TIME -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(R.string.condition_from, from, Modifier.weight(1f)) { from = it }
                    TimeField(R.string.condition_to, to, Modifier.weight(1f)) { to = it }
                }
                Note(stringResource(R.string.condition_time_note))
                Text(stringResource(R.string.condition_days), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                DayChips(days) { days = it }
            }
            ConditionKind.BATTERY_ABOVE, ConditionKind.BATTERY_BELOW -> {
                Text(stringResource(R.string.condition_percent, percent.roundToInt()), color = Oled.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = percent,
                    onValueChange = { percent = it },
                    valueRange = 5f..95f,
                    steps = 17,
                    colors = SliderDefaults.colors(thumbColor = Oled.TextPrimary, activeTrackColor = Oled.Accent, inactiveTrackColor = Color(0xFF3A3A3A)),
                )
            }
            ConditionKind.PROFILE -> {
                Note(stringResource(R.string.condition_profiles_note))
                profiles.forEach { name ->
                    CheckChoice(name, checked = name in chosenProfiles) { chosenProfiles = if (name in chosenProfiles) chosenProfiles - name else chosenProfiles + name }
                }
            }
            else -> Unit
        }
        SwitchRow(stringResource(R.string.condition_negate), stringResource(R.string.condition_negate_note), negate) { negate = it }
    }
}

/** «07:30» → хвилини від півночі; також «7», «730», «7.30». */
internal fun parseTime(text: String): Int? {
    val digits = text.filter(Char::isDigit)
    if (digits.isEmpty() || digits.length > 4) return null
    val (h, m) = if (digits.length <= 2) digits.toInt() to 0 else digits.dropLast(2).toInt() to digits.takeLast(2).toInt()
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

private fun formatTime(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun TimeField(@StringRes label: Int, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == ':' || it == '.' }.take(5)) },
        singleLine = true,
        label = { Text(stringResource(label)) },
        placeholder = { Text("07:00", color = Oled.TextSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = glassFieldColors(),
        shape = FieldShape,
    )
}

/** Дні тижня мовою телефону, з понеділка; нічого не вибрано — щодня. */
@Composable
private fun DayChips(days: Int, onChange: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    val names = weekdayNames()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEachIndexed { i, name ->
            val bit = 1 shl i
            val on = days and bit != 0
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color(0xFF3A3A3A) else Color(0xFF1C1C1C))
                    .clickable { haptics.tap(); onChange(days xor bit) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(name, color = if (on) Oled.TextPrimary else Oled.TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** Короткі назви днів (пн … нд) мовою телефону. */
private fun weekdayNames(): List<String> {
    val symbols = java.text.DateFormatSymbols.getInstance().shortWeekdays
    // Calendar: SUNDAY = 1 … SATURDAY = 7.
    return listOf(2, 3, 4, 5, 6, 7, 1).map { symbols[it].trimEnd('.') }
}

/** «Запускати саму»: подія, від якої команда стартує без фрази (Bluetooth, зарядка, навушники, час). */
@Composable
internal fun TriggerDialog(devices: DeviceAccess, profiles: List<String>, onPick: (CommandTrigger) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(TriggerKind.BLUETOOTH_CONNECTED) }
    var device by remember { mutableStateOf<PairedDevice?>(null) }
    var profile by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(0) }
    val bluetooth = kind == TriggerKind.BLUETOOTH_CONNECTED || kind == TriggerKind.BLUETOOTH_DISCONNECTED
    val minute = parseTime(time)
    val forProfile = kind == TriggerKind.PROFILE_ON || kind == TriggerKind.PROFILE_OFF
    val canSave = when {
        bluetooth -> device != null
        kind == TriggerKind.TIME -> minute != null
        forProfile -> profile != null
        else -> true
    }
    PickDialog(
        title = R.string.command_auto_add,
        onDismiss = onDismiss,
        confirm = if (canSave) ({
            onPick(
                CommandTrigger(
                    kind, device?.address.takeIf { bluetooth }, device?.name.takeIf { bluetooth },
                    minute = minute.takeIf { kind == TriggerKind.TIME }, days = if (kind == TriggerKind.TIME) days else 0,
                    profile = profile.takeIf { forProfile },
                ),
            )
        }) else null,
    ) {
        Note(stringResource(R.string.event_dialog_note))
        TriggerKind.entries.forEach { option -> Choice(stringResource(option.label()), selected = kind == option) { kind = option } }
        if (bluetooth) DevicePicker(devices, device) { device = it }
        if (kind == TriggerKind.TIME) {
            TimeField(R.string.trigger_time_at_label, time, Modifier.fillMaxWidth()) { time = it }
            Text(stringResource(R.string.condition_days), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            DayChips(days) { days = it }
            Note(stringResource(R.string.trigger_time_note))
        }
        if (forProfile) {
            Text(stringResource(R.string.trigger_profile_pick), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            profiles.forEach { name -> Choice(name, selected = profile == name) { profile = name } }
        }
    }
}

/**
 * «Голос і режими»: розумне перемикання режимів, час щогодини й читання повідомлень у навушники.
 * Кожне — окремим перемикачем, усе вимкнено за замовчуванням.
 */
@Composable
internal fun VoiceExtrasCard(
    modeAppsOnly: Boolean,
    onModeAppsOnly: (Boolean) -> Unit,
    smartProfiles: Boolean,
    hourlyChime: Boolean,
    readMessages: Boolean,
    notificationAccess: Boolean,
    onSmartProfiles: (Boolean) -> Unit,
    onHourlyChime: (Boolean) -> Unit,
    onReadMessages: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    Card {
        Text(stringResource(R.string.extras_title), color = Oled.TextPrimary, fontSize = 17.sp)
        SwitchRow(stringResource(R.string.extras_mode_apps), stringResource(R.string.extras_mode_apps_note), modeAppsOnly, onModeAppsOnly)
        SwitchRow(stringResource(R.string.extras_smart), stringResource(R.string.extras_smart_note), smartProfiles, onSmartProfiles)
        SwitchRow(stringResource(R.string.extras_hourly), stringResource(R.string.extras_hourly_note), hourlyChime, onHourlyChime)
        SwitchRow(stringResource(R.string.extras_read), stringResource(R.string.extras_read_note), readMessages, onReadMessages)
        // Без доступу до сповіщень Android їх Black Glass не передає — читати нічого.
        if (readMessages && !notificationAccess) {
            Note(stringResource(R.string.extras_read_access))
            ActionOption(R.string.perm_notification_access, Icons.Filled.Lock, onOpenNotificationAccess)
        }
    }
}

@Composable
private fun DevicePicker(devices: DeviceAccess, selected: PairedDevice?, onSelect: (PairedDevice) -> Unit) {
    val list = remember(devices.allowed) { if (devices.allowed) devices.list() else emptyList() }
    Text(stringResource(R.string.automation_device), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    when {
        !devices.allowed -> ActionOption(R.string.automation_bt_permission, Icons.Filled.Lock, devices.request)
        list.isEmpty() -> Note(stringResource(R.string.automation_no_devices))
        else -> list.forEach { option -> Choice(option.name, selected = selected?.address == option.address) { onSelect(option) } }
    }
}

@Composable
private fun PickDialog(@StringRes title: Int, onDismiss: () -> Unit, confirm: (() -> Unit)?, content: @Composable () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(title)) },
        text = { Column(Modifier.bouncyScroll(), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } },
        confirmButton = {
            DialogButton(stringResource(R.string.custom_step_add_confirm), { confirm?.invoke() }, Tone.Confirm, enabled = confirm != null)
        },
        dismissButton = { DialogButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/** Позначка для вибору кількох (режими умови). */
@Composable
private fun CheckChoice(label: String, checked: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { haptics.tap(); onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { haptics.tap(); onClick() },
            colors = CheckboxDefaults.colors(checkedColor = Oled.TextPrimary, uncheckedColor = Oled.TextSecondary, checkmarkColor = Color.Black),
        )
        Text(label, color = Oled.TextPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { haptics.tap(); onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = { haptics.tap(); onClick() },
            colors = RadioButtonDefaults.colors(selectedColor = Oled.TextPrimary, unselectedColor = Oled.TextSecondary),
        )
        Text(label, color = Oled.TextPrimary, fontSize = 15.sp)
    }
}

/** «Підключено Bluetooth-пристрій · Мої навушники». */
@Composable
internal fun triggerText(trigger: CommandTrigger): String {
    if (trigger.kind == TriggerKind.TIME && trigger.minute != null) {
        val names = weekdayNames()
        val days = if (trigger.days == 0) {
            stringResource(R.string.trigger_time_daily)
        } else {
            names.filterIndexed { i, _ -> trigger.days and (1 shl i) != 0 }.joinToString(", ")
        }
        return stringResource(R.string.trigger_time_text, formatTime(trigger.minute), days)
    }
    if (trigger.kind == TriggerKind.PROFILE_ON || trigger.kind == TriggerKind.PROFILE_OFF) {
        return listOfNotNull(stringResource(trigger.kind.label()), trigger.profile).joinToString(" · ")
    }
    return listOfNotNull(stringResource(trigger.kind.label()), trigger.deviceName).joinToString(" · ")
}

@Composable
internal fun conditionText(condition: CommandCondition): String {
    val base = when (condition.kind) {
        ConditionKind.BLUETOOTH_DEVICE -> stringResource(R.string.condition_device_named, condition.deviceName ?: condition.deviceAddress.orEmpty())
        ConditionKind.TIME -> {
            val names = weekdayNames()
            listOfNotNull(
                if (condition.from != null && condition.to != null) {
                    stringResource(R.string.condition_time_range, formatTime(condition.from), formatTime(condition.to))
                } else {
                    null
                },
                condition.days.takeIf { it != 0 }?.let { bits -> names.filterIndexed { i, _ -> bits and (1 shl i) != 0 }.joinToString(", ") },
            ).joinToString(" · ")
        }
        ConditionKind.BATTERY_ABOVE -> stringResource(R.string.condition_battery_above_value, condition.percent ?: 0)
        ConditionKind.BATTERY_BELOW -> stringResource(R.string.condition_battery_below_value, condition.percent ?: 0)
        ConditionKind.PROFILE -> condition.profileNames.let { names ->
            if (names.size == 1) stringResource(R.string.condition_profile_named, names.first()) else stringResource(R.string.condition_profiles_named, names.joinToString(", "))
        }
        else -> stringResource(condition.kind.label())
    }
    return if (condition.negate) stringResource(R.string.condition_not, base) else base
}

/** Кілька умов — «… і …». */
@Composable
internal fun conditionsText(conditions: List<CommandCondition>): String =
    conditions.map { conditionText(it) }.joinToString(stringResource(R.string.condition_and))

@StringRes
private fun TriggerKind.label(): Int = when (this) {
    TriggerKind.BLUETOOTH_CONNECTED -> R.string.trigger_bt_on
    TriggerKind.BLUETOOTH_DISCONNECTED -> R.string.trigger_bt_off
    TriggerKind.POWER_CONNECTED -> R.string.trigger_power_on
    TriggerKind.POWER_DISCONNECTED -> R.string.trigger_power_off
    TriggerKind.HEADSET_PLUGGED -> R.string.trigger_headset_on
    TriggerKind.HEADSET_UNPLUGGED -> R.string.trigger_headset_off
    TriggerKind.TIME -> R.string.trigger_time
    TriggerKind.PROFILE_ON -> R.string.trigger_profile_on
    TriggerKind.PROFILE_OFF -> R.string.trigger_profile_off
}

@StringRes
private fun ConditionKind.label(): Int = when (this) {
    ConditionKind.BLUETOOTH_DEVICE -> R.string.condition_device
    ConditionKind.CHARGING -> R.string.condition_charging
    ConditionKind.WIRED_HEADSET -> R.string.condition_wired
    ConditionKind.TIME -> R.string.condition_time
    ConditionKind.BATTERY_ABOVE -> R.string.condition_battery_above
    ConditionKind.BATTERY_BELOW -> R.string.condition_battery_below
    ConditionKind.PROFILE -> R.string.condition_profile
    ConditionKind.WIFI -> R.string.condition_wifi
    ConditionKind.MOBILE_DATA -> R.string.condition_mobile
}
