package com.blacklaunch.launcher.profiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Що видно на головному екрані й у порожньому пошуку — окремо для кожного профілю. */
data class ProfileDisplay(
    val glance: Boolean = false,
    val profileLabel: Boolean = true,
    val hideStatusBar: Boolean = true,
    val suggestions: Boolean = true,
    val recentQueries: Boolean = true,
    val allAppsButton: Boolean = true,
) {
    fun encode(): String = listOf(glance, profileLabel, hideStatusBar, suggestions, recentQueries, allAppsButton)
        .joinToString(",") { if (it) "1" else "0" }

    companion object {
        fun decode(text: String?): ProfileDisplay? {
            val bits = text?.split(',')?.map { it == "1" } ?: return null
            if (bits.size != 6) return null
            return ProfileDisplay(bits[0], bits[1], bits[2], bits[3], bits[4], bits[5])
        }
    }
}

/**
 * Налаштування екрана для кожного профілю. Профіль без власного вибору бере [defaults] —
 * значення, що були загальними до 0.18.0, тож після оновлення нічого не змінюється.
 */
class PrefsProfileDisplay(context: Context, private val defaults: () -> ProfileDisplay) {
    private val prefs = context.applicationContext.getSharedPreferences("profile_display", Context.MODE_PRIVATE)
    private val _displays = MutableStateFlow(
        prefs.all.mapNotNull { (id, value) -> ProfileDisplay.decode(value as? String)?.let { id to it } }.toMap(),
    )
    val displays: StateFlow<Map<String, ProfileDisplay>> = _displays.asStateFlow()

    fun of(profileId: String?): ProfileDisplay = profileId?.let { _displays.value[it] } ?: defaults()

    fun set(profileId: String, display: ProfileDisplay) = setAll(listOf(profileId), display)

    fun setAll(profileIds: List<String>, display: ProfileDisplay) {
        _displays.value = _displays.value + profileIds.associateWith { display }
        prefs.edit().apply { profileIds.forEach { putString(it, display.encode()) } }.apply()
    }
}
