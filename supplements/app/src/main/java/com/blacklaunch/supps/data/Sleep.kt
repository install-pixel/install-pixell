package com.blacklaunch.supps.data

import java.time.Instant
import java.time.ZoneId

/*
 * Сон — з часу відміток, без окремих налаштувань: ранкові добавки п'ються одразу після пробудження, вечірні — за
 * якийсь час до сну. Підйом дня = найраніша відмітка «після пробудження» (або ранкова оцінка), відбій = найраніша
 * відмітка «перед сном» (або вечірня оцінка) + [offsetMinutes]. Відмітки, дописані заднім числом, часу не мають
 * (at = 0) і не враховуються.
 */

/** Сон перед днем [day]: відбій напередодні → підйом цього дня. */
data class NightSleep(val day: Long, val wakeAt: Long?, val bedBeforeAt: Long?, val minutes: Int?)

object Sleep {
    /** Найкоротший і найдовший правдоподібний сон, хв. */
    private const val MIN_SLEEP = 120
    private const val MAX_SLEEP = 16 * 60

    fun wakeAt(day: Long, intakes: List<Intake>, slots: Map<Long, Slot>, checkIn: CheckIn?): Long? {
        val fromIntakes = intakes.filter { it.day == day && it.at > 0 && slots[it.supplementId] == Slot.Wake }.minOfOrNull { it.at }
        return listOfNotNull(fromIntakes, checkIn?.morningAt?.takeIf { it > 0 }).minOrNull()
    }

    fun bedAt(day: Long, intakes: List<Intake>, slots: Map<Long, Slot>, checkIn: CheckIn?, offsetMinutes: Int): Long? {
        val fromIntakes = intakes.filter { it.day == day && it.at > 0 && slots[it.supplementId] == Slot.Bed }.minOfOrNull { it.at }
        val anchor = listOfNotNull(fromIntakes, checkIn?.eveningAt?.takeIf { it > 0 }).minOrNull() ?: return null
        return anchor + offsetMinutes * 60_000L
    }

    /** Ночі для днів [from]..[to]. */
    fun nights(
        from: Long,
        to: Long,
        intakes: List<Intake>,
        supplements: List<Supplement>,
        checkIns: List<CheckIn>,
        offsetMinutes: Int,
    ): List<NightSleep> {
        val slots = supplements.associate { it.id to it.slotE }
        val byDay = checkIns.associateBy { it.day }
        val relevant = intakes.filter { it.day in (from - 1)..to }
        return (from..to).map { day ->
            val wake = wakeAt(day, relevant, slots, byDay[day])
            val bed = bedAt(day - 1, relevant, slots, byDay[day - 1], offsetMinutes)
            val minutes = if (wake != null && bed != null) ((wake - bed) / 60_000L).toInt().takeIf { it in MIN_SLEEP..MAX_SLEEP } else null
            NightSleep(day, wake, bed, minutes)
        }
    }

    /**
     * Середній час доби (хвилини від півночі) для моментів [times]. [pivotHour] — година, навколо якої рахуємо,
     * щоб відбій о 23:30 і о 00:30 давав північ, а не полудень.
     */
    fun averageClock(times: List<Long>, zone: ZoneId, pivotHour: Int): Int? {
        if (times.isEmpty()) return null
        val pivot = pivotHour * 60
        val shifted = times.map { t ->
            val local = Instant.ofEpochMilli(t).atZone(zone).toLocalTime()
            val m = local.hour * 60 + local.minute
            ((m - pivot) % 1440 + 1440) % 1440
        }
        return ((shifted.average().toInt() + pivot) % 1440 + 1440) % 1440
    }
}
