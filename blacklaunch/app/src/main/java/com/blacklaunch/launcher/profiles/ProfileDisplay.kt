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
    /** Профіль у швидкому перемиканні (подвійний дотик, плитка, назва профілю, свайпи). */
    val quickSwitch: Boolean = true,
) {
    fun encode(): String = listOf(glance, profileLabel, hideStatusBar, suggestions, recentQueries, allAppsButton, quickSwitch)
        .joinToString(",") { if (it) "1" else "0" }

    companion object {
        /** До 0.28.0 бітів було 6 — тоді профіль у швидкому перемиканні. */
        fun decode(text: String?): ProfileDisplay? {
            val bits = text?.split(',')?.map { it == "1" } ?: return null
            if (bits.size != 6 && bits.size != 7) return null
            return ProfileDisplay(bits[0], bits[1], bits[2], bits[3], bits[4], bits[5], bits.getOrElse(6) { true })
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

    fun set(profileId: String, display: ProfileDisplay) {
        _displays.value = _displays.value + (profileId to display)
        prefs.edit().putString(profileId, display.encode()).apply()
    }

    /** «Застосувати до всіх» — крім швидкого перемикання: його кожен профіль зберігає свій. */
    fun setAll(profileIds: List<String>, display: ProfileDisplay) {
        val updated = profileIds.associateWith { display.copy(quickSwitch = of(it).quickSwitch) }
        _displays.value = _displays.value + updated
        prefs.edit().apply { updated.forEach { (id, value) -> putString(id, value.encode()) } }.apply()
    }
}
