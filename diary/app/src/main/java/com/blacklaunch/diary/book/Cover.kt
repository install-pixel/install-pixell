package com.blacklaunch.diary.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.GlassFont
import com.blacklaunch.diary.ui.HandFont
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilFrame
import com.blacklaunch.diary.ui.pencilLoop
import com.blacklaunch.diary.ui.pencilTick
import com.blacklaunch.diary.ui.quadPoints
import com.blacklaunch.diary.ui.taperedPath
import com.blacklaunch.diary.ui.theme.Oled
import kotlin.math.cos
import kotlin.math.sin

/**
 * Обкладинка щоденника.
 *  - «Скло»: товста пластина чорного димчастого скла — подвійний кант (товщина), відблиск угорі й косий відсвіт;
 *    своє фото (чорно-біле) лежить під склом.
 *  - «Аркуш»: обкладинка, обведена олівцем, з орнаментом у кутах і резинкою-застібкою; своє фото — олівцевим ескізом.
 * Назва — на табличці (скляній чи намальованій олівцем) з темним тлом: вона читається на будь-якому фото.
 * [lock] — замок (барабани) на застібці, якщо щоденник закритий на код.
 */
@Composable
fun CoverFace(
    name: String,
    photo: ImageBitmap?,
    pattern: Int,
    locked: Boolean,
    modifier: Modifier = Modifier,
    lock: (@Composable () -> Unit)? = null,
) {
    val glass = AppStyle.glass
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawCover(photo, pattern, glass, locked) }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(0.26f))
            // Табличка з назвою.
            Box(
                Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    // «Аркуш» — непрозора табличка (як наклеєна на обкладинку); «Скло» — темне скло, крізь яке ледь видно фото.
                    .background(if (glass) Color(0xD9000000) else Color.Black)
                    .pencilFrame(16.dp, seed = 401)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    style = TextStyle(
                        fontFamily = if (glass) GlassFont else HandFont,
                        fontWeight = if (glass) FontWeight.Light else FontWeight.Medium,
                        fontSize = if (glass) 30.sp else 36.sp,
                        letterSpacing = if (glass) 2.sp else 0.sp,
                        color = Oled.TextPrimary,
                        shadow = if (glass) Shadow(Color(0x59FFFFFF), blurRadius = 18f) else null,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.weight(0.34f))
            if (lock != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xE6000000))
                        .pencilFrame(22.dp, seed = 409)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) { lock() }
            }
            Box(Modifier.weight(0.4f))
        }
        // Резинка — поверх усього (і таблички з назвою), як справжня резинка поверх обкладинки.
        if (!glass) Canvas(Modifier.fillMaxSize()) { drawBand(locked) }
    }
}

private fun DrawScope.drawCover(photo: ImageBitmap?, pattern: Int, glass: Boolean, locked: Boolean) {
    val r = 14.dp.toPx()
    val cr = CornerRadius(r)
    val inset = if (glass) 10.dp.toPx() else 14.dp.toPx()
    val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
    val innerPath = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r * 0.7f)))
    }
    // Тло обкладинки.
    if (glass) {
        drawRoundRect(Brush.verticalGradient(0f to Color(0xFF1A1A1A), 0.5f to Color(0xFF0B0B0B), 1f to Color(0xFF060606)), cornerRadius = cr)
    } else {
        drawRoundRect(Color(0xFF040404), cornerRadius = cr)
    }
    // Фото під склом / у рамці олівцем.
    if (photo != null) {
        clipPath(innerPath) {
            val k = maxOf(inner.width / photo.width, inner.height / photo.height)
            val w = photo.width * k
            val h = photo.height * k
            drawImage(
                photo,
                dstOffset = IntOffset((inset + (inner.width - w) / 2).toInt(), (inset + (inner.height - h) / 2).toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
            )
        }
    } else {
        clipPath(innerPath) { drawPattern(pattern, glass, inset, inner) }
    }
    if (glass) {
        // Товщина скла: два канти — зовнішній яскравий, внутрішній тьмяний; відблиск і косий відсвіт.
        drawRoundRect(Brush.verticalGradient(0f to Color(0x1AFFFFFF), 1f to Color.Transparent, startY = 0f, endY = size.height * 0.4f), cornerRadius = cr)
        clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, cr)) }) {
            val a = Offset(size.width * 0.15f, 0f)
            drawPath(
                Path().apply {
                    moveTo(a.x, 0f); lineTo(a.x + size.width * 0.22f, 0f)
                    lineTo(a.x - size.width * 0.18f, size.height); lineTo(a.x - size.width * 0.32f, size.height); close()
                },
                Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color(0x0FFFFFFF), 1f to Color.Transparent),
            )
        }
        drawRoundRect(Oled.Glint, cornerRadius = cr, style = Stroke(1.2.dp.toPx()))
        drawPath(innerPath, Brush.verticalGradient(0f to Color(0x40FFFFFF), 0.3f to Color(0x10FFFFFF), 1f to Color(0x20FFFFFF)), style = Stroke(1.dp.toPx()))
        // Корінець зліва — темна смуга з гранню.
        drawRect(Brush.horizontalGradient(0f to Color(0x99000000), 1f to Color.Transparent, startX = 0f, endX = 18.dp.toPx()))
    } else {
        drawPath(taperedPath(pencilLoop(size, r, 311, 1.6.dp.toPx()), 2.6.dp.toPx()), SheetInk.copy(alpha = 0.85f))
        drawPath(taperedPath(pencilLoop(size, r, 412, 2.dp.toPx()), 1.2.dp.toPx()), SheetInk.copy(alpha = 0.3f))
        drawPath(innerPath, SheetInk.copy(alpha = 0f))
        val loop = pencilLoop(inner, r * 0.7f, 313, 1.4.dp.toPx()).map { it + Offset(inset, inset) }
        drawPath(taperedPath(loop, 1.6.dp.toPx()), SheetInk.copy(alpha = 0.55f))
        drawCorners(inset)
    }
}

/** «Аркуш»: резинка-застібка праворуч — суцільна смуга (під нею нічого не просвічує), обведена двома лініями. */
private fun DrawScope.drawBand(locked: Boolean) {
    val x = size.width * 0.86f
    drawPath(
        Path().apply {
            moveTo(x, -2f); quadraticTo(x + 2.dp.toPx(), size.height / 2, x - 1.dp.toPx(), size.height + 2f)
            lineTo(x + 8.dp.toPx(), size.height + 2f); quadraticTo(x + 11.dp.toPx(), size.height / 2, x + 9.dp.toPx(), -2f); close()
        },
        Color(0xFF080808),
    )
    for (dx in listOf(0f, 9.dp.toPx())) {
        drawPath(taperedPath(quadPoints(Offset(x + dx, -2f), Offset(x + dx + 2.dp.toPx(), size.height / 2), Offset(x + dx - 1.dp.toPx(), size.height + 2f), 40), 2.dp.toPx()), SheetInk.copy(alpha = if (locked) 0.8f else 0.5f))
    }
}

/** Візерунок обкладинки без фото — щоб щоденники на полиці розрізнялись (сірим, без кольору). */
private fun DrawScope.drawPattern(pattern: Int, glass: Boolean, inset: Float, inner: Size) {
    val ink = if (glass) Color(0x12FFFFFF) else SheetInk.copy(alpha = 0.12f)
    val w = 1.dp.toPx()
    val left = inset
    val top = inset
    when (pattern % 6) {
        0 -> Unit // чиста обкладинка
        1 -> { // тонкі горизонтальні лінії
            var y = top + 20.dp.toPx()
            var i = 0
            while (y < top + inner.height) {
                if (glass) drawLine(ink, Offset(left, y), Offset(left + inner.width, y), w)
                else drawPath(taperedPath(quadPoints(Offset(left + 6, y), Offset(left + inner.width / 2, y + sin(i * 1.3f) * 2), Offset(left + inner.width - 6, y), 24), 1.2.dp.toPx()), ink)
                y += 18.dp.toPx(); i++
            }
        }
        2 -> { // косі лінії
            var x = left - inner.height
            var i = 0
            while (x < left + inner.width) {
                val a = Offset(x, top + inner.height)
                val b = Offset(x + inner.height, top)
                if (glass) drawLine(ink, a, b, w) else pencilTick(a, b, 1.2.dp.toPx(), ink, i)
                x += 22.dp.toPx(); i++
            }
        }
        3 -> { // кола, що розходяться від центру
            val c = Offset(left + inner.width / 2, top + inner.height * 0.62f)
            for (k in 1..9) {
                val rr = k * 22.dp.toPx()
                if (glass) drawCircle(ink, rr, c, style = Stroke(w))
                else drawPath(taperedPath(pencilLoop(Size(rr * 2, rr * 2), rr, k * 7, 1.dp.toPx()).map { it + c - Offset(rr, rr) }, 1.2.dp.toPx()), ink)
            }
        }
        4 -> { // сітка точок
            var y = top + 16.dp.toPx()
            while (y < top + inner.height) {
                var x = left + 16.dp.toPx()
                while (x < left + inner.width) {
                    drawCircle(ink.copy(alpha = ink.alpha * 1.6f), 1.2.dp.toPx(), Offset(x, y))
                    x += 18.dp.toPx()
                }
                y += 18.dp.toPx()
            }
        }
        else -> { // хвилі
            var y = top + 24.dp.toPx()
            var i = 0
            while (y < top + inner.height) {
                val pts = List(41) { k ->
                    val x = left + inner.width * k / 40f
                    Offset(x, y + sin(k * 0.5f + i) * 5.dp.toPx())
                }
                if (glass) {
                    drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, ink, style = Stroke(w))
                } else {
                    drawPath(taperedPath(pts, 1.2.dp.toPx()), ink)
                }
                y += 26.dp.toPx(); i++
            }
        }
    }
}

/** «Аркуш»: завитки олівцем у кутах обкладинки. */
private fun DrawScope.drawCorners(inset: Float) {
    val s = 30.dp.toPx()
    val ink = SheetInk.copy(alpha = 0.5f)
    val corners = listOf(
        Offset(inset, inset) to Offset(1f, 1f),
        Offset(size.width - inset, inset) to Offset(-1f, 1f),
        Offset(inset, size.height - inset) to Offset(1f, -1f),
        Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
    )
    corners.forEachIndexed { i, (c, d) ->
        val pts = List(30) { k ->
            val t = k / 29f
            val a = t * 4.2f
            val rr = s * (0.9f - 0.6f * t)
            Offset(c.x + d.x * (s * 0.3f + rr * (1 - cos(a)) * 0.5f), c.y + d.y * (s * 0.3f + rr * sin(a) * 0.5f))
        }
        drawPath(taperedPath(pts, 1.5.dp.toPx()), ink)
        pencilTick(c + Offset(d.x * s * 0.15f, d.y * s * 1.1f), c + Offset(d.x * s * 1.1f, d.y * s * 0.15f), 1.3.dp.toPx(), ink, i + 9)
    }
}
