package com.blacklaunch.diary.book

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Size
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
 * Клаптик вирваної сторінки біля корінця: смужка паперу (скла) від корінця до рваного краю, ширша за подвійну лінію
 * корінця (закриває її), з тінню на наступну сторінку — лежить зверху, а не намальований на ній.
 */
fun DrawScope.drawTornStub(seed: Int, glass: Boolean) {
    drawTornRemnant(16.dp.toPx(), 0f, size.height, seed, glass, fill = true)
}

/** «Зерно» паперу сторінки: від дня й частини (як у [DayPage]); з нього ж — вік для звороту. */
fun pageSeed(day: Long, part: Int): Int = ((day % 997).toInt() + part * 31) or (day.toInt() shl 12)

/** Вік у днях за [pageSeed] (для старіння звороту). */
@Suppress("FunctionName")
fun DiaryViewModel_ageOf(seed: Int): Long = (com.blacklaunch.diary.DiaryViewModel.today() - (seed ushr 12)).coerceAtLeast(0)

/**
 * Лінія розриву біля корінця — одна й та сама для того самого аркуша: точки рахуються знизу вгору від того самого
 * [seed], тож розрив, що продовжують, лягає рівно на колишній (не малюється другий). [x] — середня лінія розриву.
 * [fill] — ще й смужка паперу від корінця до розриву з тінню праворуч (клаптик, що лишився); інакше — лише сам розрив
 * (на цілій, заклеєній сторінці). Мікротріщини — рідкі, тонкі, нерівні, ідуть углиб паперу.
 */
fun DrawScope.drawTornRemnant(x: Float, yTop: Float, yBottom: Float, seed: Int, glass: Boolean, fill: Boolean) {
    if (yTop >= yBottom) return
    val rnd = java.util.Random(seed * 104729L + 11)
    val pts = ArrayList<Offset>()
    var y = size.height
    while (y > yTop) {
        val p = Offset(x + (rnd.nextFloat() - 0.5f) * 6f * density, y)
        if (y <= yBottom) pts += p
        y -= (3f + rnd.nextFloat() * 7f) * density
    }
    if (pts.isEmpty()) return
    pts += Offset(pts.last().x, yTop)
    if (fill) {
        // Тінь клаптика на наступній сторінці.
        val sh = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.forEach { lineTo(it.x + 2f * density, it.y) }
            lineTo(pts.last().x + 9.dp.toPx(), yTop)
            lineTo(pts.first().x + 9.dp.toPx(), pts.first().y)
            close()
        }
        drawPath(sh, Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), startX = x, endX = x + 9.dp.toPx()))
        val strip = Path().apply {
            moveTo(0f, pts.first().y)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(0f, yTop)
            close()
        }
        drawPath(strip, if (glass) Color(0xFF16181C) else Color(0xFF1A1712))
        drawLine(Color.White.copy(alpha = if (glass) 0.10f else 0.06f), Offset(x * 0.35f, yTop), Offset(x * 0.35f, pts.first().y), 1f * density)
    }
    // Сам розрив — світла серцевина паперу (товща) і темна лінія тіні.
    val edge = Path().apply {
        moveTo(pts.first().x, pts.first().y)
        pts.forEach { lineTo(it.x, it.y) }
    }
    drawPath(edge, if (glass) Color(0xFF4A505A) else Color(0xFF7D7465), style = Stroke(2.6f * density))
    drawPath(edge, Color.Black.copy(alpha = 0.45f), style = Stroke(0.8f * density))
    // Рідкі мікротріщини углиб аркуша (праворуч від розриву).
    val crack = Color.Black.copy(alpha = if (glass) 0.45f else 0.55f)
    pts.forEachIndexed { i, p ->
        if (i % 3 != 1 || rnd.nextFloat() > 0.55f) return@forEachIndexed
        var q = p
        var a = (rnd.nextFloat() - 0.5f) * 1.4f
        repeat(2 + rnd.nextInt(3)) {
            val len = (1.5f + rnd.nextFloat() * 3.5f) * density
            val nq = q + Offset(kotlin.math.cos(a) * len, kotlin.math.sin(a) * len)
            drawLine(crack, q, nq, 0.6f * density)
            q = nq
            a += (rnd.nextFloat() - 0.5f) * 0.9f
        }
    }
}

/** Сумісність: розрив без смужки (для клаптя, що загинається). */
fun DrawScope.drawRaggedEdge(x: Float, yTop: Float, seed: Int, glass: Boolean, outward: Float) =
    drawTornRemnant(x, yTop, size.height, seed, glass, fill = outward > 0f)

/**
 * Надірвано й заклеєно: лінія розриву біля корінця до висоти [k] (0…1) і кілька шматочків непрозорого скотчу —
 * лише в лівому полі ([marginPx]), тож на рядки з текстом не заходять. «Аркуш» — паперовий (крафт), «Скло» — матовий.
 */
fun DrawScope.drawTapedTear(k: Float, seed: Int, glass: Boolean, marginPx: Float) {
    if (k <= 0.01f) return
    val x = TEAR_X.toPx()
    val yTop = size.height * (1f - k)
    drawTornRemnant(x, yTop, size.height, seed, glass, fill = false)
    val rnd = java.util.Random(seed * 31L + 5)
    val left = 1.5.dp.toPx()
    val w = (marginPx - 3.dp.toPx() - left).coerceAtLeast(10.dp.toPx())
    val n = 1 + (k * 3.5f).toInt()
    for (i in 0 until n) {
        val h = (13f + rnd.nextFloat() * 4f) * density
        val cy = (yTop + (size.height - yTop) * (i + 0.5f) / n + (rnd.nextFloat() - 0.5f) * 10f * density).coerceIn(yTop + h, size.height - h)
        val ang = (rnd.nextFloat() - 0.5f) * 10f
        rotate(ang, pivot = Offset(left + w / 2, cy)) {
            val tl = Offset(left, cy - h / 2)
            val sz = Size(w, h)
            if (glass) {
                // Матовий скотч: світла каламутна смужка (крізь неї нічого не розібрати).
                drawRect(Color(0xFF8E97A3).copy(alpha = 0.75f), tl, sz)
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), startY = tl.y, endY = tl.y + h), tl, sz)
            } else {
                // Паперовий скотч-пластир: щільний, теплий, з нерівними торцями.
                drawRect(Color(0xFF8C7A5A), tl, sz)
                repeat(6) { drawCircle(Color(0xFF6F5F44).copy(alpha = 0.5f), 0.6f * density, Offset(tl.x + rnd.nextFloat() * w, tl.y + rnd.nextFloat() * h)) }
            }
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(tl.x, tl.y + h), Offset(tl.x + w, tl.y + h), 1f * density)
        }
    }
}

/** Де біля корінця йде лінія розриву (однаково для живого розриву, заклеєного й клаптика). */
val TEAR_X = 8.dp

/** Зерно лінії розриву сторінки — одне на всі її стани. */
fun tearSeedOf(day: Long, part: Int): Int = (day * 13 + part).toInt()
