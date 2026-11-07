package com.blacklaunch.paleta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacklaunch.paleta.pack.BoxSize
import com.blacklaunch.paleta.pack.LayoutCheck
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

/** На скільки градусів крутнути двома пальцями, щоб коробка повернулась. */
private const val TWIST_DEG = 40f

/** Співвідношення сторін малюнка палети — однакове скрізь (редактор, картка в історії). */
const val PALLET_ASPECT = (PALLET_L + 2f * MARGIN_MM) / (PALLET_W + 2f * MARGIN_MM)

/** Висота смуги «кошика» під палетою в редакторі (мм у масштабі малюнка). */
private const val TRAY_MM = 190

/** Співвідношення сторін у редакторі — з кошиком (і для 3D там же, щоб вікно не стрибало). */
const val PALLET_EDIT_ASPECT = (PALLET_L + 2f * MARGIN_MM) / (PALLET_W + 2f * MARGIN_MM + TRAY_MM)

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
    /** Кошик під палетою (редактор): скільки коробок відкладено; null — без кошика. */
    tray: Int? = null,
    trayLabel: String = "",
    /** Коробку перетягнули в кошик. */
    onPark: (Int) -> Unit = {},
    /** Дотик по кошику — повернути одну коробку на палету. */
    onUnpark: () -> Unit = {},
) {
    val haptics = LocalHaptics.current
    val scale = remember { mutableFloatStateOf(1f) }
    val currentLayout by rememberUpdatedState(layout)
    val currentBox by rememberUpdatedState(box)
    val select by rememberUpdatedState(onSelect)
    val move by rememberUpdatedState(onMove)
    val rotate by rememberUpdatedState(onRotate)
    // Контури олівцем — один раз на розмір (а не щокадру, поки тягнеш).
    val pencilCache = remember { HashMap<Long, Path>() }

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

    val currentSelected by rememberUpdatedState(selected)
    val park by rememberUpdatedState(onPark)
    val unpark by rememberUpdatedState(onUnpark)
    val hasTray = tray != null
    // Коробку тягнуть над кошиком — кошик підсвічується.
    val overTray = remember { androidx.compose.runtime.mutableStateOf(false) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val trayStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
    /** Чи точка (px) — у смузі кошика. */
    fun inTray(p: Offset): Boolean {
        if (!hasTray) return false
        val k = scale.floatValue
        return p.y > (PALLET_W + 2 * MARGIN_MM) * k
    }
    // Поки крутять двома пальцями — коробка видимо повертається за пальцями (кут, яка коробка).
    val twistAngle = remember { mutableFloatStateOf(0f) }
    val twistIndex = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val gestures = if (!editable) Modifier else Modifier.pointerInput(Unit) {
        var lastTapAt = 0L
        var lastTapIndex = -1
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val index = hit(down.position)
            if (index != null) {
                haptics.tap()
                select(index)
                down.consume()
            }
            // Яку коробку тягнемо і звідки (після повороту двома пальцями — та, яку крутили, з нового місця).
            var dragIndex = index
            var start = index?.let { currentLayout.getOrNull(it) }
            val slop = viewConfiguration.touchSlop
            var acc = Offset.Zero
            var dragging = false
            var twoFingers = false
            var twist = 0f
            var handedOff = false
            fun emit() {
                val i = dragIndex ?: return
                val st = start ?: return
                val k = scale.floatValue
                move(i, (st.x + acc.x / k).roundToInt(), (st.y + acc.y / k).roundToInt())
            }
            var lastTarget: Int? = null
            // Щойно крутили двома пальцями — коли лишиться один палець, почати тягнути заново з нового місця.
            var afterTwist = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    // Два пальці — крутнути: кожні ~90° повороту пальцями повертають коробку на 90°.
                    // Яку крутити: під першим пальцем, під другим або вибрану.
                    val target = index ?: pressed.firstNotNullOfOrNull { hit(it.position) } ?: currentSelected
                    if (target == null) { handedOff = true; break }
                    if (!twoFingers) select(target)
                    twoFingers = true
                    lastTarget = target
                    afterTwist = true
                    twist += event.calculateRotation()
                    if (kotlin.math.abs(twist) >= TWIST_DEG) {
                        haptics.tick()
                        rotate(target)
                        twist -= kotlin.math.sign(twist) * 90f
                    }
                    twistIndex.intValue = target
                    twistAngle.floatValue = twist
                    event.changes.forEach { it.consume() }
                    continue
                }
                val c = pressed.first()
                if (afterTwist) {
                    afterTwist = false
                    // Після повороту один палець лишився — далі тягнемо цю коробку з того місця, де вона тепер
                    // (без стрибка: рахуємо рух від цієї миті).
                    dragIndex = lastTarget
                    start = lastTarget?.let { currentLayout.getOrNull(it) }
                    acc = Offset.Zero
                    dragging = true
                    twistAngle.floatValue = 0f
                    twistIndex.intValue = -1
                    c.consume()
                    continue
                }
                if (dragIndex == null) {
                    // Повз коробки: рух — це прокрутка сторінки, віддаємо її.
                    if ((c.position - down.position).getDistance() > slop) { handedOff = true; break }
                    continue
                }
                acc += c.positionChange()
                c.consume()
                if (!dragging && acc.getDistance() > slop) dragging = true
                if (dragging) {
                    val over = inTray(c.position)
                    if (over != overTray.value) { overTray.value = over; if (over) haptics.tick() }
                    if (!over) emit()
                }
            }
            twistAngle.floatValue = 0f
            twistIndex.intValue = -1
            val dropped = overTray.value
            overTray.value = false
            if (dragging && dropped && dragIndex != null) {
                // Відпустили над кошиком — коробку відкладено.
                park(dragIndex!!)
                lastTapAt = 0L
                return@awaitEachGesture
            }
            if (twoFingers || dragging) {
                lastTapAt = 0L
                return@awaitEachGesture
            }
            if (index == null) {
                // Дотик по кошику — повернути коробку; повз коробки (не прокрутка) — зняти вибір.
                if (!handedOff) {
                    if (inTray(down.position)) { haptics.tap(); unpark() } else select(null)
                }
                return@awaitEachGesture
            }
            // Дотик без руху: другий швидкий дотик по тій самій — повернути.
            val now = down.uptimeMillis
            if (now - lastTapAt < 320 && lastTapIndex == index) {
                rotate(index)
                lastTapAt = 0L
            } else {
                lastTapAt = now
                lastTapIndex = index
            }
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (hasTray) PALLET_EDIT_ASPECT else PALLET_ASPECT)
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

        // --- Кошик під палетою ---
        if (tray != null) {
            val top = (PALLET_W + 2 * MARGIN_MM) * s + 4.dp.toPx()
            val tl = Offset(px(-MARGIN_MM) + 6.dp.toPx(), top)
            val sz = Size(size.width - 12.dp.toPx(), size.height - top - 4.dp.toPx())
            val hot = overTray.value
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            val ink = if (hot) Oled.TextPrimary else Oled.TextSecondary.copy(alpha = if (tray > 0) 0.9f else 0.5f)
            if (glass) {
                if (hot) drawRoundRect(Color(0x1AFFFFFF), tl, sz, CornerRadius(12.dp.toPx()))
                drawRoundRect(ink, tl, sz, CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = dash))
            } else {
                outline(tl, sz, 12.dp.toPx(), 303, ink.copy(alpha = if (hot) 0.95f else 0.55f), (if (hot) 2.2f else 1.5f).dp.toPx())
            }
            val m = measurer.measure(trayLabel, trayStyle.copy(color = ink))
            drawText(m, topLeft = Offset(tl.x + (sz.width - m.size.width) / 2, tl.y + (sz.height - m.size.height) / 2))
        }

        // --- Коробки ---
        val b = box ?: return@Canvas
        val bad = check?.bad.orEmpty()
        // Вибрана — останньою, поверх інших (видно, на що вона налазить).
        val order = layout.indices.sortedBy { if (it == selected) 1 else 0 }
        order.forEach { i ->
            val p = layout[i]
            val tl = Offset(px(p.x), px(p.y))
            val inset = if (thumbnail) 0.5f else 1.2f
            val sz = Size(p.sx(b) * s - 2 * inset, p.sy(b) * s - 2 * inset)
            val at = tl + Offset(inset, inset)
            val isBad = i in bad
            val isSel = i == selected
            val angle = if (i == twistIndex.intValue) twistAngle.floatValue else 0f
            rotate(angle, pivot = at + Offset(sz.width / 2, sz.height / 2)) {
                if (glass) drawGlassBox(at, sz, isSel, isBad, line) else drawPencilBox(at, sz, i, isSel, isBad, line, pencilCache)
                // Вибрана серед червоних — ще й яскравий світлий кант довкола: видно, яку тягнеш.
                if (isSel && isBad) {
                    val g = 2.5.dp.toPx()
                    if (glass) {
                        drawRoundRect(Color.White, at - Offset(g, g), Size(sz.width + 2 * g, sz.height + 2 * g), CornerRadius(6.dp.toPx()), style = Stroke(1.8.dp.toPx()))
                    } else {
                        outline(at - Offset(g, g), Size(sz.width + 2 * g, sz.height + 2 * g), 5.dp.toPx(), 404 + i, SelectedInk, 2.4.dp.toPx())
                    }
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
    // Коробка непрозора: усередині — чорне тло (дошки й сусідні коробки під нею не просвічують).
    drawRoundRect(Oled.Black, at, sz, CornerRadius(3.dp.toPx()))
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
