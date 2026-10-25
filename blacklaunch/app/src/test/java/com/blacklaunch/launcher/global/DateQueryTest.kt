package com.blacklaunch.launcher.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateQueryTest {
    // Неділя, 25 жовтня 2026.
    private val today = LocalDate.of(2026, 10, 25)

    private fun parse(query: String) = DateQuery.parse(query, today)

    @Test fun relativeDays() {
        assertEquals(DateQuery(LocalDate.of(2026, 10, 24), today, setOf(GroupKind.PHOTOS), ""), parse("фото вчора"))
        assertEquals(DateQuery(today, LocalDate.of(2026, 10, 26), setOf(GroupKind.CALLS), ""), parse("calls today"))
        assertEquals(LocalDate.of(2026, 10, 23), parse("zdjęcia przedwczoraj")?.start)
    }

    @Test fun weeksAndMonths() {
        val lastWeek = parse("відео минулого тижня")!!
        assertEquals(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 19), lastWeek.start to lastWeek.endExclusive)
        assertEquals(setOf(GroupKind.VIDEO), lastWeek.sources)
        val polish = parse("zdjęcia w zeszłym tygodniu")!!
        assertEquals(LocalDate.of(2026, 10, 12), polish.start)
        assertEquals("", polish.text)
        assertEquals(LocalDate.of(2026, 9, 1), parse("last month")?.start)
    }

    @Test fun dayAndMonthNames() {
        assertEquals(DateQuery(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), setOf(GroupKind.PHOTOS), ""), parse("фото 12 жовтня"))
        assertEquals(LocalDate.of(2026, 10, 12), parse("october 12 photos")?.start)
        // Грудень ще не настав — значить, торішній.
        assertEquals(LocalDate.of(2025, 12, 1), parse("фото грудень")?.start)
        assertEquals(LocalDate.of(2025, 10, 1) to LocalDate.of(2025, 11, 1), parse("фото жовтень 2025")?.let { it.start to it.endExclusive })
        assertEquals(LocalDate.of(2026, 10, 1), parse("у жовтні")?.start)
    }

    @Test fun numericDatesAndText() {
        assertEquals(LocalDate.of(2026, 10, 12), parse("фото 12.10")?.start)
        assertEquals(LocalDate.of(2025, 3, 5), parse("5.03.2025")?.start)
        val withText = parse("screenshots вчора")!!
        assertEquals("screenshots", withText.text)
        assertEquals(emptySet<GroupKind>(), withText.sources)
        assertEquals(LocalDate.of(2025, 1, 1), parse("фото 2025")?.start)
    }

    @Test fun rollingPeriods() {
        val week = parse("фото тиждень")!!
        assertEquals(LocalDate.of(2026, 10, 19) to LocalDate.of(2026, 10, 26), week.start to week.endExclusive)
        assertEquals(LocalDate.of(2026, 10, 19), parse("фото за тиждень")?.start)
        assertEquals(LocalDate.of(2026, 10, 19), parse("відео за тиждень")?.start)
        assertEquals(LocalDate.of(2026, 9, 26), parse("фото місяць")?.start)
        assertEquals(LocalDate.of(2025, 10, 26), parse("photos year")?.start)
        assertEquals(LocalDate.of(2026, 1, 1), parse("фото цього року")?.start)
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2026, 1, 1), parse("zdjęcia w zeszłym roku")?.let { it.start to it.endExclusive })
        assertEquals("", parse("фото за тиждень")?.text)
        // Голе слово без джерела — не дата.
        assertNull(parse("тиждень"))
    }

    @Test fun notADate() {
        assertNull(parse("telegram"))
        assertNull(parse("2025"))
        assertNull(parse("фото"))
        assertNull(parse("31.02"))
        assertNull(parse("jan"))
        assertNull(parse("may"))
    }
}
