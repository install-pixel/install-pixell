package com.blacklaunch.launcher.wellbeing

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.blacklaunch.launcher.BlackLaunchApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/** Налаштування «Звички»: пауза перед програмами, денні ліміти, крапки сповіщень, «Що грає». */
class WellbeingSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wellbeing", Context.MODE_PRIVATE)

    private val _pauseApps = MutableStateFlow(prefs.getStringSet(KEY_APPS, emptySet()).orEmpty().toSet())

    /** Пакети програм, перед якими — пауза. */
    val pauseApps: StateFlow<Set<String>> = _pauseApps.asStateFlow()

    private val _pauseSeconds = MutableStateFlow(prefs.getInt(KEY_SECONDS, PauseRules.DEFAULT_PAUSE_S))
    val pauseSeconds: StateFlow<Int> = _pauseSeconds.asStateFlow()

    private val _limits = MutableStateFlow(
        prefs.all.filterKeys { it.startsWith(PREFIX_LIMIT) }
            .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(PREFIX_LIMIT) to it } }
            .toMap(),
    )

    /** Пакет → хвилин на день (немає — без ліміту). */
    val limits: StateFlow<Map<String, Int>> = _limits.asStateFlow()

    private val _dots = MutableStateFlow(prefs.getBoolean(KEY_DOTS, true))
    val dots: StateFlow<Boolean> = _dots.asStateFlow()

    private val _nowPlaying = MutableStateFlow(prefs.getBoolean(KEY_NOW_PLAYING, true))
    val nowPlaying: StateFlow<Boolean> = _nowPlaying.asStateFlow()

    fun setPauseApps(packages: Set<String>) {
        _pauseApps.value = packages
        prefs.edit().putStringSet(KEY_APPS, packages).apply()
        // Прибрали програму з паузи — її ліміт теж.
        (_limits.value.keys - packages).forEach { setLimit(it, 0) }
    }

    fun setPauseSeconds(seconds: Int) {
        _pauseSeconds.value = seconds
        prefs.edit().putInt(KEY_SECONDS, seconds).apply()
    }

    fun setLimit(pkg: String, minutes: Int) {
        _limits.value = if (minutes > 0) _limits.value + (pkg to minutes) else _limits.value - pkg
        prefs.edit().apply { if (minutes > 0) putInt(PREFIX_LIMIT + pkg, minutes) else remove(PREFIX_LIMIT + pkg) }.apply()
    }

    fun setDots(on: Boolean) {
        _dots.value = on
        prefs.edit().putBoolean(KEY_DOTS, on).apply()
    }

    fun setNowPlaying(on: Boolean) {
        _nowPlaying.value = on
        prefs.edit().putBoolean(KEY_NOW_PLAYING, on).apply()
    }

    private companion object {
        const val KEY_APPS = "pause_apps"
        const val KEY_SECONDS = "pause_seconds"
        const val PREFIX_LIMIT = "limit_"
        const val KEY_DOTS = "notification_dots"
        const val KEY_NOW_PLAYING = "now_playing"
    }
}

/** Екранний час з подій Android («Доступ до використання»). Читається лише на вимогу — у фоні нічого не працює. */
class UsageReader(private val app: BlackLaunchApp) {
    fun hasAccess(): Boolean = app.systemStats.hasUsageAccess()

    /** Сьогодні з півночі в програмі [pkg]; null — немає доступу. */
    fun todayMs(pkg: String): Long? {
        if (!hasAccess()) return null
        val now = System.currentTimeMillis()
        return perApp(startOfDay(now), now)?.get(pkg) ?: 0L
    }

    /** Останні 7 днів, від найдавнішого до сьогодні; null — немає доступу. */
    fun week(): List<DayUsage>? {
        if (!hasAccess()) return null
        val now = System.currentTimeMillis()
        val today = startOfDay(now)
        return (6 downTo 0).map { back ->
            val start = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis
            val end = if (back == 0) now else Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            val apps = perApp(start, end).orEmpty().filterKeys { it != app.packageName }
            DayUsage(start, apps.values.sum(), apps)
        }
    }

    private fun perApp(start: Long, end: Long): Map<String, Long>? {
        val usage = app.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { usage.queryEvents(start, end) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        val list = sequence {
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val type = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageMath.RESUMED
                    // «Зупинено» не беремо: воно приходить і після переходу на інший екран тієї ж програми.
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageMath.PAUSED
                    else -> continue
                }
                yield(UsageMath.Event(pkg, type, event.timeStamp))
            }
        }
        return UsageMath.perApp(list, start, end)
    }

    private fun startOfDay(now: Long) = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
