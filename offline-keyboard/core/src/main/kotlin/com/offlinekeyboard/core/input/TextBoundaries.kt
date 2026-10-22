package com.offlinekeyboard.core.input

import java.text.BreakIterator

/**
 * Grapheme- and word-aware distances, so Backspace removes a whole emoji, flag or letter with accent
 * instead of half a surrogate pair. Android's java.text.BreakIterator is backed by ICU.
 */
object TextBoundaries {

    /** UTF-16 length of the last user-perceived character of [textBefore]. */
    fun previousGraphemeLength(textBefore: CharSequence): Int {
        if (textBefore.isEmpty()) return 0
        val text = textBefore.toString()
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val start = it.preceding(text.length)
        return if (start == BreakIterator.DONE) text.length else text.length - start
    }

    /** UTF-16 length of the first user-perceived character of [textAfter]. */
    fun nextGraphemeLength(textAfter: CharSequence): Int {
        if (textAfter.isEmpty()) return 0
        val text = textAfter.toString()
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val end = it.following(0)
        return if (end == BreakIterator.DONE) text.length else end
    }

    /** Ctrl+Backspace / Ctrl+←: trailing spaces, then one word (or one run of punctuation). */
    fun previousWordLength(textBefore: CharSequence): Int {
        var i = textBefore.length
        while (i > 0 && textBefore[i - 1].isWhitespace()) i--
        if (i > 0) {
            val wordRun = isWordChar(textBefore[i - 1])
            while (i > 0 && !textBefore[i - 1].isWhitespace() && isWordChar(textBefore[i - 1]) == wordRun) i--
        }
        return textBefore.length - i
    }

    /** Ctrl+Delete / Ctrl+→: one word (or punctuation run), then the spaces after it. */
    fun nextWordLength(textAfter: CharSequence): Int {
        var i = 0
        val n = textAfter.length
        if (i < n && !textAfter[i].isWhitespace()) {
            val wordRun = isWordChar(textAfter[i])
            while (i < n && !textAfter[i].isWhitespace() && isWordChar(textAfter[i]) == wordRun) i++
        }
        while (i < n && textAfter[i].isWhitespace() && textAfter[i] != '\n') i++
        if (i == 0 && n > 0) i = 1 // a lone newline
        return i
    }

    private fun isWordChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '\'' || c == 'ʼ' || c == '’' || Character.isSurrogate(c) ||
            Character.getType(c) == Character.NON_SPACING_MARK.toInt()
}
