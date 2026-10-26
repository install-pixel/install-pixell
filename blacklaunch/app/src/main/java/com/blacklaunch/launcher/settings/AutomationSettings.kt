package com.blacklaunch.launcher.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** «Голос і режими»: розумне перемикання режимів, час щогодини й читання повідомлень — усе вимкнено за замовчуванням. */
class AutomationSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("automation_settings", Context.MODE_PRIVATE)

    private val _smartProfiles = MutableStateFlow(prefs.getBoolean(KEY_SMART, false))
    val smartProfiles: StateFlow<Boolean> = _smartProfiles.asStateFlow()

    private val _hourlyChime = MutableStateFlow(prefs.getBoolean(KEY_HOURLY, false))
    val hourlyChime: StateFlow<Boolean> = _hourlyChime.asStateFlow()

    private val _readMessages = MutableStateFlow(prefs.getBoolean(KEY_READ, false))
    val readMessages: StateFlow<Boolean> = _readMessages.asStateFlow()

    private val _modeAppsOnly = MutableStateFlow(prefs.getBoolean(KEY_MODE_APPS, false))
    /** Кроки команд відкривають лише програми поточного режиму (програми на його головному екрані). */
    val modeAppsOnly: StateFlow<Boolean> = _modeAppsOnly.asStateFlow()

    fun setModeAppsOnly(on: Boolean) = save(KEY_MODE_APPS, on, _modeAppsOnly)

    fun setSmartProfiles(on: Boolean) = save(KEY_SMART, on, _smartProfiles)

    fun setHourlyChime(on: Boolean) = save(KEY_HOURLY, on, _hourlyChime)

    fun setReadMessages(on: Boolean) = save(KEY_READ, on, _readMessages)

    private fun save(key: String, on: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, on).apply()
        flow.value = on
    }

    private companion object {
        const val KEY_SMART = "smart_profiles"
        const val KEY_HOURLY = "hourly_chime"
        const val KEY_READ = "read_messages"
        const val KEY_MODE_APPS = "mode_apps_only"
    }
}
