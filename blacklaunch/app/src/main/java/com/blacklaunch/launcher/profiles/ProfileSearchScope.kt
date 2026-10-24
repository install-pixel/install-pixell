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
    val scopes: StateFlow<Map<String, SearchScope>> = _scopes.asStateFlow()

    fun set(profileId: String, scope: SearchScope) {
        prefs.edit().putString(profileId, scope.name).apply()
        _scopes.value = _scopes.value + (profileId to scope)
    }
}
