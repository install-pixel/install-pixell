package com.blacklaunch.launcher.commands

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Перевірка «Лише якщо» в момент запуску команди — офіційними API, без стеження у фоні. */
class CommandConditions(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Пристрої, про підключення яких Android щойно повідомив. Аудіоз'єднання встановлюється на секунду-дві
     * пізніше, і без цього «Лише якщо навушники» в момент підключення бачило б, що їх ще немає.
     */
    private val justConnected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun noteBluetooth(address: String, connected: Boolean) {
        val key = address.uppercase()
        if (connected) justConnected.add(key) else justConnected.remove(key)
    }

    suspend fun met(condition: CommandCondition): Boolean = when (condition.kind) {
        ConditionKind.CHARGING -> appContext.getSystemService(BatteryManager::class.java)?.isCharging == true
        ConditionKind.WIRED_HEADSET -> wiredHeadset()
        ConditionKind.BLUETOOTH_DEVICE -> condition.deviceAddress?.let { address ->
            address.uppercase() in justConnected || connectedBluetooth().any { it.equals(address, ignoreCase = true) }
        } ?: false
    }

    private fun wiredHeadset(): Boolean {
        val audio = appContext.getSystemService(AudioManager::class.java) ?: return false
        val wired = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in wired }
    }

    /** Адреси підключених зараз аудіопристроїв Bluetooth (навушники, колонки, авто). */
    @SuppressLint("MissingPermission")
    private suspend fun connectedBluetooth(): Set<String> {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return emptySet()
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptySet()
        if (!adapter.isEnabled) return emptySet()
        val profiles = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(BluetoothProfile.LE_AUDIO)
        }
        return profiles.flatMap { profile ->
            withTimeoutOrNull(PROXY_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                            val addresses = runCatching { proxy.connectedDevices.map { it.address } }.getOrDefault(emptyList())
                            runCatching { adapter.closeProfileProxy(type, proxy) }
                            if (continuation.isActive) continuation.resume(addresses)
                        }

                        override fun onServiceDisconnected(type: Int) = Unit
                    }
                    if (!runCatching { adapter.getProfileProxy(appContext, listener, profile) }.getOrDefault(false) && continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
            }.orEmpty()
        }.toSet()
    }

    private companion object {
        const val PROXY_TIMEOUT_MS = 1_500L
    }
}
