package com.blacklaunch.launcher.global

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Які джерела глобального пошуку увімкнені. */
class GlobalSearchSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("global_search", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(read())
    val enabled: StateFlow<Set<GroupKind>> = _enabled.asStateFlow()

    fun set(kind: GroupKind, on: Boolean) {
        val next = if (on) _enabled.value + kind else _enabled.value - kind
        prefs.edit().putStringSet(KEY, next.map { it.name }.toSet()).apply()
        _enabled.value = next
    }

    private fun read(): Set<GroupKind> =
        prefs.getStringSet(KEY, null)
            ?.mapNotNull { name -> GroupKind.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: DEFAULT_GLOBAL_KINDS

    private companion object {
        const val KEY = "enabled"
    }
}
