package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilTick
import com.blacklaunch.diary.ui.taperedPath
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Простір над і під сторінкою — там висить кінчик стрічки. */
val RibbonTop = 34.dp
val RibbonBottom = 24.dp

/** Де на сторінці висить стрічка (частка ширини). */
private const val RIBBON_X = 0.72f
private val RibbonWidth = 15.dp

/**
 * Закладка — тканинна стрічка з корінця, як у справжньому щоденнику.
 *  - Висить коротким кінчиком над сторінкою. Смикнув униз і відпустив — лягла на цю сторінку.
 *  - Лежить на сторінці (видно її на всю висоту, кінець із вирізом стирчить знизу). Смикнув так само — прибрав.
 *  - Лежить на іншій сторінці — знизу видно її кінчик; дотик — перегорнути до неї.
 * Гойдається, коли її чіпаєш. [pageW]/[pageH] — розмір сторінки; стрічка малюється в просторі над сторінкою.
 */
@Composable
fun BookmarkRibbon(
    pageW: Dp,
    pageH: Dp,
    here: Boolean,
    elsewhere: Boolean,
    enabled: Boolean,
    onPlace: () -> Unit,
    onRemove: () -> Unit,
    onJump: () -> Unit,
    description: String,
) {
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val topPx = with(density) { RibbonTop.toPx() }
    val hPx = with(density) { pageH.toPx() }
    val tail = with(density) { 30.dp.toPx() }
    val full = topPx + hPx + with(density) { 18.dp.toPx() }
    // Довжина стрічки від верхнього краю простору над сторінкою.
    val length = remember { Animatable(if (here) full else topPx + tail * 0.3f) }
    val sway = remember { Animatable(0f) }
    LaunchedEffect(here, hPx) {
        length.animateTo(if (here) full else topPx + tail * 0.3f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(here) {
        sway.snapTo(if (here) 3.5f else -3f)
        sway.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 60f))
    }
    val xDp = pageW * RIBBON_X
    val glass = AppStyle.glass
    Box(Modifier.size(pageW, pageH + RibbonTop + RibbonBottom)) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * RIBBON_X
            val w = RibbonWidth.toPx()
            val len = length.value
            rotate(sway.value, pivot = Offset(x, 0f)) {
                val notch = w * 0.55f
                val p = Path().apply {
                    moveTo(x - w / 2, 0f)
                    lineTo(x + w / 2, 0f)
                    lineTo(x + w / 2, len)
                    lineTo(x, len - notch)
                    lineTo(x - w / 2, len)
                    close()
                }
                if (glass) {
                    // Скляна стрічка: напівпрозора (текст під нею видно), з гранями й відблиском.
                    drawPath(p, Brush.horizontalGradient(0f to Color(0xCC2C2C2C), 0.5f to Color(0xB3121212), 1f to Color(0xCC262626), startX = x - w / 2, endX = x + w / 2))
                    drawPath(p, Color(0x80FFFFFF), style = Stroke(1.dp.toPx()))
                    drawLine(Color(0x33FFFFFF), Offset(x - w * 0.18f, 2f), Offset(x - w * 0.18f, len - notch - 2f), 1.dp.toPx())
                } else {
                    drawPath(p, Color(0xB0000000))
                    drawPath(taperedPath(listOf(Offset(x - w / 2, 0f), Offset(x - w / 2 + 0.6f, len * 0.5f), Offset(x - w / 2, len)), 1.8.dp.toPx()), SheetInk.copy(alpha = 0.85f))
                    drawPath(taperedPath(listOf(Offset(x + w / 2, 0f), Offset(x + w / 2 - 0.6f, len * 0.5f), Offset(x + w / 2, len)), 1.8.dp.toPx()), SheetInk.copy(alpha = 0.85f))
                    drawPath(taperedPath(listOf(Offset(x - w / 2, len), Offset(x, len - notch), Offset(x + w / 2, len)), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.85f))
                    // Тканина: рідке косе штрихування.
                    var y = 6.dp.toPx()
                    var i = 0
                    while (y < len - notch - 4.dp.toPx()) {
                        pencilTick(Offset(x - w * 0.35f, y + w * 0.3f), Offset(x + w * 0.35f, y - w * 0.3f), 0.9.dp.toPx(), SheetInk.copy(alpha = 0.28f), i++)
                        y += 9.dp.toPx() + sin(i * 1.7f) * 1.5f
                    }
                }
            }
            // Закладка на іншій сторінці: її кінчик стирчить знизу книжки.
            if (elsewhere && !here) {
                val top = topPx + hPx - 4.dp.toPx()
                val end = top + 22.dp.toPx()
                val notch = w * 0.5f
                val p = Path().apply {
                    moveTo(x - w / 2, top); lineTo(x + w / 2, top); lineTo(x + w / 2, end); lineTo(x, end - notch); lineTo(x - w / 2, end); close()
                }
                if (glass) {
                    drawPath(p, Color(0xCC1E1E1E))
                    drawPath(p, Color(0x80FFFFFF), style = Stroke(1.dp.toPx()))
                } else {
                    drawPath(p, Color.Black)
                    drawPath(taperedPath(listOf(Offset(x - w / 2, top), Offset(x - w / 2, end), Offset(x, end - notch), Offset(x + w / 2, end), Offset(x + w / 2, top)), 1.7.dp.toPx()), SheetInk.copy(alpha = 0.8f))
                }
            }
        }
        // Де можна взяти стрічку — лише біля неї (решта сторінки живе своїм життям).
        if (enabled) {
            Box(
                Modifier
                    .offset(xDp - 26.dp, 0.dp)
                    .size(52.dp, RibbonTop + 18.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(here) {
                        var pulled = 0f
                        var start = 0f
                        detectVerticalDragGestures(
                            onDragStart = { pulled = 0f; start = length.value; haptics.tick() },
                            onVerticalDrag = { c, dy ->
                                c.consume()
                                pulled += dy
                                scope.launch {
                                    if (here) length.snapTo((start + pulled * 0.25f).coerceAtLeast(start))
                                    else length.snapTo((start + pulled).coerceIn(start, full))
                                    sway.snapTo((pulled / 40f).coerceIn(-4f, 4f) * 0.3f)
                                }
                            },
                            onDragEnd = {
                                scope.launch { sway.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 80f)) }
                                if (here) {
                                    if (pulled > 36.dp.toPx()) { haptics.longPress(); onRemove() }
                                    else scope.launch { length.animateTo(full, tween(220)) }
                                } else {
                                    if (length.value > topPx + hPx * 0.35f) { haptics.longPress(); onPlace() }
                                    else scope.launch { length.animateTo(topPx + tail * 0.3f, spring(dampingRatio = 0.6f)) }
                                }
                            },
                        )
                    },
            )
        }
        if (elsewhere && !here) {
            Box(
                Modifier
                    .offset(xDp - 22.dp, RibbonTop + pageH - 6.dp)
                    .size(44.dp, RibbonBottom + 6.dp)
                    .pointerInput(Unit) { detectTapGestures { haptics.tap(); onJump() } },
            )
        }
    }
}
