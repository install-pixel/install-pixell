package com.offlinekeyboard.keyboard

import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Text as struck by an old typewriter: every character sits a little above or below the line and is a
 * little lighter or darker, the same way for the same position, so the text does not shimmer as it grows.
 */
object TypewriterInk {

    fun apply(text: CharSequence, textSizePx: Float): CharSequence {
        if (text.isEmpty()) return text
        val out = SpannableString(text)
        for (i in text.indices) {
            if (text[i].isWhitespace()) continue
            val h = (i * 2654435761L + text[i].code * 40503L).toInt()
            val shift = (((h and 0xFF) / 255f) - 0.5f) * textSizePx * 0.07f
            val ink = 0.72f + ((h ushr 8) and 0xFF) / 255f * 0.28f
            out.setSpan(Strike(shift.toInt(), ink), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    private class Strike(private val shift: Int, private val ink: Float) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.baselineShift += shift
            tp.alpha = (tp.alpha * ink).toInt()
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.baselineShift += shift
        }
    }
}
