package com.blacklaunch.launcher.profiles

import android.content.Context
import com.blacklaunch.launcher.global.GroupKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Що шукати в профілі: усі програми, програми профілю першими або лише їх. */
enum class SearchScope { ALL, PROFILE_FIRST, PROFILE_ONLY }

fun defaultGlobalMode(scope: SearchScope) = if (scope == SearchScope.PROFILE_ONLY) GlobalMode.BY_APPS else GlobalMode.ON

/** Глобальний пошук у профілі: вимкнено / лише те, для чого в профілі є програма / усе. */
enum class GlobalMode { OFF, BY_APPS, ON }

/** Програми за замовчуванням для дзвінків, SMS, календаря й налаштувань (пакети). */
data class RolePackages(
    val dialer: Set<String> = emptySet(),
    val sms: Set<String> = emptySet(),
    val calendar: Set<String> = emptySet(),
    val settings: Set<String> = emptySet(),
)

/** Що дозволяє глобальний пошук у профілі (чисте правило — перевіряється тестами). */
data class GlobalAccess(val kinds: Set<GroupKind>, val canCall: Boolean, val canMessage: Boolean, val shortcutsOfProfileOnly: Boolean) {
    companion object {
        private val LOCAL = setOf(GroupKind.CALCULATOR, GroupKind.PROFILES)

        fun of(mode: GlobalMode, all: Set<GroupKind>, profilePackages: Set<String>, roles: RolePackages): GlobalAccess = when (mode) {
            GlobalMode.ON -> GlobalAccess(all, canCall = true, canMessage = true, shortcutsOfProfileOnly = false)
            GlobalMode.OFF -> GlobalAccess(all intersect LOCAL, canCall = false, canMessage = false, shortcutsOfProfileOnly = true)
            GlobalMode.BY_APPS -> {
                val call = profilePackages.any { it in roles.dialer }
                val message = profilePackages.any { it in roles.sms }
                val kinds = buildSet {
                    addAll(LOCAL)
                    add(GroupKind.SHORTCUTS)
                    if (call || message) add(GroupKind.CONTACTS)
                    if (message) add(GroupKind.SMS)
                    if (profilePackages.any { it in roles.calendar }) add(GroupKind.CALENDAR)
                    if (profilePackages.any { it in roles.settings }) add(GroupKind.SETTINGS)
                }
                GlobalAccess(all intersect kinds, call, message, shortcutsOfProfileOnly = true)
            }
        }
    }
}

/** Вибір для кожного профілю — у налаштуваннях телефона (без зміни бази). */
class PrefsSearchScopes(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("profile_search_scope", Context.MODE_PRIVATE)
    private val _scopes = MutableStateFlow(
        prefs.all.mapNotNull { (id, value) ->
            SearchScope.entries.firstOrNull { it.name == value }?.let { id to it }
        }.toMap(),
    )
    // Вибраний режим глобального пошуку; старе «вимкнено» (до 0.17.1) переходить у OFF.
    private val _globalModes = MutableStateFlow(
        prefs.getStringSet(KEY_GLOBAL_OFF, emptySet()).orEmpty().associateWith { GlobalMode.OFF } +
            prefs.all.keys.filter { it.startsWith(PREFIX_GLOBAL) }.mapNotNull { key ->
                GlobalMode.entries.firstOrNull { it.name == prefs.getString(key, null) }?.let { key.removePrefix(PREFIX_GLOBAL) to it }
            },
    )

    /** Явно вибрані режими; без вибору — «За програмами» для «Лише програми профілю», інакше «Увімкнено». */
    val globalModes: StateFlow<Map<String, GlobalMode>> = _globalModes.asStateFlow()

    fun setGlobalMode(profileId: String, mode: GlobalMode) {
        _globalModes.value = _globalModes.value + (profileId to mode)
        prefs.edit().putString(PREFIX_GLOBAL + profileId, mode.name).apply()
    }

    private val _notificationFilter = MutableStateFlow(prefs.getStringSet(KEY_NOTIF_FILTER, emptySet()).orEmpty().toSet())

    /** Профілі, де сповіщення лише від їхніх програм. */
    val notificationFilter: StateFlow<Set<String>> = _notificationFilter.asStateFlow()

    private val _notificationExtras = MutableStateFlow(
        prefs.all.keys.filter { it.startsWith(PREFIX_EXTRA) }
            .associate { it.removePrefix(PREFIX_EXTRA) to prefs.getStringSet(it, emptySet()).orEmpty().toSet() },
    )

    /** «Також дозволити сповіщення від…»: профіль → пакети програм поза профілем. */
    val notificationExtras: StateFlow<Map<String, Set<String>>> = _notificationExtras.asStateFlow()

    fun setNotificationExtras(profileId: String, packages: Set<String>) {
        _notificationExtras.value = _notificationExtras.value + (profileId to packages)
        prefs.edit().putStringSet(PREFIX_EXTRA + profileId, packages).apply()
    }

    fun setNotificationFilter(profileId: String, on: Boolean) {
        _notificationFilter.value = if (on) _notificationFilter.value + profileId else _notificationFilter.value - profileId
        prefs.edit().putStringSet(KEY_NOTIF_FILTER, _notificationFilter.value).apply()
    }

    val scopes: StateFlow<Map<String, SearchScope>> = _scopes.asStateFlow()

    fun set(profileId: String, scope: SearchScope) {
        prefs.edit().putString(profileId, scope.name).apply()
        _scopes.value = _scopes.value + (profileId to scope)
    }

    private companion object {
        // Окремий ключ: решта ключів — id профілів.
        const val KEY_GLOBAL_OFF = "_global_off"
        const val PREFIX_GLOBAL = "_global_mode_"
        const val KEY_NOTIF_FILTER = "_notification_filter"
        const val PREFIX_EXTRA = "_notif_extra_"
    }
}
