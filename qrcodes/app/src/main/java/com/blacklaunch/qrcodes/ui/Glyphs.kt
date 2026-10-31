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

enum class Glyph { Keyboard, ScanQr, Photo, Gallery, Save, Star, StarEmpty, Share, Print, Delete, Edit, Close, Back, Plus, Minus, Copy, Flash, Shutter }

@Composable
fun GlyphIcon(glyph: Glyph, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = SheetInk, size: Dp = 24.dp) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val strokes = glyphStrokes(glyph)
    Canvas(modifier.then(described).size(size)) {
        val unit = this.size.minDimension / 24f
        val origin = Offset((this.size.width - unit * 24f) / 2f, (this.size.height - unit * 24f) / 2f)
        strokes.forEach { stroke ->
            drawPath(taperedPath(stroke.map { origin + it * unit }, 2.1f * unit), tint)
        }
        if (glyph == Glyph.Star) {
            // Вибране — зірка ще й «заштрихована» кількома рисками.
            listOf(Offset(9.5f, 12f) to Offset(14.6f, 11.6f), Offset(9.6f, 14.6f) to Offset(14.2f, 14.4f)).forEachIndexed { i, (a, b) ->
                pencilTick(origin + a * unit, origin + b * unit, 1.6f * unit, tint, i + 3)
            }
        }
    }
}

private val cache = HashMap<Glyph, List<List<Offset>>>()

private fun glyphStrokes(glyph: Glyph): List<List<Offset>> = cache.getOrPut(glyph) { strokes(glyph) }

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
    // Три кружечки, з'єднані лініями.
    Glyph.Share -> listOf(
        arc(17.5f, 5.8f, 2.3f, 0f, 370f, 14), arc(6.5f, 12f, 2.3f, 0f, 370f, 14), arc(17.5f, 18.2f, 2.3f, 0f, 370f, 14),
        line(8.6f, 10.8f, 15.4f, 7f), line(8.6f, 13.2f, 15.4f, 17f),
    )
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
    Glyph.Plus -> listOf(line(12.1f, 5f, 11.9f, 19.2f), line(5f, 12.1f, 19.1f, 11.8f))
    Glyph.Minus -> listOf(line(5f, 12.1f, 19.1f, 11.8f))
    // Два аркуші один на одному.
    Glyph.Copy -> listOf(box(8.5f, 8f, 11.5f, 12.5f, 4), line(5.5f, 15.5f, 4.2f, 15.4f, 4f, 3.8f, 14.8f, 3.6f, 15f, 5.2f))
    // Блискавка.
    Glyph.Flash -> listOf(line(13.5f, 3f, 6.5f, 13.2f, 11.6f, 13f, 10.2f, 21f, 17.6f, 10.4f, 12.2f, 10.6f, 13.6f, 2.8f))
    // Спуск затвора: подвійне коло.
    Glyph.Shutter -> listOf(arc(12f, 12f, 9f, -90f, 280f, 36), arc(12f, 12f, 5.2f, 30f, 400f, 24))
}
