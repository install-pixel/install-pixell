package com.offlinekeyboard.settings

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                    HorizontalDivider()
                    Text(stringResource(R.string.bluetooth_paired), style = MaterialTheme.typography.titleSmall)
                    for (device in devices) {
                        val name = Bluetooth.deviceName(device)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, modifier = Modifier.weight(1f))
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

            Section(stringResource(R.string.bluetooth_section_use)) {
                Text(stringResource(R.string.bluetooth_use_steps), style = MaterialTheme.typography.bodyMedium)
                var sample by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.bluetooth_open_keyboard)) },
                )
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
