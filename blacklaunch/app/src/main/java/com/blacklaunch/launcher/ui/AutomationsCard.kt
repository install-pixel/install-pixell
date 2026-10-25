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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.launcher.R
import com.blacklaunch.launcher.automation.Automation
import com.blacklaunch.launcher.automation.TriggerKind
import com.blacklaunch.launcher.commands.CustomCommand
import com.blacklaunch.launcher.ui.theme.Oled

/** Спарений Bluetooth-пристрій для вибору в автоматизації. */
data class PairedDevice(val name: String, val address: String)

/** Автоматизації «Коли → Що»: подія запускає «Мою команду». */
@Composable
internal fun AutomationsCard(
    automations: List<Automation>,
    commands: List<CustomCommand>,
    bluetoothAllowed: Boolean,
    pairedDevices: () -> List<PairedDevice>,
    onRequestBluetooth: () -> Unit,
    canOpenDirectly: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onSave: (Automation) -> Unit,
    onDelete: (String) -> Unit,
) {
    // null — діалог закрито; порожній id — нова.
    var editing by remember { mutableStateOf<Automation?>(null) }
    val haptics = LocalHaptics.current
    Card {
        Text(stringResource(R.string.automations_title), color = Oled.TextPrimary, fontSize = 17.sp)
        Note(stringResource(R.string.automations_note))
        automations.forEach { automation ->
            val phrase = commands.firstOrNull { it.id == automation.commandId }?.phrase ?: "—"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { haptics.tap(); editing = automation }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val trigger = stringResource(automation.kind.label())
                    Text(listOfNotNull(trigger, automation.deviceName).joinToString(" · "), color = Oled.TextPrimary, fontSize = 15.sp)
                    Text("→ $phrase", color = Oled.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(
                    checked = automation.enabled,
                    onCheckedChange = { haptics.tap(); onSave(automation.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Oled.TextSecondary, checkedThumbColor = Oled.TextPrimary),
                )
            }
        }
        if (commands.isEmpty()) {
            Note(stringResource(R.string.automation_no_commands))
        } else {
            ActionOption(R.string.automation_add, Icons.Filled.Add) {
                editing = Automation("", TriggerKind.BLUETOOTH_CONNECTED, commands.first().id)
            }
        }
        Note(stringResource(R.string.automation_live_note))
    }
    Card {
        Text(stringResource(R.string.automation_direct_title), color = Oled.TextPrimary, fontSize = 17.sp)
        Note(stringResource(if (canOpenDirectly) R.string.automation_direct_on else R.string.automation_direct_off))
        if (!canOpenDirectly) ActionOption(R.string.automation_direct_open, Icons.Filled.Settings, onOpenOverlaySettings)
    }
    editing?.let { automation ->
        AutomationDialog(
            automation = automation,
            commands = commands,
            bluetoothAllowed = bluetoothAllowed,
            pairedDevices = pairedDevices,
            onRequestBluetooth = onRequestBluetooth,
            onSave = { onSave(it); editing = null },
            onDelete = if (automation.id.isEmpty()) null else ({ onDelete(automation.id); editing = null }),
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AutomationDialog(
    automation: Automation,
    commands: List<CustomCommand>,
    bluetoothAllowed: Boolean,
    pairedDevices: () -> List<PairedDevice>,
    onRequestBluetooth: () -> Unit,
    onSave: (Automation) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(automation.kind) }
    var device by remember { mutableStateOf(automation.deviceAddress?.let { PairedDevice(automation.deviceName.orEmpty(), it) }) }
    var commandId by remember { mutableStateOf(automation.commandId) }
    val bluetooth = kind == TriggerKind.BLUETOOTH_CONNECTED || kind == TriggerKind.BLUETOOTH_DISCONNECTED
    val devices = remember(bluetoothAllowed) { if (bluetoothAllowed) pairedDevices() else emptyList() }
    val canSave = commands.any { it.id == commandId } && (!bluetooth || device != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        title = { Text(stringResource(R.string.automations_title), color = Oled.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.automation_when), color = Oled.TextSecondary, fontSize = 13.sp)
                TriggerKind.entries.forEach { option ->
                    Choice(stringResource(option.label()), selected = kind == option) { kind = option }
                }
                if (bluetooth) {
                    Text(stringResource(R.string.automation_device), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    when {
                        !bluetoothAllowed -> ActionOption(R.string.automation_bt_permission, Icons.Filled.Lock, onRequestBluetooth)
                        devices.isEmpty() -> Note(stringResource(R.string.automation_no_devices))
                        else -> devices.forEach { option ->
                            Choice(option.name, selected = device?.address == option.address) { device = option }
                        }
                    }
                }
                Text(stringResource(R.string.automation_then), color = Oled.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                commands.forEach { command ->
                    Choice(command.phrase, selected = commandId == command.id) { commandId = command.id }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.automation_delete), color = Oled.TextSecondary) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val chosen = device.takeIf { bluetooth }
                    val base = if (automation.id.isEmpty()) Automation.create(kind, commandId, null, null) else automation
                    onSave(base.copy(kind = kind, commandId = commandId, deviceAddress = chosen?.address, deviceName = chosen?.name))
                },
            ) { Text(stringResource(R.string.custom_command_save), color = if (canSave) Oled.TextPrimary else Oled.TextSecondary) }
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

@StringRes
private fun TriggerKind.label(): Int = when (this) {
    TriggerKind.BLUETOOTH_CONNECTED -> R.string.trigger_bt_on
    TriggerKind.BLUETOOTH_DISCONNECTED -> R.string.trigger_bt_off
    TriggerKind.POWER_CONNECTED -> R.string.trigger_power_on
    TriggerKind.POWER_DISCONNECTED -> R.string.trigger_power_off
    TriggerKind.HEADSET_PLUGGED -> R.string.trigger_headset_on
    TriggerKind.HEADSET_UNPLUGGED -> R.string.trigger_headset_off
}
