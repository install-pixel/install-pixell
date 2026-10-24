package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.LaunchRecord
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min

/**
 * Підказки програм (як Siri Suggestions), лише з локальної історії запусків:
 * що зазвичай відкриваєте в цей час дня, плюс нещодавні. Жодної фонової роботи —
 * рахується в момент відкриття пошуку.
 */
object AppSuggestions {
    private const val NEAR_MINUTES = 90
    private const val DAY_MINUTES = 24 * 60
    private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
    private const val RECENT_BONUS = 3.0

    fun suggest(
        records: List<LaunchRecord>,
        apps: List<AppInfo>,
        now: Long,
        limit: Int = 8,
        zone: TimeZone = TimeZone.getDefault(),
    ): List<AppInfo> {
        if (records.isEmpty()) return emptyList()
        val installed = apps.associateBy { it.key }
        val nowMinute = minuteOfDay(now, zone)
        val scores = HashMap<String, Double>()
        records.forEach { record ->
            if (record.appKey !in installed) return@forEach
            val diff = abs(minuteOfDay(record.timeMillis, zone) - nowMinute).let { min(it, DAY_MINUTES - it) }
            val timeWeight = if (diff <= NEAR_MINUTES) 1.0 else 0.2
            val ageWeight = if (now - record.timeMillis > MONTH_MS) 0.5 else 1.0
            scores.merge(record.appKey, timeWeight * ageWeight, Double::plus)
        }
        // Дві останні програми — завжди серед підказок.
        records.asReversed().map { it.appKey }.filter { it in installed }.distinct().take(2).forEach {
            scores.merge(it, RECENT_BONUS, Double::plus)
        }
        return scores.entries.sortedByDescending { it.value }.take(limit).mapNotNull { installed[it.key] }
    }

    private fun minuteOfDay(millis: Long, zone: TimeZone): Int {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = millis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
}
