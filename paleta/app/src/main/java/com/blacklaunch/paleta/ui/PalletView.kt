package com.blacklaunch.paleta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.paleta.pack.BoxSize
import com.blacklaunch.paleta.pack.LayoutCheck
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.pack.PALLET_L
import com.blacklaunch.paleta.pack.PALLET_W
import com.blacklaunch.paleta.pack.Placed
import com.blacklaunch.paleta.pack.sx
import com.blacklaunch.paleta.pack.sy
import com.blacklaunch.paleta.ui.theme.Oled
import kotlin.math.roundToInt

/** Поле довкола палети на малюнку (мм): видно звис і коробку, витягнуту за край. */
private const val MARGIN_MM = 70

/** Дошки настилу палети EUR (уздовж довгого боку): ширини, мм. */
private val BOARDS = intArrayOf(145, 100, 145, 100, 145)

/** Співвідношення сторін малюнка палети — однакове скрізь (редактор, картка в історії). */
const val PALLET_ASPECT = (PALLET_L + 2f * MARGIN_MM) / (PALLET_W + 2f * MARGIN_MM)

/**
 * Палета зверху в правильних пропорціях з коробками шару. [editable]: дотик — вибрати коробку, тягнути — пересунути
 * (прилипає до країв і сусідів), подвійний дотик — повернути. Коробки, що накладаються чи виступають більше, ніж
 * дозволено, — червоні. Усі кольори — з токенів теми ([Oled]), тож світлі теми потім міняють лише токени.
 */
@Composable
fun PalletView(
    layout: List<Placed>,
    box: BoxSize?,
    overhang: Int,
    check: LayoutCheck?,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    editable: Boolean = false,
    /** Маленька мініатюра (у списку історії): без підписів, тонші лінії. */
    thumbnail: Boolean = false,
    onSelect: (Int?) -> Unit = {},
    onMove: (index: Int, x: Int, y: Int) -> Unit = { _, _, _ -> },
    onRotate: (Int) -> Unit = {},
) {
    val haptics = LocalHaptics.current
    val measurer = rememberTextMeasurer()
    val sheet = LocalSheet.current
    val scale = remember { mutableFloatStateOf(1f) }
    val currentLayout by rememberUpdatedState(layout)
    val currentBox by rememberUpdatedState(box)
    val select by rememberUpdatedState(onSelect)
    val move by rememberUpdatedState(onMove)
    val rotate by rememberUpdatedState(onRotate)
    // Контури олівцем — один раз на розмір (а не щокадру, поки тягнеш).
    val pencilCache = remember { HashMap<Long, Path>() }
    val labelStyle = TextStyle(color = Oled.TextSecondary, fontSize = if (sheet) 12.sp else 10.sp)

    fun toMm(p: Offset): Offset = Offset(p.x / scale.floatValue - MARGIN_MM, p.y / scale.floatValue - MARGIN_MM)

    fun hit(p: Offset): Int? {
        val b = currentBox ?: return null
        val mm = toMm(p)
        // Зверху — остання намальована; трохи більша зона дотику (пальцем у вузьку коробку важко влучити).
        val pad = 6
        for (i in currentLayout.indices.reversed()) {
            val q = currentLayout[i]
            if (mm.x >= q.x - pad && mm.x <= q.x + q.sx(b) + pad && mm.y >= q.y - pad && mm.y <= q.y + q.sy(b) + pad) return i
        }
        return null
    }

    val gestures = if (!editable) Modifier else Modifier.pointerInput(Unit) {
        var lastTapAt = 0L
        var lastTapIndex = -1
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val index = hit(down.position)
            if (index == null) {
                // Дотик повз коробки (не прокрутка) — зняти вибір.
                if (waitForUpOrCancellation() != null) select(null)
                return@awaitEachGesture
            }
            haptics.tap()
            select(index)
            val start = currentLayout.getOrNull(index) ?: return@awaitEachGesture
            var acc = Offset.Zero
            val moved = awaitTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                acc += over
            }
            if (moved == null) {
                // Дотик без руху: другий швидкий дотик по тій самій — повернути.
                val now = down.uptimeMillis
                if (now - lastTapAt < 320 && lastTapIndex == index) {
                    rotate(index)
                    lastTapAt = 0L
                } else {
                    lastTapAt = now
                    lastTapIndex = index
                }
                return@awaitEachGesture
            }
            lastTapAt = 0L
            acc = moved.position - down.position
            fun emit() {
                val s = scale.floatValue
                move(index, (start.x + acc.x / s).roundToInt(), (start.y + acc.y / s).roundToInt())
            }
            emit()
            drag(moved.id) { change ->
                acc += change.positionChange()
                change.consume()
                emit()
            }
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(PALLET_ASPECT)
            .onSizeChanged { scale.floatValue = it.width / (PALLET_L + 2f * MARGIN_MM) }
            .then(gestures),
    ) {
        val s = size.width / (PALLET_L + 2f * MARGIN_MM)
        fun px(mm: Int): Float = (mm + MARGIN_MM) * s
        val glass = AppStyle.glass
        val line = if (thumbnail) 0.7f else 1f

        // --- Палета: дошки настилу ---
        val gap = (PALLET_W - BOARDS.sum()) / (BOARDS.size - 1f)
        var y = 0f
        BOARDS.forEachIndexed { i, w ->
            val top = px(0) + y * s
            val h = w * s
            if (glass) {
                drawRoundRect(
                    Brush.verticalGradient(0f to Color(0xFF181818), 1f to Color(0xFF0B0B0B), startY = top, endY = top + h),
                    topLeft = Offset(px(0), top), size = Size(PALLET_L * s, h), cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawRoundRect(
                    Brush.verticalGradient(0f to Color(0x40FFFFFF), 1f to Color(0x10FFFFFF), startY = top, endY = top + h),
                    topLeft = Offset(px(0), top), size = Size(PALLET_L * s, h), cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(1.dp.toPx() * line),
                )
            } else {
                outline(Offset(px(0), top), Size(PALLET_L * s, h), 3.dp.toPx(), 200 + i, SheetInk.copy(alpha = 0.28f), 1.6.dp.toPx() * line)
            }
            y += w + gap
        }

        // --- Межа звису ---
        if (overhang > 0) {
            val tl = Offset(px(-overhang), px(-overhang))
            val sz = Size((PALLET_L + 2 * overhang) * s, (PALLET_W + 2 * overhang) * s)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            drawRoundRect(Oled.TextSecondary.copy(alpha = 0.55f), tl, sz, CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx() * line, pathEffect = dash))
        }

        // --- Коробки ---
        val b = box ?: return@Canvas
        val bad = check?.bad.orEmpty()
        layout.forEachIndexed { i, p ->
            val tl = Offset(px(p.x), px(p.y))
            val inset = if (thumbnail) 0.5f else 1.2f
            val sz = Size(p.sx(b) * s - 2 * inset, p.sy(b) * s - 2 * inset)
            val at = tl + Offset(inset, inset)
            val isBad = i in bad
            val isSel = i == selected
            if (glass) drawGlassBox(at, sz, isSel, isBad, line) else drawPencilBox(at, sz, i, isSel, isBad, line, pencilCache)
            // Розмір коробки посередині, якщо вміщається.
            if (!thumbnail) {
                val text = "${Num.cm(p.sx(b))}×${Num.cm(p.sy(b))}"
                val m = measurer.measure(text, labelStyle)
                if (m.size.width < sz.width - 6.dp.toPx() && m.size.height < sz.height - 4.dp.toPx()) {
                    drawText(m, color = if (isBad) Oled.Danger else if (isSel) Oled.TextPrimary else Oled.TextSecondary, topLeft = at + Offset((sz.width - m.size.width) / 2, (sz.height - m.size.height) / 2))
                }
            }
        }
    }
}

/** Коробка «Скло»: чорне скло з кантом; вибрана — яскравіша; неправильна — червоний кант. */
private fun DrawScope.drawGlassBox(at: Offset, sz: Size, selected: Boolean, bad: Boolean, line: Float) {
    val r = CornerRadius(4.dp.toPx())
    drawRoundRect(
        Brush.verticalGradient(0f to Color(0xFF2A2A2A), 0.45f to Color(0xFF161616), 1f to Color(0xFF0E0E0E), startY = at.y, endY = at.y + sz.height),
        at, sz, r,
    )
    if (selected) drawRoundRect(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color.Transparent), center = at + Offset(sz.width / 2, sz.height / 2), radius = maxOf(sz.width, sz.height) / 1.4f), at, sz, r)
    if (bad) drawRoundRect(Oled.Danger.copy(alpha = 0.16f), at, sz, r)
    val edge: Brush = when {
        bad -> androidx.compose.ui.graphics.SolidColor(Oled.Danger)
        selected -> Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.3f to Color(0x80FFFFFF), 1f to Color(0x66FFFFFF), startY = at.y, endY = at.y + sz.height)
        else -> Brush.verticalGradient(0f to Color(0x80FFFFFF), 0.25f to Color(0x30FFFFFF), 0.8f to Color(0x18FFFFFF), 1f to Color(0x30FFFFFF), startY = at.y, endY = at.y + sz.height)
    }
    drawRoundRect(edge, at, sz, r, style = Stroke((if (selected || bad) 1.6.dp.toPx() else 1.dp.toPx()) * line))
}

/** Коробка «Аркуш»: рамка олівцем (кожна трохи інакша); вибрана — товща й заштрихована; неправильна — червоним. */
private fun DrawScope.drawPencilBox(at: Offset, sz: Size, index: Int, selected: Boolean, bad: Boolean, line: Float, cache: HashMap<Long, Path>) {
    val ink = if (bad) Oled.Danger else SheetInk
    val w = (if (selected) 2.6.dp.toPx() else 1.9.dp.toPx()) * line
    val seed = 31 + index * 7
    val key = (sz.width.roundToInt().toLong() shl 40) or (sz.height.roundToInt().toLong() shl 20) or ((seed.toLong() and 0xFFFF) shl 4) or (if (selected) 1L else 0L)
    val path = cache.getOrPut(key) {
        if (cache.size > 400) cache.clear()
        taperedPath(pencilLoop(sz, 3.dp.toPx(), seed, 1.dp.toPx()), w)
    }
    translate(at.x, at.y) {
        drawPath(path, ink.copy(alpha = if (selected || bad) 0.95f else 0.75f))
        if (selected || bad) {
            // Штрихування олівцем — видно, яка вибрана (чи неправильна).
            val n = 3
            for (k in 1..n) {
                val t = k / (n + 1f)
                val a = Offset(sz.width * (t - 0.18f).coerceAtLeast(0.08f), sz.height * 0.78f)
                val c = Offset(sz.width * (t + 0.18f).coerceAtMost(0.92f), sz.height * 0.22f)
                pencilTick(a, c, 1.3.dp.toPx(), ink.copy(alpha = 0.45f), seed + k)
            }
        }
    }
}
