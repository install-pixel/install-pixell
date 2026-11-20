package com.offlinekeyboard.keyboard

import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Text as struck by an old typewriter: every character sits a little above or below the line and is a
 * little lighter or darker, the same way for the same position, so the text does not shimmer as it grows.
 */
object TypewriterInk {

    /**
     * [fold] tells, for the character at an index, how much it sits on a crease of the paper (0 off any,
     * up to ±1 on one): such a letter bends with the fold (a little off its line and slanted) and its ink
     * takes less on the ridge.
     */
    fun apply(text: CharSequence, textSizePx: Float, fold: ((Int) -> Float)? = null): CharSequence {
        if (text.isEmpty()) return text
        val out = SpannableString(text)
        for (i in text.indices) {
            if (text[i].isWhitespace()) continue
            val h = (i * 2654435761L + text[i].code * 40503L).toInt()
            val bend = fold?.invoke(i) ?: 0f
            val shift = (((h and 0xFF) / 255f) - 0.5f) * textSizePx * 0.07f + bend * textSizePx * 0.09f
            val ink = (0.72f + ((h ushr 8) and 0xFF) / 255f * 0.28f) * (1f - 0.35f * kotlin.math.abs(bend))
            out.setSpan(Strike(shift.toInt(), ink, bend * 0.14f), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    private class Strike(private val shift: Int, private val ink: Float, private val skew: Float) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.baselineShift += shift
            tp.alpha = (tp.alpha * ink).toInt()
            tp.textSkewX += skew
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.baselineShift += shift
        }
    }
}
