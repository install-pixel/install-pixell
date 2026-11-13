package com.blacklaunch.diary.book

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blacklaunch.diary.data.InkLine
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.theme.Oled
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Чотири інструменти: олівець (вільне письмо), підкреслення (рівні лінії й фігури — лінія, рамка, овал), маркер
 * (напівпрозорий, з легким відтінком) і гумка (точна чи м'яка — з розмитим краєм).
 */
enum class InkTool(val kind: Int, val sizes: FloatArray) {
    Pencil(InkKind.PENCIL, floatArrayOf(0.0034f, 0.0054f, 0.0085f)),
    Underline(InkKind.UNDERLINE, floatArrayOf(0.0040f, 0.0064f, 0.0100f)),
    Marker(InkKind.MARKER, floatArrayOf(0.018f, 0.028f, 0.040f)),
    Eraser(InkKind.ERASER, floatArrayOf(0.012f, 0.026f, 0.050f)),
}

/** Вид штриха, що зберігається ([InkLine.kind]). 0…2 — давні (ручка, твердий і м'який олівці), лише показуються. */
object InkKind {
    const val PEN = 0
    const val HARD = 1
    const val SOFT = 2
    const val MARKER = 3
    const val PENCIL = 4
    const val UNDERLINE = 5
    const val ERASER = 10
    const val ERASER_SOFT = 11
}

/**
 * Вигляд лінії ([InkLine.style]). Для підкреслення — ще й фігура: `style + 4 × фігура` (0 лінія, 1 рамка, 2 овал);
 * для маркера — `100 + 10 × відтінок + прозорість`.
 */
object InkStyle {
    const val SOLID = 0
    const val DASHED = 1
    const val DOTTED = 2
    const val WAVY = 3
    val LINES = intArrayOf(SOLID, DASHED, WAVY)
}

/** Відтінки маркера: нейтральний (колір чорнила), легкий червоний, легкий синій. */
internal val MarkerTints = listOf(Color.Unspecified, Color(0xFFFF8A80), Color(0xFF82B1FF))
/** Прозорість маркера: ледь, помірно, насичено. */
internal val MarkerAlphas = floatArrayOf(0.16f, 0.28f, 0.42f)

/**
 * Режим малювання на сторінці. Олівець — окремо від пальця: палець ставиш будь-де, олівець іде на стільки, на скільки
 * провів пальцем (як курсор на тачпаді), тож кінчик ніколи не закритий. Затримав палець — висувається стрижень і пише;
 * відпустив — стрижень ховається. Подвійний дотик — стрижень лишається висунутим (пише одразу).
 * Вибір інструментів запам'ятовується між сеансами.
 */
@Stable
class InkSession(val day: Long, val part: Int) {
    var tool by mutableStateOf(last.tool)
    private val sizes = HashMap(last.sizes)
    var size: Int
        get() { sizeTick; return sizes[tool] ?: 1 }
        set(v) { sizes[tool] = v; last.sizes[tool] = v; sizeTick++ }
    var sizeTick by mutableIntStateOf(0)
    /** Олівець: вигляд лінії і згладжування (0 — немає, 1 — помірне, 2 — сильне). */
    var pencilStyle by mutableIntStateOf(last.pencilStyle)
    var smooth by mutableIntStateOf(last.smooth)
    /** Підкреслення: вигляд лінії і фігура (0 лінія, 1 рамка, 2 овал). */
    var lineStyle by mutableIntStateOf(last.lineStyle)
    var shape by mutableIntStateOf(last.shape)
    /** Маркер: прозорість (0…2) і відтінок (0 нейтральний, 1 червоний, 2 синій). */
    var markerAlpha by mutableIntStateOf(last.markerAlpha)
    var tint by mutableIntStateOf(last.tint)
    var softEraser by mutableStateOf(last.softEraser)
    /** Де кінчик олівця (пікселі сторінки); null — ще не ставили (стане посередині). */
    var pen by mutableStateOf<Offset?>(null)
    /** Стрижень висунутий (пише). */
    var lead by mutableStateOf(false)
    /** Подвійним дотиком зафіксовано: стрижень лишається висунутим. */
    var locked by mutableStateOf(false)
    internal var lastTapAt = 0L
    val undo = mutableStateListOf<List<InkLine>>()
    val redo = mutableStateListOf<List<InkLine>>()

    val width: Float get() = tool.sizes[size.coerceIn(0, 2)]
    val kind: Int get() = if (tool == InkTool.Eraser) (if (softEraser) InkKind.ERASER_SOFT else InkKind.ERASER) else tool.kind
    val style: Int get() = when (tool) {
        InkTool.Pencil -> pencilStyle
        InkTool.Underline -> lineStyle + 4 * shape
        InkTool.Marker -> 100 + 10 * tint + markerAlpha
        InkTool.Eraser -> InkStyle.SOLID
    }

    /** Запам'ятати вибір (для наступного сеансу). */
    fun remember() {
        last.tool = tool; last.pencilStyle = pencilStyle; last.smooth = smooth; last.lineStyle = lineStyle
        last.shape = shape; last.markerAlpha = markerAlpha; last.tint = tint; last.softEraser = softEraser
    }

    private object last {
        var tool = InkTool.Pencil
        val sizes = HashMap<InkTool, Int>()
        var pencilStyle = InkStyle.SOLID
        var smooth = 1
        var lineStyle = InkStyle.SOLID
        var shape = 0
        var markerAlpha = 1
        var tint = 0
        var softEraser = false
    }
}

private const val HOLD_MS = 260L

/**
 * Шар малювання над сторінкою: показує намальоване, а в режимі малювання ([session] не null) — олівець-курсор.
 * Гумка — теж штрих: стирає те, що під нею, не чіпаючи самої сторінки.
 *
 * Олівець не тремтить разом із пальцем: лінія тягнеться за кінчиком, як на нитці — поки палець ворушиться в межах
 * кількох десятих міліметра, лінія стоїть; рушив далі — вона плавно йде слідом (сила — «згладжування»).
 */
@Composable
fun InkLayer(
    lines: List<InkLine>,
    part: Int,
    pageWidth: Float,
    session: InkSession?,
    dry: (InkLine) -> Boolean,
    onChange: (List<InkLine>) -> Unit,
    onDryErase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val current by rememberUpdatedState(lines)
    val change by rememberUpdatedState(onChange)
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var liveFactors by remember { mutableStateOf<List<Float>>(emptyList()) }
    val active = session != null
    val leadAnim by animateFloatAsState(if (session?.lead == true || session?.locked == true) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "lead")
    Canvas(
        modifier
            .fillMaxSize()
            .then(
                if (!active) Modifier else Modifier.pointerInput(session, pageWidth) {
                    val s = session!!
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val w = pageWidth.coerceAtLeast(1f)
                        val h = size.height.toFloat()
                        fun clampPen(p: Offset) = Offset(p.x.coerceIn(0f, size.width.toFloat()), p.y.coerceIn(0f, h))
                        if (s.pen == null) s.pen = Offset(size.width * 0.5f, h * 0.45f)
                        val mm = density * 160f / 25.4f
                        val stepPx = Ink.STEP_MM * mm
                        val still = 4.dp.toPx()
                        val tool = s.tool
                        val shapes = tool == InkTool.Underline
                        // «Нитка»: на скільки кінчик може відійти від лінії, перш ніж потягне її за собою.
                        val string = mm * when (tool) {
                            InkTool.Pencil -> floatArrayOf(0.15f, 1.1f, 2.2f)[s.smooth.coerceIn(0, 2)]
                            InkTool.Marker -> 1.4f
                            InkTool.Eraser -> 0.5f
                            InkTool.Underline -> 0f
                        }
                        // Ще й усереднення сусідніх точок (півширина вікна в точках, крок 0,4 мм): дрібний зигзаг зникає.
                        val avg = when (tool) {
                            InkTool.Pencil -> intArrayOf(0, 3, 6)[s.smooth.coerceIn(0, 2)]
                            InkTool.Marker -> 3
                            else -> 0
                        }
                        fun shown(pts: List<Offset>): List<Offset> = if (avg == 0 || pts.size < 3) pts.toList() else List(pts.size) { i ->
                            // Вікно звужується до кінців — перша й остання точки лишаються на місці.
                            val k = minOf(avg, i, pts.size - 1 - i)
                            var sx = 0f
                            var sy = 0f
                            for (j in i - k..i + k) { sx += pts[j].x; sy += pts[j].y }
                            Offset(sx / (2 * k + 1), sy / (2 * k + 1))
                        }
                        var finger = down.position
                        var target = s.pen!!
                        var moved = 0f
                        var stillSince = down.uptimeMillis
                        var drawing = false
                        var anchor = target
                        val pts = ArrayList<Offset>()
                        val fs = ArrayList<Float>()
                        var brush = target
                        var factor = 1f
                        var lastTime = down.uptimeMillis
                        var lastEvent = down.uptimeMillis
                        fun begin() {
                            drawing = true
                            s.lead = true
                            brush = target
                            anchor = target
                            pts.clear(); fs.clear()
                            pts += brush; fs += 1f
                            if (shapes) { pts += brush; fs += 1f }
                            live = pts.toList(); liveFactors = fs.toList()
                        }
                        if (s.locked) begin()
                        while (true) {
                            // Чекаємо руху, але не довше, ніж до «затримки» (щоб висунути стрижень).
                            val nowT = android.os.SystemClock.uptimeMillis()
                            val wait = if (!drawing) (HOLD_MS - (nowT - stillSince)).coerceAtLeast(1L) else 10_000L
                            val ev = withTimeoutOrNull(wait) { awaitPointerEvent() }
                            if (ev == null) {
                                lastEvent = android.os.SystemClock.uptimeMillis()
                                if (!drawing) {
                                    // Затримав палець — клац, стрижень висувається: далі пише.
                                    haptics.keyDown()
                                    begin()
                                    stillSince = lastEvent
                                }
                                continue
                            }
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            lastEvent = c.uptimeMillis
                            if (!c.pressed) break
                            c.consume()
                            val d = c.position - finger
                            finger = c.position
                            val dist = d.getDistance()
                            if (dist > 0f) {
                                moved += dist
                                target = clampPen(target + d)
                            }
                            if (dist > still * 0.25f) stillSince = c.uptimeMillis
                            if (!drawing) { s.pen = target; continue }
                            if (shapes) {
                                // Підкреслення: від точки, де висунувся стрижень, до кінчика — рівно.
                                s.pen = target
                                pts[1] = if (s.shape == 0) snapLine(anchor, target) else target
                                live = pts.toList()
                                continue
                            }
                            // Лінія тягнеться за кінчиком на «нитці».
                            val gapV = target - brush
                            val gapLen = gapV.getDistance()
                            if (gapLen > string) brush += gapV * (1f - string / gapLen)
                            s.pen = brush
                            val step = hypot(brush.x - pts.last().x, brush.y - pts.last().y)
                            if (step >= stepPx) {
                                val dt = (c.uptimeMillis - lastTime).coerceAtLeast(1)
                                val speed = step / mm / dt
                                // Швидко — трохи тонше, повільно — трохи товще (як олівець, без різких стрибків).
                                val tf = 1.1f - 0.35f * (speed / Ink.FAST).coerceIn(0f, 1f)
                                factor += (tf - factor) * 0.25f
                                pts += brush; fs += factor
                                lastTime = c.uptimeMillis
                                live = shown(pts); liveFactors = fs.toList()
                            }
                        }
                        // Відпустив палець.
                        val tap = moved < 10.dp.toPx() && (lastEvent - down.uptimeMillis) < 230
                        if (tap) {
                            // Подвійний дотик — зафіксувати (чи відпустити) висунутий стрижень.
                            if (down.uptimeMillis - s.lastTapAt < 330) {
                                s.locked = !s.locked
                                s.lastTapAt = 0L
                                haptics.longPress()
                            } else s.lastTapAt = down.uptimeMillis
                            pts.clear()
                        }
                        live = emptyList(); liveFactors = emptyList()
                        if (!s.locked && s.lead) { s.lead = false; if (!tap) haptics.keyUp() }
                        if (s.locked) s.lead = true
                        val enough = if (shapes) pts.size == 2 && (pts[1] - pts[0]).getDistance() > mm * 2f else pts.size >= 2
                        if (enough) {
                            if (!shapes) { val sm = shown(pts); pts.clear(); pts.addAll(sm) }
                            val line = InkLine(
                                page = part,
                                xs = FloatArray(pts.size) { pts[it].x / w },
                                ys = FloatArray(pts.size) { pts[it].y / w },
                                factors = if (shapes) FloatArray(0) else FloatArray(fs.size) { fs[it] },
                                width = s.width,
                                time = System.currentTimeMillis(),
                                kind = s.kind,
                                style = s.style,
                            )
                            if (line.kind >= InkKind.ERASER) {
                                // Гумка над висохлим нічого не робить — підказати.
                                val hitDry = current.any { it.page == part && it.kind < InkKind.ERASER && dry(it) }
                                if (hitDry) onDryErase()
                            }
                            s.undo += current
                            s.redo.clear()
                            change(current + line)
                        }
                    }
                },
            ),
    ) {
        val w = size.width
        // Увесь малюнок — в окремому шарі: гумка стирає лише намальоване, а не папір під ним.
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        lines.forEach { l -> if (l.page == part) drawStroke(l.xs, l.ys, l.factors, l.width * w, w, dry(l), l.kind, l.style) }
        if (live.size >= 2 && session != null) {
            drawStroke(
                FloatArray(live.size) { live[it].x / w },
                FloatArray(live.size) { live[it].y / w },
                FloatArray(liveFactors.size) { liveFactors[it] },
                session.width * w,
                w,
                false,
                session.kind,
                session.style,
                live = true,
            )
        }
        drawContext.canvas.restore()
        // Олівець-курсор.
        if (session != null) {
            val p = session.pen ?: Offset(size.width * 0.5f, size.height * 0.45f)
            drawPencilCursor(p, session.tool, session.width * w, leadAnim, session.locked)
        }
    }
}

/** Майже горизонтальна лінія стає рівно горизонтальною (підкреслення тексту). */
private fun snapLine(a: Offset, b: Offset): Offset {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return if (kotlin.math.abs(dy) < kotlin.math.abs(dx) * 0.12f) Offset(b.x, a.y) else b
}

/** Штрих за видом і вигляд лінії. «Аркуш» — графіт, «Скло» — світло; висохлий — трохи тьмяніший. */
private fun DrawScope.drawStroke(xs: FloatArray, ys: FloatArray, factors: FloatArray, base: Float, w: Float, dry: Boolean, kind: Int, style: Int, live: Boolean = false) {
    val n = xs.size
    if (n < 2) return
    var px = FloatArray(n) { xs[it] * w }
    var py = FloatArray(n) { ys[it] * w }
    val glass = AppStyle.glass
    val ink = if (glass) (if (dry) DryInk else FreshInk) else (if (dry) SheetInk.copy(alpha = 0.8f) else SheetInk)
    if (kind >= InkKind.ERASER) {
        val path = polyline(px, py)
        if (kind == InkKind.ERASER_SOFT) {
            // М'яка гумка: посередині стирає начисто, а край розмитий — кілька ширших смуг, кожна слабша.
            for ((k, a) in listOf(1.35f to 0.16f, 1.12f to 0.28f, 0.9f to 0.45f, 0.65f to 1f)) {
                drawPath(path, Color.Black.copy(alpha = a), style = Stroke(base * k, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
            }
        } else {
            drawPath(path, Color.Black, style = Stroke(base, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
        }
        return
    }
    when (kind) {
        InkKind.PENCIL -> { pencilLine(px, py, factors.takeIf { it.size == n }, base, ink, glass, style, 0.14f); return }
        InkKind.UNDERLINE -> { drawFigure(Offset(px[0], py[0]), Offset(px[n - 1], py[n - 1]), base, ink, glass, style % 4, style / 4); return }
        InkKind.MARKER -> if (style >= 100) {
            val tint = MarkerTints.getOrNull((style - 100) / 10) ?: Color.Unspecified
            val a = MarkerAlphas[((style - 100) % 10).coerceIn(0, 2)] * (if (glass) 0.85f else 1f)
            val c = if (tint == Color.Unspecified) ink else tint
            drawPath(polyline(px, py), c.copy(alpha = a), style = Stroke(base, cap = StrokeCap.Round, join = StrokeJoin.Round))
            return
        }
    }
    // Давні штрихи (ручка, твердий і м'який олівці, давній маркер) — як були.
    if (style == InkStyle.WAVY) {
        val wv = wavy(px, py, maxOf(base * 1.3f, 2.4.dp.toPx()))
        px = wv.first; py = wv.second
    }
    if (style == InkStyle.DASHED || style == InkStyle.DOTTED) {
        val effect = if (style == InkStyle.DASHED) PathEffect.dashPathEffect(floatArrayOf(base * 3.5f + 4.dp.toPx(), base * 2.6f + 3.dp.toPx()))
        else PathEffect.dashPathEffect(floatArrayOf(0.01f, base * 2.6f + 3.dp.toPx()))
        val a = when (kind) { InkKind.MARKER -> 0.32f; InkKind.HARD -> 0.75f; InkKind.SOFT -> 0.85f; else -> 1f }
        drawPath(polyline(px, py), ink.copy(alpha = ink.alpha * a), style = Stroke(base, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
        return
    }
    when (kind) {
        InkKind.MARKER -> drawPath(polyline(px, py), ink.copy(alpha = ink.alpha * (if (glass) 0.24f else 0.3f)), style = Stroke(base, cap = StrokeCap.Square, join = StrokeJoin.Round))
        InkKind.HARD -> drawPath(variablePath(px, py, Ink.widths(px, py, null, base)), ink.copy(alpha = ink.alpha * 0.72f))
        InkKind.SOFT -> pencilBody(px, py, Ink.widths(px, py, factors.toList().takeIf { it.isNotEmpty() }, base), ink, false)
        else -> {
            val widths = Ink.widths(px, py, factors.toList().takeIf { it.isNotEmpty() }, base)
            if (glass) drawPath(variablePath(px, py, FloatArray(widths.size) { widths[it] * 2.6f }), Oled.TextPrimary.copy(alpha = 0.14f))
            drawPath(variablePath(px, py, widths), ink)
        }
    }
}

/**
 * Олівцева лінія: тонкий кінчик на початку, розходиться, наприкінці знову сходить нанівець (як у нотатниках).
 * Пунктир — окремі рисочки, кожна з гострими кінцями; хвиля — плавна, теж зі звуженням.
 */
private fun DrawScope.pencilLine(xs: FloatArray, ys: FloatArray, factors: FloatArray?, base: Float, ink: Color, glass: Boolean, style: Int, tip: Float) {
    var px = xs
    var py = ys
    var fs = factors
    if (style == InkStyle.WAVY) {
        // Фігури (гострі кінці) — дрібніша хвиля, щоб рамка чи овал не ставали «зірочкою».
        val wv = wavy(px, py, if (tip < 0.1f) maxOf(base * 0.8f, 1.6.dp.toPx()) else maxOf(base * 1.2f, 2.2.dp.toPx()))
        px = wv.first; py = wv.second; fs = null
    }
    if (style == InkStyle.DASHED || style == InkStyle.DOTTED) {
        val dash = base * 3.2f + 5.dp.toPx()
        val gap = base * 1.8f + 3.dp.toPx()
        for ((dx, dy) in dashes(px, py, dash, gap)) {
            val m = dx.size
            val d = arcLengths(dx, dy)
            val total = d[m - 1].coerceAtLeast(0.001f)
            pencilBody(dx, dy, FloatArray(m) { base * (0.12f + 0.88f * Math.pow(sin(PI * d[it] / total), 0.6).toFloat()) }, ink, glass)
        }
        return
    }
    if (px.size < 2) return
    pencilBody(px, py, taper(px, py, fs, base, tip), ink, glass)
}

/** Товщина вздовж лінії: звуження на обох кінцях (до 38 % довжини), множник швидкості — якщо є. */
private fun DrawScope.taper(xs: FloatArray, ys: FloatArray, factors: FloatArray?, base: Float, tip: Float): FloatArray {
    val n = xs.size
    val d = arcLengths(xs, ys)
    val total = d[n - 1]
    val len = minOf(maxOf(base * 6f, 14.dp.toPx()), total * 0.38f).coerceAtLeast(0.001f)
    return FloatArray(n) { i ->
        val f = factors?.getOrNull(i) ?: 1f
        val t = minOf(ease(d[i] / len), ease((total - d[i]) / len))
        base * f * (tip + (1f - tip) * t)
    }
}

private fun ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun arcLengths(xs: FloatArray, ys: FloatArray): FloatArray {
    val d = FloatArray(xs.size)
    for (i in 1 until xs.size) d[i] = d[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    return d
}

/** Тіло олівцевої лінії: «Аркуш» — графіт із зерном, «Скло» — світла лінія з ледь помітним сяйвом. */
private fun DrawScope.pencilBody(px: FloatArray, py: FloatArray, widths: FloatArray, ink: Color, glass: Boolean) {
    val n = px.size
    if (n < 2) return
    if (glass) {
        drawPath(variablePath(px, py, FloatArray(n) { widths[it] * 2.4f }), Oled.TextPrimary.copy(alpha = 0.1f))
        drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.92f))
        return
    }
    drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.62f))
    val o = (widths.maxOrNull() ?: 0f) * 0.16f
    drawPath(variablePath(FloatArray(n) { px[it] + o }, FloatArray(n) { py[it] - o * 0.6f }, FloatArray(n) { widths[it] * 0.45f }), ink.copy(alpha = ink.alpha * 0.32f))
    drawPath(variablePath(FloatArray(n) { px[it] - o * 0.7f }, FloatArray(n) { py[it] + o }, FloatArray(n) { widths[it] * 0.35f }), ink.copy(alpha = ink.alpha * 0.28f))
}

/** Рисочки пунктиру вздовж ламаної: кожна — свій масив точок. */
private fun dashes(xs: FloatArray, ys: FloatArray, dash: Float, gap: Float): List<Pair<FloatArray, FloatArray>> {
    val out = ArrayList<Pair<FloatArray, FloatArray>>()
    var cx = ArrayList<Float>()
    var cy = ArrayList<Float>()
    var on = true
    var left = dash
    cx += xs[0]; cy += ys[0]
    fun flush() { if (on && cx.size >= 2) out += cx.toFloatArray() to cy.toFloatArray() }
    for (i in 0 until xs.size - 1) {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val len = hypot(dx, dy)
        if (len < 0.0001f) continue
        var t = 0f
        while (len - t > left) {
            t += left
            val x = xs[i] + dx * t / len
            val y = ys[i] + dy * t / len
            cx += x; cy += y
            flush()
            cx = arrayListOf(x); cy = arrayListOf(y)
            on = !on
            left = if (on) dash else gap
        }
        left -= len - t
        cx += xs[i + 1]; cy += ys[i + 1]
    }
    flush()
    return out
}

/** Рівна ділянка [a]→[b] точками через ~2 пікселі (щоб звуження й пунктир лягли рівно), з виступом [over] на кінцях. */
private fun segment(a: Offset, b: Offset, over: Float): Pair<FloatArray, FloatArray> {
    val v = b - a
    val len = v.getDistance().coerceAtLeast(0.001f)
    val dir = v / len
    val s = a - dir * over
    val total = len + 2 * over
    val n = (total / 2f).toInt().coerceIn(2, 4000)
    return FloatArray(n) { s.x + dir.x * total * it / (n - 1) } to FloatArray(n) { s.y + dir.y * total * it / (n - 1) }
}

/** Підкреслення: лінія, рамка (чотири окремі риски з виступами на кутах, як від руки) чи овал. Кінці — гострі. */
private fun DrawScope.drawFigure(a: Offset, b: Offset, base: Float, ink: Color, glass: Boolean, style: Int, shape: Int) {
    when (shape) {
        1 -> {
            val over = base * 1.2f + 2.dp.toPx()
            val c = listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y))
            for (k in 0..3) {
                val (x, y) = segment(c[k], c[(k + 1) % 4], over)
                pencilLine(x, y, null, base, ink, glass, style, 0.08f)
            }
        }
        2 -> {
            val cx = (a.x + b.x) / 2
            val cy = (a.y + b.y) / 2
            val rx = kotlin.math.abs(b.x - a.x) / 2
            val ry = kotlin.math.abs(b.y - a.y) / 2
            val per = (2 * PI * kotlin.math.sqrt((rx * rx + ry * ry) / 2.0)).toFloat()
            val n = (per / 2f).toInt().coerceIn(24, 4000)
            // Трохи більше за повне коло — кінці заходять один на одного, як коли обводиш рукою.
            val sweep = 2 * PI.toFloat() + 0.28f
            val start = -1.9f
            val xs = FloatArray(n) { cx + rx * kotlin.math.cos(start + sweep * it / (n - 1)) }
            val ys = FloatArray(n) { cy + ry * sin(start + sweep * it / (n - 1)) }
            pencilLine(xs, ys, null, base, ink, glass, style, 0.08f)
        }
        else -> {
            val (x, y) = segment(a, b, 0f)
            pencilLine(x, y, null, base, ink, glass, style, 0.08f)
        }
    }
}

private fun polyline(xs: FloatArray, ys: FloatArray): Path = Path().apply {
    moveTo(xs[0], ys[0])
    for (i in 1 until xs.size) lineTo(xs[i], ys[i])
}

/** Хвиляста лінія вздовж штриха: відступ по нормалі за синусом пройденої відстані. */
private fun wavy(xs: FloatArray, ys: FloatArray, amp: Float): Pair<FloatArray, FloatArray> {
    val step = (amp * 0.35f).coerceAtLeast(1f)
    val ox = ArrayList<Float>()
    val oy = ArrayList<Float>()
    var dist = 0f
    val lambda = amp * 4.2f
    for (i in 0 until xs.size - 1) {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val len = hypot(dx, dy)
        if (len < 0.001f) continue
        val nx = -dy / len
        val ny = dx / len
        var t = 0f
        while (t < len) {
            val d = dist + t
            val off = amp * sin(2f * PI.toFloat() * d / lambda)
            ox += xs[i] + dx * (t / len) + nx * off
            oy += ys[i] + dy * (t / len) + ny * off
            t += step
        }
        dist += len
    }
    if (ox.size < 2) return xs to ys
    return ox.toFloatArray() to oy.toFloatArray()
}

/**
 * Курсор. Олівець (і підкреслення, і маркер) — тіло нахилене вгору-праворуч, кінчик точно там, де пише; стрижень
 * висувається ([lead] 0…1), захований — на місці кінчика тонке кільце-приціл. Гумка — брусок: стирає його нижній
 * край (на ширину гумки), і коли гумка «опущена» — край підсвічується.
 */
private fun DrawScope.drawPencilCursor(p: Offset, tool: InkTool, width: Float, lead: Float, locked: Boolean) {
    val glass = AppStyle.glass
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val body = if (glass) Color(0xFF2A2B30) else Color(0xFF15130F)
    if (tool == InkTool.Eraser) {
        val bw = maxOf(width, 12.dp.toPx())
        val bh = 34.dp.toPx()
        val rubber = 10.dp.toPx()
        rotate(30f, p) {
            val left = p.x - bw / 2
            val top = p.y - bh
            val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            drawRoundRect(body, Offset(left, top), androidx.compose.ui.geometry.Size(bw, bh), r)
            // Нижня частина — гумка; опущена (стирає) — світиться.
            drawRoundRect(ink.copy(alpha = 0.14f + 0.6f * lead), Offset(left, p.y - rubber), androidx.compose.ui.geometry.Size(bw, rubber), r)
            drawRoundRect(ink.copy(alpha = 0.85f), Offset(left, top), androidx.compose.ui.geometry.Size(bw, bh), r, style = Stroke(1.2.dp.toPx()))
            drawLine(ink.copy(alpha = 0.5f), Offset(left, p.y - rubber), Offset(left + bw, p.y - rubber), 1.dp.toPx())
            // Сам край, що стирає (на ширину гумки).
            drawLine(ink.copy(alpha = 0.35f + 0.65f * lead), Offset(p.x - width / 2, p.y), Offset(p.x + width / 2, p.y), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        return
    }
    val leadLen = 7.dp.toPx() * lead
    val coneLen = 9.dp.toPx()
    val bodyW = if (tool == InkTool.Marker) 13.dp.toPx() else 9.dp.toPx()
    val bodyLen = 34.dp.toPx()
    if (lead < 0.99f) drawCircle(ink.copy(alpha = 0.6f * (1f - lead)), maxOf(width / 2f, 2.dp.toPx()) + 2.dp.toPx(), p, style = Stroke(1.dp.toPx()))
    rotate(45f, p) {
        val leadTip = p
        val coneTip = Offset(p.x, p.y - leadLen)
        val coneBase = coneTip.y - coneLen
        if (leadLen > 0.5f) drawLine(ink, leadTip, coneTip, maxOf(width, 1.6.dp.toPx()).coerceAtMost(4.dp.toPx()), cap = StrokeCap.Round)
        val cone = Path().apply {
            moveTo(coneTip.x - 1.6.dp.toPx(), coneTip.y)
            lineTo(coneTip.x + 1.6.dp.toPx(), coneTip.y)
            lineTo(p.x + bodyW / 2, coneBase)
            lineTo(p.x - bodyW / 2, coneBase)
            close()
        }
        drawPath(cone, if (glass) Color(0xFF8C8F96) else Color(0xFF2A251D))
        drawPath(cone, ink.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()))
        val top = coneBase - bodyLen
        drawRect(body, Offset(p.x - bodyW / 2, top), androidx.compose.ui.geometry.Size(bodyW, bodyLen))
        drawRect(ink.copy(alpha = 0.8f), Offset(p.x - bodyW / 2, top), androidx.compose.ui.geometry.Size(bodyW, bodyLen), style = Stroke(1.1.dp.toPx()))
        drawLine(ink.copy(alpha = 0.3f), Offset(p.x, top + 2.dp.toPx()), Offset(p.x, coneBase - 2.dp.toPx()), 0.8.dp.toPx())
        val clickH = 6.dp.toPx()
        drawRect(if (locked) ink.copy(alpha = 0.85f) else body, Offset(p.x - bodyW * 0.32f, top - clickH), androidx.compose.ui.geometry.Size(bodyW * 0.64f, clickH))
        drawRect(ink.copy(alpha = 0.8f), Offset(p.x - bodyW * 0.32f, top - clickH), androidx.compose.ui.geometry.Size(bodyW * 0.64f, clickH), style = Stroke(1.dp.toPx()))
        val mark = when (tool) {
            InkTool.Underline -> ink.copy(alpha = 0.6f)
            InkTool.Marker -> ink.copy(alpha = 0.4f)
            else -> ink.copy(alpha = 0.3f)
        }
        drawRect(mark, Offset(p.x - bodyW / 2, top + bodyLen * 0.2f), androidx.compose.ui.geometry.Size(bodyW, 3.dp.toPx()))
    }
}

/** Контур лінії, що в кожній точці має свою товщину (кінці закруглені півколами). */
private fun variablePath(xs: FloatArray, ys: FloatArray, widths: FloatArray): Path {
    val n = xs.size
    val path = Path()
    val left = ArrayList<Offset>(n)
    val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val a = maxOf(0, i - 1)
        val b = minOf(n - 1, i + 1)
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val h = widths[i] / 2f
        left += Offset(xs[i] - dy / len * h, ys[i] + dx / len * h)
        right += Offset(xs[i] + dy / len * h, ys[i] - dx / len * h)
    }
    path.moveTo(left[0].x, left[0].y)
    for (i in 1 until n) path.lineTo(left[i].x, left[i].y)
    for (i in n - 1 downTo 0) path.lineTo(right[i].x, right[i].y)
    path.close()
    path.addOval(Rect(Offset(xs[0], ys[0]), widths[0] / 2f))
    path.addOval(Rect(Offset(xs[n - 1], ys[n - 1]), widths[n - 1] / 2f))
    return path
}
