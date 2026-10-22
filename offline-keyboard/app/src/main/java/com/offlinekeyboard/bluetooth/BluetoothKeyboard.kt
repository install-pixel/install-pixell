package com.offlinekeyboard.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.offlinekeyboard.core.HidStroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/** The HID device profile exists from Android 9. */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
val bluetoothKeyboardSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

enum class BluetoothState { UNSUPPORTED, NO_PERMISSION, BLUETOOTH_OFF, STOPPED, STARTING, WAITING, CONNECTED, FAILED }

data class BluetoothStatus(val state: BluetoothState, val deviceName: String? = null)

private val unsupported = MutableStateFlow(BluetoothStatus(BluetoothState.UNSUPPORTED))

/** Safe on every Android version: callers need no version checks. */
object Bluetooth {
    val status: StateFlow<BluetoothStatus>
        get() = if (bluetoothKeyboardSupported) BluetoothKeyboard.status else unsupported

    val isConnected: Boolean get() = status.value.state == BluetoothState.CONNECTED

    val deviceName: String? get() = status.value.deviceName

    fun start(context: Context) {
        if (bluetoothKeyboardSupported) BluetoothKeyboard.start(context)
    }

    fun stop() {
        if (bluetoothKeyboardSupported) BluetoothKeyboard.stop()
    }

    fun send(stroke: HidStroke): Boolean = bluetoothKeyboardSupported && BluetoothKeyboard.send(stroke)

    fun hasPermission(context: Context): Boolean = bluetoothKeyboardSupported && BluetoothKeyboard.hasPermission(context)

    val permissions: Array<String> get() = if (bluetoothKeyboardSupported) BluetoothKeyboard.permissions else emptyArray()

    fun pairedDevices(context: Context): List<BluetoothDevice> =
        if (bluetoothKeyboardSupported) BluetoothKeyboard.pairedDevices(context) else emptyList()

    fun deviceName(device: BluetoothDevice): String =
        if (bluetoothKeyboardSupported) BluetoothKeyboard.deviceName(device) else device.address

    fun connect(device: BluetoothDevice): Boolean = bluetoothKeyboardSupported && BluetoothKeyboard.connect(device)
}

/**
 * The phone as a Bluetooth keyboard (HID device profile, Android 9+). Bluetooth is a direct radio link to
 * the paired computer; nothing goes to the internet. Lives as long as the app process, so the settings and
 * the keyboard share one registration.
 */
@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission") // Every entry point checks hasPermission() first.
object BluetoothKeyboard {

    private val _status = MutableStateFlow(BluetoothStatus(BluetoothState.STOPPED))
    val status: StateFlow<BluetoothStatus> = _status

    private var appContext: Context? = null
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Permissions to request before [start] (empty below Android 12). */
    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Registers the keyboard with the phone's Bluetooth; afterwards the computer can connect. */
    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!hasPermission(app)) return set(BluetoothState.NO_PERMISSION)
        val adapter = adapter(app) ?: return set(BluetoothState.UNSUPPORTED)
        if (!adapter.isEnabled) return set(BluetoothState.BLUETOOTH_OFF)
        if (hid != null || _status.value.state == BluetoothState.STARTING) return
        set(BluetoothState.STARTING)
        val ok = adapter.getProfileProxy(app, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) set(BluetoothState.UNSUPPORTED)
    }

    @Synchronized
    fun stop() {
        val proxy = hid
        hid = null
        host = null
        if (proxy != null) {
            runCatching { proxy.unregisterApp() }
            appContext?.let { adapter(it)?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy) }
        }
        set(BluetoothState.STOPPED)
    }

    /** Paired devices, to reconnect without pairing again. */
    fun pairedDevices(context: Context): List<BluetoothDevice> {
        if (!hasPermission(context)) return emptyList()
        return adapter(context)?.bondedDevices?.sortedBy { it.name ?: it.address }.orEmpty()
    }

    fun deviceName(device: BluetoothDevice): String = runCatching { device.name }.getOrNull() ?: device.address

    fun connect(device: BluetoothDevice): Boolean = hid?.connect(device) == true

    /** Presses and releases one key. Returns false when no computer is connected. */
    fun send(stroke: HidStroke): Boolean {
        val proxy = hid ?: return false
        val device = host ?: return false
        val down = byteArrayOf(stroke.modifiers.toByte(), 0, stroke.usage.toByte(), 0, 0, 0, 0, 0)
        val up = ByteArray(8)
        return proxy.sendReport(device, REPORT_ID, down) && proxy.sendReport(device, REPORT_ID, up)
    }

    private fun set(state: BluetoothState, device: BluetoothDevice? = host) {
        _status.value = BluetoothStatus(state, device?.let(::deviceName))
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val device = proxy as BluetoothHidDevice
            hid = device
            val sdp = BluetoothHidDeviceAppSdpSettings(
                NAME, DESCRIPTION, PROVIDER, BluetoothHidDevice.SUBCLASS1_KEYBOARD, DESCRIPTOR,
            )
            if (!device.registerApp(sdp, null, null, executor, callback)) set(BluetoothState.FAILED)
        }

        override fun onServiceDisconnected(profile: Int) {
            hid = null
            host = null
            set(BluetoothState.STOPPED)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!registered) {
                if (hid != null) set(BluetoothState.FAILED)
                return
            }
            set(BluetoothState.WAITING)
            // Reconnect the computer used last time, if it is paired.
            val last = pluggedDevice ?: lastHost()
            if (last != null) hid?.connect(last)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    rememberHost(device)
                    set(BluetoothState.CONNECTED, device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> if (host == null || host == device) {
                    host = null
                    if (hid != null) set(BluetoothState.WAITING, null)
                }
            }
        }
    }

    private fun prefs() = appContext?.getSharedPreferences("bluetooth_keyboard", Context.MODE_PRIVATE)

    private fun rememberHost(device: BluetoothDevice) {
        prefs()?.edit()?.putString(KEY_LAST, device.address)?.apply()
    }

    private fun lastHost(): BluetoothDevice? {
        val address = prefs()?.getString(KEY_LAST, null) ?: return null
        val context = appContext ?: return null
        return pairedDevices(context).firstOrNull { it.address == address }
    }

    private const val KEY_LAST = "last_host"
    private const val REPORT_ID = 1
    private const val NAME = "Offlingo"
    private const val DESCRIPTION = "Phone keyboard"
    private const val PROVIDER = "Offlingo"

    /** Standard keyboard: 8 modifier bits, a reserved byte, 5 LEDs, 6 keys; report id 1. */
    private val DESCRIPTOR = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, REPORT_ID,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
        0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0,
    ).map { it.toByte() }.toByteArray()
}
