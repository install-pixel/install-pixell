package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import java.util.Calendar
import java.util.TimeZone

/** Коли наступного разу спрацює подія «за часом» — чиста логіка (місцевий час, дні тижня). */
object TimeSchedule {
    /** Найближча мить після [nowMillis] для [minute] (від півночі) у дні [days] (біти пн=1 … нд=64, 0 — щодня). */
    fun next(nowMillis: Long, minute: Int, days: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        // Сьогодні вже минуло — з завтрашнього; далі шукаємо дозволений день (не більше тижня).
        if (c.timeInMillis <= nowMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        repeat(DAYS_IN_WEEK) {
            val isoDay = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
            if (days == 0 || (days shr (isoDay - 1)) and 1 == 1) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    /** Найближче спрацювання серед подій «за часом» команди; null — таких подій немає. */
    fun nextFor(command: CustomCommand, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long? =
        command.triggers.filter { it.kind == TriggerKind.TIME && it.minute != null }
            .minOfOrNull { next(nowMillis, it.minute!!, it.days, zone) }

    private const val DAYS_IN_WEEK = 7
}
