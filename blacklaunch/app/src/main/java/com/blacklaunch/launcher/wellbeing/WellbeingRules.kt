package com.blacklaunch.launcher.wellbeing

/**
 * Пауза перед відволікальною програмою (етап 16g): скільки чекати й що сказати.
 * [usedMinutes] — сьогодні в цій програмі (null — немає «Доступу до використання»).
 */
data class LaunchPause(val seconds: Int, val usedMinutes: Int?, val limitMinutes: Int?, val overLimit: Boolean)

/** Чисті правила паузи й денних лімітів — перевіряються тестами. */
object PauseRules {
    val PAUSE_SECONDS = listOf(5, 10, 20)
    const val DEFAULT_PAUSE_S = 10

    /** 0 — без ліміту. */
    val LIMITS = listOf(0, 15, 30, 60, 90, 120)

    /** Ліміт на сьогодні вичерпано — пауза довша. */
    const val OVER_LIMIT_S = 30

    fun pause(pkg: String, pauseApps: Set<String>, seconds: Int, limitMinutes: Int?, usedMs: Long?): LaunchPause? {
        if (pkg !in pauseApps) return null
        val used = usedMs?.let { (it / 60_000).toInt() }
        val limit = limitMinutes?.takeIf { it > 0 }
        val over = limit != null && used != null && used >= limit
        return LaunchPause(if (over) maxOf(seconds, OVER_LIMIT_S) else seconds, used, limit, over)
    }
}

/** Один день екранного часу: усього й по програмах (мс). */
data class DayUsage(val dayStart: Long, val totalMs: Long, val perApp: Map<String, Long>)

/**
 * Час у програмах з подій «відкрито/закрито» (так точніше, ніж зведена статистика Android,
 * що захоплює й сусідні дні). Логіка та сама, що в моніторингу.
 */
object UsageMath {
    const val RESUMED = 1
    const val PAUSED = 2

    data class Event(val pkg: String, val type: Int, val time: Long)

    fun perApp(events: Sequence<Event>, start: Long, end: Long): Map<String, Long> {
        val perApp = HashMap<String, Long>()
        val resumed = HashMap<String, Long>()
        for (event in events) {
            when (event.type) {
                RESUMED -> resumed.putIfAbsent(event.pkg, event.time)
                PAUSED -> {
                    // Без «відкрито» — програма була на екрані ще до початку проміжку.
                    val from = resumed.remove(event.pkg) ?: if (perApp.containsKey(event.pkg)) null else start
                    if (from != null) perApp[event.pkg] = (perApp[event.pkg] ?: 0L) + (event.time - from).coerceAtLeast(0)
                }
            }
        }
        resumed.forEach { (pkg, from) -> perApp[pkg] = (perApp[pkg] ?: 0L) + (end - from).coerceAtLeast(0) }
        return perApp
    }

    /** Тиждень: середнє на день — лише з днів, коли телефоном користувались. */
    fun averageMs(days: List<DayUsage>): Long {
        val used = days.filter { it.totalMs > 0 }
        return if (used.isEmpty()) 0 else used.sumOf { it.totalMs } / used.size
    }

    /** Найчастіші програми тижня. */
    fun topApps(days: List<DayUsage>, limit: Int): List<Pair<String, Long>> =
        days.flatMap { it.perApp.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }

    /** «1 год 05 хв» / «47 хв» — числа; слова дає екран. */
    fun hoursMinutes(ms: Long): Pair<Int, Int> {
        val minutes = (ms / 60_000).toInt()
        return minutes / 60 to minutes % 60
    }
}
