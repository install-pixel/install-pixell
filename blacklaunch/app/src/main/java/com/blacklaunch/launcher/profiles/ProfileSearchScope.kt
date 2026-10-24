package com.blacklaunch.launcher.profiles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Що шукати в профілі: усі програми, програми профілю першими або лише їх. */
enum class SearchScope { ALL, PROFILE_FIRST, PROFILE_ONLY }

/** Вибір для кожного профілю — у налаштуваннях телефона (без зміни бази). */
class PrefsSearchScopes(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("profile_search_scope", Context.MODE_PRIVATE)
    private val _scopes = MutableStateFlow(
        prefs.all.mapNotNull { (id, value) ->
            SearchScope.entries.firstOrNull { it.name == value }?.let { id to it }
        }.toMap(),
    )
    private val _globalOff = MutableStateFlow(prefs.getStringSet(KEY_GLOBAL_OFF, emptySet()).orEmpty().toSet())

    /** Профілі, у яких глобальний пошук вимкнено. */
    val globalOff: StateFlow<Set<String>> = _globalOff.asStateFlow()

    private val _notificationFilter = MutableStateFlow(prefs.getStringSet(KEY_NOTIF_FILTER, emptySet()).orEmpty().toSet())

    /** Профілі, де сповіщення лише від їхніх програм. */
    val notificationFilter: StateFlow<Set<String>> = _notificationFilter.asStateFlow()

    fun setNotificationFilter(profileId: String, on: Boolean) {
        _notificationFilter.value = if (on) _notificationFilter.value + profileId else _notificationFilter.value - profileId
        prefs.edit().putStringSet(KEY_NOTIF_FILTER, _notificationFilter.value).apply()
    }

    fun setGlobal(profileId: String, on: Boolean) {
        _globalOff.value = if (on) _globalOff.value - profileId else _globalOff.value + profileId
        prefs.edit().putStringSet(KEY_GLOBAL_OFF, _globalOff.value).apply()
    }
    val scopes: StateFlow<Map<String, SearchScope>> = _scopes.asStateFlow()

    fun set(profileId: String, scope: SearchScope) {
        prefs.edit().putString(profileId, scope.name).apply()
        _scopes.value = _scopes.value + (profileId to scope)
    }

    private companion object {
        // Окремий ключ: решта ключів — id профілів.
        const val KEY_GLOBAL_OFF = "_global_off"
        const val KEY_NOTIF_FILTER = "_notification_filter"
    }
}
