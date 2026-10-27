package com.blacklaunch.launcher.games

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Три гри в олівцевому стилі котика. */
enum class GameKind { STARS, BLOCKS, SNAKE }

/**
 * Ігри: чи ввімкнені (вимкнені — не видно ні в налаштуваннях, ні в пошуку), збережена гра (продовжити з того місця)
 * і рекорд кожної. Усе лише на телефоні.
 */
class GameStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("games", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _best = MutableStateFlow(GameKind.entries.associateWith { prefs.getInt(KEY_BEST + it.name, 0) })
    val best: StateFlow<Map<GameKind, Int>> = _best.asStateFlow()

    private val _saved = MutableStateFlow(GameKind.entries.filter { prefs.contains(KEY_STATE + it.name) }.toSet())

    /** Ігри, які можна продовжити. */
    val saved: StateFlow<Set<GameKind>> = _saved.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun state(kind: GameKind): String? = prefs.getString(KEY_STATE + kind.name, null)

    fun save(kind: GameKind, state: String?) {
        prefs.edit().apply { if (state != null) putString(KEY_STATE + kind.name, state) else remove(KEY_STATE + kind.name) }.apply()
        _saved.value = if (state != null) _saved.value + kind else _saved.value - kind
    }

    /** true — новий рекорд. */
    fun record(kind: GameKind, score: Int): Boolean {
        if (score <= (_best.value[kind] ?: 0)) return false
        _best.value = _best.value + (kind to score)
        prefs.edit().putInt(KEY_BEST + kind.name, score).apply()
        return true
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_STATE = "state_"
        const val KEY_BEST = "best_"
    }
}
