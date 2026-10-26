package com.blacklaunch.launcher.commands

import com.blacklaunch.launcher.automation.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeScheduleTest {
    private val zone = TimeZone.getTimeZone("Europe/Kyiv")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    @Test fun laterTodayOrTomorrow() {
        // Понеділок, 26.10.2026, 10:00 → сьогодні 11:20.
        assertEquals(at(2026, 10, 26, 11, 20), TimeSchedule.next(at(2026, 10, 26, 10, 0), 11 * 60 + 20, 0, zone))
        // Уже 11:20 — завтра.
        assertEquals(at(2026, 10, 27, 11, 20), TimeSchedule.next(at(2026, 10, 26, 11, 20), 11 * 60 + 20, 0, zone))
    }

    @Test fun onlyChosenDays() {
        val weekdays = 0b0011111
        // П'ятниця 30.10 після 18:20 → понеділок 02.11.
        assertEquals(at(2026, 11, 2, 18, 20), TimeSchedule.next(at(2026, 10, 30, 19, 0), 18 * 60 + 20, weekdays, zone))
        // Субота → понеділок.
        assertEquals(at(2026, 11, 2, 18, 20), TimeSchedule.next(at(2026, 10, 31, 9, 0), 18 * 60 + 20, weekdays, zone))
    }

    @Test fun earliestOfSeveralTriggersAndEncoding() {
        val command = CustomCommand(
            "1", "", listOf(CommandStep("скажи Час обідати")),
            triggers = listOf(CommandTrigger(TriggerKind.TIME, minute = 18 * 60 + 20, days = 31), CommandTrigger(TriggerKind.TIME, minute = 11 * 60 + 20, days = 31)),
        )
        assertEquals(at(2026, 10, 26, 11, 20), TimeSchedule.nextFor(command, at(2026, 10, 26, 10, 0), zone))
        assertEquals(at(2026, 10, 26, 18, 20), TimeSchedule.nextFor(command, at(2026, 10, 26, 12, 0), zone))
        assertEquals(listOf(command), InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(listOf(command))))
    }
}

class HourMathTest {
    private val zone = TimeZone.getTimeZone("Europe/Kyiv")

    private fun at(hour: Int, minute: Int, second: Int, ms: Int = 0): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, 9, 27, hour, minute, second)
        set(Calendar.MILLISECOND, ms)
    }.timeInMillis

    @Test fun earlyAlarmStillMeansThatHourAndNextIsTheFollowingOne() {
        // Будильник на 13:00 прийшов о 12:59:59.900: це 13:00, а наступна — 14:00, не знову 13:00.
        val early = at(12, 59, 59, 900)
        assertEquals(at(13, 0, 0), HourMath.nearestFullHour(early, zone))
        assertEquals(at(14, 0, 0), HourMath.nextFullHour(maxOf(early, at(13, 0, 0)), HourMath.GRACE_MS, zone))
        // Без запасу (увімкнули о 12:59:50) — найближча 13:00.
        assertEquals(at(13, 0, 0), HourMath.nextFullHour(at(12, 59, 50), zone = zone))
        assertEquals(at(14, 0, 0), HourMath.nextFullHour(at(13, 0, 0), zone = zone))
    }
}
