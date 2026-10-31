package com.blacklaunch.qrcodes.scan

/**
 * Які тексти з фото пропонувати для QR-коду: лише довші за [MIN_LENGTH] символів (без пробілів по краях) —
 * короткі шматки камера не «плодить», їх простіше ввести вручну. Однакові рядки — один раз.
 */
object TextPick {
    const val MIN_LENGTH = 5

    fun accept(text: String): Boolean = text.trim().length > MIN_LENGTH

    fun <T> filter(items: List<T>, text: (T) -> String): List<T> {
        val seen = HashSet<String>()
        return items.filter { item ->
            val t = text(item).trim()
            accept(t) && seen.add(t)
        }
    }

    /** Вибрані слова — одним рядком у порядку читання (рядок за рядком, зліва направо), через пробіл. */
    fun join(lines: List<List<String>>, selected: Set<WordRef>): String =
        selected.sortedWith(compareBy({ it.line }, { it.word }))
            .mapNotNull { lines.getOrNull(it.line)?.getOrNull(it.word) }
            .joinToString(" ")
}

/** Слово з фото: номер рядка й номер слова в ньому. */
data class WordRef(val line: Int, val word: Int)

/**
 * Вибір пальцем: затиснув слово [start] і ведеш — до вибраного раніше додається (або з нього знімається, якщо почали з
 * уже вибраного слова) усе від першого слова до того, що під пальцем, у порядку читання.
 */
class RangeDrag(private val order: List<WordRef>) {
    private var base: Set<WordRef> = emptySet()
    private var anchor: Int = -1
    private var adding = true
    private var last: Int = -1

    fun start(ref: WordRef, selected: Set<WordRef>): Set<WordRef> {
        base = selected
        anchor = order.indexOf(ref)
        adding = ref !in selected
        last = anchor
        return apply()
    }

    /** null — нічого не змінилось (палець між словами чи на тому самому). */
    fun move(ref: WordRef?): Set<WordRef>? {
        if (anchor < 0 || ref == null) return null
        val i = order.indexOf(ref)
        if (i < 0 || i == last) return null
        last = i
        return apply()
    }

    fun end() {
        anchor = -1
    }

    private fun apply(): Set<WordRef> {
        if (anchor < 0) return base
        val range = order.subList(minOf(anchor, last), maxOf(anchor, last) + 1).toSet()
        return if (adding) base + range else base - range
    }
}
