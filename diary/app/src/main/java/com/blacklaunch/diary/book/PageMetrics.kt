package com.blacklaunch.diary.book

/**
 * Розкладка сторінки в «одиницях сторінки»: ширина сторінки = 100 одиниць. Сторінка має сталі пропорції (як аркуш
 * щоденника), а шрифт і лінії — сталу частку ширини. Тож на будь-якому телефоні на сторінку вміщається однаково
 * тексту — у дня справді фіксована «кількість місця».
 */
object PageMetrics {
    /** Висота сторінки до ширини: 1 : 2 — майже весь екран сучасного телефона, але стала (однаково тексту на день). */
    const val ASPECT = 2f

    const val WIDTH = 100f
    const val HEIGHT = WIDTH * ASPECT

    /** Поля тексту. */
    const val MARGIN_LEFT = 8f
    const val MARGIN_RIGHT = 7f

    /** Висота рядка (відстань між лініями). */
    const val LINE = 6.4f

    /** Шрифт: «Скло» (Exo 2) і «Аркуш» (Caveat — на око дрібніший, тож більший). */
    const val FONT_GLASS = 3.55f
    const val FONT_SHEET = 4.5f

    /** Шапка (дата, місце) і де починається перший рядок. */
    const val HEADER_TOP = 5.5f
    const val HEADER_HEIGHT = 9f
    const val TEXT_TOP_FIRST = 17f
    const val TEXT_TOP_SECOND = 13f
    const val BOTTOM = 8f

    /** Фото дня — висота блоку, і смужка аудіо. */
    const val PHOTO_HEIGHT = 50f
    const val AUDIO_HEIGHT = 12f
    const val BLOCK_GAP = 2.4f

    val textWidth: Float get() = WIDTH - MARGIN_LEFT - MARGIN_RIGHT

    /** Звідки починаються рядки на першій сторінці (після фото й аудіо). */
    fun firstTextTop(photo: Boolean, audio: Boolean): Float {
        var y = TEXT_TOP_FIRST
        if (photo) y += PHOTO_HEIGHT + BLOCK_GAP
        if (audio) y += AUDIO_HEIGHT + BLOCK_GAP
        return y
    }

    /** Скільки рядків на першій сторінці дня. */
    fun firstLines(photo: Boolean, audio: Boolean): Int = ((HEIGHT - BOTTOM - firstTextTop(photo, audio)) / LINE).toInt()

    /** Скільки рядків на другій сторінці дня. */
    val secondLines: Int get() = ((HEIGHT - BOTTOM - TEXT_TOP_SECOND) / LINE).toInt()
}

/**
 * Поділ тексту дня між двома сторінками. [lineStarts] — початок кожного рядка тексту (з розкладки шрифту).
 * Повертає індекс символу, з якого починається друга сторінка, і чи вміщається все в дві сторінки.
 */
data class PageSplit(val split: Int, val fits: Boolean, val lines: Int)

fun splitPages(textLength: Int, lineStarts: List<Int>, firstLines: Int, secondLines: Int): PageSplit {
    val lines = lineStarts.size
    val split = if (lines > firstLines) lineStarts[firstLines] else textLength
    return PageSplit(split = split, fits = lines <= firstLines + secondLines, lines = lines)
}

/**
 * Порожні рядки після курсора — вільне місце, як на папері: поки текст не вміщається ([fits]), прибираємо найближчий
 * порожній рядок (лише з пробілів) після [cursor]. Написане нижче лишається на місці, просто підтягується.
 */
fun absorbBlankLines(text: String, cursor: Int, fits: (String) -> Boolean): String {
    var t = text
    val blank = Regex("\n[ \t]*(?=\n|$)")
    while (!fits(t)) {
        val cut = blank.find(t, cursor.coerceIn(0, t.length)) ?: break
        t = t.removeRange(cut.range)
    }
    return t
}
