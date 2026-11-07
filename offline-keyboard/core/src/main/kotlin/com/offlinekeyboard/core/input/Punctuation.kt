package com.offlinekeyboard.core.input

/**
 * Small writing rules for punctuation, applied while typing in ordinary text (never in passwords, addresses or
 * code). [adjust] looks at the text just before the cursor and what is being typed and says how to put it in:
 *
 * 1. No space before a closing mark: "word ," → "word, " (the space moves after the mark), also for . ! ? : ; … ) » ”.
 * 2. A space after a comma-like mark: typing a letter right after , ; ! ? : puts a space in between ("so,then" →
 *    "so, then") — but not between digits (1,5 · 10:30), not in addresses (http://, mail@, www.), and never after
 *    "." (file.txt, e.g., 3.14 stay as typed).
 * 3. No space right after an opening mark: a space after ( « „ is not typed.
 *
 * The returned [Edit] replaces [Edit.delete] characters before the cursor with [Edit.insert]; null means "type
 * it as it is". [Edit.undo] is what a Backspace right after should bring back.
 */
object Punctuation {

    data class Edit(val delete: Int, val insert: String, val undo: String)

    private const val CLOSING = ",.!?:;…)»”%"
    private const val SPACED_AFTER = ",;!?:"
    private const val OPENING = "(«„"

    fun adjust(before: String, typed: String): Edit? {
        if (typed.length != 1) return null
        val c = typed[0]
        // 1. "word ," → "word, "
        if (c in CLOSING) {
            val spaces = before.length - before.trimEnd(' ').length
            if (spaces > 0) {
                val word = before.dropLast(spaces)
                val last = word.lastOrNull() ?: return null
                if (last.isWhitespace() || last in OPENING) return null
                // "a - b": a dash between spaces keeps its spaces; so does a mark typed after another mark.
                if (last == '-' || last == '–' || last == '—') return null
                val trailing = if (c == '%') "" else " "
                return Edit(spaces, typed + trailing, " ".repeat(spaces) + typed)
            }
            return null
        }
        // 2. "so,then" → "so, then"
        if (c.isLetter()) {
            val mark = before.lastOrNull() ?: return null
            if (mark !in SPACED_AFTER) return null
            val beforeMark = before.getOrNull(before.length - 2) ?: return null
            if (!beforeMark.isLetter() && beforeMark != ')' && beforeMark != '»' && beforeMark != '"') return null
            val token = before.takeLastWhile { !it.isWhitespace() }.lowercase()
            if ("://" in token || '@' in token || token.startsWith("www.")) return null
            if (mark == ':' && token.dropLast(1) in URL_SCHEMES) return null
            return Edit(0, " $typed", typed)
        }
        // 3. "( word" → "(word"
        if (c == ' ') {
            val last = before.lastOrNull() ?: return null
            if (last in OPENING) return Edit(0, "", " ")
        }
        return null
    }

    private val URL_SCHEMES = setOf("http", "https", "ftp", "mailto", "tel", "sms", "geo")
}
