package com.blacklaunch.launcher.profiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.blacklaunch.launcher.system.GlanceParts

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
    /**
     * Значки програм профілю на екрані й у порожньому пошуку. Вимкнено — програми лишаються в профілі
     * (пошук «лише свої», фільтр сповіщень, глобальний пошук «за програмами»), просто їх не видно.
     */
    val showApps: Boolean = true,
    /** Що показує «Короткий огляд» саме в цьому профілі; null — як у загальних налаштуваннях. */
    val ownGlance: GlanceParts? = null,
) {
    fun encode(): String {
        val bits = listOf(glance, profileLabel, hideStatusBar, suggestions, recentQueries, allAppsButton, quickSwitch, showApps)
            .joinToString(",") { if (it) "1" else "0" }
        val own = ownGlance?.let { g -> ",g" + listOf(g.clock, g.alarm, g.event, g.battery).joinToString("") { if (it) "1" else "0" } }
        return bits + own.orEmpty()
    }

    companion object {
        /** До 0.28.0 бітів було 6, до 0.30.0 — 7; нових налаштувань тоді не було — значення за замовчуванням. */
        fun decode(text: String?): ProfileDisplay? {
            val parts = text?.split(',') ?: return null
            // «g1011» у кінці — свій вибір для «Короткого огляду» (з 0.31.0).
            val own = parts.lastOrNull()?.takeIf { it.length == 5 && it.startsWith("g") }?.let { g ->
                GlanceParts(clock = g[1] == '1', alarm = g[2] == '1', event = g[3] == '1', battery = g[4] == '1')
            }
            val bits = (if (own != null) parts.dropLast(1) else parts).map { it == "1" }
            if (bits.size !in 6..8) return null
            return ProfileDisplay(
                bits[0], bits[1], bits[2], bits[3], bits[4], bits[5], bits.getOrElse(6) { true }, bits.getOrElse(7) { true }, own,
            )
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
