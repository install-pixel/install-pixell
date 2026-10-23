package com.blacklaunch.launcher.search

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Власні псевдоніми користувача (пакет → назви), зберігаються локально.
 * Редактор у налаштуваннях — Етап 11; на Етапі 7 сховище може переїхати в Room.
 */
class UserAliasStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("user_aliases", Context.MODE_PRIVATE)
    private val _aliases = MutableStateFlow(read())
    val aliases: StateFlow<Map<String, Set<String>>> = _aliases.asStateFlow()

    fun add(packageName: String, alias: String) {
        val clean = alias.trim()
        if (clean.isEmpty()) return
        write(packageName, (_aliases.value[packageName] ?: emptySet()) + clean)
    }

    fun remove(packageName: String, alias: String) {
        write(packageName, (_aliases.value[packageName] ?: emptySet()) - alias)
    }

    private fun write(packageName: String, values: Set<String>) {
        prefs.edit().apply {
            if (values.isEmpty()) remove(packageName) else putStringSet(packageName, values)
        }.apply()
        _aliases.value = read()
    }

    private fun read(): Map<String, Set<String>> =
        prefs.all.mapNotNull { (key, value) ->
            (value as? Set<*>)?.filterIsInstance<String>()?.toSet()?.let { key to it }
        }.toMap()
}
