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

    /** Малюнок дня: плашка, як смужка голосу (пунктиром — порожнє запрошення, суцільно — малюнок уже є). */
    const val DRAW_EMPTY = AUDIO_HEIGHT

    /**
     * Звідки починаються місця (фото, голос, малюнок): нижче закладки-пошуку, що звисає згори біля корінця, — вона на
     * них не налягає і не перехоплює дотики.
     */
    const val SLOTS_TOP = 20.5f

    /** Висота місця під малюнок: 0 — немає, 1 — порожнє запрошення, 2 — малюнок є. */
    fun drawHeight(drawing: Int): Float = if (drawing > 0) DRAW_EMPTY else 0f

    /** Звідки починаються рядки на першій сторінці (після фото, аудіо й малюнка). */
    fun firstTextTop(photo: Boolean, audio: Boolean, drawing: Int = 0): Float {
        if (!photo && !audio && drawing == 0) return TEXT_TOP_FIRST
        var y = SLOTS_TOP + 1.6f
        if (photo) y += PHOTO_HEIGHT + BLOCK_GAP
        if (audio) y += AUDIO_HEIGHT + BLOCK_GAP
        if (drawing > 0) y += drawHeight(drawing) + BLOCK_GAP
        return y
    }

    /** Скільки рядків на першій сторінці дня. */
    fun firstLines(photo: Boolean, audio: Boolean, drawing: Int = 0): Int = ((HEIGHT - BOTTOM - firstTextTop(photo, audio, drawing)) / LINE).toInt()

    /** Скільки рядків на кожній наступній сторінці дня. */
    val secondLines: Int get() = ((HEIGHT - BOTTOM - TEXT_TOP_SECOND) / LINE).toInt()
}

/**
 * Поділ тексту дня між його сторінками — їх скільки завгодно: що не вмістилось, переходить на наступну. [starts] —
 * з якого символу починається кожна сторінка (у вирваної місця немає — вона починається там само, де наступна),
 * [end] — довжина тексту, [lines] — скільки рядків займає написане.
 */
data class PageSplit(val starts: List<Int>, val end: Int, val lines: Int) {
    /** Місця вистачає завжди: сторінок стільки, скільки треба. */
    val fits: Boolean get() = true
    /** Де починається друга сторінка. */
    val split: Int get() = start(1)
    val pages: Int get() = starts.size
    fun start(part: Int): Int = starts.getOrElse(part) { end }
    fun end(part: Int): Int = starts.getOrElse(part + 1) { end }
    /** На якій сторінці символ [offset] (вирвані пропускаються). */
    fun partOf(offset: Int): Int = starts.indexOfLast { it <= offset }.coerceAtLeast(0)
}

/** Найбільше сторінок на день (і бітів у позначці вирваних). */
const val MAX_DAY_PAGES = 30

/**
 * Поділ тексту довжиною [textLength] на сторінки. [capacity] — скільки рядків уміщає сторінка (вирвана — 0);
 * [measure] — розкладка тексту від символу `from`, що починається вгорі сторінки `part`: початки рядків (індекси в
 * усьому тексті) і скільки рядків займає написане (порожні в кінці не рахуються).
 */
fun splitPages(textLength: Int, capacity: (Int) -> Int, measure: (part: Int, from: Int) -> Pair<List<Int>, Int>): PageSplit {
    val starts = ArrayList<Int>()
    var from = 0
    var lines = 0
    var part = 0
    while (part < MAX_DAY_PAGES) {
        starts += from
        val cap = capacity(part)
        if (cap > 0) {
            val (ls, used) = measure(part, from)
            if (used <= cap || from >= textLength || part == MAX_DAY_PAGES - 1) { lines += used; break }
            from = ls[cap]
            lines += cap
        }
        part++
    }
    return PageSplit(starts, textLength, lines)
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
