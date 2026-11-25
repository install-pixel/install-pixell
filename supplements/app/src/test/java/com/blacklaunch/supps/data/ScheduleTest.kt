package com.blacklaunch.supps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTest {
    private fun cycle(on: Int, off: Int, start: Long) = Supplement(id = 1, name = "c", plan = Plan.Cycle.name, onDays = on, offDays = off, startDay = start)

    @Test
    fun cycleOnAndOff() {
        val s = cycle(3, 2, 100)
        val g = emptyList<AltGroup>()
        val all = listOf(s)
        assertEquals(Phase.NotStarted, Schedule.status(s, 99, g, all).phase)
        val d0 = Schedule.status(s, 100, g, all)
        assertEquals(Phase.Active, d0.phase)
        assertEquals(1, d0.dayNo)
        assertEquals(3, d0.length)
        assertEquals(103L, d0.changeOn)
        assertEquals(Phase.Active, Schedule.status(s, 102, g, all).phase)
        val off = Schedule.status(s, 103, g, all)
        assertEquals(Phase.Off, off.phase)
        assertEquals(105L, off.changeOn)
        assertEquals(Phase.Active, Schedule.status(s, 105, g, all).phase)
    }

    @Test
    fun cycleStartFromDoneDays() {
        val start = Schedule.cycleStartFor(today = 200, doneDays = 30)
        val s = cycle(84, 28, start)
        val st = Schedule.status(s, 200, emptyList(), listOf(s))
        assertEquals(Phase.Active, st.phase)
        assertEquals(31, st.dayNo)
    }

    @Test
    fun groupAlternates() {
        val g = AltGroup(id = 7, name = "a", breakDays = 2, startDay = 0)
        val a = Supplement(id = 1, name = "A", plan = Plan.Group.name, groupId = 7, groupOrder = 0, onDays = 4)
        val b = Supplement(id = 2, name = "B", plan = Plan.Group.name, groupId = 7, groupOrder = 1, onDays = 3)
        val all = listOf(a, b)
        val groups = listOf(g)
        // 0..3 A, 4..5 перерва, 6..8 B, 9..10 перерва, 11 — знову A.
        val first = Schedule.status(a, 0, groups, all)
        assertEquals(Phase.Active, first.phase)
        assertEquals(true, first.nextIsBreak)
        assertEquals(Phase.Off, Schedule.status(b, 0, groups, all).phase)
        assertEquals(6L, Schedule.status(b, 0, groups, all).changeOn)
        assertNull(Schedule.activeInGroup(g, all, 4))
        assertEquals(2L, Schedule.activeInGroup(g, all, 6)?.id)
        assertEquals(1L, Schedule.activeInGroup(g, all, 11)?.id)
        assertEquals(11L, Schedule.status(a, 7, groups, all).changeOn)
    }

    @Test
    fun groupStartForMember() {
        val a = Supplement(id = 1, name = "A", plan = Plan.Group.name, groupId = 7, groupOrder = 0, onDays = 56)
        val b = Supplement(id = 2, name = "B", plan = Plan.Group.name, groupId = 7, groupOrder = 1, onDays = 56)
        val all = listOf(a, b)
        val start = Schedule.groupStartFor(b, Schedule.members(7, all), 0, today = 1000, doneDays = 10)
        val g = AltGroup(id = 7, name = "a", startDay = start)
        val st = Schedule.status(b, 1000, listOf(g), all)
        assertEquals(Phase.Active, st.phase)
        assertEquals(11, st.dayNo)
        assertEquals(1L, st.nextId)
    }

    @Test
    fun runsOut() {
        val s = Supplement(id = 1, name = "d", unitsPerDose = 2.0, unitsLeft = 10.0)
        assertEquals(104L, Schedule.runsOutOn(s, 100, false, emptyList(), listOf(s)))
        val c = cycle(2, 1, 100).copy(unitsLeft = 3.0)
        // Дні 100, 101 (прийом), 102 перерва, 103 прийом.
        assertEquals(103L, Schedule.runsOutOn(c, 100, false, emptyList(), listOf(c)))
        assertNull(Schedule.runsOutOn(s.copy(unitsLeft = null), 100, false, emptyList(), listOf(s)))
    }

    @Test
    fun compareSplitsDays() {
        val checkIns = listOf(
            CheckIn(day = 1, a1 = 4, dayType = DayType.Shift1.name),
            CheckIn(day = 2, a1 = 2, dayType = DayType.Shift2.name),
            CheckIn(day = 3, a1 = 5, dayType = DayType.Shift1.name),
        )
        val r = Stats.compare(setOf(1L, 3L), checkIns, 1, null).first()
        assertEquals(4.5, r.withAvg!!, 1e-9)
        assertEquals(2, r.withN)
        assertEquals(2.0, r.withoutAvg!!, 1e-9)
        val shift2 = Stats.compare(setOf(1L, 3L), checkIns, 1, DayType.Shift2).first()
        assertNull(shift2.withAvg)
        assertEquals(1, shift2.withoutN)
    }
}
