package com.blacklaunch.launcher.settings

import android.content.Context
import com.blacklaunch.launcher.system.WallpaperTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Appearance(
    /** Повертати чорні шпалери, якщо їх змінили (тема, інша програма). */
    val autoBlackWallpaper: Boolean = false,
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.HOME_AND_LOCK,
    /** Сірі одноколірні значки в результатах пошуку. */
    val monochromeIcons: Boolean = true,
    /** Де на головному екрані стоїть панель закріплених програм. */
    val dockPosition: DockPosition = DockPosition.BOTTOM,
)

enum class DockPosition { TOP, CENTER, BOTTOM }

/** Налаштування оформлення. На Етапі 7 можуть переїхати в DataStore разом з іншими. */
class AppearanceSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _appearance = MutableStateFlow(read())
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    /** Ідентифікатор шпалер, які встановив BlackLaunch, щоб помітити заміну. */
    var blackWallpaperId: Int
        get() = prefs.getInt(KEY_WALLPAPER_ID, NO_ID)
        set(value) = prefs.edit().putInt(KEY_WALLPAPER_ID, value).apply()

    fun update(transform: (Appearance) -> Appearance) {
        val next = transform(_appearance.value)
        prefs.edit()
            .putBoolean(KEY_AUTO, next.autoBlackWallpaper)
            .putString(KEY_TARGET, next.wallpaperTarget.name)
            .putBoolean(KEY_MONO, next.monochromeIcons)
            .putString(KEY_DOCK, next.dockPosition.name)
            .apply()
        _appearance.value = next
    }

    private fun read(): Appearance {
        val defaults = Appearance()
        return Appearance(
            autoBlackWallpaper = prefs.getBoolean(KEY_AUTO, defaults.autoBlackWallpaper),
            wallpaperTarget = prefs.getString(KEY_TARGET, null)
                ?.let { name -> WallpaperTarget.entries.firstOrNull { it.name == name } }
                ?: defaults.wallpaperTarget,
            monochromeIcons = prefs.getBoolean(KEY_MONO, defaults.monochromeIcons),
            dockPosition = prefs.getString(KEY_DOCK, null)
                ?.let { name -> DockPosition.entries.firstOrNull { it.name == name } }
                ?: defaults.dockPosition,
        )
    }

    companion object {
        const val NO_ID = -1
        private const val KEY_AUTO = "auto_black_wallpaper"
        private const val KEY_TARGET = "wallpaper_target"
        private const val KEY_MONO = "monochrome_icons"
        private const val KEY_DOCK = "dock_position"
        private const val KEY_WALLPAPER_ID = "black_wallpaper_id"
    }
}
