package com.blacklaunch.diary.book

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.quadPoints
import com.blacklaunch.diary.ui.taperedPath
import kotlin.math.sin

/** Одна форма для всієї книжки — обкладинки, сторінок, гортання: кути однакові, нічому виступати. */
val BookCorner = 12.dp

/** Одна рамка для всієї книжки: тонка, стала (не підсвічується й не змінюється), лежить ВСЕРЕДИНІ форми. */
private val FrameWidth = 1.dp
private val GlassFrame = Color(0x3DFFFFFF)
private val SheetFrame = SheetInk.copy(alpha = 0.42f)

/**
 * Рамка книжки — однакова в обкладинки й сторінок. Лінія лежить усередині форми (а не по центру краю), тож обрізання
 * по краю не робить її тоншою, а за край вона не виходить.
 */
fun DrawScope.drawBookFrame(glass: Boolean) {
    val w = FrameWidth.toPx()
    drawRoundRect(
        if (glass) GlassFrame else SheetFrame,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(BookCorner.toPx() - w / 2),
        style = Stroke(w),
    )
}

/**
 * Папір сторінки.
 *  - «Скло»: товста темна плівка — ледь світліша за тло, з великими м'якими переливами й товщею біля краю.
 *  - «Аркуш»: старий чорний папір ([PaperTexture]) — фактура, заломи, витерті краї; лінії — штрихи олівця.
 * Рамка — одна на всю книжку ([drawBookFrame]). [unit] — px в одній одиниці сторінки ([PageMetrics]).
 */
fun DrawScope.drawPaper(unit: Float, seed: Int, lines: List<Float>) {
    val r = BookCorner.toPx()
    val shape = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    if (AppStyle.glass) {
        drawRoundRect(Color(0xFF111215), cornerRadius = CornerRadius(r))
        clipPath(shape) {
            // Переливи в товщі плівки — великі, ледь помітні.
            listOf(Triple(0.25f, 0.2f, 0.8f), Triple(0.8f, 0.55f, 0.7f), Triple(0.35f, 0.85f, 0.6f)).forEach { (cx, cy, rr) ->
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x0BFFFFFF), Color.Transparent), center = Offset(size.width * cx, size.height * cy), radius = size.width * rr),
                    radius = size.width * rr,
                    center = Offset(size.width * cx, size.height * cy),
                )
            }
            // Відблиск угорі.
            drawRect(Brush.verticalGradient(0f to Color(0x10FFFFFF), 1f to Color.Transparent, startY = 0f, endY = size.height * 0.35f))
            // Товща біля краю: широкий м'який кант (сталий — не підсвічується).
            val band = 6.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(0f to Color(0x14FFFFFF), 1f to Color(0x06FFFFFF)),
                topLeft = Offset(band / 2, band / 2),
                size = Size(size.width - band, size.height - band),
                cornerRadius = CornerRadius(r - band / 2),
                style = Stroke(band),
            )
            // Корінець: м'яка тінь зліва, ніби аркуш виходить із палітурки.
            drawRect(Brush.horizontalGradient(0f to Color(0x55000000), 1f to Color.Transparent, startX = 0f, endX = 5f * unit))
        }
        lines.forEach { y ->
            drawLine(Color(0x1CFFFFFF), Offset(PageMetrics.MARGIN_LEFT * unit * 0.7f, y), Offset(size.width - PageMetrics.MARGIN_RIGHT * unit * 0.7f, y), 0.8f * density)
            drawLine(Color(0x0AFFFFFF), Offset(PageMetrics.MARGIN_LEFT * unit * 0.7f, y + 1.2f * density), Offset(size.width - PageMetrics.MARGIN_RIGHT * unit * 0.7f, y + 1.2f * density), 0.8f * density)
        }
    } else {
        PaperTexture.ensure()
        clipPath(shape) {
            val tex = PaperTexture.page
            if (tex != null) {
                drawImage(tex, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt() + 1, size.height.toInt() + 1))
            } else {
                drawRect(PaperTexture.PAGE_BASE)
            }
            // Корінець — тінь палітурки й подвійна лінія олівцем.
            drawRect(Brush.horizontalGradient(0f to Color(0x66000000), 1f to Color.Transparent, startX = 0f, endX = 5f * unit))
        }
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
            drawPath(taperedPath(pts, 1.1f * density), SheetInk.copy(alpha = 0.16f))
        }
    }
    drawBookFrame(AppStyle.glass)
}

/** Розмір сторінки, що вміщається в [avail] зі сталими пропорціями. */
fun pageSize(avail: Size): Size {
    val w = minOf(avail.width, avail.height / PageMetrics.ASPECT)
    return Size(w, w * PageMetrics.ASPECT)
}
