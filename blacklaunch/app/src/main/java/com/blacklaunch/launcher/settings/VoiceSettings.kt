package com.blacklaunch.launcher.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OfflineVoiceMode {
    /** Лише системне розпізнавання (онлайн або вбудоване офлайн від Google). */
    OFF,

    /** Власна модель — коли немає інтернету. */
    WHEN_OFFLINE,

    /** Власна модель — завжди. */
    ALWAYS,
}

class VoiceSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(
        prefs.getString(KEY_MODE, null)
            ?.let { name -> OfflineVoiceMode.entries.firstOrNull { it.name == name } }
            ?: OfflineVoiceMode.WHEN_OFFLINE,
    )
    val mode: StateFlow<OfflineVoiceMode> = _mode.asStateFlow()

    fun setMode(mode: OfflineVoiceMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private companion object {
        const val KEY_MODE = "offline_mode"
    }
}
