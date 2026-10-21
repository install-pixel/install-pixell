package com.offlinekeyboard.keyboard

import android.view.inputmethod.InputConnection
import com.offlinekeyboard.core.input.TextBoundaries

/**
 * Translate mode. Only the segment typed in this mode is ever replaced (the field's composing text);
 * other text in the field is never touched.
 *
 * - Manual: the source stays in the field (underlined); Enter swaps it for the translation.
 * - Auto: the field shows the live translation of what is typed; the source is shown above the keys.
 *   The segment is committed at the end of a sentence, on Enter or when the cursor leaves.
 *
 * Every change bumps [generation]; a translation that arrives for an older generation is ignored.
 */
class TranslationComposer(
    private val connection: () -> InputConnection?,
    /** Absolute cursor position in the field, or -1 if unknown. Read when a segment starts. */
    private val cursorPosition: () -> Int = { -1 },
) {

    var auto = true

    private val buffer = StringBuilder()

    var generation = 0
        private set

    /** Manual mode: waiting for a translation of [generation]. */
    var pending = false
        private set

    // Auto mode: what the field currently shows as composing text, and for which generation.
    private var shown: String? = null
    private var shownGeneration = -1

    // Where the segment starts in the field and where our own edits put the cursor. Many apps (messengers,
    // browsers) do not report the composing region, so cursor updates are matched against these positions
    // to tell our own edits from the user moving the cursor.
    private var segmentStart = -1
    private val recentEnds = ArrayDeque<Int>()

    val text: String get() = buffer.toString()
    val isEmpty: Boolean get() = buffer.isEmpty()

    /** Auto mode: the field already shows the translation of the current text. */
    val isUpToDate: Boolean get() = shown != null && shownGeneration == generation

    fun append(text: String) {
        if (buffer.isEmpty() && shown == null) segmentStart = cursorPosition()
        buffer.append(text)
        changed()
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

    private fun setComposing(ic: InputConnection, text: CharSequence) {
        ic.setComposingText(text, 1)
        if (segmentStart >= 0) {
            recentEnds.addLast(segmentStart + text.length)
            while (recentEnds.size > RECENT_ENDS) recentEnds.removeFirst()
        }
    }

    /** Removes one character of the source; false when there is nothing to remove. */
    fun backspace(): Boolean {
        if (buffer.isEmpty()) return false
        val n = TextBoundaries.previousGraphemeLength(buffer)
        buffer.setLength(buffer.length - n)
        changed()
        return true
    }

    // ---------------------------------------------------------------- manual mode

    /** Starts a translation; returns the text and the generation to pass to [applyTranslation]. */
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

    // ---------------------------------------------------------------- auto mode

    /** Shows [translation] of [forGeneration] in the field. False if the text changed meanwhile. */
    fun showLive(forGeneration: Int, translation: String): Boolean {
        if (forGeneration != generation || buffer.isEmpty()) return false
        val ic = connection() ?: return false
        val result = keepSpaces(buffer.toString(), translation)
        setComposing(ic, result)
        shown = result
        shownGeneration = forGeneration
        return true
    }

    /** Commits the up-to-date live translation. Returns it, or null if it is not up to date. */
    fun commitShown(): String? {
        if (!isUpToDate) return null
        val result = shown
        connection()?.finishComposingText()
        reset()
        return result
    }

    // ---------------------------------------------------------------- leaving the segment

    /**
     * The user moved on (cursor moved, other key, mode switched). Auto mode keeps an up-to-date translation;
     * otherwise the typed source is committed unchanged, so nothing is lost and nothing wrong is inserted.
     * Returns the committed translation, if that is what stayed.
     */
    fun leave(): String? {
        if (buffer.isEmpty() && !pending) return null
        if (auto && isUpToDate) return commitShown()
        val ic = connection()
        if (ic != null) {
            if (auto && shown != null) setComposing(ic, buffer)
            ic.finishComposingText()
        }
        reset()
        return null
    }

    /** The field changed under us (another field got focus): forget the segment without touching it. */
    fun abandon() {
        reset()
    }

    private fun changed() {
        generation++
        pending = false
        val ic = connection() ?: return
        when {
            buffer.isEmpty() -> {
                setComposing(ic, "")
                shown = null
            }
            // Auto mode: keep showing the last translation until the new one arrives (no flicker);
            // before the first translation, show what is typed.
            !auto || shown == null -> setComposing(ic, buffer)
        }
    }

    private fun reset() {
        buffer.setLength(0)
        generation++
        pending = false
        shown = null
        shownGeneration = -1
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
