package com.blacklaunch.qrcodes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Значки «Аркуша» для QR-кодів: намальовані від руки загостреними штрихами олівця, як у Black Glass (HandIcons).
 * Сітка 24×24.
 */

enum class Glyph {
    Keyboard, ScanQr, Photo, Gallery, Save, Star, StarEmpty, Print, Delete, Edit, Close, Back, Copy, Flash, Shutter, Wifi, Phone, TextT, Frame, Globe, Download, Mail, Pin, Person, Smile, Heart, ScanPhone, Undo, Grid, RotateLeft, RotateRight, Center, More, Settings,
}

@Composable
fun GlyphIcon(glyph: Glyph, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = SheetInk, size: Dp = 24.dp) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val strokes = glyphStrokes(glyph)
    Canvas(modifier.then(described).size(size)) {
        val unit = this.size.minDimension / 24f
        val origin = Offset((this.size.width - unit * 24f) / 2f, (this.size.height - unit * 24f) / 2f)
        val glass = AppStyle.glass
        strokes.forEach { stroke ->
            val points = stroke.map { origin + it * unit }
            if (glass) {
                // «Скло»: рівна тонка лінія із заокругленими кінцями, як системні значки.
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                }
                drawPath(
                    path,
                    tint,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        1.8f * unit,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            } else {
                drawPath(taperedPath(points, 2.1f * unit), tint)
            }
        }
        if (glyph == Glyph.Star && !glass) {
            // Вибране — зірка ще й «заштрихована» кількома рисками.
            listOf(Offset(9.5f, 12f) to Offset(14.6f, 11.6f), Offset(9.6f, 14.6f) to Offset(14.2f, 14.4f)).forEachIndexed { i, (a, b) ->
                pencilTick(origin + a * unit, origin + b * unit, 1.6f * unit, tint, i + 3)
            }
        }
    }
}

private val cache = java.util.concurrent.ConcurrentHashMap<Glyph, List<List<Offset>>>()

internal fun glyphStrokes(glyph: Glyph): List<List<Offset>> = cache.getOrPut(glyph) { strokes(glyph) }

private fun line(vararg xy: Float): List<Offset> = buildList {
    add(Offset(xy[0], xy[1]))
    var i = 2
    while (i + 1 < xy.size) {
        val a = Offset(xy[i - 2], xy[i - 1])
        val b = Offset(xy[i], xy[i + 1])
        for (s in 1..8) add(a + (b - a) * (s / 8f))
        i += 2
    }
}

private fun arc(cx: Float, cy: Float, r: Float, from: Float, to: Float, steps: Int = 24): List<Offset> = List(steps + 1) { i ->
    val a = (from + (to - from) * i / steps) * PI.toFloat() / 180f
    Offset(cx + r * cos(a), cy + r * sin(a))
}

private fun box(x: Float, y: Float, w: Float, h: Float, seed: Int = 3, r: Float = 2f): List<Offset> =
    pencilLoop(Size(w, h), r, seed, 0.25f).map { it + Offset(x, y) }

private val STAR = line(12f, 4f, 14.3f, 9.6f, 20f, 9.9f, 15.6f, 13.6f, 17.1f, 19.6f, 12f, 16.3f, 6.9f, 19.6f, 8.4f, 13.6f, 4f, 9.9f, 9.7f, 9.6f, 12.2f, 3.7f)

private fun strokes(glyph: Glyph): List<List<Offset>> = when (glyph) {
    // Клавіатура: рамка й рядок клавіш.
    Glyph.Keyboard -> listOf(
        box(3f, 6.5f, 18f, 11f, 5),
        line(6.5f, 10f, 7.5f, 10f), line(10f, 10f, 11f, 10f), line(13.2f, 10f, 14.2f, 10f), line(16.5f, 10f, 17.5f, 10f),
        line(8f, 14f, 16f, 14.2f),
    )
    // Рамка видошукача з кутиками й риска сканування.
    Glyph.ScanQr -> listOf(
        line(4f, 9f, 4.2f, 4.2f, 9f, 4f), line(15f, 4.1f, 19.8f, 4f, 20f, 9f),
        line(20f, 15f, 19.8f, 19.8f, 15f, 20f), line(9f, 19.9f, 4.1f, 19.8f, 4f, 15f),
        line(7f, 12.1f, 17f, 11.9f),
    )
    // Фотоапарат.
    Glyph.Photo -> listOf(
        line(3.5f, 8f, 8f, 7.8f, 9.5f, 5.5f, 14.5f, 5.4f, 16f, 7.8f, 20.5f, 8f, 20.4f, 18.6f, 3.6f, 18.5f, 3.4f, 7.6f),
        arc(12f, 13f, 3.4f, -80f, 285f, 18),
    )
    // Картинка з горою й сонцем.
    Glyph.Gallery -> listOf(
        box(3.5f, 4.5f, 17f, 15f, 9),
        line(5f, 17.5f, 9.5f, 11.5f, 13f, 15.5f, 15f, 13.2f, 19f, 17.6f),
        arc(15.2f, 8.8f, 1.6f, 0f, 360f, 12),
    )
    Glyph.Save -> listOf(line(5f, 12.5f, 9.8f, 17.4f, 19.2f, 6.8f))
    Glyph.Star, Glyph.StarEmpty -> listOf(STAR)
    // Принтер: корпус, аркуш зверху, аркуш знизу.
    Glyph.Print -> listOf(
        line(7f, 8.5f, 7.1f, 3.6f, 16.9f, 3.5f, 17f, 8.5f),
        line(7f, 16.5f, 3.6f, 16.4f, 3.5f, 8.6f, 20.5f, 8.5f, 20.4f, 16.5f, 17f, 16.6f),
        line(7.2f, 13.5f, 7f, 20.5f, 17.1f, 20.4f, 16.9f, 13.4f, 7.2f, 13.5f),
        line(9.5f, 17f, 14.5f, 17f),
    )
    Glyph.Delete -> listOf(
        line(4.8f, 6.6f, 19.2f, 6.4f), line(9.6f, 6.4f, 10f, 4.4f, 14f, 4.4f, 14.4f, 6.4f),
        line(6.6f, 6.6f, 7.6f, 19.6f, 16.4f, 19.5f, 17.4f, 6.5f), line(10.2f, 9.8f, 10.4f, 16.4f), line(13.8f, 9.8f, 13.6f, 16.4f),
    )
    Glyph.Edit -> listOf(
        line(5.2f, 18.8f, 16.2f, 7.8f),
        line(4.6f, 19.4f, 5.4f, 16.2f, 7.8f, 18.6f, 4.6f, 19.4f),
        line(14.6f, 6.2f, 17.8f, 9.4f),
    )
    Glyph.Close -> listOf(line(6.2f, 5.8f, 18f, 18.3f), line(17.8f, 6f, 6f, 18f))
    Glyph.Back -> listOf(line(19.5f, 12.3f, 5.5f, 11.8f), line(11f, 5.8f, 5f, 12f, 11.2f, 18.2f))
    // Два аркуші один на одному.
    Glyph.Copy -> listOf(box(8.5f, 8f, 11.5f, 12.5f, 4), line(5.5f, 15.5f, 4.2f, 15.4f, 4f, 3.8f, 14.8f, 3.6f, 15f, 5.2f))
    // Блискавка.
    Glyph.Flash -> listOf(line(13.5f, 3f, 6.5f, 13.2f, 11.6f, 13f, 10.2f, 21f, 17.6f, 10.4f, 12.2f, 10.6f, 13.6f, 2.8f))
    // Спуск затвора: подвійне коло.
    Glyph.Shutter -> listOf(arc(12f, 12f, 9f, -90f, 280f, 36), arc(12f, 12f, 5.2f, 30f, 400f, 24))
    // Хвилі Wi‑Fi і крапка.
    Glyph.Wifi -> listOf(arc(12f, 19f, 13f, 225f, 315f, 18), arc(12f, 19f, 8.6f, 228f, 312f, 14), arc(12f, 19f, 4.3f, 232f, 308f, 10), arc(12f, 18.6f, 0.9f, 0f, 360f, 8))
    // Телефон: корпус і кнопка.
    Glyph.Phone -> listOf(box(7f, 2.8f, 10f, 18.4f, 7, 2.2f), line(10.8f, 17.6f, 13.2f, 17.6f), line(10.5f, 5.2f, 13.5f, 5.2f))
    // Глобус — сайт, посилання.
    Glyph.Globe -> listOf(
        arc(12f, 12f, 8.6f, -90f, 275f, 32),
        line(3.6f, 12.1f, 20.4f, 11.9f),
        arc(12f, 12f, 8.6f, -90f, 90f, 18).map { Offset(12f + (it.x - 12f) * 0.45f, it.y) },
        arc(12f, 12f, 8.6f, 90f, 270f, 18).map { Offset(12f + (it.x - 12f) * 0.45f, it.y) },
        line(5.2f, 7.6f, 18.8f, 7.4f), line(5.2f, 16.4f, 18.8f, 16.6f),
    )
    // Стрілка вниз у лоток — завантажити.
    Glyph.Download -> listOf(
        line(12f, 3.5f, 12.1f, 14.8f), line(7.4f, 10.4f, 12f, 15.2f, 16.7f, 10.3f),
        line(4f, 15.5f, 4.2f, 20f, 19.8f, 19.9f, 20f, 15.4f),
    )
    // Конверт — пошта.
    Glyph.Mail -> listOf(box(3.2f, 5.8f, 17.6f, 12.6f, 9, 1.5f), line(3.8f, 6.8f, 12f, 13.4f, 20.2f, 6.7f))
    // Шпилька мапи — адреса.
    Glyph.Pin -> listOf(
        arc(12f, 9.6f, 6.2f, 150f, 390f, 26) + line(17.4f, 12.7f, 12.1f, 21f, 6.6f, 12.7f),
        arc(12f, 9.6f, 2.3f, 0f, 365f, 14),
    )
    // Людина — контакт.
    Glyph.Person -> listOf(arc(12f, 8f, 3.8f, -90f, 275f, 20), arc(12f, 21.5f, 7.6f, 200f, 340f, 18))
    // Смайлик.
    Glyph.Smile -> listOf(
        arc(12f, 12f, 8.8f, -100f, 268f, 36),
        arc(12f, 12.2f, 4.8f, 25f, 155f, 14),
        line(9.1f, 8.8f, 9.2f, 10.2f), line(14.8f, 8.7f, 14.9f, 10.1f),
    )
    // Серце.
    Glyph.Heart -> listOf(
        arc(8.2f, 9f, 3.9f, 135f, 340f, 16) + arc(15.8f, 9f, 3.9f, 200f, 405f, 16) + line(18.6f, 11.8f, 12f, 19.6f, 5.4f, 11.8f),
    )
    // Телефон наводять на код — «скануй».
    Glyph.ScanPhone -> listOf(
        box(8.5f, 6.5f, 9f, 15f, 21, 1.8f),
        line(11.6f, 18.8f, 14.4f, 18.8f),
        line(3f, 6f, 3f, 3f, 6f, 3f), line(18f, 3f, 21f, 3f, 21f, 6f),
        line(10.8f, 10.5f, 13.2f, 10.5f, 13.2f, 13f, 10.8f, 13f, 10.8f, 10.5f),
    )
    // Крок назад: дуга зі стрілкою.
    Glyph.Undo -> listOf(arc(12.5f, 13.5f, 6.5f, 200f, 470f, 24), line(4.6f, 7.2f, 6.2f, 11.4f, 10.4f, 9.8f))
    // Поворот: дуга зі стрілкою (ліворуч / праворуч).
    Glyph.RotateLeft -> listOf(arc(12f, 12.5f, 7f, 200f, 470f, 26), line(3.8f, 6.8f, 5.2f, 11.2f, 9.6f, 10f))
    Glyph.RotateRight -> listOf(arc(12f, 12.5f, 7f, 70f, 340f, 26).reversed(), line(20.2f, 6.8f, 18.8f, 11.2f, 14.4f, 10f))
    // Приціл — по центру.
    Glyph.Center -> listOf(arc(12f, 12f, 6.5f, -90f, 275f, 26), line(12f, 2.5f, 12f, 7f), line(12f, 17f, 12f, 21.5f), line(2.5f, 12f, 7f, 12f), line(17f, 12f, 21.5f, 12f))
    // Шестірня — налаштування: коло й вісім зубців.
    Glyph.Settings -> listOf(arc(12f, 12f, 3f, -90f, 275f, 18), arc(12f, 12f, 6.6f, -80f, 285f, 30)) +
        (0 until 8).map { i ->
            val a = i * Math.PI / 4
            val c = kotlin.math.cos(a).toFloat()
            val sn = kotlin.math.sin(a).toFloat()
            line(12f + 6.8f * c, 12f + 6.8f * sn, 12f + 9.4f * c, 12f + 9.4f * sn)
        }
    // Три крапки — меню.
    Glyph.More -> listOf(arc(5.5f, 12f, 1.3f, 0f, 370f, 10), arc(12f, 12f, 1.3f, 0f, 370f, 10), arc(18.5f, 12f, 1.3f, 0f, 370f, 10))
    // Сітка 2×2.
    Glyph.Grid -> listOf(box(3.5f, 3.5f, 7.5f, 7.5f, 31, 1.4f), box(13f, 3.5f, 7.5f, 7.5f, 32, 1.4f), box(3.5f, 13f, 7.5f, 7.5f, 33, 1.4f), box(13f, 13f, 7.5f, 7.5f, 34, 1.4f))
    // «Т» — свій текст.
    Glyph.TextT -> listOf(line(5f, 5.6f, 19f, 5.3f), line(12f, 5.4f, 12.2f, 19.2f), line(9.6f, 19.2f, 14.6f, 19.1f))
    // Рамка олівцем.
    Glyph.Frame -> listOf(box(3.5f, 4f, 17f, 16f, 12, 4f), box(6.5f, 7f, 11f, 10f, 14, 2f))
}
