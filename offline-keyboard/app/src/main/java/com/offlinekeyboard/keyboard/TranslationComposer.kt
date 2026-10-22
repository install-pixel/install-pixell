package com.offlinekeyboard.keyboard

import android.view.inputmethod.InputConnection
import com.offlinekeyboard.core.input.TextBoundaries

/**
 * Translate mode. What the user types stays in the field as composing text (underlined), so they always
 * see and can correct their own words; a live preview of the translation is shown above the keys. When
 * the segment is finished (end of sentence, Enter, a pause in automatic mode) its translation replaces
 * exactly this segment once. Other text in the field is never touched.
 *
 * Every change bumps [generation]; a translation that arrives for an older generation is ignored.
 */
class TranslationComposer(
    private val connection: () -> InputConnection?,
    /** Absolute cursor position in the field, or -1 if unknown. Read when a segment starts. */
    private val cursorPosition: () -> Int = { -1 },
) {
    private val buffer = StringBuilder()

    var generation = 0
        private set

    /** Waiting for the final translation of [generation]. */
    var pending = false
        private set

    // Where the segment starts in the field and where our own edits put the cursor. Many apps (messengers,
    // browsers) do not report the composing region, so cursor updates are matched against these positions
    // to tell our own edits from the user moving the cursor.
    private var segmentStart = -1
    private val recentEnds = ArrayDeque<Int>()

    val text: String get() = buffer.toString()
    val isEmpty: Boolean get() = buffer.isEmpty()

    fun append(text: String) {
        if (buffer.isEmpty()) segmentStart = cursorPosition()
        buffer.append(text)
        changed()
    }

    /** Removes one character of the source; false when there is nothing to remove. */
    fun backspace(): Boolean {
        if (buffer.isEmpty()) return false
        val n = TextBoundaries.previousGraphemeLength(buffer)
        buffer.setLength(buffer.length - n)
        changed()
        return true
    }

    /**
     * True if a cursor update was caused by our own edits of the segment (so the segment continues),
     * false if the user moved the cursor or selected text.
     */
    fun isOwnCursorUpdate(selStart: Int, selEnd: Int, candidatesStart: Int, candidatesEnd: Int): Boolean {
        if (selStart != selEnd) return false
        if (candidatesStart >= 0) return selEnd == candidatesEnd
        if (segmentStart < 0) return true // position unknown: never interrupt typing
        return selEnd in recentEnds
    }

    /** Starts the final translation; returns the text and the generation to pass to [applyTranslation]. */
    fun beginTranslation(): Pair<String, Int>? {
        if (buffer.isBlank()) return null
        pending = true
        return buffer.toString() to generation
    }

    /**
     * Replaces the composing source with [translation] and commits it. Returns the inserted text, or null
     * if the segment changed meanwhile (then nothing is touched).
     */
    fun applyTranslation(forGeneration: Int, translation: String): String? {
        if (forGeneration != generation || !pending) return null
        val ic = connection() ?: return null
        val result = keepSpaces(buffer.toString(), translation)
        ic.beginBatchEdit()
        setComposing(ic, result)
        ic.finishComposingText()
        ic.endBatchEdit()
        reset()
        return result
    }

    /** Translation failed: keep the source as composing text so the user can edit or commit it. */
    fun translationFailed(forGeneration: Int) {
        if (forGeneration == generation) pending = false
    }

    /** The user moved on (cursor moved, other key, mode switched): the typed source stays as it is. */
    fun leave() {
        if (buffer.isEmpty() && !pending) return
        connection()?.finishComposingText()
        reset()
    }

    /** The field changed under us (another field got focus): forget the segment without touching it. */
    fun abandon() {
        reset()
    }

    private fun setComposing(ic: InputConnection, text: CharSequence) {
        ic.setComposingText(text, 1)
        if (segmentStart >= 0) {
            recentEnds.addLast(segmentStart + text.length)
            while (recentEnds.size > RECENT_ENDS) recentEnds.removeFirst()
        }
    }

    private fun changed() {
        generation++
        pending = false
        connection()?.let { setComposing(it, buffer) }
    }

    private fun reset() {
        buffer.setLength(0)
        generation++
        pending = false
        segmentStart = -1
        recentEnds.clear()
    }

    private companion object {
        const val RECENT_ENDS = 32

        /** Keeps the user's leading/trailing spaces around the translated words. */
        fun keepSpaces(source: String, translation: String) =
            source.takeWhile { it.isWhitespace() } + translation.trim() + source.takeLastWhile { it.isWhitespace() }
    }
}
