package com.blacklaunch.diary

import com.blacklaunch.diary.book.CurlGeometry
import com.blacklaunch.diary.book.PageMetrics
import com.blacklaunch.diary.book.splitPages
import com.blacklaunch.diary.book.wordRange
import com.blacklaunch.diary.data.Day
import com.blacklaunch.diary.data.DayCodec
import com.blacklaunch.diary.data.InkLine
import com.blacklaunch.diary.data.Strike
import com.blacklaunch.diary.image.Stylize
import com.blacklaunch.diary.search.Searcher
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class LogicTest {

    // ——— Фото під тему: ніколи не світліше за стелю (текст поверх завжди читається) ———

    private fun photo(w: Int, h: Int): IntArray {
        val r = Random(3)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            // Біле небо вгорі, темна земля внизу, різкий перехід і шум — найгірший випадок для тексту поверх.
            val v = if (y < h / 2) 250 else (40 + r.nextInt(60))
            val c = (v + (x % 7)).coerceAtMost(255)
            (0xFF shl 24) or (c shl 16) or (c shl 8) or c
        }
    }

    private fun maxLight(px: IntArray) = px.maxOf { (it and 0xFF) / 255f }

    @Test
    fun glassNeverTooBright() {
        val out = Stylize.glass(photo(120, 90), 120, 90)
        assertTrue("найсвітліше ${maxLight(out)}", maxLight(out) <= Stylize.MAX_LIGHT + 0.01f)
    }

    @Test
    fun pencilNeverTooBright() {
        val out = Stylize.pencil(photo(120, 90), 120, 90)
        assertTrue("найсвітліше ${maxLight(out)}", maxLight(out) <= Stylize.MAX_LIGHT + 0.09f)
    }

    @Test
    fun pencilKeepsLikeness() {
        // Білим олівцем малюють світло: світле на фото — світле й на малюнку, темне — чорний папір;
        // на межі — світла лінія обрису.
        val w = 120
        val h = 90
        val out = Stylize.pencil(photo(w, h), w, h)
        fun v(y: Int) = (0 until w).map { out[y * w + it] and 0xFF }.average()
        val light = v(10)
        val dark = v(h - 10)
        val edge = v(h / 2 + 1)
        assertTrue("світле $light має бути світлішим за темне $dark", light > dark + 40)
        assertTrue("обрис $edge має бути світлішим за темне $dark", edge > dark + 5)
    }

    @Test
    fun blurKeepsAverage() {
        val src = FloatArray(100) { if (it % 2 == 0) 1f else 0f }
        val out = Stylize.boxBlur(src, 10, 10, 2, 3)
        assertEquals(0.5f, out.average().toFloat(), 0.05f)
    }

    // ——— Чорнило висихає ———

    @Test
    fun freshInkDriesAfterTime() {
        val t0 = 1_000_000L
        val d = Day(day = 1, sealed = "Ранок. ", fresh = "Вечір.", freshSince = t0)
        assertEquals(d, d.dried(t0 + 23 * Day.HOUR, 24))
        val dry = d.dried(t0 + 25 * Day.HOUR, 24)
        assertEquals("Ранок. Вечір.", dry.sealed)
        assertEquals("", dry.fresh)
        assertEquals("Ранок. Вечір.", dry.text)
    }

    @Test
    fun inkStrokeDries() {
        val line = InkLine(0, floatArrayOf(0f, 1f), floatArrayOf(0f, 1f), FloatArray(0), 0.01f, time = 0L)
        val d = Day(1)
        assertFalse(d.isInkDry(line, Day.HOUR, 24))
        assertTrue(d.isInkDry(line, 24 * Day.HOUR, 24))
    }

    @Test
    fun emptyDayIsEmpty() {
        assertTrue(Day(5).isEmpty)
        assertFalse(Day(5, place = "Київ").isEmpty)
        assertFalse(Day(5, photo = true).isEmpty)
    }

    // ——— Дві сторінки на день ———

    @Test
    fun splitBetweenPages() {
        // 10 рядків по 5 символів; на першій сторінці 4 рядки, на другій 6.
        val starts = List(10) { it * 5 }
        val s = splitPages(50, starts, 4, 6)
        assertEquals(20, s.split)
        assertTrue(s.fits)
        assertFalse(splitPages(55, List(11) { it * 5 }, 4, 6).fits)
        assertEquals(12, splitPages(12, listOf(0, 5, 10), 4, 6).split)
    }

    @Test
    fun pageHasRoomForText() {
        // Перша сторінка без фото — понад 18 рядків, друга — понад 20: день вміщає ~1,5–2 тис. символів.
        assertTrue(PageMetrics.firstLines(photo = false, audio = false) >= 18)
        assertTrue(PageMetrics.secondLines >= 20)
        assertTrue(PageMetrics.firstLines(photo = true, audio = true) >= 8)
    }

    // ——— Загинання сторінки ———

    @Test
    fun curlStartsFlatAndEndsOffPage() {
        // Початок: валик за правим краєм — кожна точка аркуша там, де лежала.
        val flat = CurlGeometry.at(0f, 300f, heavy = false)
        for (x in listOf(0f, 120f, 299f)) assertEquals(x, flat.map(x), 0.01f)
        // Кінець: увесь аркуш (і валик) уже за корінцем — на сторінці нічого не лишилось.
        for (heavy in listOf(false, true)) {
            val gone = CurlGeometry.at(1f, 300f, heavy)
            for (x in listOf(0f, 150f, 300f)) assertTrue(gone.map(x) <= 0.01f)
        }
    }

    @Test
    fun curlIsContinuousAndRollsOver() {
        val g = CurlGeometry.at(0.4f, 300f, heavy = false)
        // Безперервно: сусідні точки аркуша — поруч і на екрані (немає розривів між смужками).
        var x = 0f
        while (x < 300f) {
            assertTrue(abs(g.map(x + 1f) - g.map(x)) <= 1.001f)
            x += 1f
        }
        // Біля осі — лицем, далеко за нею — зворотом, і зворот лягає лівіше за вершину валика.
        assertTrue(g.front(g.axis + 1f))
        assertTrue(!g.front(300f))
        assertTrue(g.map(300f) < g.axis + g.radius)
    }

    @Test
    fun curlEdgeStaysOnPaper() {
        // Край, де малюється лінія й тінь, — справді найправіша точка аркуша (не «відривається» від сторінки).
        for (p in listOf(0.01f, 0.03f, 0.08f, 0.2f, 0.5f, 0.9f)) {
            for (heavy in listOf(false, true)) {
                val g = CurlGeometry.at(p, 300f, heavy)
                var max = -1e9f
                var x = 0f
                while (x <= 300f) { max = maxOf(max, g.map(x)); x += 0.5f }
                assertEquals(max, g.rightmost, 0.6f)
            }
        }
        // На самому початку загнутий край уже перевалив через вершину — видно круглий валик.
        val start = CurlGeometry.at(0.03f, 300f, heavy = false)
        assertTrue(!start.front(300f))
    }

    @Test
    fun curlProgressFromFinger() {
        assertEquals(0f, CurlGeometry.forwardProgress(250f, 250f, 300f), 0.001f)
        assertTrue(CurlGeometry.forwardProgress(250f, 100f, 300f) in 0.4f..0.9f)
        assertEquals(1f, CurlGeometry.backwardProgress(60f, 60f, 300f), 0.001f)
        assertEquals(0f, CurlGeometry.backwardProgress(60f, 300f, 300f), 0.001f)
    }

    // ——— Пошук ———

    private val days = listOf(
        Day(LocalDate.of(2025, 3, 12).toEpochDay(), place = "Львів", sealed = "Гуляли біля моря, вітер був сильний."),
        Day(LocalDate.of(2025, 7, 1).toEpochDay(), sealed = "Ми їли морозиво. Żółw на пляжі!", photo = true),
        Day(LocalDate.of(2026, 3, 2).toEpochDay(), sealed = "Нічого особливого"),
    )

    @Test
    fun searchWordsIgnoreCaseAndDiacritics() {
        assertEquals(1, Searcher.search(days, "МОРЯ", false, false).size)
        assertEquals(1, Searcher.search(days, "zolw", false, false).size)
        assertEquals(1, Searcher.search(days, "львів", false, false).size)
        assertTrue(Searcher.search(days, "львів", false, false)[0].inPlace)
    }

    @Test
    fun searchByDates() {
        assertEquals(1, Searcher.search(days, "12.03", false, false).size)
        assertEquals(1, Searcher.search(days, "12.03.2025", false, false).size)
        assertEquals(2, Searcher.search(days, "березень", false, false).size)
        assertEquals(2, Searcher.search(days, "marzec", false, false).size)
        assertEquals(2, Searcher.search(days, "2025", false, false).size)
        // Слово й місяць разом: обидва мають збігтися.
        assertEquals(1, Searcher.search(days, "моря березня", false, false).size)
        assertEquals(0, Searcher.search(days, "моря липня", false, false).size)
    }

    @Test
    fun searchFiltersAndHighlight() {
        val r = Searcher.search(days, "", photo = true, audio = false)
        assertEquals(1, r.size)
        val h = Searcher.search(days, "вітер", false, false).single()
        assertEquals("вітер", h.snippet.substring(h.snippetFrom, h.snippetTo))
        assertEquals("вітер", days[0].text.substring(h.start, h.end))
    }

    @Test
    fun normKeepsLength() {
        val s = "Żółw Їжак Ґанок ёлка"
        assertEquals(s.length, Searcher.norm(s).length)
    }

    // ——— Викреслення ———

    @Test
    fun strikeSnapsToWords() {
        val t = "Сьогодні був дощ"
        assertEquals(9 to 12, wordRange(t, 10, 11))
        assertEquals(0 to 12, wordRange(t, 3, 10))
    }

    @Test
    fun strikesMerge() {
        val m = DiaryViewModel.mergeStrikes(listOf(Strike(5, 9), Strike(0, 3), Strike(8, 12)))
        assertEquals(listOf(Strike(0, 3), Strike(5, 12)), m)
    }

    @Test
    fun waveCompacts() {
        val w = List(1000) { (it % 10) / 10f }
        val c = DiaryViewModel.compactWave(w)
        assertEquals(120, c.size)
        assertTrue(c.all { it <= 0.9f })
    }

    // ——— Кодування ———

    @Test
    fun inkRoundTrip() {
        val l = InkLine(1, floatArrayOf(0.1f, 0.2f), floatArrayOf(0.3f, 0.4f), floatArrayOf(1f, 0.5f), 0.007f, 42L)
        val back = DayCodec.inkFromJson(DayCodec.inkToJson(listOf(l))).single()
        assertEquals(1, back.page)
        assertEquals(0.2f, back.xs[1], 0.0001f)
        assertEquals(0.5f, back.factors[1], 0.01f)
        assertEquals(42L, back.time)
        assertEquals(listOf(Strike(1, 4)), DayCodec.strikesFromJson(DayCodec.strikesToJson(listOf(Strike(1, 4)))))
    }
}
