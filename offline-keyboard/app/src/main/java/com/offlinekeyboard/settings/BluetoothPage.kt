package com.offlinekeyboard.settings

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinekeyboard.R
import com.offlinekeyboard.bluetooth.Bluetooth
import com.offlinekeyboard.bluetooth.BluetoothState
import com.offlinekeyboard.bluetooth.bluetoothKeyboardSupported

/** The phone as a Bluetooth keyboard for a computer: switch, pairing help and paired computers. */
@Composable
internal fun BluetoothPage(s: KeyboardSettings, update: UpdateSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by Bluetooth.status.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }

    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Bluetooth.start(context)
        refresh++
    }

    fun turnOn() {
        update { it.copy(bluetoothKeyboard = true) }
        if (Bluetooth.permissions.isNotEmpty() && !Bluetooth.hasPermission(context)) {
            permissionRequest.launch(Bluetooth.permissions)
        } else {
            Bluetooth.start(context)
        }
    }

    LifecycleResumeEffect(s.bluetoothKeyboard) {
        // Bluetooth may have been switched on in the meantime.
        if (s.bluetoothKeyboard) Bluetooth.start(context)
        refresh++
        onPauseOrDispose { }
    }

    PageScaffold(stringResource(R.string.page_bluetooth), onBack) {
        Text(stringResource(R.string.bluetooth_intro), style = MaterialTheme.typography.bodyMedium)

        if (!bluetoothKeyboardSupported) {
            Text(stringResource(R.string.bluetooth_unsupported_android), color = MaterialTheme.colorScheme.error)
            return@PageScaffold
        }

        Section(stringResource(R.string.bluetooth_section_switch)) {
            SwitchRow(stringResource(R.string.bluetooth_enable), s.bluetoothKeyboard) { on ->
                if (on) {
                    turnOn()
                } else {
                    update { it.copy(bluetoothKeyboard = false) }
                    Bluetooth.stop()
                }
            }
            if (s.bluetoothKeyboard) {
                val (text, ok) = when (status.state) {
                    BluetoothState.CONNECTED -> stringResource(R.string.bluetooth_state_connected, status.deviceName.orEmpty()) to true
                    BluetoothState.WAITING -> stringResource(R.string.bluetooth_state_waiting) to false
                    BluetoothState.STARTING -> stringResource(R.string.bluetooth_state_starting) to false
                    BluetoothState.BLUETOOTH_OFF -> stringResource(R.string.bluetooth_state_off) to false
                    BluetoothState.NO_PERMISSION -> stringResource(R.string.bluetooth_state_permission) to false
                    BluetoothState.UNSUPPORTED, BluetoothState.FAILED -> stringResource(R.string.bluetooth_state_failed) to false
                    BluetoothState.STOPPED -> stringResource(R.string.bluetooth_state_starting) to false
                }
                StatusLine(text, ok)
                when (status.state) {
                    BluetoothState.NO_PERMISSION -> ActionButton(onClick = { turnOn() }) {
                        Text(stringResource(R.string.bluetooth_allow))
                    }
                    BluetoothState.BLUETOOTH_OFF -> ActionButton(onClick = {
                        openSafely(context, Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) { Text(stringResource(R.string.bluetooth_open_settings)) }
                    else -> Unit
                }
            }
        }

        if (s.bluetoothKeyboard && status.state in setOf(BluetoothState.WAITING, BluetoothState.CONNECTED)) {
            Section(stringResource(R.string.bluetooth_section_pair)) {
                Text(stringResource(R.string.bluetooth_pair_steps), style = MaterialTheme.typography.bodyMedium)
                ActionButton(onClick = {
                    openSafely(
                        context,
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS),
                    )
                }) { Text(stringResource(R.string.bluetooth_make_visible)) }

                val devices = remember(refresh, status) { Bluetooth.pairedDevices(context) }
                if (devices.isNotEmpty()) {
                    RowDivider()
                    Text(stringResource(R.string.bluetooth_paired), style = MaterialTheme.typography.titleSmall)
                    // ★ marks the devices a long press on the Bluetooth button switches between, in the order marked.
                    Text(stringResource(R.string.bluetooth_switch_hint), style = MaterialTheme.typography.bodySmall)
                    var chosenVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    // The switching order (★1, ★2, …), read once per change rather than for every row.
                    val order = remember(chosenVersion, devices) { Bluetooth.switchHosts(context).map { it.address } }
                    // The marked devices first, in their order; then the rest as the phone lists them.
                    val sorted = remember(order, devices) {
                        devices.sortedBy { d -> order.indexOf(d.address).let { if (it < 0) Int.MAX_VALUE else it } }
                    }
                    // A window of about four devices with its own scroll: a long list does not stretch the page.
                    val listScroll = androidx.compose.foundation.rememberScrollState()
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = (DEVICE_ROW_DP * 4 + DEVICE_ROW_DP / 2).dp)
                            .verticalScroll(listScroll),
                    ) {
                    for (device in sorted) {
                        val name = Bluetooth.deviceName(device)
                        val place = order.indexOf(device.address) + 1
                        val chosen = place > 0
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = DEVICE_ROW_DP.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InlineButton(onClick = {
                                Bluetooth.setSwitchHost(device.address, !chosen)
                                chosenVersion++
                            }) { Text(if (chosen && place > 0) "★$place" else "☆") }
                            Text(name, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            if (status.state == BluetoothState.CONNECTED && status.deviceName == name) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            } else {
                                InlineButton(onClick = { Bluetooth.connect(device) }) {
                                    Text(stringResource(R.string.bluetooth_connect))
                                }
                            }
                        }
                    }
                    }
                }
            }

            Section(stringResource(R.string.bluetooth_section_use)) {
                Text(stringResource(R.string.bluetooth_use_steps), style = MaterialTheme.typography.bodyMedium)
                var sample by rememberSaveable { mutableStateOf("") }
                TryField(sample, stringResource(R.string.bluetooth_open_keyboard), height = 84.dp) { sample = it }
            }
        }

        Section(stringResource(R.string.bluetooth_section_notes)) {
            Text(stringResource(R.string.bluetooth_notes), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun openSafely(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing to open on this phone; the status line already says what is missing.
    } catch (e: SecurityException) {
        // Making the phone visible needs the Bluetooth permission; the status line asks for it.
    }
}

private const val DISCOVERABLE_SECONDS = 120
private const val DEVICE_ROW_DP = 48
