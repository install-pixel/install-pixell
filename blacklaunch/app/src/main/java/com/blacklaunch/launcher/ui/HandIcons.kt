package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Наш власний набір значків — для обох стилів одна родина форм (сітка 24×24, як у стандартних, тож розміри й місця
 * не змінюються): в «Аркуші» — загостреними штрихами олівця, у склі — рівною світлою лінією з легким світінням.
 * Значок без власного малюнка лишається стандартним.
 */

/** Значки, яких немає серед стандартних (наші малюнки з res/drawable). */
internal enum class Glyph { MIC, TIMER, PALETTE, BATTERY }

/** Замість [Icon]: наш малюнок значка в стилі теми (якщо він є), інакше — стандартний значок. */
@Composable
internal fun HandIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val sheet = LocalSheet.current
    val strokes = handStrokes(imageVector, sheet)
    if (strokes == null) {
        Icon(imageVector, contentDescription, modifier, tint)
        return
    }
    StrokeIcon(strokes, sheet, contentDescription, modifier, tint)
}

/** Наш значок без стандартного відповідника (мікрофон, таймер, палітра, батарея). */
@Composable
internal fun HandGlyph(
    glyph: Glyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val sheet = LocalSheet.current
    StrokeIcon(glyphStrokes(glyph, sheet), sheet, contentDescription, modifier, tint)
}

@Composable
private fun StrokeIcon(strokes: List<List<Offset>>, sheet: Boolean, contentDescription: String?, modifier: Modifier, tint: Color) {
    val description = contentDescription
    val described = if (description != null) Modifier.semantics { this.contentDescription = description } else Modifier
    Canvas(modifier.then(described).size(24.dp)) {
        val unit = size.minDimension / 24f
        val origin = Offset((size.width - unit * 24f) / 2f, (size.height - unit * 24f) / 2f)
        strokes.forEach { stroke ->
            val points = stroke.map { origin + it * unit }
            if (sheet) {
                drawPath(taperedPath(points, 2.1f * unit), tint)
            } else {
                // Скло: рівна лінія з м'яким світінням довкола.
                val path = Path().apply {
                    points.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                }
                drawPath(path, tint.copy(alpha = tint.alpha * 0.18f), style = Stroke(4.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, tint, style = Stroke(1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Ламана через [corners], густо розбита на точки (щоб штрих рівно тоншав до кінців). */
private fun line(vararg corners: P): List<Offset> = buildList {
    add(corners.first().offset)
    for (i in 1 until corners.size) {
        val a = corners[i - 1].offset
        val b = corners[i].offset
        for (s in 1..8) add(a + (b - a) * (s / 8f))
    }
}

/** Дуга кола з центром [c] і радіусом [r] від кута [from] до [to] (у градусах, за годинниковою стрілкою від «на третій»). */
private fun arc(c: P, r: Float, from: Float, to: Float, steps: Int = 24): List<Offset> = List(steps + 1) { i ->
    val a = (from + (to - from) * i / steps) * PI.toFloat() / 180f
    Offset(c.x + r * cos(a), c.y + r * sin(a))
}

/** Заокруглений прямокутник одним штрихом ([wobble] — тремтіння олівця; у склі — рівно). */
private fun box(x: Float, y: Float, w: Float, h: Float, wobble: Float, r: Float = 2f): List<Offset> =
    pencilLoop(Size(w, h), r, 3, wobble).map { it + Offset(x, y) }

/** Коло одним штрихом. */
private fun circle(cx: Float, cy: Float, r: Float, steps: Int = 28): List<Offset> = arc(o(cx, cy), r, -90f, 272f, steps)

/** Шестерня: зубці по колу. */
private fun gear(cx: Float, cy: Float, outer: Float, inner: Float, teeth: Int): List<Offset> = buildList {
    val step = 360f / teeth
    for (t in 0..teeth) {
        val base = t * step - 90f
        listOf(base - step * 0.22f to outer, base + step * 0.22f to outer, base + step * 0.32f to inner, base + step * 0.68f to inner).forEach { (deg, r) ->
            val a = deg * PI.toFloat() / 180f
            add(Offset(cx + r * cos(a), cy + r * sin(a)))
        }
    }
}

/** Точка сітки значка (Offset — value-клас, у vararg його не передати). */
private class P(val x: Float, val y: Float) {
    val offset get() = Offset(x, y)
}

private fun o(x: Float, y: Float) = P(x, y)

private fun handStrokes(icon: ImageVector, sheet: Boolean): List<List<Offset>>? {
    val wob = if (sheet) 0.25f else 0f
    return when (icon) {
        Icons.AutoMirrored.Filled.ArrowBack -> listOf(line(o(19.5f, 12.3f), o(5.5f, 11.8f)), line(o(11f, 5.8f), o(5f, 12f), o(11.2f, 18.2f)))
        Icons.Filled.Close -> listOf(line(o(6.2f, 5.8f), o(18f, 18.3f)), line(o(17.8f, 6f), o(6f, 18f)))
        Icons.Filled.KeyboardArrowUp -> listOf(line(o(6f, 15.2f), o(12f, 8.8f), o(18.2f, 15f)))
        Icons.Filled.KeyboardArrowDown -> listOf(line(o(6f, 9f), o(12f, 15.3f), o(18.1f, 9.2f)))
        Icons.Filled.Add -> listOf(line(o(12.1f, 5f), o(11.9f, 19.2f)), line(o(5f, 12.1f), o(19.1f, 11.8f)))
        Icons.Filled.Done, Icons.Filled.Check -> listOf(line(o(5f, 12.5f), o(9.8f, 17.4f), o(19.2f, 6.8f)))
        Icons.Filled.CheckCircle -> listOf(circle(12f, 12f, 8.8f), line(o(7.6f, 12.4f), o(10.6f, 15.4f), o(16.6f, 9f)))
        Icons.Filled.Edit -> listOf(
            line(o(4.6f, 19.4f), o(5.6f, 15.6f), o(15.4f, 5.8f), o(18.2f, 8.6f), o(8.4f, 18.4f), o(4.6f, 19.4f)),
            line(o(13.6f, 7.6f), o(16.4f, 10.4f)),
        )
        Icons.Filled.Refresh -> listOf(arc(o(12f, 12f), 7f, -60f, 250f), line(o(16.8f, 3.6f), o(15.7f, 6.4f), o(18.6f, 7.2f)))
        Icons.Filled.Lock -> listOf(arc(o(12f, 9.5f), 3.6f, 180f, 360f, 12) + line(o(15.6f, 9.5f), o(15.6f, 11f)), line(o(8.4f, 11f), o(8.4f, 9.5f)), box(6f, 11f, 12f, 9f, wob))
        Icons.Filled.PlayArrow -> listOf(line(o(8f, 5.5f), o(18.8f, 12f), o(8.2f, 18.6f), o(7.9f, 5.2f)))
        Icons.Filled.Home -> listOf(line(o(4f, 11.5f), o(12f, 4.6f), o(20f, 11.6f)), line(o(6.4f, 9.8f), o(6.5f, 19.4f), o(17.6f, 19.3f), o(17.5f, 9.6f)), line(o(10.4f, 19.3f), o(10.4f, 14.4f), o(13.6f, 14.4f), o(13.7f, 19.2f)))
        Icons.Filled.AccountCircle -> listOf(arc(o(12f, 12f), 8.8f, -100f, 268f, 36), arc(o(12f, 9.6f), 2.9f, -90f, 275f, 18), arc(o(12f, 18.6f), 5.4f, 205f, 335f, 14))
        Icons.Filled.Person -> listOf(circle(12f, 8.2f, 3.6f, 20), arc(o(12f, 20.6f), 7.2f, 190f, 350f, 18))
        Icons.Filled.AccountBox -> listOf(box(4f, 4f, 16f, 16f, wob, 3f), circle(12f, 10f, 2.8f, 16), arc(o(12f, 19.5f), 5f, 200f, 340f, 12))
        Icons.Filled.Face -> listOf(circle(12f, 12f, 8.8f), line(o(9.2f, 9.8f), o(9.2f, 10.6f)), line(o(14.8f, 9.8f), o(14.8f, 10.6f)), arc(o(12f, 12.6f), 4.2f, 25f, 155f, 12))
        Icons.Filled.Notifications -> listOf(
            line(o(6f, 17f), o(7.4f, 15f), o(7.4f, 10.6f)) + arc(o(12f, 10.6f), 4.6f, 180f, 360f, 14) + line(o(16.6f, 10.6f), o(16.6f, 15f), o(18f, 17.1f), o(6f, 17f)),
            arc(o(12f, 18.4f), 1.8f, 0f, 180f, 8),
        )
        Icons.Filled.Delete -> listOf(line(o(4.8f, 6.6f), o(19.2f, 6.4f)), line(o(9.6f, 6.4f), o(10f, 4.4f), o(14f, 4.4f), o(14.4f, 6.4f)), line(o(6.6f, 6.6f), o(7.6f, 19.6f), o(16.4f, 19.5f), o(17.4f, 6.5f)), line(o(10.2f, 9.8f), o(10.4f, 16.4f)), line(o(13.8f, 9.8f), o(13.6f, 16.4f)))
        Icons.Filled.Star -> listOf(line(o(12f, 4f), o(14.3f, 9.6f), o(20f, 9.9f), o(15.6f, 13.6f), o(17.1f, 19.6f), o(12f, 16.3f), o(6.9f, 19.6f), o(8.4f, 13.6f), o(4f, 9.9f), o(9.7f, 9.6f), o(12.2f, 3.7f)))
        Icons.Filled.Settings -> listOf(gear(12f, 12f, 8.8f, 6.6f, 8), circle(12f, 12f, 2.8f, 16))
        Icons.Filled.Call -> listOf(
            line(o(6f, 4.6f), o(9.3f, 4.3f), o(10.6f, 8.4f), o(8.6f, 10.1f), o(10.6f, 13.4f), o(13.9f, 15.4f), o(15.5f, 13.4f), o(19.7f, 14.7f), o(19.5f, 18.4f), o(16.6f, 19.9f), o(11.2f, 18.1f), o(6.6f, 13.6f), o(4.3f, 8.6f), o(4.6f, 5.7f), o(6f, 4.6f)),
        )
        Icons.Filled.Send -> listOf(line(o(3.6f, 11.6f), o(20.4f, 4f), o(13.6f, 20.4f), o(11f, 13f), o(3.6f, 11.6f)), line(o(11f, 13f), o(20.4f, 4f)))
        Icons.Filled.Place -> listOf(arc(o(12f, 9.6f), 6f, 150f, 390f, 24) + line(o(17.2f, 12.6f), o(12f, 20.8f), o(6.8f, 12.6f)), circle(12f, 9.6f, 2.2f, 14))
        Icons.Filled.Info -> listOf(circle(12f, 12f, 8.8f), line(o(12f, 11f), o(12f, 16.6f)), line(o(12f, 7.6f), o(12f, 7.9f)))
        Icons.Filled.DateRange -> listOf(box(4f, 5.6f, 16f, 14.4f, wob), line(o(4.2f, 9.8f), o(19.8f, 9.8f)), line(o(8.4f, 3.8f), o(8.4f, 7f)), line(o(15.6f, 3.8f), o(15.6f, 7f)), line(o(8f, 13.6f), o(8.4f, 13.6f)), line(o(12f, 13.6f), o(12.4f, 13.6f)), line(o(16f, 13.6f), o(16.4f, 13.6f)))
        Icons.Filled.Search -> listOf(circle(10.4f, 10.4f, 6f, 24), line(o(14.8f, 14.8f), o(20f, 20f)))
        Icons.Filled.Email -> listOf(box(3.6f, 6f, 16.8f, 12.4f, wob), line(o(4f, 6.6f), o(12f, 12.8f), o(20f, 6.6f)))
        Icons.Filled.Share -> listOf(circle(17.4f, 6f, 2.4f, 12), circle(6.6f, 12f, 2.4f, 12), circle(17.4f, 18f, 2.4f, 12), line(o(8.7f, 10.9f), o(15.3f, 7.1f)), line(o(8.7f, 13.1f), o(15.3f, 16.9f)))
        Icons.Filled.Menu -> listOf(line(o(5f, 7f), o(19f, 7f)), line(o(5f, 12f), o(19f, 12f)), line(o(5f, 17f), o(19f, 17f)))
        Icons.Filled.List -> listOf(
            line(o(5f, 7f), o(5.4f, 7f)), line(o(9f, 7f), o(19f, 7f)),
            line(o(5f, 12f), o(5.4f, 12f)), line(o(9f, 12f), o(19f, 12f)),
            line(o(5f, 17f), o(5.4f, 17f)), line(o(9f, 17f), o(19f, 17f)),
        )
        else -> null
    }
}

private fun glyphStrokes(glyph: Glyph, sheet: Boolean): List<List<Offset>> {
    val wob = if (sheet) 0.25f else 0f
    return when (glyph) {
        Glyph.MIC -> listOf(box(9f, 3.4f, 6f, 10.6f, wob, 3f), arc(o(12f, 11f), 6f, 0f, 180f, 16), line(o(12f, 17f), o(12f, 20.4f)), line(o(9f, 20.4f), o(15f, 20.4f)))
        Glyph.TIMER -> listOf(circle(12f, 13.6f, 7.4f), line(o(12f, 13.6f), o(12f, 9.4f)), line(o(9.8f, 3.4f), o(14.2f, 3.4f)), line(o(12f, 3.4f), o(12f, 6.2f)), line(o(18.2f, 6.6f), o(19.4f, 7.8f)))
        Glyph.PALETTE -> listOf(
            arc(o(12f, 12f), 8.6f, 60f, 400f, 32) + line(o(16.3f, 19.5f), o(14.6f, 16.6f), o(15.6f, 15.2f), o(17.8f, 15.6f), o(20.2f, 14.8f)),
            circle(8f, 10.6f, 1.2f, 8), circle(11.4f, 7.4f, 1.2f, 8), circle(15.6f, 8.4f, 1.2f, 8), circle(8.4f, 14.8f, 1.2f, 8),
        )
        Glyph.BATTERY -> listOf(box(8f, 5f, 8f, 15.6f, wob), line(o(10.4f, 3.4f), o(13.6f, 3.4f)), line(o(10.4f, 16.4f), o(13.6f, 16.4f)), line(o(10.4f, 13.4f), o(13.6f, 13.4f)))
    }
}
