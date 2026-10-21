package com.offlinekeyboard.core.input

/** A selection inside a known piece of text. [anchor] stays put while Shift+arrows move [active]. */
data class TextSelection(val anchor: Int, val active: Int) {
    val start: Int get() = minOf(anchor, active)
    val end: Int get() = maxOf(anchor, active)
    val isCollapsed: Boolean get() = anchor == active
}

/**
 * Horizontal cursor movement computed from the field's text, used when the field exposes its text
 * (ExtractedText). Vertical movement depends on line wrapping, which only the app knows, so it is sent
 * as key events instead.
 */
object SelectionMath {

    fun supports(direction: Direction): Boolean =
        direction == Direction.LEFT || direction == Direction.RIGHT || direction == Direction.HOME || direction == Direction.END

    fun move(
        text: CharSequence,
        selection: TextSelection,
        direction: Direction,
        select: Boolean,
        byWord: Boolean = false,
        toDocumentEdge: Boolean = false,
    ): TextSelection {
        require(supports(direction)) { "Vertical movement is not computed from text: $direction" }
        val len = text.length
        val sel = TextSelection(selection.anchor.coerceIn(0, len), selection.active.coerceIn(0, len))

        // Like a PC: ←/→ without Shift on a selection collapses it to the matching edge.
        if (!select && !sel.isCollapsed && !byWord) {
            when (direction) {
                Direction.LEFT -> return TextSelection(sel.start, sel.start)
                Direction.RIGHT -> return TextSelection(sel.end, sel.end)
                else -> Unit
            }
        }

        val from = sel.active
        val target = when (direction) {
            Direction.LEFT -> from - if (byWord) {
                TextBoundaries.previousWordLength(text.subSequence(0, from))
            } else {
                TextBoundaries.previousGraphemeLength(text.subSequence(0, from))
            }
            Direction.RIGHT -> from + if (byWord) {
                TextBoundaries.nextWordLength(text.subSequence(from, len))
            } else {
                TextBoundaries.nextGraphemeLength(text.subSequence(from, len))
            }
            Direction.HOME -> if (toDocumentEdge) 0 else lineStart(text, from)
            Direction.END -> if (toDocumentEdge) len else lineEnd(text, from)
            else -> from
        }.coerceIn(0, len)

        return if (select) TextSelection(sel.anchor, target) else TextSelection(target, target)
    }

    private fun lineStart(text: CharSequence, pos: Int): Int {
        var i = pos
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun lineEnd(text: CharSequence, pos: Int): Int {
        var i = pos
        while (i < text.length && text[i] != '\n') i++
        return i
    }
}
