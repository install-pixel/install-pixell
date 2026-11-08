package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.GlassFont
import com.blacklaunch.diary.ui.HandFont
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.quadPoints
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Кодовий замок, як на старому дипломаті: 4 барабани з цифрами 0–9. Барабан крутиться вертикальним свайпом (з
 * інерцією), стає точно на цифру, на кожній цифрі — легке «тік». [shake] > 0 — замок «смикається» (неправильний код).
 */
@Composable
fun CombinationLock(
    digits: List<Int>,
    onDigit: (wheel: Int, digit: Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shake: Int = 0,
    wheelWidth: Dp = 50.dp,
    wheelHeight: Dp = 118.dp,
    description: String = "",
) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) {
            for (k in listOf(1f, -1f, 0.7f, -0.7f, 0.35f, 0f)) offset.animateTo(k * 10f, spring(stiffness = Spring.StiffnessHigh))
        }
    }
    Row(
        modifier.graphicsLayer { translationX = offset.value * density }.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        digits.forEachIndexed { i, d ->
            Wheel(d, { onDigit(i, it) }, enabled, wheelWidth, wheelHeight, seed = i * 13 + 5)
        }
    }
}

@Composable
private fun Wheel(digit: Int, onDigit: (Int) -> Unit, enabled: Boolean, w: Dp, h: Dp, seed: Int) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    // Положення барабана в «цифрах» (неперервне): ціла частина — цифра навпроти віконця.
    val pos = remember { Animatable(digit.toFloat()) }
    val change by rememberUpdatedState(onDigit)
    LaunchedEffect(digit) {
        if (!pos.isRunning && ((pos.value.roundToInt() % 10) + 10) % 10 != digit) pos.snapTo(digit.toFloat())
    }
    val measurer = rememberTextMeasurer()
    val glass = AppStyle.glass
    Box(
        Modifier
            .size(w, h)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val step = h.toPx() / 3.2f
                val tracker = VelocityTracker()
                var lastTick = pos.value.roundToInt()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking(); scope.launch { pos.stop() } },
                    onVerticalDrag = { c, dy ->
                        c.consume()
                        tracker.addPosition(c.uptimeMillis, c.position)
                        scope.launch {
                            pos.snapTo(pos.value - dy / step)
                            val now = pos.value.roundToInt()
                            if (now != lastTick) { lastTick = now; haptics.tick() }
                        }
                    },
                    onDragEnd = {
                        val v = -tracker.calculateVelocity().y / step
                        // Інерція: прокрутиться ще трохи і стане на найближчу цифру.
                        val target = (pos.value + (v * 0.18f).coerceIn(-6f, 6f)).roundToInt().toFloat()
                        scope.launch {
                            var last = pos.value.roundToInt()
                            pos.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 220f)) {
                                val now = value.roundToInt()
                                if (now != last) { last = now; haptics.tick() }
                            }
                            change(((target.roundToInt() % 10) + 10) % 10)
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.size(w, h)) {
            val r = 10.dp.toPx()
            val cr = CornerRadius(r)
            // Корпус барабана.
            if (glass) {
                drawRoundRect(Brush.verticalGradient(0f to Color(0xFF050505), 0.5f to Color(0xFF1C1C1C), 1f to Color(0xFF050505)), cornerRadius = cr)
            } else {
                drawRoundRect(Color.Black, cornerRadius = cr)
            }
            val step = size.height / 3.2f
            val p = pos.value
            val base = floor(p).toInt()
            clipRect {
                for (k in -2..3) {
                    val n = base + k
                    val d = ((n % 10) + 10) % 10
                    // Кут на циліндрі: посередині — пряма цифра, вище й нижче — стиснута й тьмяна.
                    val rel = (n - p)
                    val angle = rel * 0.62f
                    if (abs(angle) > 1.5f) continue
                    val y = size.height / 2 + sin(angle) * size.height * 0.48f
                    val squash = cos(angle).coerceAtLeast(0.05f)
                    val alpha = (squash * squash).coerceIn(0f, 1f) * if (enabled) 1f else 0.5f
                    val style = TextStyle(
                        fontFamily = if (glass) GlassFont else HandFont,
                        fontWeight = if (glass) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = (if (glass) 30 else 38).sp,
                        color = Oled.TextPrimary.copy(alpha = alpha),
                        shadow = if (glass) androidx.compose.ui.graphics.Shadow(Color(0x59FFFFFF), blurRadius = 18f) else null,
                    )
                    val layout = measurer.measure(d.toString(), style)
                    translate(size.width / 2 - layout.size.width / 2f, y - layout.size.height / 2f * squash) {
                        scale(1f, squash, pivot = Offset(layout.size.width / 2f, 0f)) {
                            drawText(layout)
                        }
                    }
                }
            }
            if (glass) {
                // Циліндр: темніє до верху й низу, по центру — світла смуга віконця, кант.
                drawRoundRect(Brush.verticalGradient(0f to Color(0xEE000000), 0.32f to Color.Transparent, 0.68f to Color.Transparent, 1f to Color(0xEE000000)), cornerRadius = cr)
                drawRect(Color(0x10FFFFFF), topLeft = Offset(0f, size.height / 2 - step * 0.45f), size = Size(size.width, step * 0.9f))
                drawRoundRect(com.blacklaunch.diary.ui.theme.Oled.Glint, cornerRadius = cr, style = Stroke(1.dp.toPx()))
                drawLine(Color(0x33FFFFFF), Offset(4.dp.toPx(), size.height / 2 - step * 0.45f), Offset(size.width - 4.dp.toPx(), size.height / 2 - step * 0.45f), 1.dp.toPx())
                drawLine(Color(0x22FFFFFF), Offset(4.dp.toPx(), size.height / 2 + step * 0.45f), Offset(size.width - 4.dp.toPx(), size.height / 2 + step * 0.45f), 1.dp.toPx())
            } else {
                drawRoundRect(Brush.verticalGradient(0f to Color(0xF0000000), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0xF0000000)), cornerRadius = cr)
                drawPath(taperedPath(pencilLoop(size, r, seed, 1.2.dp.toPx()), 2.dp.toPx()), SheetInk.copy(alpha = 0.8f))
                // Віконце — дві лінії олівцем; і дуги, що показують, що це циліндр.
                val yt = size.height / 2 - step * 0.45f
                val yb = size.height / 2 + step * 0.45f
                drawPath(taperedPath(quadPoints(Offset(3.dp.toPx(), yt), Offset(size.width / 2, yt - 2.dp.toPx()), Offset(size.width - 3.dp.toPx(), yt), 16), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.6f))
                drawPath(taperedPath(quadPoints(Offset(3.dp.toPx(), yb), Offset(size.width / 2, yb + 2.dp.toPx()), Offset(size.width - 3.dp.toPx(), yb), 16), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.6f))
            }
        }
    }
}
