package com.blacklaunch.diary.search

import android.content.Context
import com.blacklaunch.diary.data.Day
import java.text.Normalizer
import java.time.LocalDate

/** Що шукаємо: нотатки повністю, лише фото чи лише голос. */
enum class SearchMode { Notes, Photo, Audio }

/** Період, сказаний словом: «тиждень», «місяць», «рік» — останні N днів. */
enum class Period(val days: Int) { Week(7), Month(31), Year(366) }

/** Розібраний запит: решта слів / дат, період і чи просили «найкраще». */
data class Query(val rest: String, val period: Period?, val best: Boolean)

/**
 * Результат пошуку. [best] — це добірка «найкраще»: по одному випадковому фото з кожного тижня (місяць) чи місяця (рік);
 * [hits] тоді вже вибрані.
 */
data class Found(val hits: List<SearchHit>, val period: Period?, val best: Boolean)

/** Знахідка: день, де саме в тексті ([start]…[end] — у повному тексті дня; -1 — збіг у даті чи місці) і уривок. */
data class SearchHit(
    val day: Long,
    val start: Int,
    val end: Int,
    val snippet: String,
    /** Виділене в уривку. */
    val snippetFrom: Int,
    val snippetTo: Int,
    val inPlace: Boolean,
)

/**
 * Пошук у щоденнику: за словами (без різниці великих літер і діакритики), датами («12.03», «12.03.2026», «березень»,
 * «2026») і місцем. Слова — усі мають бути в дні (як у звичайному пошуку). Без мережі й без індексу: днів небагато,
 * перебір миттєвий.
 */
object Searcher {
    private val DATE = Regex("""^(\d{1,2})[./\-](\d{1,2})(?:[./\-](\d{2,4}))?$""")
    private val YEAR = Regex("""^(19|20)\d{2}$""")
    private val BARE = Regex("""^\d{1,2}$""")

    /** Початки назв місяців трьома мовами (і в різних відмінках) → номер місяця. */
    private val MONTHS: List<Pair<String, Int>> = listOf(
        "січ" to 1, "лют" to 2, "берез" to 3, "квіт" to 4, "трав" to 5, "черв" to 6,
        "лип" to 7, "серп" to 8, "верес" to 9, "жовт" to 10, "листоп" to 11, "груд" to 12,
        "stycz" to 1, "styc" to 1, "lut" to 2, "marz" to 3, "marc" to 3, "kwie" to 4, "maj" to 5, "czerw" to 6,
        "lipi" to 7, "lipc" to 7, "sierp" to 8, "wrze" to 9, "pazd" to 10, "listop" to 11, "grud" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    /** Розбирає «фото за останній тиждень», «найкраще за місяць», «audio year» тощо трьома мовами. */
    fun parse(query: String): Query {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var period: Period? = null
        var best = false
        val rest = ArrayList<String>()
        for (t in tokens) {
            val n = norm(t).trim(',', '.', '!', '?', '«', '»', '"')
            val p = periodOf(n)
            when {
                p != null -> period = p
                BEST.any { n.startsWith(it) } -> best = true
                else -> rest += t
            }
        }
        // Службові слова («за», «останній», «фото»…) мають сенс лише поруч із періодом чи «найкраще».
        val kept = if (period != null || best) rest.filter { norm(it).trim(',', '.') !in FILLER } else rest
        return Query(kept.joinToString(" "), period, best)
    }

    private val BEST by lazy { listOf("найкращ", "найліпш", "best", "najlep").map { norm(it) } }
    private val FILLER by lazy {
        setOf(
            "за", "останній", "останні", "останнє", "остання", "минулий", "цей", "цьому", "цього",
            "фото", "фотографії", "аудіо", "голос", "записи", "з", "кожного",
            "last", "past", "the", "this", "for", "of", "in", "photo", "photos", "audio", "voice", "from", "every",
            "ostatni", "ostatnie", "ostatnia", "za", "z", "w", "ten", "tym", "zdjęcia", "zdjęcie", "głos", "nagrania",
        ).map { norm(it) }.toSet()
    }

    private fun periodOf(n: String): Period? = when {
        n.startsWith(norm("тижд")) || n.startsWith(norm("тижн")) || n == "week" || n == "weeks" || n.startsWith("tydz") || n.startsWith("tygod") -> Period.Week
        n.startsWith(norm("місяц")) || n == "month" || n == "months" || n.startsWith("miesi") -> Period.Month
        n == norm("рік") || n == norm("року") || n == "year" || n == "years" || n == "rok" || n == "roku" -> Period.Year
        else -> null
    }

    /**
     * Пошук у режимі [mode]: слова й дати — як завжди, плюс період («тиждень», «місяць», «рік» — від [today] назад) і
     * «найкраще» (лише фото: за тиждень — усі, за місяць — по одному з тижня, за рік — по одному з місяця, навмання).
     */
    fun find(days: List<Day>, query: String, mode: SearchMode, today: Long, random: kotlin.random.Random = kotlin.random.Random): Found {
        // Поки нічого не написав — нічого й не знаходимо (і для фото, і для записів): спершу підказка.
        if (query.isBlank()) return Found(emptyList(), null, false)
        val q = parse(query)
        val best = q.best && mode == SearchMode.Photo
        val period = q.period ?: if (best) Period.Month else null
        val pool = if (period != null) days.filter { it.day in (today - period.days + 1)..today } else days
        var hits = search(pool, q.rest, mode == SearchMode.Photo, mode == SearchMode.Audio, all = period != null)
        if (best && period != Period.Week) {
            hits = hits
                .groupBy { h -> if (period == Period.Year) LocalDate.ofEpochDay(h.day).let { it.year * 12L + it.monthValue } else Math.floorDiv(today - h.day, 7L) }
                .values.map { it.random(random) }
                .sortedByDescending { it.day }
        }
        return Found(hits, period, best)
    }

    fun search(days: List<Day>, query: String, photo: Boolean, audio: Boolean, context: Context? = null, all: Boolean = false): List<SearchHit> {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() && !photo && !audio && !all) return emptyList()
        var dayFilter: ((LocalDate) -> Boolean)? = null
        val words = ArrayList<String>()
        // Просто число («11»): число місяця, місяць (до 12) — або те саме число в тексті чи місці.
        val numbers = ArrayList<Int>()
        for (t in tokens) {
            val bare = BARE.matchEntire(t)?.value?.toInt()
            if (bare != null && bare in 1..31) { numbers += bare; continue }
            val f = dateFilter(t)
            if (f != null) {
                val prev = dayFilter
                dayFilter = if (prev == null) f else { d -> prev(d) && f(d) }
            } else {
                words += norm(t)
            }
        }
        val out = ArrayList<SearchHit>()
        for (d in days.sortedByDescending { it.day }) {
            if (photo && !d.photo) continue
            if (audio && !d.audio && !d.audioB) continue
            val date = LocalDate.ofEpochDay(d.day)
            if (dayFilter != null && !dayFilter(date)) continue
            val text = d.text
            val nText = norm(text)
            val nPlace = norm(d.place)
            if (words.any { !nText.contains(it) && !nPlace.contains(it) }) continue
            val numberInText = ArrayList<String>()
            if (numbers.any { n ->
                    val inDate = date.dayOfMonth == n || (n <= 12 && date.monthValue == n)
                    val s = n.toString()
                    val inText = nText.contains(s) || nPlace.contains(s)
                    if (!inDate && inText) numberInText += s
                    !inDate && !inText
                }
            ) continue
            // Підсвічуємо перше слово; якщо слів немає — число, знайдене в тексті (а не в даті).
            val first = words.firstOrNull() ?: numberInText.firstOrNull()
            val idx = if (first != null) nText.indexOf(first) else -1
            if (idx >= 0) {
                out += hit(d.day, text, idx, idx + first!!.length, inPlace = false)
            } else if (first != null) {
                val p = nPlace.indexOf(first)
                out += SearchHit(d.day, -1, -1, d.place, p.coerceAtLeast(0), (p + first.length).coerceAtMost(d.place.length), inPlace = true)
            } else {
                out += hit(d.day, text, -1, -1, inPlace = false)
            }
        }
        return out
    }

    private fun hit(day: Long, text: String, start: Int, end: Int, inPlace: Boolean): SearchHit {
        if (start < 0) {
            val s = text.take(110).replace('\n', ' ')
            return SearchHit(day, -1, -1, if (text.length > 110) "$s…" else s, 0, 0, inPlace)
        }
        val from = (start - 40).coerceAtLeast(0).let { f -> if (f > 0) text.lastIndexOf(' ', f).let { if (it in 0 until start) it + 1 else f } else 0 }
        val to = (end + 80).coerceAtMost(text.length)
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < text.length) "…" else ""
        val snippet = prefix + text.substring(from, to).replace('\n', ' ') + suffix
        return SearchHit(day, start, end, snippet, prefix.length + start - from, prefix.length + end - from, inPlace)
    }

    /** Без діакритики й у нижньому регістрі; довжина рядка не міняється (щоб індекси збігались з оригіналом). */
    fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = ch.lowercaseChar()
            val base = when (c) {
                'ł' -> 'l'
                'ї', 'і' -> 'і'
                'й' -> 'и'
                'ё' -> 'е'
                'ґ' -> 'г'
                else -> {
                    val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                    if (d.isNotEmpty()) d[0] else c
                }
            }
            sb.append(base)
        }
        return sb.toString()
    }

    private fun dateFilter(token: String): ((LocalDate) -> Boolean)? {
        DATE.matchEntire(token)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            if (d in 1..31 && mo in 1..12) return { date -> date.dayOfMonth == d && date.monthValue == mo && (y == null || date.year == y) }
        }
        if (YEAR.matches(token)) {
            val y = token.toInt()
            return { it.year == y }
        }
        val n = norm(token)
        if (n.length >= 3) {
            MONTHS.firstOrNull { (stem, _) -> n.startsWith(norm(stem)) }?.let { (_, month) -> return { it.monthValue == month } }
        }
        return null
    }
}
