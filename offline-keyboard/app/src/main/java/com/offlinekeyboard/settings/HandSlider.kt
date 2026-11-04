package com.offlinekeyboard.settings

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.offlinekeyboard.keyboard.Pencil
import kotlin.math.roundToInt

/**
 * The slider of the app family, as in QRAFT (and the launcher's playback bar):
 *  - sheet: the whole scale a thin, faint, slightly wavy pencil line, the chosen part a thicker stroke along
 *    it, the knob a little circle drawn round with a pencil on black;
 *  - glass: a straight thin light line, the chosen part brighter, the knob a glass button with a light rim.
 * A touch jumps there, dragging moves smoothly; with [steps] the value snaps and every step ticks.
 */
@Composable
internal fun HandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val change by rememberUpdatedState(onValueChange)
    val range by rememberUpdatedState(valueRange)
    val view = LocalView.current
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val ink = Tone.TextPrimary
    val glass = LocalLook.current == Look.GLASS
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(steps) {
                val pad = 12.dp.toPx()
                fun valueAt(x: Float): Float {
                    var f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    if (steps > 0) f = (f * (steps + 1)).roundToInt() / (steps + 1f)
                    return range.start + f * (range.endInclusive - range.start)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    var last = valueAt(down.position.x)
                    change(last)
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        c.consume()
                        val v = valueAt(c.position.x)
                        if (v != last) {
                            if (steps > 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            last = v
                            change(v)
                        }
                    }
                }
            },
    ) {
        val pad = 12.dp.toPx()
        val y = size.height / 2
        val start = pad
        val end = size.width - pad
        val x = start + (end - start) * fraction
        if (glass) {
            drawLine(ink.copy(alpha = 0.22f), Offset(start, y), Offset(end, y), 2.dp.toPx(), cap = StrokeCap.Round)
            if (x > start) drawLine(ink.copy(alpha = 0.9f), Offset(start, y), Offset(x, y), 3.dp.toPx(), cap = StrokeCap.Round)
            val r = 9.dp.toPx()
            // The knob: a light rim under a glass ball lit from above (two-stop gradients only, see glassFrame).
            drawCircle(Color(0xFF9A9A9A), r, Offset(x, y))
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101010)), startY = y - r, endY = y + r), r - 1.2.dp.toPx(), Offset(x, y))
        } else {
            // The whole scale: a light, barely wavy line (a quadratic curve), drawn as a tapered pencil stroke.
            val steps40 = 40
            val ax = start; val ay = y + 1.dp.toPx()
            val cx = (start + end) / 2; val cy = y - 1.5.dp.toPx()
            val bx = end; val by = y + 0.5.dp.toPx()
            val track = FloatArray((steps40 + 1) * 2)
            for (i in 0..steps40) {
                val t = i / steps40.toFloat()
                val u = 1 - t
                track[i * 2] = u * u * ax + 2 * u * t * cx + t * t * bx
                track[i * 2 + 1] = u * u * ay + 2 * u * t * cy + t * t * by
            }
            drawPath(Pencil.tapered(track, 2.dp.toPx()).asComposePath(), ink.copy(alpha = 0.3f))
            // The chosen part: a denser stroke along the same line, up to the knob.
            val count = (0..steps40).count { track[it * 2] <= x }
            var thumbY = y
            if (count >= 2) {
                thumbY = track[(count - 1) * 2 + 1]
                val played = FloatArray((count + 1) * 2)
                System.arraycopy(track, 0, played, 0, count * 2)
                played[count * 2] = x
                played[count * 2 + 1] = thumbY
                drawPath(Pencil.tapered(played, 4.dp.toPx()).asComposePath(), ink)
            }
            // The knob: a little circle drawn round with a pencil, on black.
            val r = 8.dp.toPx()
            drawCircle(Color.Black, r, Offset(x, thumbY))
            translate(x - r, thumbY - r) {
                drawPath(Pencil.tapered(Pencil.loop(2 * r, 2 * r, r, 37, 0.6.dp.toPx()), 2.dp.toPx()).asComposePath(), ink)
            }
        }
    }
}
