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
import com.blacklaunch.diary.ui.theme.Oled
import kotlin.math.sin

/** Одна форма для всієї книжки — обкладинки, сторінок, гортання: кути однакові, нічому виступати. */
val BookCorner = 12.dp

/** Одна рамка для всієї книжки: тонка, стала (не підсвічується й не змінюється), лежить ВСЕРЕДИНІ форми. */
private val FrameWidth = 1.dp
private val SheetFrame = SheetInk.copy(alpha = 0.42f)

/**
 * Рамка книжки — однакова в обкладинки й сторінок. Лінія лежить усередині форми (а не по центру краю), тож обрізання
 * по краю не робить її тоншою, а за край вона не виходить.
 */
fun DrawScope.drawBookFrame(glass: Boolean) {
    val w = FrameWidth.toPx()
    val topLeft = Offset(w / 2, w / 2)
    val frame = Size(size.width - w, size.height - w)
    val corner = CornerRadius(BookCorner.toPx() - w / 2)
    // «Скло» — скляна грань (як у правилах тем: світліша вгорі), «Аркуш» — рівна лінія олівця. Обидві сталі.
    if (glass) drawRoundRect(Oled.Glint, topLeft = topLeft, size = frame, cornerRadius = corner, style = Stroke(w))
    else drawRoundRect(SheetFrame, topLeft = topLeft, size = frame, cornerRadius = corner, style = Stroke(w))
}

/**
 * Папір сторінки.
 *  - «Скло»: чорне скло за правилами тем, із ледь помітною товщею біля краю (товста темна плівка).
 *  - «Аркуш»: старий чорний папір ([PaperTexture]) — фактура, заломи, витерті краї; лінії — штрихи олівця.
 * Рамка — одна на всю книжку ([drawBookFrame]). [unit] — px в одній одиниці сторінки ([PageMetrics]).
 */
fun DrawScope.drawPaper(unit: Float, seed: Int, lines: List<Float>) {
    val r = BookCorner.toPx()
    val shape = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    if (AppStyle.glass) {
        // Чорне скло за спільними правилами (THEMES.md): темне, ледь світліше вгорі, відблиск угорі. Додано лише товщу —
        // ледь помітний широкий кант біля краю, як у товстої темної плівки.
        // Скляний аркуш ледь прозорий: крізь нього слабко видно наступну сторінку (див. [FlipBook]).
        drawRoundRect(Oled.Surface, cornerRadius = CornerRadius(r), alpha = GLASS_PAGE_ALPHA)
        clipPath(shape) {
            drawRect(Brush.verticalGradient(0f to Color(0x12FFFFFF), 1f to Color.Transparent, startY = 0f, endY = size.height * 0.35f))
            val band = 5.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(0f to Color(0x0FFFFFFF), 1f to Color(0x04FFFFFF)),
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
        val creases = PaperTexture.creases
        lines.forEachIndexed { i, y ->
            val a = PageMetrics.MARGIN_LEFT * unit * 0.7f
            val b = size.width - PageMetrics.MARGIN_RIGHT * unit * 0.7f
            val wob = 0.5f * density
            val pts = quadPoints(
                Offset(a, y + sin(seed + i * 1.7f) * wob),
                Offset((a + b) / 2, y + sin(seed * 0.3f + i * 2.3f) * wob * 2f),
                Offset(b, y + sin(seed + i * 0.9f) * wob),
                64,
            ).map { p -> p + creaseShift(p, creases) }
            drawPath(taperedPath(pts, 1.1f * density), SheetInk.copy(alpha = 0.14f))
        }
    }
    drawBookFrame(AppStyle.glass)
}

/** Лінія, що переходить через залом, ледь «ламається» разом із папером (як і світло на ньому — [PaperTexture.shade]). */
private fun DrawScope.creaseShift(p: Offset, creases: List<PaperTexture.Crease>): Offset {
    var shift = 0f
    for (c in creases) {
        val px = p.x - c.x * size.width
        val py = p.y - c.y * size.height
        val t = px * c.dx + py * c.dy
        val half = c.len * size.width / 2f
        if (kotlin.math.abs(t) > half) continue
        val width = c.width * size.width
        val s = (px * -c.dy + py * c.dx) / width
        if (kotlin.math.abs(s) > 3f) continue
        val along = 1f - (t / half).let { it * it }
        shift += c.amp / 7f * along * s * kotlin.math.exp(-s * s) * 2.3f
    }
    return Offset(0f, shift * 1.2f * density)
}

/** Розмір сторінки, що вміщається в [avail] зі сталими пропорціями. */
fun pageSize(avail: Size): Size {
    val w = minOf(avail.width, avail.height / PageMetrics.ASPECT)
    return Size(w, w * PageMetrics.ASPECT)
}

/** Наскільки непрозорий скляний аркуш: крізь решту видно силует наступної сторінки. */
const val GLASS_PAGE_ALPHA = 0.86f
