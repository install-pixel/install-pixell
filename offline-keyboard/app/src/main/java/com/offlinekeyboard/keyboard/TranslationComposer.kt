package com.offlinekeyboard.keyboard

import android.view.inputmethod.InputConnection
import com.offlinekeyboard.core.input.TextBoundaries

/**
 * Translate mode for typed text. What the user types is kept as the field's composing text (underlined)
 * and only that segment is ever replaced: Enter swaps it for the translation and commits it. Moving the
 * cursor, switching fields or leaving translate mode commits the typed source text unchanged, so nothing
 * typed is lost and no other text in the field is touched.
 *
 * Every change bumps [generation]; a translation that finishes after a change is discarded.
 */
class TranslationComposer(private val connection: () -> InputConnection?) {

    private val buffer = StringBuilder()

    var generation = 0
        private set

    /** Waiting for a translation of [generation]. */
    var pending = false
        private set

    val text: String get() = buffer.toString()
    val isEmpty: Boolean get() = buffer.isEmpty()

    fun append(text: String) {
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

    /** Starts a translation; returns the text and the generation to pass to [applyTranslation]. */
    fun beginTranslation(): Pair<String, Int>? {
        if (buffer.isBlank()) return null
        pending = true
        return buffer.toString() to generation
    }

    /**
     * Replaces the composing source with [translation]. Returns the inserted text, or null if the segment
     * changed meanwhile (then nothing is touched).
     */
    fun applyTranslation(forGeneration: Int, translation: String): String? {
        if (forGeneration != generation || !pending) return null
        val ic = connection() ?: return null
        // Keep the user's leading/trailing spaces around the translated words.
        val source = buffer.toString()
        val result = source.takeWhile { it.isWhitespace() } + translation.trim() + source.takeLastWhile { it.isWhitespace() }
        ic.beginBatchEdit()
        ic.setComposingText(result, 1)
        ic.finishComposingText()
        ic.endBatchEdit()
        reset()
        return result
    }

    /** Translation failed: keep the source as composing text so the user can edit or commit it. */
    fun translationFailed(forGeneration: Int) {
        if (forGeneration == generation) pending = false
    }

    /** Commits the typed source unchanged (cursor moved, field changed, translate turned off). */
    fun commitSource() {
        if (buffer.isEmpty() && !pending) return
        connection()?.finishComposingText()
        reset()
    }

    /** The field changed under us (cursor moved by the user): forget the segment without touching the field. */
    fun abandon() {
        reset()
    }

    private fun changed() {
        generation++
        pending = false
        connection()?.setComposingText(buffer, 1)
    }

    private fun reset() {
        buffer.setLength(0)
        generation++
        pending = false
    }
}
