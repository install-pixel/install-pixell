package com.blacklaunch.diary.book

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.quadPoints
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlin.math.sin

/**
 * Папір сторінки.
 *  - «Скло»: пластина чорного димчастого скла — градієнт, відблиск угорі, яскравий кант, лінії ніби вигравірувані.
 *  - «Аркуш»: чорний папір, край обведено олівцем, лінії — ледь помітні штрихи олівця.
 * [unit] — px в одній одиниці сторінки ([PageMetrics]).
 */
fun DrawScope.drawPaper(unit: Float, seed: Int, lines: List<Float>, edgeBefore: Int, edgeAfter: Int) {
    val r = 2.2f * unit
    drawPageEdges(unit, edgeBefore, edgeAfter)
    if (AppStyle.glass) {
        drawRoundRect(Oled.Surface, cornerRadius = CornerRadius(r))
        val sheenH = (size.height * 0.35f)
        drawRoundRect(
            Brush.verticalGradient(0f to Color(0x12FFFFFF), 1f to Color.Transparent, startY = 0f, endY = sheenH),
            cornerRadius = CornerRadius(r),
        )
        drawRoundRect(Oled.Glint, cornerRadius = CornerRadius(r), style = Stroke(1f * density))
        // Корінець: м'яка тінь зліва, ніби аркуш виходить із палітурки.
        drawRect(Brush.horizontalGradient(0f to Color(0x55000000), 1f to Color.Transparent, startX = 0f, endX = 5f * unit))
        lines.forEach { y ->
            drawLine(Color(0x1CFFFFFF), Offset(PageMetrics.MARGIN_LEFT * unit * 0.7f, y), Offset(size.width - PageMetrics.MARGIN_RIGHT * unit * 0.7f, y), 0.8f * density)
            drawLine(Color(0x0AFFFFFF), Offset(PageMetrics.MARGIN_LEFT * unit * 0.7f, y + 1.2f * density), Offset(size.width - PageMetrics.MARGIN_RIGHT * unit * 0.7f, y + 1.2f * density), 0.8f * density)
        }
    } else {
        drawRoundRect(Color(0xFF030303), cornerRadius = CornerRadius(r))
        drawPath(taperedPath(pencilLoop(size, r, seed, 1.4f * density), 2f * density), SheetInk.copy(alpha = 0.7f))
        drawPath(taperedPath(pencilLoop(size, r, seed + 77, 1.8f * density), 1.1f * density), SheetInk.copy(alpha = 0.22f))
        // Корінець — подвійна лінія олівцем.
        drawPath(taperedPath(quadPoints(Offset(3.2f * unit, 2f * unit), Offset(3.6f * unit, size.height / 2), Offset(3.1f * unit, size.height - 2f * unit), 40), 1.2f * density), SheetInk.copy(alpha = 0.25f))
        lines.forEachIndexed { i, y ->
            val a = PageMetrics.MARGIN_LEFT * unit * 0.7f
            val b = size.width - PageMetrics.MARGIN_RIGHT * unit * 0.7f
            val wob = 0.5f * density
            val pts = quadPoints(
                Offset(a, y + sin(seed + i * 1.7f) * wob),
                Offset((a + b) / 2, y + sin(seed * 0.3f + i * 2.3f) * wob * 2f),
                Offset(b, y + sin(seed + i * 0.9f) * wob),
                32,
            )
            drawPath(taperedPath(pts, 1.1f * density), SheetInk.copy(alpha = 0.13f))
        }
    }
}

/** Відстань між рисками торців (в одиницях сторінки). */
const val PAGE_EDGE_STEP = 0.55f

/**
 * Товщина книжки: збоку видно стос сторінок — перегорнутих (зліва) і тих, що попереду (справа). Що більше записів,
 * то товща книжка.
 */
fun DrawScope.drawPageEdges(unit: Float, before: Int, after: Int) {
    val glass = AppStyle.glass
    val step = PAGE_EDGE_STEP * unit
    val color = if (glass) Color(0x33FFFFFF) else SheetInk.copy(alpha = 0.25f)
    for (i in 1..after.coerceAtMost(6)) {
        val x = size.width + i * step
        drawLine(color.copy(alpha = color.alpha * (1f - i / 8f)), Offset(x, 1.5f * unit + i * step * 0.5f), Offset(x, size.height - 1.5f * unit - i * step * 0.3f), 0.8f * density)
    }
    for (i in 1..before.coerceAtMost(6)) {
        val x = -i * step
        drawLine(color.copy(alpha = color.alpha * (1f - i / 8f)), Offset(x, 1.5f * unit + i * step * 0.5f), Offset(x, size.height - 1.5f * unit - i * step * 0.3f), 0.8f * density)
    }
}

/** Скільки «рисок» товщини показати для N сторінок. */
fun edgeCount(pages: Int): Int = when {
    pages <= 0 -> 0
    pages < 4 -> 1
    pages < 12 -> 2
    pages < 30 -> 3
    pages < 80 -> 4
    pages < 200 -> 5
    else -> 6
}

/** Розмір сторінки, що вміщається в [avail] зі сталими пропорціями. */
fun pageSize(avail: Size): Size {
    val w = minOf(avail.width, avail.height / PageMetrics.ASPECT)
    return Size(w, w * PageMetrics.ASPECT)
}
