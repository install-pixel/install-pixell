package com.blacklaunch.launcher.global

import com.blacklaunch.launcher.search.SearchNormalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Пошук за датою: «фото вчора», «відео 12 жовтня», «дзвінки минулого тижня», «screenshots жовтень 2025».
 * [start] включно, [endExclusive] — ні; [sources] — порожньо, якщо джерело не назвали
 * (тоді шукаємо в усіх, що мають дату); [text] — решта запиту для звичайного збігу.
 */
data class DateQuery(val start: LocalDate, val endExclusive: LocalDate, val sources: Set<GroupKind>, val text: String) {
    companion object {
        /** Джерела, у яких є дата: фото, відео, дзвінки, сповіщення. */
        val DATED = setOf(GroupKind.PHOTOS, GroupKind.VIDEO, GroupKind.CALLS, GroupKind.NOTIFICATIONS)

        private val sourceWords: Map<String, GroupKind> = buildMap {
            listOf("фото", "фотографії", "фотки", "знімки", "photo", "photos", "pictures", "zdjecia", "zdjecie", "foto")
                .forEach { put(it, GroupKind.PHOTOS) }
            listOf("відео", "video", "videos", "filmy", "film", "wideo").forEach { put(it, GroupKind.VIDEO) }
            listOf("дзвінки", "дзвінок", "дзвінків", "calls", "call", "polaczenia", "polaczenie")
                .forEach { put(it, GroupKind.CALLS) }
            listOf("сповіщення", "notifications", "powiadomienia").forEach { put(it, GroupKind.NOTIFICATIONS) }
        }

        private val months: Map<String, Int> = buildMap {
            val forms = listOf(
                "січень січня січні styczen stycznia styczniu january jan",
                "лютий лютого лютому luty lutego lutym february feb",
                "березень березня березні marzec marca marcu march mar",
                "квітень квітня квітні kwiecien kwietnia kwietniu april apr",
                "травень травня травні maj maja maju may",
                "червень червня червні czerwiec czerwca czerwcu june jun",
                "липень липня липні lipiec lipca lipcu july jul",
                "серпень серпня серпні sierpien sierpnia sierpniu august aug",
                "вересень вересня вересні wrzesien wrzesnia wrzesniu september sep sept",
                "жовтень жовтня жовтні pazdziernik pazdziernika pazdzierniku october oct",
                "листопад листопада листопаді listopad listopada listopadzie november nov",
                "грудень грудня грудні grudzien grudnia grudniu december dec",
            )
            forms.forEachIndexed { index, line -> line.split(' ').forEach { put(it, index + 1) } }
        }

        private val prepositions = setOf("у", "в", "за", "w", "we", "in", "on", "from", "z", "з")

        private enum class Span {
            TODAY, YESTERDAY, BEFORE_YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH,
            /** «за тиждень / місяць / рік» — останні 7 / 30 / 365 днів до сьогодні включно. */
            PAST_WEEK, PAST_MONTH, PAST_YEAR, THIS_YEAR, LAST_YEAR,
        }

        private val phrases: List<Pair<List<String>, Span>> = listOf(
            "сьогодні" to Span.TODAY, "today" to Span.TODAY, "dzisiaj" to Span.TODAY, "dzis" to Span.TODAY,
            "вчора" to Span.YESTERDAY, "учора" to Span.YESTERDAY, "yesterday" to Span.YESTERDAY, "wczoraj" to Span.YESTERDAY,
            "позавчора" to Span.BEFORE_YESTERDAY, "przedwczoraj" to Span.BEFORE_YESTERDAY,
            "day before yesterday" to Span.BEFORE_YESTERDAY,
            "минулого тижня" to Span.LAST_WEEK, "минулий тиждень" to Span.LAST_WEEK, "last week" to Span.LAST_WEEK,
            "zeszlym tygodniu" to Span.LAST_WEEK, "zeszly tydzien" to Span.LAST_WEEK, "poprzedni tydzien" to Span.LAST_WEEK,
            "цього тижня" to Span.THIS_WEEK, "цей тиждень" to Span.THIS_WEEK, "this week" to Span.THIS_WEEK,
            "tym tygodniu" to Span.THIS_WEEK, "ten tydzien" to Span.THIS_WEEK,
            "минулого місяця" to Span.LAST_MONTH, "минулий місяць" to Span.LAST_MONTH, "last month" to Span.LAST_MONTH,
            "zeszlym miesiacu" to Span.LAST_MONTH, "zeszly miesiac" to Span.LAST_MONTH, "poprzedni miesiac" to Span.LAST_MONTH,
            "цього місяця" to Span.THIS_MONTH, "цей місяць" to Span.THIS_MONTH, "this month" to Span.THIS_MONTH,
            "tym miesiacu" to Span.THIS_MONTH, "ten miesiac" to Span.THIS_MONTH,
            "за тиждень" to Span.PAST_WEEK, "за останній тиждень" to Span.PAST_WEEK, "past week" to Span.PAST_WEEK,
            "ostatni tydzien" to Span.PAST_WEEK, "z ostatniego tygodnia" to Span.PAST_WEEK,
            "за місяць" to Span.PAST_MONTH, "за останній місяць" to Span.PAST_MONTH, "past month" to Span.PAST_MONTH,
            "ostatni miesiac" to Span.PAST_MONTH, "z ostatniego miesiaca" to Span.PAST_MONTH,
            "за рік" to Span.PAST_YEAR, "за останній рік" to Span.PAST_YEAR, "past year" to Span.PAST_YEAR,
            "ostatni rok" to Span.PAST_YEAR, "z ostatniego roku" to Span.PAST_YEAR,
            "цього року" to Span.THIS_YEAR, "цей рік" to Span.THIS_YEAR, "this year" to Span.THIS_YEAR, "tym roku" to Span.THIS_YEAR,
            "ten rok" to Span.THIS_YEAR,
            "минулого року" to Span.LAST_YEAR, "минулий рік" to Span.LAST_YEAR, "last year" to Span.LAST_YEAR,
            "zeszlym roku" to Span.LAST_YEAR, "zeszly rok" to Span.LAST_YEAR,
        ).map { (text, span) -> text.split(' ') to span }.sortedByDescending { it.first.size }

        /** Голе «тиждень», «місяць», «рік» — лише з назвою джерела («фото тиждень»), інакше це просто слово. */
        private val bareSpans = mapOf(
            "тиждень" to Span.PAST_WEEK, "week" to Span.PAST_WEEK, "tydzien" to Span.PAST_WEEK,
            "місяць" to Span.PAST_MONTH, "month" to Span.PAST_MONTH, "miesiac" to Span.PAST_MONTH,
            "рік" to Span.PAST_YEAR, "year" to Span.PAST_YEAR, "rok" to Span.PAST_YEAR,
        )

        private val numericDate = Regex("""(?<!\d)(\d{1,2})[./](\d{1,2})(?:[./](\d{4}|\d{2}))?(?!\d)""")

        fun parse(query: String, today: LocalDate): DateQuery? {
            // «12.10» і «12.10.2025» — до нормалізації, бо вона прибирає крапки.
            var numeric: Pair<LocalDate, LocalDate>? = null
            var raw = query
            numericDate.find(query)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val year = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                dayRange(day, month, year, today)?.let {
                    numeric = it
                    raw = query.removeRange(match.range)
                }
            }
            val tokens = SearchNormalizer.words(SearchNormalizer.normalize(raw))
            val used = BooleanArray(tokens.size)
            val sources = tokens.indices.mapNotNull { i -> sourceWords[tokens[i]]?.also { used[i] = true } }.toSet()
            var range = numeric
            var at = -1
            var i = 0
            while (range == null && i < tokens.size) {
                if (used[i]) { i++; continue }
                val phrase = phrases.firstOrNull { (words, _) -> tokens.drop(i).take(words.size) == words }
                if (phrase != null) {
                    range = spanRange(phrase.second, today)
                    for (k in i until i + phrase.first.size) used[k] = true
                    at = i
                    break
                }
                val bare = bareSpans[tokens[i]]
                if (bare != null && sources.isNotEmpty()) {
                    range = spanRange(bare, today)
                    used[i] = true
                    at = i
                    break
                }
                val month = months[tokens[i]]
                if (month != null) {
                    val dayBefore = tokens.getOrNull(i - 1)?.toIntOrNull()?.takeIf { i > 0 && !used[i - 1] && it in 1..31 }
                    val dayAfter = tokens.getOrNull(i + 1)?.toIntOrNull()?.takeIf { it in 1..31 }
                    val day = dayBefore ?: dayAfter
                    val yearIndex = if (dayAfter != null && dayBefore == null) i + 2 else i + 1
                    val year = tokens.getOrNull(yearIndex)?.toIntOrNull()?.takeIf { it in 1900..2100 }
                    // Голий місяць («jan», «may» — це й ім'я, й слово) — лише з джерелом або прийменником («у жовтні»).
                    val bare = day == null && year == null
                    val confident = !bare || sources.isNotEmpty() || (i > 0 && tokens[i - 1] in prepositions)
                    range = when {
                        !confident -> null
                        day != null -> dayRange(day, month, year, today)
                        else -> monthRange(month, year, today)
                    }
                    if (range != null) {
                        used[i] = true
                        if (dayBefore != null) used[i - 1] = true
                        if (dayAfter != null && dayBefore == null) used[i + 1] = true
                        if (year != null) used[yearIndex] = true
                        at = if (dayBefore != null) i - 1 else i
                        break
                    }
                }
                i++
            }
            // «фото 2025» — рік лише разом із назвою джерела, інакше це просто число.
            if (range == null && sources.isNotEmpty()) {
                tokens.indices.firstOrNull { !used[it] && tokens[it].toIntOrNull() in 1900..2100 }?.let { index ->
                    val year = tokens[index].toInt()
                    range = LocalDate.of(year, 1, 1) to LocalDate.of(year + 1, 1, 1)
                    used[index] = true
                    at = index
                }
            }
            val found = range ?: return null
            if (at > 0 && tokens[at - 1] in prepositions && !used[at - 1]) used[at - 1] = true
            // «w zeszłym tygodniu», «w tym miesiącu» — прийменник перед фразою.
            val text = tokens.indices.filterNot { used[it] || (tokens[it] in prepositions && it + 1 < tokens.size && used[it + 1]) }
                .joinToString(" ") { tokens[it] }
            return DateQuery(found.first, found.second, sources, text)
        }

        private fun spanRange(span: Span, today: LocalDate): Pair<LocalDate, LocalDate> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val month = today.withDayOfMonth(1)
            return when (span) {
                Span.TODAY -> today to today.plusDays(1)
                Span.YESTERDAY -> today.minusDays(1) to today
                Span.BEFORE_YESTERDAY -> today.minusDays(2) to today.minusDays(1)
                Span.THIS_WEEK -> monday to today.plusDays(1)
                Span.LAST_WEEK -> monday.minusWeeks(1) to monday
                Span.THIS_MONTH -> month to today.plusDays(1)
                Span.LAST_MONTH -> month.minusMonths(1) to month
                Span.PAST_WEEK -> today.minusDays(6) to today.plusDays(1)
                Span.PAST_MONTH -> today.minusDays(29) to today.plusDays(1)
                Span.PAST_YEAR -> today.minusDays(364) to today.plusDays(1)
                Span.THIS_YEAR -> today.withDayOfYear(1) to today.plusDays(1)
                Span.LAST_YEAR -> today.withDayOfYear(1).minusYears(1) to today.withDayOfYear(1)
            }
        }

        /** Без року — найближча дата, що вже минула (у жовтні «12 грудня» — торішнє). */
        private fun dayRange(day: Int, month: Int, year: Int?, today: LocalDate): Pair<LocalDate, LocalDate>? {
            if (month !in 1..12) return null
            val chosenYear = year ?: today.year
            if (day !in 1..YearMonth.of(chosenYear, month).lengthOfMonth()) return null
            var date = LocalDate.of(chosenYear, month, day)
            if (year == null && date.isAfter(today)) {
                val previous = YearMonth.of(chosenYear - 1, month)
                if (day > previous.lengthOfMonth()) return null
                date = LocalDate.of(chosenYear - 1, month, day)
            }
            return date to date.plusDays(1)
        }

        private fun monthRange(month: Int, year: Int?, today: LocalDate): Pair<LocalDate, LocalDate> {
            var start = LocalDate.of(year ?: today.year, month, 1)
            if (year == null && start.isAfter(today)) start = start.minusYears(1)
            return start to start.plusMonths(1)
        }
    }
}
