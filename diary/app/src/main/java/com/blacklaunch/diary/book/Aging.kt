package com.blacklaunch.diary.book

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Старіння сторінки — за віком дня [ageDays]. Кожна сторінка старіє по-своєму ([seed] — від дати): плями, потертості й
 * сліди лягають у своїх місцях, але щоразу однаково. Етапи:
 *  - 1 місяць — папір / скло ледь тьмяніє;
 *  - 3 місяці — потерті краї й кути, дрібні подряпини;
 *  - 6 місяців — потертості виразніші, легкі плями;
 *  - 9 місяців — слід від чашки, жирна пляма чи краплі (не на кожній сторінці);
 *  - рік — найстаріше: усе разом, трохи сильніше.
 * «Аркуш» старіє як папір (теплі буруваті плями), «Скло» — як скло (подряпини, мутні сліди, водяне кільце).
 */
fun DrawScope.drawAging(ageDays: Long, seed: Int, glass: Boolean) {
    if (ageDays < 30) return
    val rnd = java.util.Random(seed * 1_000_003L + 17)
    val k = when {
        ageDays >= 365 -> 1.4f
        ageDays >= 270 -> 1.15f
        ageDays >= 180 -> 1f
        else -> 0.8f
    }
    val w = size.width
    val h = size.height
    // 1 місяць: тьмяніє.
    drawRect(if (glass) Color(0xFF8A96A6).copy(alpha = 0.035f * k) else Color(0xFF3A2E20).copy(alpha = 0.08f * k))
    if (ageDays < 90) return

    // 3 місяці: потерті краї й кути, подряпини.
    val wear = if (glass) Color.White else Color(0xFFD8CBB4)
    repeat(5 + rnd.nextInt(5) + if (ageDays >= 180) 5 else 0) {
        val side = rnd.nextInt(4)
        val t = rnd.nextFloat()
        val c = when (side) {
            0 -> Offset(t * w, 0f)
            1 -> Offset(w, t * h)
            2 -> Offset(t * w, h)
            else -> Offset(0f, t * h)
        }
        val r = w * (0.03f + rnd.nextFloat() * 0.07f)
        drawCircle(Brush.radialGradient(listOf(wear.copy(alpha = 0.07f * k), Color.Transparent), center = c, radius = r), radius = r, center = c)
    }
    listOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h)).forEach { c ->
        if (rnd.nextFloat() < 0.7f) {
            val r = w * (0.06f + rnd.nextFloat() * 0.06f)
            drawCircle(Brush.radialGradient(listOf(wear.copy(alpha = 0.08f * k), Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
    }
    repeat(2 + rnd.nextInt(4) + if (ageDays >= 365) 3 else 0) {
        val a = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        val ang = rnd.nextFloat() * 6.28f
        val len = w * (0.04f + rnd.nextFloat() * 0.12f)
        val b = a + Offset(cos(ang) * len, sin(ang) * len)
        drawLine(wear.copy(alpha = (if (glass) 0.09f else 0.06f) * k), a, b, (0.6f + rnd.nextFloat() * 0.6f) * density)
    }
    if (ageDays < 180) return

    // 6 місяців: легкі плями (папір — буруваті, скло — мутні сліди пальців).
    repeat(1 + rnd.nextInt(3)) {
        val c = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        val r = w * (0.06f + rnd.nextFloat() * 0.12f)
        val tint = if (glass) Color(0xFFB8C2D0).copy(alpha = 0.035f * k) else Color(0xFF6B5132).copy(alpha = 0.10f * k)
        drawCircle(Brush.radialGradient(0f to tint, 0.7f to tint.copy(alpha = tint.alpha * 0.6f), 1f to Color.Transparent, center = c, radius = r), radius = r, center = c)
    }
    if (ageDays < 270) return

    // 9 місяців: слід від чашки (не на кожній сторінці), жирна пляма, краплі.
    if (rnd.nextFloat() < 0.6f) {
        val c = Offset(w * (0.15f + rnd.nextFloat() * 0.7f), h * (0.1f + rnd.nextFloat() * 0.8f))
        val r = w * (0.12f + rnd.nextFloat() * 0.08f)
        val ring = if (glass) Color(0xFFCAD4E0) else Color(0xFF7A5A36)
        // Кільце нерівне: місцями густіше, місцями майже зникає; трохи не замкнене.
        val gap = rnd.nextFloat() * 6.28f
        for (i in 0 until 48) {
            val a0 = i / 48f * 6.28f
            val strength = (0.5f + 0.5f * sin(a0 * 3f + gap)) * (if (kotlin.math.abs(a0 - gap) < 0.5f) 0.15f else 1f)
            val path = Path().apply {
                moveTo(c.x + cos(a0) * r, c.y + sin(a0) * r)
                lineTo(c.x + cos(a0 + 0.14f) * r, c.y + sin(a0 + 0.14f) * r)
            }
            drawPath(path, ring.copy(alpha = (if (glass) 0.08f else 0.16f) * k * strength), style = Stroke((2f + rnd.nextFloat()) * density))
        }
        drawCircle(ring.copy(alpha = (if (glass) 0.015f else 0.03f) * k), r * 0.95f, c)
    }
    if (!glass && rnd.nextFloat() < 0.5f) {
        val c = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        val r = w * (0.05f + rnd.nextFloat() * 0.06f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF0E0C0).copy(alpha = 0.06f * k), Color.Transparent), center = c, radius = r), radius = r, center = c)
    }
    repeat(rnd.nextInt(4)) {
        val c = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        val r = (3f + rnd.nextFloat() * 5f) * density
        drawCircle((if (glass) Color(0xFFCAD4E0) else Color(0xFF6B5132)).copy(alpha = 0.08f * k), r, c, style = Stroke(1f * density))
    }
}

/**
 * Рваний клаптик біля корінця — тут була вирвана сторінка: вузька смужка паперу (скла) з нерівним краєм уздовж
 * лівого боку наступної сторінки.
 */
fun DrawScope.drawTornStub(seed: Int, glass: Boolean) {
    val rnd = java.util.Random(seed * 7919L + 3)
    val base = 7.dp.toPx()
    val path = Path().apply {
        moveTo(0f, 0f)
        var y = 0f
        lineTo(base, 0f)
        while (y < size.height) {
            y += (4f + rnd.nextFloat() * 9f) * density
            lineTo(base + (rnd.nextFloat() - 0.4f) * 5.dp.toPx(), y.coerceAtMost(size.height))
        }
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, if (glass) Color(0xFF1C1E22) else Color(0xFF1E1B16))
    drawPath(path, if (glass) Color(0x40FFFFFF) else Color(0x55E8E0D0), style = Stroke(0.8f * density))
}
