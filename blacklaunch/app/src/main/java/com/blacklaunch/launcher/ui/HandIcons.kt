package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Значки «Аркуша»: ті самі знаки, але намальовані від руки загостреними штрихами олівця. Сітка — 24×24, як у
 * стандартних значків, тож розміри й місця не змінюються. Значок без власного малюнка лишається звичайним.
 */

/** Замість [Icon]: в «Аркуші» — малюнок олівцем (якщо він є для цього значка), інакше — звичайний значок. */
@Composable
internal fun HandIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val strokes = if (LocalSheet.current) handStrokes(imageVector) else null
    if (strokes == null) {
        Icon(imageVector, contentDescription, modifier, tint)
        return
    }
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(described).size(24.dp)) {
        val unit = size.minDimension / 24f
        val origin = Offset((size.width - unit * 24f) / 2f, (size.height - unit * 24f) / 2f)
        strokes.forEach { stroke ->
            val points = stroke.map { origin + it * unit }
            drawPath(taperedPath(points, 2.1f * unit), tint)
        }
    }
}

/** Ламана через [corners], густо розбита на точки (щоб штрих рівно тоншав до кінців). */
private fun line(vararg corners: Offset): List<Offset> = buildList {
    add(corners.first())
    for (i in 1 until corners.size) {
        val a = corners[i - 1]
        val b = corners[i]
        for (s in 1..8) add(a + (b - a) * (s / 8f))
    }
}

/** Дуга кола з центром [c] і радіусом [r] від кута [from] до [to] (у градусах, за годинниковою стрілкою від «на третій»). */
private fun arc(c: Offset, r: Float, from: Float, to: Float, steps: Int = 24): List<Offset> = List(steps + 1) { i ->
    val a = (from + (to - from) * i / steps) * PI.toFloat() / 180f
    Offset(c.x + r * cos(a), c.y + r * sin(a))
}

/** Заокруглений прямокутник одним штрихом, що трохи заходить за початок. */
private fun box(x: Float, y: Float, w: Float, h: Float): List<Offset> = pencilLoop(Size(w, h), 2f, 3, 0.25f).map { it + Offset(x, y) }

private fun o(x: Float, y: Float) = Offset(x, y)

private fun handStrokes(icon: ImageVector): List<List<Offset>>? = when (icon) {
    Icons.AutoMirrored.Filled.ArrowBack -> listOf(line(o(19.5f, 12.3f), o(5.5f, 11.8f)), line(o(11f, 5.8f), o(5f, 12f), o(11.2f, 18.2f)))
    Icons.Filled.Close -> listOf(line(o(6.2f, 5.8f), o(18f, 18.3f)), line(o(17.8f, 6f), o(6f, 18f)))
    Icons.Filled.KeyboardArrowUp -> listOf(line(o(6f, 15.2f), o(12f, 8.8f), o(18.2f, 15f)))
    Icons.Filled.KeyboardArrowDown -> listOf(line(o(6f, 9f), o(12f, 15.3f), o(18.1f, 9.2f)))
    Icons.Filled.Add -> listOf(line(o(12.1f, 5f), o(11.9f, 19.2f)), line(o(5f, 12.1f), o(19.1f, 11.8f)))
    Icons.Filled.Done -> listOf(line(o(5f, 12.5f), o(9.8f, 17.4f), o(19.2f, 6.8f)))
    Icons.Filled.Edit -> listOf(
        line(o(5.2f, 18.8f), o(16.2f, 7.8f)),
        line(o(4.6f, 19.4f), o(5.4f, 16.2f), o(7.8f, 18.6f), o(4.6f, 19.4f)),
        line(o(14.6f, 6.2f), o(17.8f, 9.4f)),
    )
    Icons.Filled.Refresh -> listOf(arc(o(12f, 12f), 7f, -60f, 250f), line(o(16.8f, 3.6f), o(15.7f, 6.4f), o(18.6f, 7.2f)))
    Icons.Filled.Lock -> listOf(arc(o(12f, 9.5f), 3.6f, 180f, 360f, 12) + line(o(15.6f, 9.5f), o(15.6f, 11f)), line(o(8.4f, 11f), o(8.4f, 9.5f)), box(6f, 11f, 12f, 9f))
    Icons.Filled.PlayArrow -> listOf(line(o(8f, 5.5f), o(18.8f, 12f), o(8.2f, 18.6f), o(7.9f, 5.2f)))
    Icons.Filled.Home -> listOf(line(o(4f, 11.5f), o(12f, 4.6f), o(20f, 11.6f)), line(o(6.4f, 9.8f), o(6.5f, 19.4f), o(17.6f, 19.3f), o(17.5f, 9.6f)), line(o(10.4f, 19.3f), o(10.4f, 14.4f), o(13.6f, 14.4f), o(13.7f, 19.2f)))
    Icons.Filled.AccountCircle -> listOf(arc(o(12f, 12f), 8.8f, -100f, 268f, 36), arc(o(12f, 9.6f), 2.9f, -90f, 275f, 18), arc(o(12f, 18.6f), 5.4f, 205f, 335f, 14))
    Icons.Filled.Notifications -> listOf(
        line(o(6f, 17f), o(7.4f, 15f), o(7.4f, 10.6f)) + arc(o(12f, 10.6f), 4.6f, 180f, 360f, 14) + line(o(16.6f, 10.6f), o(16.6f, 15f), o(18f, 17.1f), o(6f, 17f)),
        arc(o(12f, 18.4f), 1.8f, 0f, 180f, 8),
    )
    Icons.Filled.Delete -> listOf(line(o(4.8f, 6.6f), o(19.2f, 6.4f)), line(o(9.6f, 6.4f), o(10f, 4.4f), o(14f, 4.4f), o(14.4f, 6.4f)), line(o(6.6f, 6.6f), o(7.6f, 19.6f), o(16.4f, 19.5f), o(17.4f, 6.5f)), line(o(10.2f, 9.8f), o(10.4f, 16.4f)), line(o(13.8f, 9.8f), o(13.6f, 16.4f)))
    Icons.Filled.Star -> listOf(line(o(12f, 4f), o(14.3f, 9.6f), o(20f, 9.9f), o(15.6f, 13.6f), o(17.1f, 19.6f), o(12f, 16.3f), o(6.9f, 19.6f), o(8.4f, 13.6f), o(4f, 9.9f), o(9.7f, 9.6f), o(12.2f, 3.7f)))
    else -> null
}
