package com.blacklaunch.qrcodes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blacklaunch.qrcodes.ui.theme.Oled

/**
 * Повзунок у стилі програми (як шкала програвання в лаунчері).
 *  - «Аркуш»: уся шкала — тонка тьмяна, ледь хвиляста лінія олівцем, вибране — товстіший штрих по ній, повзунок —
 *    кружечок, обведений олівцем.
 *  - «Скло»: рівна тонка світла лінія, вибране — яскравіше, повзунок — скляна кнопка зі світлим кантом.
 * Дотик — одразу туди, тягнути — плавно. [valueRange] — як у звичайного повзунка.
 */
@Composable
fun HandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val change by rememberUpdatedState(onValueChange)
    val range by rememberUpdatedState(valueRange)
    val haptics = LocalHaptics.current
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val ink = if (enabled) Oled.TextPrimary else Oled.TextSecondary
    val glass = AppStyle.glass
    Canvas(
        modifier
            .height(36.dp)
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        val pad = 12.dp.toPx()
                        fun valueAt(x: Float): Float {
                            val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                            return range.start + f * (range.endInclusive - range.start)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            haptics.tick()
                            change(valueAt(down.position.x))
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                change(valueAt(c.position.x))
                            }
                        }
                    }
                },
            ),
    ) {
        val pad = 12.dp.toPx()
        val y = size.height / 2
        val start = pad
        val end = size.width - pad
        val x = start + (end - start) * fraction
        if (glass) {
            drawLine(ink.copy(alpha = 0.22f), Offset(start, y), Offset(end, y), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (x > start) drawLine(ink.copy(alpha = 0.9f), Offset(start, y), Offset(x, y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val r = 9.dp.toPx()
            drawCircle(Oled.Knob, r, Offset(x, y))
            drawCircle(Color(0x99FFFFFF), r, Offset(x, y), style = Stroke(1.2.dp.toPx()))
        } else {
            // Уся шкала — легка, ледь хвиляста лінія.
            val track = quadPoints(Offset(start, y + 1.dp.toPx()), Offset((start + end) / 2, y - 1.5.dp.toPx()), Offset(end, y + 0.5.dp.toPx()), steps = 40)
            drawPath(taperedPath(track, 2.dp.toPx()), ink.copy(alpha = 0.3f))
            // Вибране — густіший штрих по тій самій лінії.
            val played = track.filter { it.x <= x }
            if (played.size >= 2) drawPath(taperedPath(played + Offset(x, played.last().y), 4.dp.toPx()), ink)
            // Повзунок — кружечок олівцем на чорному.
            val r = 8.dp.toPx()
            val thumbY = played.lastOrNull()?.y ?: y
            drawCircle(Color.Black, r, Offset(x, thumbY))
            translate(x - r, thumbY - r) {
                drawPath(taperedPath(pencilLoop(Size(2 * r, 2 * r), r, 37, 0.6.dp.toPx()), 2.dp.toPx()), ink)
            }
        }
    }
}
