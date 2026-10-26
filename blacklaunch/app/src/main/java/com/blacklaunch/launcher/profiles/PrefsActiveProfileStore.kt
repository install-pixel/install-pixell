package com.blacklaunch.launcher.profiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Активний профіль у SharedPreferences, як і решта простих налаштувань BlackLaunch
 * (DataStore не додаємо: окрема бібліотека тут нічого не дає).
 */
class PrefsActiveProfileStore(context: Context) : ActiveProfileStore {
    private val prefs = context.applicationContext.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    override val activeId: StateFlow<String?> = _activeId.asStateFlow()

    override val previousId: String? get() = prefs.getString(KEY_PREVIOUS, null)

    override fun setActive(id: String) {
        val current = _activeId.value
        prefs.edit().putString(KEY_ACTIVE, id).apply {
            if (current != null && current != id) putString(KEY_PREVIOUS, current)
        }.apply()
        _activeId.value = id
    }

    private companion object {
        const val KEY_ACTIVE = "active_profile"
        const val KEY_PREVIOUS = "previous_profile"
    }
}
