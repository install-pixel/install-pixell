package com.blacklaunch.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
internal fun OpenDirectlyCard(canOpenDirectly: Boolean, onOpenOverlaySettings: () -> Unit) {
    Card {
        Text(stringResource(R.string.automation_direct_title), color = Oled.TextPrimary, fontSize = 17.sp)
        Note(stringResource(R.string.automations_note))
        Note(stringResource(if (canOpenDirectly) R.string.automation_direct_on else R.string.automation_direct_off))
        if (!canOpenDirectly) ActionOption(R.string.automation_direct_open, Icons.Filled.Settings, onOpenOverlaySettings)
        Note(stringResource(R.string.automation_live_note))
    }
}

/** «Лише якщо»: будь-коли, вибраний Bluetooth-пристрій, зарядка чи дротові навушники. */
@Composable
internal fun ConditionDialog(current: CommandCondition?, devices: DeviceAccess, onPick: (CommandCondition?) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(current?.kind) }
    var device by remember { mutableStateOf(current?.deviceAddress?.let { PairedDevice(current.deviceName.orEmpty(), it) }) }
    val canSave = kind != ConditionKind.BLUETOOTH_DEVICE || device != null
    PickDialog(
        title = R.string.condition_title,
        onDismiss = onDismiss,
        confirm = if (canSave) ({ onPick(kind?.let { CommandCondition(it, device?.address.takeIf { _ -> it == ConditionKind.BLUETOOTH_DEVICE }, device?.name.takeIf { _ -> it == ConditionKind.BLUETOOTH_DEVICE }) }) }) else null,
    ) {
        Choice(stringResource(R.string.condition_always), selected = kind == null) { kind = null }
        ConditionKind.entries.forEach { option -> Choice(stringResource(option.label()), selected = kind == option) { kind = option } }
        if (kind == ConditionKind.BLUETOOTH_DEVICE) DevicePicker(devices, device) { device = it }
    }
}

/** «Запускати саму»: подія, від якої команда стартує без фрази. */
@Composable
internal fun TriggerDialog(devices: DeviceAccess, onPick: (CommandTrigger) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(TriggerKind.BLUETOOTH_CONNECTED) }
    var device by remember { mutableStateOf<PairedDevice?>(null) }
    val bluetooth = kind == TriggerKind.BLUETOOTH_CONNECTED || kind == TriggerKind.BLUETOOTH_DISCONNECTED
    val canSave = !bluetooth || device != null
    PickDialog(
        title = R.string.command_auto_add,
        onDismiss = onDismiss,
        confirm = if (canSave) ({ onPick(CommandTrigger(kind, device?.address.takeIf { bluetooth }, device?.name.takeIf { bluetooth })) }) else null,
    ) {
        TriggerKind.entries.forEach { option -> Choice(stringResource(option.label()), selected = kind == option) { kind = option } }
        if (bluetooth) DevicePicker(devices, device) { device = it }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(stringResource(title), color = Oled.TextPrimary) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = { confirm?.invoke() }, enabled = confirm != null) {
                Text(stringResource(R.string.custom_step_add_confirm), color = if (confirm != null) Oled.TextPrimary else Oled.TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Oled.TextSecondary) }
        },
    )
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
internal fun triggerText(trigger: CommandTrigger): String =
    listOfNotNull(stringResource(trigger.kind.label()), trigger.deviceName).joinToString(" · ")

@Composable
internal fun conditionText(condition: CommandCondition): String =
    if (condition.kind == ConditionKind.BLUETOOTH_DEVICE) {
        stringResource(R.string.condition_device_named, condition.deviceName ?: condition.deviceAddress.orEmpty())
    } else {
        stringResource(condition.kind.label())
    }

@StringRes
private fun TriggerKind.label(): Int = when (this) {
    TriggerKind.BLUETOOTH_CONNECTED -> R.string.trigger_bt_on
    TriggerKind.BLUETOOTH_DISCONNECTED -> R.string.trigger_bt_off
    TriggerKind.POWER_CONNECTED -> R.string.trigger_power_on
    TriggerKind.POWER_DISCONNECTED -> R.string.trigger_power_off
    TriggerKind.HEADSET_PLUGGED -> R.string.trigger_headset_on
    TriggerKind.HEADSET_UNPLUGGED -> R.string.trigger_headset_off
}

@StringRes
private fun ConditionKind.label(): Int = when (this) {
    ConditionKind.BLUETOOTH_DEVICE -> R.string.condition_device
    ConditionKind.CHARGING -> R.string.condition_charging
    ConditionKind.WIRED_HEADSET -> R.string.condition_wired
}
