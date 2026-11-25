package com.blacklaunch.supps.data

/*
 * Розклад: чи приймати добавку в певний день і що буде далі. Чиста логіка без Android — покрита тестами.
 * Дні — epochDay (LocalDate.toEpochDay()).
 */

enum class Phase {
    /** Сьогодні за схемою — приймати. */
    Active,

    /** Перерва (цикл) або черга іншої добавки (чергування). */
    Off,

    /** За потреби: не плануємо, лише відмічаємо. */
    AsNeeded,

    /** Схема ще не почалась. */
    NotStarted,
}

/**
 * Стан на день. [dayNo]/[length] — «день 12 з 56» поточної фази (0 — без меж); [changeOn] — день, коли фаза
 * зміниться; [nextId] — яка добавка групи піде наступною (для чергування); [nextIsBreak] — далі перерва.
 */
data class DayStatus(
    val phase: Phase,
    val dayNo: Int = 0,
    val length: Int = 0,
    val changeOn: Long? = null,
    val nextId: Long? = null,
    val nextIsBreak: Boolean = false,
)

object Schedule {
    /** Учасники групи в порядку черги. */
    fun members(groupId: Long, all: List<Supplement>): List<Supplement> =
        all.filter { it.groupId == groupId && it.planE == Plan.Group && !it.archived }.sortedWith(compareBy({ it.groupOrder }, { it.id }))

    fun status(s: Supplement, day: Long, groups: List<AltGroup>, all: List<Supplement>): DayStatus = when (s.planE) {
        Plan.Daily -> DayStatus(Phase.Active)
        Plan.AsNeeded -> DayStatus(Phase.AsNeeded)
        Plan.Cycle -> cycle(s, day)
        Plan.Group -> {
            val g = groups.firstOrNull { it.id == s.groupId }
            if (g == null) DayStatus(Phase.Active) else group(s, g, members(g.id, all), day)
        }
    }

    private fun cycle(s: Supplement, day: Long): DayStatus {
        val on = s.onDays.coerceAtLeast(1)
        val off = s.offDays.coerceAtLeast(0)
        val d = day - s.startDay
        if (d < 0) return DayStatus(Phase.NotStarted, changeOn = s.startDay)
        if (off == 0) return DayStatus(Phase.Active)
        val period = on + off
        val pos = (d % period).toInt()
        return if (pos < on) {
            DayStatus(Phase.Active, dayNo = pos + 1, length = on, changeOn = day + (on - pos), nextIsBreak = true)
        } else {
            DayStatus(Phase.Off, dayNo = pos - on + 1, length = off, changeOn = day + (period - pos))
        }
    }

    private fun blockOf(m: Supplement): Int = m.onDays.coerceAtLeast(1)

    private fun group(s: Supplement, g: AltGroup, members: List<Supplement>, day: Long): DayStatus {
        if (members.none { it.id == s.id }) return DayStatus(Phase.Active)
        val brk = g.breakDays.coerceAtLeast(0)
        val d = day - g.startDay
        if (d < 0) {
            // Черга ще не почалась: перший учасник стартує в день початку, решта — за ним.
            val until = offsetOf(s, members, brk)
            return DayStatus(Phase.NotStarted, changeOn = g.startDay + until)
        }
        val period = members.sumOf { blockOf(it) + brk }
        if (period <= 0) return DayStatus(Phase.Active)
        val pos = (d % period).toInt()
        val idx = members.indexOfFirst { it.id == s.id }
        val start = offsetOf(s, members, brk)
        val block = blockOf(s)
        val next = members[(idx + 1) % members.size]
        return if (pos >= start && pos < start + block) {
            val inBlock = pos - start
            DayStatus(
                Phase.Active,
                dayNo = inBlock + 1,
                length = block,
                changeOn = day + (block - inBlock),
                nextId = if (next.id != s.id) next.id else null,
                nextIsBreak = brk > 0,
            )
        } else {
            val wait = if (pos < start) start - pos else period - pos + start
            DayStatus(Phase.Off, changeOn = day + wait)
        }
    }

    /** Через скільки днів від початку черги стартує блок [s]. */
    private fun offsetOf(s: Supplement, members: List<Supplement>, brk: Int): Int {
        var off = 0
        for (m in members) {
            if (m.id == s.id) return off
            off += blockOf(m) + brk
        }
        return off
    }

    /** Хто з групи активний у день [day] (null — перерва між блоками або черга не почалась). */
    fun activeInGroup(g: AltGroup, all: List<Supplement>, day: Long): Supplement? =
        members(g.id, all).firstOrNull { status(it, day, listOf(g), all).phase == Phase.Active }

    /**
     * День початку черги, щоб учасник [s] був зараз на [doneDays]-му вже прийнятому дні (тобто сьогодні — день
     * doneDays + 1). Для ситуації «я вже місяць це п'ю».
     */
    fun groupStartFor(s: Supplement, members: List<Supplement>, brk: Int, today: Long, doneDays: Int): Long =
        today - offsetOf(s, members, brk) - doneDays.coerceAtLeast(0)

    /** День початку циклу, щоб сьогодні був день doneDays + 1. */
    fun cycleStartFor(today: Long, doneDays: Int): Long = today - doneDays.coerceAtLeast(0)

    /** Скільки днів із [from] по [from]+[days)-1 добавку треба приймати. */
    fun activeDays(s: Supplement, from: Long, days: Int, groups: List<AltGroup>, all: List<Supplement>): Int =
        (0 until days).count { status(s, from + it, groups, all).phase == Phase.Active }

    /**
     * Коли закінчиться банка: останній день, на який вистачить залишку (null — невідомий залишок, «за потреби» або
     * вистачить більш ніж на два роки). [takenToday] — сьогоднішній прийом уже списаний із залишку.
     */
    fun runsOutOn(s: Supplement, today: Long, takenToday: Boolean, groups: List<AltGroup>, all: List<Supplement>): Long? {
        val left = s.unitsLeft ?: return null
        if (s.planE == Plan.AsNeeded) return null
        val dose = s.unitsPerDose.takeIf { it > 0 } ?: return null
        var doses = kotlin.math.floor(left / dose + 1e-9).toInt()
        var day = if (takenToday) today + 1 else today
        var last: Long? = if (takenToday) today else null
        if (doses <= 0) return last ?: (today - 1)
        val limit = today + 730
        while (day <= limit) {
            if (status(s, day, groups, all).phase == Phase.Active) {
                doses--
                last = day
                if (doses == 0) return last
            }
            day++
        }
        return null
    }
}
