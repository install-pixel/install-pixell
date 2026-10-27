package com.blacklaunch.launcher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellbeingRulesTest {
    private val apps = setOf("video", "social")

    @Test fun onlyChosenAppsPause() {
        assertNull(PauseRules.pause("notes", apps, 10, null, 0))
        assertEquals(LaunchPause(10, 5, null, false), PauseRules.pause("video", apps, 10, null, 5 * 60_000L))
    }

    @Test fun limitUsedUpMakesPauseLonger() {
        val under = PauseRules.pause("video", apps, 5, 30, 29 * 60_000L)!!
        assertEquals(5, under.seconds)
        assertEquals(false, under.overLimit)
        val over = PauseRules.pause("video", apps, 5, 30, 30 * 60_000L)!!
        assertEquals(PauseRules.OVER_LIMIT_S, over.seconds)
        assertTrue(over.overLimit)
        // Без доступу до використання — пауза є, а ліміт не перевірити.
        assertEquals(LaunchPause(5, null, 30, false), PauseRules.pause("video", apps, 5, 30, null))
        // 0 — без ліміту.
        assertEquals(LaunchPause(5, 50, null, false), PauseRules.pause("video", apps, 5, 0, 50 * 60_000L))
    }

    private fun ev(pkg: String, type: Int, t: Long) = UsageMath.Event(pkg, type, t)

    @Test fun perAppCountsOpenCloseAndCarryOver() {
        val start = 1_000L
        val end = 10_000L
        val events = sequenceOf(
            ev("a", UsageMath.PAUSED, 2_000), // відкрита ще до початку
            ev("b", UsageMath.RESUMED, 3_000),
            ev("b", UsageMath.PAUSED, 5_000),
            ev("a", UsageMath.RESUMED, 6_000), // досі відкрита
        )
        assertEquals(mapOf("a" to 1_000L + 4_000L, "b" to 2_000L), UsageMath.perApp(events, start, end))
    }

    @Test fun weekAverageSkipsEmptyDaysAndTopSorts() {
        val days = listOf(
            DayUsage(0, 0, emptyMap()),
            DayUsage(1, 60_000, mapOf("a" to 60_000L)),
            DayUsage(2, 180_000, mapOf("a" to 60_000L, "b" to 120_000L)),
        )
        assertEquals(120_000L, UsageMath.averageMs(days))
        assertEquals(listOf("a" to 120_000L, "b" to 120_000L).map { it.second }, UsageMath.topApps(days, 5).map { it.second })
        assertEquals(1, UsageMath.topApps(days, 1).size)
        assertEquals(1 to 5, UsageMath.hoursMinutes(65 * 60_000L))
    }
}
