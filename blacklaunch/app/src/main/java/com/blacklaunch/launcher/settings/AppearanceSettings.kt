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
    /**
     * Перемикання профілів свайпом вліво/вправо на головному екрані. Для нових встановлень вимкнено (0.35.0):
     * свайп легко зачепити, а профіль може вимкнути звук. Збережений вибір не змінюється.
     */
    val profileSwipe: Boolean = false,
    /** Плашка з назвою профілю вгорі головного екрана. */
    val showProfileLabel: Boolean = true,
    /** Легкий вібровідгук на натисканнях — однаково в усьому BlackLaunch. */
    val haptics: Boolean = true,
    /** Порожній пошук: підказки програм за часом дня й нещодавні. */
    val suggestions: Boolean = true,
    /** Порожній пошук: останні запити. */
    val recentQueries: Boolean = true,
    /** Ховати час, мережу й батарею, поки головний екран чорний і нічого не відкрито. */
    val hideStatusBar: Boolean = true,
    /** «Короткий огляд»: дата, будильник, подія, батарея вгорі головного екрана. */
    val glance: Boolean = false,
    /** Рядок «Усі програми» в порожньому пошуку. */
    val allAppsButton: Boolean = true,
    /** Що показує «Короткий огляд» (однаково для всіх профілів, де він увімкнений). */
    val glanceClock: Boolean = true,
    val glanceAlarm: Boolean = true,
    val glanceEvent: Boolean = true,
    val glanceBattery: Boolean = true,
    /** Тема «Аркуш»: усе від руки олівцем (етап 12d). */
    val sheet: Boolean = false,
    /** Просити в системи найвищу частоту екрана (120 Гц), щоб рух був плавнішим. */
    val highRefresh: Boolean = true,
)

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
            .putBoolean(KEY_SWIPE, next.profileSwipe)
            .putBoolean(KEY_LABEL, next.showProfileLabel)
            .putBoolean(KEY_HAPTICS, next.haptics)
            .putBoolean(KEY_SUGGESTIONS, next.suggestions)
            .putBoolean(KEY_RECENT, next.recentQueries)
            .putBoolean(KEY_HIDE_STATUS, next.hideStatusBar)
            .putBoolean(KEY_GLANCE, next.glance)
            .putBoolean(KEY_ALL_APPS, next.allAppsButton)
            .putBoolean(KEY_GLANCE_CLOCK, next.glanceClock)
            .putBoolean(KEY_GLANCE_ALARM, next.glanceAlarm)
            .putBoolean(KEY_GLANCE_EVENT, next.glanceEvent)
            .putBoolean(KEY_GLANCE_BATTERY, next.glanceBattery)
            .putBoolean(KEY_SHEET, next.sheet)
            .putBoolean(KEY_HIGH_REFRESH, next.highRefresh)
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
            profileSwipe = prefs.getBoolean(KEY_SWIPE, defaults.profileSwipe),
            showProfileLabel = prefs.getBoolean(KEY_LABEL, defaults.showProfileLabel),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            suggestions = prefs.getBoolean(KEY_SUGGESTIONS, defaults.suggestions),
            recentQueries = prefs.getBoolean(KEY_RECENT, defaults.recentQueries),
            hideStatusBar = prefs.getBoolean(KEY_HIDE_STATUS, defaults.hideStatusBar),
            glance = prefs.getBoolean(KEY_GLANCE, defaults.glance),
            allAppsButton = prefs.getBoolean(KEY_ALL_APPS, defaults.allAppsButton),
            glanceClock = prefs.getBoolean(KEY_GLANCE_CLOCK, defaults.glanceClock),
            glanceAlarm = prefs.getBoolean(KEY_GLANCE_ALARM, defaults.glanceAlarm),
            glanceEvent = prefs.getBoolean(KEY_GLANCE_EVENT, defaults.glanceEvent),
            glanceBattery = prefs.getBoolean(KEY_GLANCE_BATTERY, defaults.glanceBattery),
            sheet = prefs.getBoolean(KEY_SHEET, defaults.sheet),
            highRefresh = prefs.getBoolean(KEY_HIGH_REFRESH, defaults.highRefresh),
        )
    }

    companion object {
        const val NO_ID = -1
        private const val KEY_AUTO = "auto_black_wallpaper"
        private const val KEY_TARGET = "wallpaper_target"
        private const val KEY_MONO = "monochrome_icons"
        private const val KEY_SWIPE = "profile_swipe"
        private const val KEY_LABEL = "profile_label"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SUGGESTIONS = "suggestions"
        private const val KEY_RECENT = "recent_queries"
        private const val KEY_HIDE_STATUS = "hide_status_bar"
        private const val KEY_GLANCE = "glance"
        private const val KEY_ALL_APPS = "all_apps_button"
        private const val KEY_GLANCE_CLOCK = "glance_clock"
        private const val KEY_GLANCE_ALARM = "glance_alarm"
        private const val KEY_GLANCE_EVENT = "glance_event"
        private const val KEY_GLANCE_BATTERY = "glance_battery"
        private const val KEY_SHEET = "sheet_theme"
        private const val KEY_HIGH_REFRESH = "high_refresh"
        private const val KEY_WALLPAPER_ID = "black_wallpaper_id"
    }
}
