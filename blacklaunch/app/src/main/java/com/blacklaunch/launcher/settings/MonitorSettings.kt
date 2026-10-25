package com.blacklaunch.launcher.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Плитки монітора системи — кожну можна вимкнути. */
enum class MonitorTile { BATTERY, MEMORY, STORAGE, THERMAL, NETWORK, DETAILS, CURRENT_GRAPH, MEMORY_GRAPH, USAGE, DEVICE }

/** Колір світіння шкал — приглушені тони, щоб не випадати з чорної скляної теми (1.1.1). */
enum class MonitorAccent(val argb: Long) { BLUE(0xFF8FB4C9), GREEN(0xFF9DBB98), AMBER(0xFFC9A77C), GREY(0xFFB9BEC6) }

data class MonitorLook(val tiles: Set<MonitorTile> = MonitorTile.entries.toSet(), val accent: MonitorAccent = MonitorAccent.BLUE)

/** Вигляд монітора — лише на телефоні. */
class MonitorSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)
    private val _look = MutableStateFlow(read())
    val look: StateFlow<MonitorLook> = _look.asStateFlow()

    fun setTile(tile: MonitorTile, on: Boolean) = save(_look.value.copy(tiles = if (on) _look.value.tiles + tile else _look.value.tiles - tile))

    fun setAccent(accent: MonitorAccent) = save(_look.value.copy(accent = accent))

    private fun save(look: MonitorLook) {
        prefs.edit()
            .putStringSet(KEY_TILES, look.tiles.map { it.name }.toSet())
            .putString(KEY_ACCENT, look.accent.name)
            .putStringSet(KEY_KNOWN, MonitorTile.entries.map { it.name }.toSet())
            .apply()
        _look.value = look
    }

    private fun read(): MonitorLook = MonitorLook(
        // Плитки, що з'явились після збереженого вибору (наприклад, «Пристрій» з 1.1.1), — увімкнені.
        tiles = prefs.getStringSet(KEY_TILES, null)?.let { saved ->
            val known = prefs.getStringSet(KEY_KNOWN, null) ?: MonitorTile.entries.filter { it != MonitorTile.DEVICE }.map { it.name }.toSet()
            MonitorTile.entries.filter { it.name in saved || it.name !in known }.toSet()
        } ?: MonitorTile.entries.toSet(),
        accent = MonitorAccent.entries.firstOrNull { it.name == prefs.getString(KEY_ACCENT, null) } ?: MonitorAccent.BLUE,
    )

    private companion object {
        const val KEY_TILES = "tiles"
        const val KEY_ACCENT = "accent"
        const val KEY_KNOWN = "known"
    }
}
