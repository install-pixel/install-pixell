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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * П'ять інструментів: олівець (вільне письмо), пензлик (мазок щетинками чи суцільний), підкреслення (рівні лінії й
 * фігури — лінія, рамка, овал), маркер (напівпрозорий, з легким відтінком) і гумка (точна чи м'яка — з розмитим краєм;
 * стирає шарами: спершу верхній штрих, пройшов там ще раз — усе).
 */
enum class InkTool(val kind: Int, val sizes: FloatArray) {
    Pencil(InkKind.PENCIL, floatArrayOf(0.0034f, 0.0054f, 0.0085f)),
    Brush(InkKind.BRUSH, floatArrayOf(0.012f, 0.022f, 0.036f)),
    Underline(InkKind.UNDERLINE, floatArrayOf(0.0040f, 0.0064f, 0.0100f)),
    Marker(InkKind.MARKER, floatArrayOf(0.018f, 0.028f, 0.040f)),
    Eraser(InkKind.ERASER, floatArrayOf(0.016f, 0.030f, 0.055f)),
}

/** Вид штриха, що зберігається ([InkLine.kind]). 0…2 — давні (ручка, твердий і м'який олівці), лише показуються. */
object InkKind {
    const val PEN = 0
    const val HARD = 1
    const val SOFT = 2
    const val MARKER = 3
    const val PENCIL = 4
    const val UNDERLINE = 5
    const val BRUSH = 6
    const val ERASER = 10
    const val ERASER_SOFT = 11
}

/**
 * Вигляд лінії ([InkLine.style]):
 *  - олівець: `лінія + 10 × прозорість`;
 *  - пензлик: `мазок (0 щетинки, 1 суцільний) + 10 × прозорість`;
 *  - підкреслення: `лінія + 4 × фігура (0 лінія, 1 рамка, 2 овал) + 100 × прозорість`;
 *  - маркер: `100 + 10 × відтінок + прозорість`.
 */
object InkStyle {
    const val SOLID = 0
    const val DASHED = 1
    const val DOTTED = 2
    const val WAVY = 3
    val LINES = intArrayOf(SOLID, DASHED, WAVY)
}

/** Прозорість олівця, пензлика, підкреслення: повна, легша, ледь (щоб і по тексту читалося). */
internal val InkAlphas = floatArrayOf(1f, 0.6f, 0.32f)
/** Відтінки маркера: нейтральний (колір чорнила), легкий червоний, легкий синій. */
internal val MarkerTints = listOf(Color.Unspecified, Color(0xFFFF8A80), Color(0xFF82B1FF))
/** Прозорість маркера: ледь, помірно, насичено. */
internal val MarkerAlphas = floatArrayOf(0.16f, 0.28f, 0.42f)

/**
 * Режим малювання на сторінці. Олівець — окремо від пальця: палець ставиш будь-де, олівець іде на стільки, на скільки
 * провів пальцем (як курсор на тачпаді), тож кінчик ніколи не закритий. Затримав палець — клац, висувається стрижень і
 * пише; відпустив — стрижень ховається. Подвійний дотик — стрижень лишається висунутим (пише одразу).
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
    /** Олівець: вигляд лінії, прозорість і згладжування (0 — немає, 1 — помірне, 2 — сильне). */
    var pencilStyle by mutableIntStateOf(last.pencilStyle)
    var pencilAlpha by mutableIntStateOf(last.pencilAlpha)
    var smooth by mutableIntStateOf(last.smooth)
    /** Пензлик: мазок (0 щетинки, 1 суцільний) і прозорість. */
    var brushMode by mutableIntStateOf(last.brushMode)
    var brushAlpha by mutableIntStateOf(last.brushAlpha)
    /** Підкреслення: вигляд лінії, фігура (0 лінія, 1 рамка, 2 овал) і прозорість. */
    var lineStyle by mutableIntStateOf(last.lineStyle)
    var shape by mutableIntStateOf(last.shape)
    var lineAlpha by mutableIntStateOf(last.lineAlpha)
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
        InkTool.Pencil -> pencilStyle + 10 * pencilAlpha
        InkTool.Brush -> brushMode + 10 * brushAlpha
        InkTool.Underline -> lineStyle + 4 * shape + 100 * lineAlpha
        InkTool.Marker -> 100 + 10 * tint + markerAlpha
        InkTool.Eraser -> InkStyle.SOLID
    }

    /** Запам'ятати вибір (для наступного сеансу). */
    fun remember() {
        last.tool = tool; last.pencilStyle = pencilStyle; last.pencilAlpha = pencilAlpha; last.smooth = smooth
        last.brushMode = brushMode; last.brushAlpha = brushAlpha
        last.lineStyle = lineStyle; last.shape = shape; last.lineAlpha = lineAlpha
        last.markerAlpha = markerAlpha; last.tint = tint; last.softEraser = softEraser
    }

    private object last {
        var tool = InkTool.Pencil
        val sizes = HashMap<InkTool, Int>()
        var pencilStyle = InkStyle.SOLID
        var pencilAlpha = 0
        var smooth = 1
        var brushMode = 0
        var brushAlpha = 0
        var lineStyle = InkStyle.SOLID
        var shape = 0
        var lineAlpha = 0
        var markerAlpha = 1
        var tint = 0
        var softEraser = false
    }
}

private const val HOLD_MS = 260L

/**
 * Шар малювання над сторінкою: показує намальоване, а в режимі малювання ([session] не null) — інструмент-курсор.
 * Гумка — теж штрих: стирає те, що під нею, не чіпаючи самої сторінки. Шарами: перший прохід — лише верхній штрих там,
 * де торкнулась; прохід там, де вже стирала, — усе під собою.
 *
 * Олівець не тремтить разом із пальцем: лінія тягнеться за кінчиком, як на нитці, а сусідні точки ще й усереднюються.
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
    // Гумка, поки стирає: -1 — ще нічого не зачепила, 0 — стирає все, інакше — time штриха, який стирає.
    var liveTarget by remember { mutableLongStateOf(-1L) }
    val active = session != null
    val leadAnim by animateFloatAsState(if (session?.lead == true || session?.locked == true) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 700f), label = "lead")
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
                            InkTool.Brush -> 1.2f
                            InkTool.Marker -> 1.4f
                            InkTool.Eraser -> 0.5f
                            InkTool.Underline -> 0f
                        }
                        // Ще й усереднення сусідніх точок (півширина вікна в точках, крок 0,4 мм): дрібний зигзаг зникає.
                        val avg = when (tool) {
                            InkTool.Pencil -> intArrayOf(0, 3, 6)[s.smooth.coerceIn(0, 2)]
                            InkTool.Brush, InkTool.Marker -> 3
                            else -> 0
                        }
                        fun shown(pts: List<Offset>): List<Offset> = if (avg == 0 || pts.size < 3) pts.toList() else List(pts.size) { i ->
                            val k = minOf(avg, i, pts.size - 1 - i)
                            var sx = 0f
                            var sy = 0f
                            for (j in i - k..i + k) { sx += pts[j].x; sy += pts[j].y }
                            Offset(sx / (2 * k + 1), sy / (2 * k + 1))
                        }
                        val eraser = tool == InkTool.Eraser
                        val eraseR = s.width / 2f * (if (s.softEraser) 0.9f else 1f)
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
                        fun decide(p: Offset) {
                            // Гумка вирішує при першому дотику до намальованого: стерти верхній штрих чи (якщо тут уже
                            // стирала) усе.
                            if (!eraser || liveTarget >= 0L) return
                            val q = Offset(p.x / w, p.y / w)
                            val pageLines = current.filter { it.page == part }
                            val top = topAt(pageLines, q, eraseR) ?: return
                            liveTarget = if (coveredByTargeted(pageLines, q)) 0L else top.time
                        }
                        fun begin() {
                            drawing = true
                            s.lead = true
                            brush = target
                            anchor = target
                            liveTarget = -1L
                            pts.clear(); fs.clear()
                            pts += brush; fs += 1f
                            if (shapes) { pts += brush; fs += 1f }
                            decide(brush)
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
                            val gapV = target - brush
                            val gapLen = gapV.getDistance()
                            if (gapLen > string) brush += gapV * (1f - string / gapLen)
                            s.pen = brush
                            val step = hypot(brush.x - pts.last().x, brush.y - pts.last().y)
                            if (step >= stepPx) {
                                val dt = (c.uptimeMillis - lastTime).coerceAtLeast(1)
                                val speed = step / mm / dt
                                val tf = 1.1f - 0.35f * (speed / Ink.FAST).coerceIn(0f, 1f)
                                factor += (tf - factor) * 0.25f
                                pts += brush; fs += factor
                                lastTime = c.uptimeMillis
                                decide(brush)
                                live = shown(pts); liveFactors = fs.toList()
                            }
                        }
                        // Відпустив палець.
                        val tap = moved < 10.dp.toPx() && (lastEvent - down.uptimeMillis) < 230
                        if (tap) {
                            if (down.uptimeMillis - s.lastTapAt < 330) {
                                s.locked = !s.locked
                                s.lastTapAt = 0L
                                haptics.longPress()
                            } else s.lastTapAt = down.uptimeMillis
                            pts.clear()
                        }
                        val erTarget = liveTarget
                        live = emptyList(); liveFactors = emptyList(); liveTarget = -1L
                        if (!s.locked && s.lead) { s.lead = false; if (!tap) haptics.keyUp() }
                        if (s.locked) s.lead = true
                        val enough = when {
                            shapes -> pts.size == 2 && (pts[1] - pts[0]).getDistance() > mm * 2f
                            eraser -> pts.size >= 2 && erTarget >= 0L
                            else -> pts.size >= 2
                        }
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
                                target = if (eraser) erTarget else 0L,
                            )
                            if (line.kind >= InkKind.ERASER) {
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
        val liveEraser = session != null && session.tool == InkTool.Eraser
        // Гумки, що стирають лише один штрих, — до свого штриха.
        val targeted = HashMap<Long, MutableList<InkLine>>()
        lines.forEach { if (it.page == part && it.kind >= InkKind.ERASER && it.target != 0L) targeted.getOrPut(it.target) { ArrayList() } += it }
        fun drawLive() {
            if (live.size < 2 || session == null) return
            drawStroke(
                FloatArray(live.size) { live[it].x / w },
                FloatArray(live.size) { live[it].y / w },
                FloatArray(liveFactors.size) { liveFactors[it] },
                session.width * w, w, false, session.kind, session.style,
            )
        }
        // Увесь малюнок — в окремому шарі: гумка стирає лише намальоване, а не папір під ним.
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        lines.forEach { l ->
            if (l.page != part) return@forEach
            if (l.kind >= InkKind.ERASER) {
                if (l.target == 0L) drawStroke(l.xs, l.ys, l.factors, l.width * w, w, false, l.kind, l.style)
                return@forEach
            }
            val ts = targeted[l.time]
            val liveHere = liveEraser && liveTarget == l.time && liveTarget > 0L
            if (ts == null && !liveHere) {
                drawStroke(l.xs, l.ys, l.factors, l.width * w, w, dry(l), l.kind, l.style)
            } else {
                drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
                drawStroke(l.xs, l.ys, l.factors, l.width * w, w, dry(l), l.kind, l.style)
                ts?.forEach { e -> drawStroke(e.xs, e.ys, e.factors, e.width * w, w, false, e.kind, e.style) }
                if (liveHere) drawLive()
                drawContext.canvas.restore()
            }
        }
        if (!liveEraser || liveTarget == 0L) drawLive()
        drawContext.canvas.restore()
        // Інструмент-курсор.
        if (session != null) {
            val p = session.pen ?: Offset(size.width * 0.5f, size.height * 0.45f)
            drawToolCursor(p, session, session.width * w, leadAnim)
        }
    }
}

/** Майже горизонтальна лінія стає рівно горизонтальною (підкреслення тексту). */
private fun snapLine(a: Offset, b: Offset): Offset {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return if (kotlin.math.abs(dy) < kotlin.math.abs(dx) * 0.12f) Offset(b.x, a.y) else b
}

// ---------- Пошук штриха під гумкою (усе — у частках ширини сторінки) ----------

/** Відстань від точки до ламаної. */
private fun distTo(xs: FloatArray, ys: FloatArray, q: Offset): Float {
    if (xs.size == 1) return hypot(xs[0] - q.x, ys[0] - q.y)
    var best = Float.MAX_VALUE
    for (i in 0 until xs.size - 1) {
        val ax = xs[i]; val ay = ys[i]
        val dx = xs[i + 1] - ax; val dy = ys[i + 1] - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-12f) 0f else (((q.x - ax) * dx + (q.y - ay) * dy) / l2).coerceIn(0f, 1f)
        val d = hypot(ax + dx * t - q.x, ay + dy * t - q.y)
        if (d < best) best = d
    }
    return best
}

/** Ламана, якою штрих лежить на сторінці (підкреслення — уся фігура, а не дві точки). */
private fun shapeOf(l: InkLine): Pair<FloatArray, FloatArray> {
    if (l.kind != InkKind.UNDERLINE || l.xs.size < 2) return l.xs to l.ys
    val a = Offset(l.xs[0], l.ys[0])
    val b = Offset(l.xs[l.xs.size - 1], l.ys[l.ys.size - 1])
    return when ((l.style % 100) / 4) {
        1 -> roundRectPoints(a, b, l.width * 2f, 0.004f)
        2 -> ovalPoints(a, b, 0.004f, 0f)
        else -> l.xs to l.ys
    }
}

/** Чи видно штрих [l] у точці [q]: близько до нього й тут його ще не стерла жодна пізніша гумка. */
private fun visibleAt(lines: List<InkLine>, idx: Int, q: Offset, r: Float): Boolean {
    val l = lines[idx]
    val (xs, ys) = shapeOf(l)
    if (distTo(xs, ys, q) > l.width / 2f + r * 0.6f) return false
    for (j in idx + 1 until lines.size) {
        val e = lines[j]
        if (e.kind < InkKind.ERASER) continue
        if (e.target != 0L && e.target != l.time) continue
        if (distTo(e.xs, e.ys, q) < e.width / 2f * 0.8f) return false
    }
    return true
}

/** Верхній (останній намальований) видимий штрих у точці [q]. */
private fun topAt(lines: List<InkLine>, q: Offset, r: Float): InkLine? {
    for (i in lines.indices.reversed()) {
        if (lines[i].kind >= InkKind.ERASER) continue
        if (visibleAt(lines, i, q, r)) return lines[i]
    }
    return null
}

/** Чи тут уже проходила гумка, що стирала один шар (тоді другий прохід стирає все). */
private fun coveredByTargeted(lines: List<InkLine>, q: Offset): Boolean =
    lines.any { it.kind >= InkKind.ERASER && it.target != 0L && distTo(it.xs, it.ys, q) < it.width / 2f }

// ---------- Малювання штрихів ----------

/** Штрих за видом і вигляд лінії. «Аркуш» — графіт, «Скло» — світло; висохлий — трохи тьмяніший. */
private fun DrawScope.drawStroke(xs: FloatArray, ys: FloatArray, factors: FloatArray, base: Float, w: Float, dry: Boolean, kind: Int, style: Int) {
    val n = xs.size
    if (n < 2) return
    var px = FloatArray(n) { xs[it] * w }
    var py = FloatArray(n) { ys[it] * w }
    val glass = AppStyle.glass
    val ink0 = if (glass) (if (dry) DryInk else FreshInk) else (if (dry) SheetInk.copy(alpha = 0.8f) else SheetInk)
    if (kind >= InkKind.ERASER) {
        val path = polyline(px, py)
        if (kind == InkKind.ERASER_SOFT) {
            // М'яка гумка: посередині стирає начисто, край розмитий — кілька смуг, кожна ширша й слабша (не ширше бруска).
            for ((k, a) in listOf(1f to 0.16f, 0.86f to 0.3f, 0.72f to 0.5f, 0.55f to 1f)) {
                drawPath(path, Color.Black.copy(alpha = a), style = Stroke(base * k, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
            }
        } else {
            drawPath(path, Color.Black, style = Stroke(base, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
        }
        return
    }
    when (kind) {
        InkKind.PENCIL -> {
            val ink = ink0.copy(alpha = ink0.alpha * InkAlphas[(style / 10).coerceIn(0, 2)])
            pencilLine(px, py, factors.takeIf { it.size == n }, base, ink, glass, style % 10, 0.14f, false)
            return
        }
        InkKind.BRUSH -> {
            val ink = ink0.copy(alpha = ink0.alpha * InkAlphas[(style / 10).coerceIn(0, 2)])
            brushLine(px, py, factors.takeIf { it.size == n }, base, ink, glass, style % 10, xs[0])
            return
        }
        InkKind.UNDERLINE -> {
            val ink = ink0.copy(alpha = ink0.alpha * InkAlphas[(style / 100).coerceIn(0, 2)])
            drawFigure(Offset(px[0], py[0]), Offset(px[n - 1], py[n - 1]), base, ink, glass, style % 4, (style % 100) / 4)
            return
        }
        InkKind.MARKER -> if (style >= 100) {
            val tint = MarkerTints.getOrNull((style - 100) / 10) ?: Color.Unspecified
            val a = MarkerAlphas[((style - 100) % 10).coerceIn(0, 2)] * (if (glass) 0.85f else 1f)
            val c = if (tint == Color.Unspecified) ink0 else tint
            drawPath(polyline(px, py), c.copy(alpha = a), style = Stroke(base, cap = StrokeCap.Round, join = StrokeJoin.Round))
            return
        }
    }
    // Давні штрихи (ручка, твердий і м'який олівці, давній маркер) — як були.
    val ink = ink0
    if (style == InkStyle.WAVY) {
        val wv = wavy(px, py, maxOf(base * 1.3f, 2.4.dp.toPx()), false)
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
 * Олівцева лінія: тонкий кінчик на початку, розходиться, наприкінці знову сходить нанівець. Пунктир — окремі рисочки
 * з гострими кінцями, рівно розкладені на всю довжину; хвиля — плавна. [closed] — замкнена фігура (рамка, овал).
 */
private fun DrawScope.pencilLine(xs: FloatArray, ys: FloatArray, factors: FloatArray?, base: Float, ink: Color, glass: Boolean, style: Int, tip: Float, closed: Boolean) {
    var px = xs
    var py = ys
    var fs = factors
    if (px.size < 2) return
    if (style == InkStyle.WAVY) {
        val wv = wavy(px, py, if (tip < 0.1f) maxOf(base * 0.8f, 1.6.dp.toPx()) else maxOf(base * 1.2f, 2.2.dp.toPx()), closed)
        px = wv.first; py = wv.second; fs = null
        if (closed) {
            // Замкнена хвиля — рівне кільце без кінців.
            pencilBody(px, py, FloatArray(px.size) { base }, ink, glass)
            return
        }
    }
    if (style == InkStyle.DASHED || style == InkStyle.DOTTED) {
        val total = arcLengths(px, py).last()
        var dash = base * 3.2f + 5.dp.toPx()
        var gap = base * 1.8f + 3.dp.toPx()
        // Розкласти рисочки рівно: відкрита лінія починається й закінчується рисочкою, замкнена — без зайвого проміжку.
        val period = dash + gap
        val count = if (closed) kotlin.math.round(total / period).coerceAtLeast(2f) else kotlin.math.round((total + gap) / period).coerceAtLeast(1f)
        val k = (if (closed) total / count else (total + gap) / count) / period
        dash *= k; gap *= k
        for ((dx, dy) in dashes(px, py, dash, gap)) {
            val m = dx.size
            if (m < 2) continue
            val d = arcLengths(dx, dy)
            val len = d[m - 1].coerceAtLeast(0.001f)
            pencilBody(dx, dy, FloatArray(m) {
                val sn = sin(PI * (d[it] / len).coerceIn(0f, 1f)).toFloat().coerceIn(0f, 1f)
                base * (0.12f + 0.88f * Math.pow(sn.toDouble(), 0.6).toFloat())
            }, ink, glass)
        }
        return
    }
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
        drawPath(variablePath(px, py, FloatArray(n) { widths[it] * 2.4f }), Oled.TextPrimary.copy(alpha = 0.1f * ink.alpha))
        drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.92f))
        return
    }
    drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.62f))
    val o = (widths.maxOrNull() ?: 0f) * 0.16f
    drawPath(variablePath(FloatArray(n) { px[it] + o }, FloatArray(n) { py[it] - o * 0.6f }, FloatArray(n) { widths[it] * 0.45f }), ink.copy(alpha = ink.alpha * 0.32f))
    drawPath(variablePath(FloatArray(n) { px[it] - o * 0.7f }, FloatArray(n) { py[it] + o }, FloatArray(n) { widths[it] * 0.35f }), ink.copy(alpha = ink.alpha * 0.28f))
}

/**
 * Мазок пензлика. Щетинки — кілька тонких ліній поруч (кожна своєї сили), що на кінцях сходяться до кінчика, і ледь
 * помітна основа між ними; суцільний — рівна заливка з м'якими краями.
 */
private fun DrawScope.brushLine(px: FloatArray, py: FloatArray, factors: FloatArray?, base: Float, ink: Color, glass: Boolean, mode: Int, seedX: Float) {
    val n = px.size
    val widths = taper(px, py, factors, base, 0.18f)
    if (mode == 1) {
        drawPath(variablePath(px, py, FloatArray(n) { widths[it] * 1.15f }), ink.copy(alpha = ink.alpha * 0.25f))
        drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * (if (glass) 0.8f else 0.7f)))
        return
    }
    drawPath(variablePath(px, py, FloatArray(n) { widths[it] * 0.9f }), ink.copy(alpha = ink.alpha * 0.14f))
    val bristles = 7
    val rnd = java.util.Random((seedX * 100000).toLong() + n)
    // Нормалі в кожній точці.
    val nx = FloatArray(n)
    val ny = FloatArray(n)
    for (i in 0 until n) {
        val a = maxOf(0, i - 1)
        val b = minOf(n - 1, i + 1)
        val dx = px[b] - px[a]
        val dy = py[b] - py[a]
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        nx[i] = -dy / len; ny[i] = dx / len
    }
    for (k in 0 until bristles) {
        val off = (k / (bristles - 1f) - 0.5f) * 0.85f
        val bw = 0.1f + rnd.nextFloat() * 0.1f
        val alpha = 0.45f + rnd.nextFloat() * 0.45f
        val wob = (rnd.nextFloat() - 0.5f) * 0.08f
        drawPath(
            variablePath(
                FloatArray(n) { px[it] + nx[it] * widths[it] * (off + wob) },
                FloatArray(n) { py[it] + ny[it] * widths[it] * (off + wob) },
                FloatArray(n) { widths[it] * bw },
            ),
            ink.copy(alpha = ink.alpha * alpha),
        )
    }
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

/** Рівна ділянка [a]→[b] точками через [step]. */
private fun segment(a: Offset, b: Offset, step: Float): Pair<FloatArray, FloatArray> {
    val len = (b - a).getDistance()
    val n = (len / step).toInt().coerceIn(2, 4000)
    return FloatArray(n) { a.x + (b.x - a.x) * it / (n - 1) } to FloatArray(n) { a.y + (b.y - a.y) * it / (n - 1) }
}

/** Рамка з ледь заокругленими кутами — одна неперервна лінія (за годинниковою, від лівого верхнього кута). */
private fun roundRectPoints(a: Offset, b: Offset, radius: Float, step: Float): Pair<FloatArray, FloatArray> {
    val l = minOf(a.x, b.x); val r = maxOf(a.x, b.x)
    val t = minOf(a.y, b.y); val bt = maxOf(a.y, b.y)
    val rr = radius.coerceAtMost(minOf(r - l, bt - t) / 3f).coerceAtLeast(0f)
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
        val (sx, sy) = segment(Offset(x0, y0), Offset(x1, y1), step)
        for (i in 0 until sx.size - 1) { xs += sx[i]; ys += sy[i] }
    }
    fun arc(cx: Float, cy: Float, from: Float) {
        val steps = 8
        for (i in 0 until steps) {
            val ang = from + (PI.toFloat() / 2) * i / steps
            xs += cx + rr * cos(ang); ys += cy + rr * sin(ang)
        }
    }
    line(l + rr, t, r - rr, t); arc(r - rr, t + rr, -PI.toFloat() / 2)
    line(r, t + rr, r, bt - rr); arc(r - rr, bt - rr, 0f)
    line(r - rr, bt, l + rr, bt); arc(l + rr, bt - rr, PI.toFloat() / 2)
    line(l, bt - rr, l, t + rr); arc(l + rr, t + rr, PI.toFloat())
    xs += l + rr; ys += t
    return xs.toFloatArray() to ys.toFloatArray()
}

/** Овал у прямокутнику [a]–[b]; [extra] — скільки радіан пройти понад повне коло (кінці заходять один на одного). */
private fun ovalPoints(a: Offset, b: Offset, step: Float, extra: Float): Pair<FloatArray, FloatArray> {
    val cx = (a.x + b.x) / 2
    val cy = (a.y + b.y) / 2
    val rx = kotlin.math.abs(b.x - a.x) / 2
    val ry = kotlin.math.abs(b.y - a.y) / 2
    val per = (2 * PI * kotlin.math.sqrt((rx * rx + ry * ry) / 2.0)).toFloat()
    val n = (per / step).toInt().coerceIn(24, 4000)
    val sweep = 2 * PI.toFloat() + extra
    val start = -1.9f
    return FloatArray(n) { cx + rx * cos(start + sweep * it / (n - 1)) } to FloatArray(n) { cy + ry * sin(start + sweep * it / (n - 1)) }
}

/** Ламану — у точки через [step] (щоб звуження, хвиля й пунктир лягли рівно). */
private fun resample(xs: FloatArray, ys: FloatArray, step: Float): Pair<FloatArray, FloatArray> {
    val ox = ArrayList<Float>()
    val oy = ArrayList<Float>()
    ox += xs[0]; oy += ys[0]
    var carry = 0f
    for (i in 0 until xs.size - 1) {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val len = hypot(dx, dy)
        if (len < 1e-6f) continue
        var t = step - carry
        while (t <= len) { ox += xs[i] + dx * t / len; oy += ys[i] + dy * t / len; t += step }
        carry = len - (t - step)
    }
    ox += xs[xs.size - 1]; oy += ys[ys.size - 1]
    return ox.toFloatArray() to oy.toFloatArray()
}

/** Підкреслення: лінія, рамка (одна лінія з легенько заокругленими кутами) чи овал. Кінці — гострі. */
private fun DrawScope.drawFigure(a: Offset, b: Offset, base: Float, ink: Color, glass: Boolean, style: Int, shape: Int) {
    val step = 2f
    when (shape) {
        1 -> {
            val (rx, ry) = roundRectPoints(a, b, base * 2f + 4.dp.toPx(), step)
            if (style == InkStyle.SOLID) {
                // Суцільна рамка: кінці трохи заходять один на одного, як коли обводиш олівцем.
                val m = (rx.size * 0.04f).toInt().coerceAtLeast(3)
                val xs = FloatArray(rx.size + m) { rx[it % rx.size] }
                val ys = FloatArray(ry.size + m) { ry[it % ry.size] }
                pencilLine(xs, ys, null, base, ink, glass, style, 0.08f, false)
            } else pencilLine(rx, ry, null, base, ink, glass, style, 0.08f, true)
        }
        2 -> {
            val solid = style == InkStyle.SOLID
            val (ox, oy) = ovalPoints(a, b, step, if (solid) 0.28f else 0f)
            pencilLine(ox, oy, null, base, ink, glass, style, 0.08f, !solid)
        }
        else -> {
            val (x, y) = segment(a, b, step)
            pencilLine(x, y, null, base, ink, glass, style, 0.08f, false)
        }
    }
}

private fun polyline(xs: FloatArray, ys: FloatArray): Path = Path().apply {
    moveTo(xs[0], ys[0])
    for (i in 1 until xs.size) lineTo(xs[i], ys[i])
}

/** Хвиляста лінія вздовж штриха: відступ по нормалі за синусом пройденої відстані; замкнена — ціле число хвиль. */
private fun wavy(xs0: FloatArray, ys0: FloatArray, amp: Float, closed: Boolean): Pair<FloatArray, FloatArray> {
    val (xs, ys) = resample(xs0, ys0, (amp * 0.35f).coerceAtLeast(1f))
    val total = arcLengths(xs, ys).last()
    var lambda = amp * 4.2f
    if (closed && total > 0f) lambda = total / kotlin.math.round(total / lambda).coerceAtLeast(3f)
    val n = xs.size
    if (n < 2) return xs to ys
    val ox = FloatArray(n)
    val oy = FloatArray(n)
    var dist = 0f
    for (i in 0 until n) {
        if (i > 0) dist += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val a = maxOf(0, i - 1)
        val b = minOf(n - 1, i + 1)
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val off = amp * sin(2f * PI.toFloat() * dist / lambda)
        ox[i] = xs[i] - dy / len * off
        oy[i] = ys[i] + dx / len * off
    }
    return ox to oy
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
        val h = (widths[i].takeIf { !it.isNaN() } ?: 0f) / 2f
        left += Offset(xs[i] - dy / len * h, ys[i] + dx / len * h)
        right += Offset(xs[i] + dy / len * h, ys[i] - dx / len * h)
    }
    path.moveTo(left[0].x, left[0].y)
    for (i in 1 until n) path.lineTo(left[i].x, left[i].y)
    for (i in n - 1 downTo 0) path.lineTo(right[i].x, right[i].y)
    path.close()
    val w0 = widths[0].takeIf { !it.isNaN() } ?: 0f
    val w1 = widths[n - 1].takeIf { !it.isNaN() } ?: 0f
    if (w0 > 0f) path.addOval(Rect(Offset(xs[0], ys[0]), w0 / 2f))
    if (w1 > 0f) path.addOval(Rect(Offset(xs[n - 1], ys[n - 1]), w1 / 2f))
    return path
}

// ---------- Курсори ----------

/** Нахил інструмента (градуси за годинниковою): тримаєш, як у правій руці. */
private const val TILT = 38f

private fun DrawScope.drawToolCursor(p: Offset, s: InkSession, width: Float, lead: Float) {
    val glass = AppStyle.glass
    when (s.tool) {
        InkTool.Eraser -> drawEraserCursor(p, width, lead, glass)
        InkTool.Marker -> drawMarkerCursor(p, width, lead, glass, MarkerTints[s.tint.coerceIn(0, 2)])
        InkTool.Brush -> drawBrushCursor(p, width, lead, glass)
        else -> drawMechPencil(p, width, lead, s.locked, glass, s.tool == InkTool.Underline)
    }
}

/** Приціл: де опиниться кінчик, поки інструмент піднятий. */
private fun DrawScope.drawAim(p: Offset, width: Float, lead: Float, glass: Boolean) {
    if (lead >= 0.99f) return
    val ink = if (glass) Oled.TextPrimary else SheetInk
    drawCircle(ink.copy(alpha = 0.55f * (1f - lead)), maxOf(width / 2f, 2.dp.toPx()) + 2.dp.toPx(), p, style = Stroke(1.dp.toPx()))
}

/** Тінь інструмента на сторінці — м'яка, трохи праворуч униз. */
private fun DrawScope.shadowRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    val o = 3.dp.toPx()
    drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(x + o, y + o * 0.6f), Size(w + 2.dp.toPx(), h), CornerRadius(r + 2.dp.toPx()))
    drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(x + o * 0.6f, y + o * 0.4f), Size(w, h), CornerRadius(r))
}

/** Об'ємна заливка циліндра: світло зліва, тінь справа (у повернутих координатах інструмента). */
private fun cylinder(x: Float, w: Float, dark: Color, mid: Color, light: Color) =
    Brush.horizontalGradient(0f to dark, 0.22f to light, 0.45f to mid, 0.8f to dark, 1f to dark.copy(alpha = 1f), startX = x, endX = x + w)

/**
 * Механічний олівець — непрозорий, з об'ємом і тінню. Сам олівець нерухомий: [lead] (0…1) висуває знизу стрижень
 * (тьмяніший за лінію, гострий), а кнопка згори натискається; ховається стрижень — кнопка вилазить назад.
 */
private fun DrawScope.drawMechPencil(p: Offset, width: Float, lead: Float, locked: Boolean, glass: Boolean, underline: Boolean) {
    drawAim(p, width, lead, glass)
    val maxLead = 6.dp.toPx()
    val sleeveL = 4.dp.toPx()
    val coneL = 11.dp.toPx()
    val gripL = 16.dp.toPx()
    val bodyL = 36.dp.toPx()
    val capL = 6.dp.toPx()
    val bw = 10.dp.toPx()
    val x = p.x
    rotate(TILT, p) {
        val y0 = p.y - maxLead            // кінчик гільзи (стрижень виходить звідси)
        val sleeveTop = y0 - sleeveL
        val coneTop = sleeveTop - coneL
        val gripTop = coneTop - gripL
        val bodyTop = gripTop - bodyL
        val capTop = bodyTop - capL
        // Тінь усього олівця.
        shadowRect(x - bw / 2, capTop, bw, y0 - capTop, bw / 2)
        // Стрижень: висувається з гільзи; тьмяно-сірий, загострений.
        val leadLen = maxLead * lead
        if (leadLen > 0.4f) {
            val lw = (width * 0.9f).coerceIn(1.3.dp.toPx(), 3.6.dp.toPx())
            val tipY = y0 + leadLen
            val body = Path().apply {
                moveTo(x - lw / 2, y0); lineTo(x - lw / 2, y0 + leadLen * 0.45f); lineTo(x, tipY)
                lineTo(x + lw / 2, y0 + leadLen * 0.45f); lineTo(x + lw / 2, y0); close()
            }
            drawPath(body, if (glass) Color(0xFF6E7178) else Color(0xFF5C5852))
            drawLine(Color.White.copy(alpha = 0.25f), Offset(x - lw * 0.2f, y0), Offset(x - lw * 0.2f, y0 + leadLen * 0.5f), 0.6.dp.toPx())
        }
        val metalDark = Color(0xFF4A4E55)
        val metalMid = Color(0xFF8E939B)
        val metalLight = Color(0xFFD9DDE3)
        // Гільза.
        val sw = 2.4.dp.toPx()
        drawRect(cylinder(x - sw / 2, sw, metalDark, metalMid, metalLight), Offset(x - sw / 2, sleeveTop), Size(sw, sleeveL))
        // Конус.
        val cone = Path().apply {
            moveTo(x - sw * 0.75f, sleeveTop); lineTo(x + sw * 0.75f, sleeveTop)
            lineTo(x + bw / 2, coneTop); lineTo(x - bw / 2, coneTop); close()
        }
        drawPath(cone, cylinder(x - bw / 2, bw, metalDark, metalMid, metalLight))
        // Гумовий тримач з рисками.
        val gripDark = Color(0xFF15161A)
        drawRect(cylinder(x - bw / 2, bw, gripDark, Color(0xFF2C2E34), Color(0xFF4A4D55)), Offset(x - bw / 2, gripTop), Size(bw, gripL))
        var ry = gripTop + 1.6.dp.toPx()
        while (ry < coneTop - 1.dp.toPx()) {
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(x - bw / 2, ry), Offset(x + bw / 2, ry), 0.8.dp.toPx())
            ry += 1.7.dp.toPx()
        }
        // Корпус.
        val bodyDark = if (underline) Color(0xFF2A2420) else Color(0xFF1E2024)
        val bodyMid = if (underline) Color(0xFF4E4339) else Color(0xFF3A3D44)
        val bodyLight = if (underline) Color(0xFF8A7A6A) else Color(0xFF767B85)
        drawRect(cylinder(x - bw / 2, bw, bodyDark, bodyMid, bodyLight), Offset(x - bw / 2, bodyTop), Size(bw, bodyL))
        // Кільце між корпусом і тримачем.
        drawRect(cylinder(x - bw / 2, bw, metalDark, metalMid, metalLight), Offset(x - bw / 2, gripTop - 1.6.dp.toPx()), Size(bw, 1.6.dp.toPx()))
        // Ковпачок і кліпса.
        drawRect(cylinder(x - bw / 2, bw, metalDark, metalMid, metalLight), Offset(x - bw / 2, capTop), Size(bw, capL))
        val clipW = 2.2.dp.toPx()
        drawRoundRect(cylinder(x + bw / 2 - clipW * 0.2f, clipW, metalDark, metalMid, metalLight), Offset(x + bw / 2 - clipW * 0.2f, capTop + 1.dp.toPx()), Size(clipW, bodyL * 0.55f), CornerRadius(clipW / 2))
        // Кнопка: натиснута, коли стрижень висунутий.
        val pressed = maxOf(lead, if (locked) 1f else 0f)
        val btnH = 6.5.dp.toPx() * (1f - 0.7f * pressed)
        val btnW = bw * 0.58f
        drawRoundRect(cylinder(x - btnW / 2, btnW, metalDark, metalMid, metalLight), Offset(x - btnW / 2, capTop - btnH), Size(btnW, btnH + 1f), CornerRadius(1.5.dp.toPx()))
    }
}

/** Маркер: товстий корпус, скошений кінчик (ширина — за товщиною лінії), ковпачок кольору відтінку. */
private fun DrawScope.drawMarkerCursor(p: Offset, width: Float, lead: Float, glass: Boolean, tint: Color) {
    drawAim(p, width, lead, glass)
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val tone = if (tint == Color.Unspecified) ink else tint
    val tipW = width.coerceIn(4.dp.toPx(), 18.dp.toPx())
    val bw = maxOf(15.dp.toPx(), tipW + 6.dp.toPx())
    val lift = 4.dp.toPx() * (1f - lead)
    val x = p.x
    rotate(TILT, p) {
        val tipTop = p.y - 6.dp.toPx() - lift
        val collarTop = tipTop - 5.dp.toPx()
        val bodyTop = collarTop - 46.dp.toPx()
        val capTop = bodyTop - 10.dp.toPx()
        shadowRect(x - bw / 2, capTop, bw, p.y - lift - capTop, 4.dp.toPx())
        // Скошений кінчик: широкий біля корпусу, ріже лінію біля сторінки.
        val tip = Path().apply {
            moveTo(x - tipW / 2, tipTop); lineTo(x + tipW / 2, tipTop)
            lineTo(x + tipW / 2, p.y - lift - 1.5.dp.toPx()); lineTo(x - tipW / 2, p.y - lift); close()
        }
        drawPath(tip, tone.copy(alpha = 0.85f))
        drawRect(cylinder(x - tipW / 2 - 2.dp.toPx(), tipW + 4.dp.toPx(), Color(0xFF3A3C40), Color(0xFF6A6D73), Color(0xFFA9ADB3)), Offset(x - tipW / 2 - 2.dp.toPx(), collarTop), Size(tipW + 4.dp.toPx(), 5.dp.toPx()))
        val dark = if (glass) Color(0xFF2E3034) else Color(0xFF34312C)
        val mid = if (glass) Color(0xFF5C6068) else Color(0xFF66615A)
        val light = if (glass) Color(0xFFAEB3BB) else Color(0xFFB4ADA2)
        drawRoundRect(cylinder(x - bw / 2, bw, dark, mid, light), Offset(x - bw / 2, bodyTop), Size(bw, collarTop - bodyTop), CornerRadius(3.dp.toPx()))
        // Смужка відтінку на корпусі й ковпачок.
        drawRect(tone.copy(alpha = 0.7f), Offset(x - bw / 2, bodyTop + 10.dp.toPx()), Size(bw, 4.dp.toPx()))
        drawRoundRect(cylinder(x - bw / 2, bw, dark, tone.copy(alpha = 0.9f).compositeOverDark(), light), Offset(x - bw / 2, capTop), Size(bw, 11.dp.toPx()), CornerRadius(5.dp.toPx()))
    }
}

private fun Color.compositeOverDark(): Color = Color(
    red = red * alpha + 0.12f * (1f - alpha),
    green = green * alpha + 0.12f * (1f - alpha),
    blue = blue * alpha + 0.12f * (1f - alpha),
)

/** Пензлик: пучок щетинок (ширина — за товщиною мазка), металева обойма, лакована ручка. */
private fun DrawScope.drawBrushCursor(p: Offset, width: Float, lead: Float, glass: Boolean) {
    drawAim(p, width, lead, glass)
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val tw = width.coerceIn(5.dp.toPx(), 20.dp.toPx())
    val lift = 4.dp.toPx() * (1f - lead)
    val x = p.x
    rotate(TILT, p) {
        val tip = p.y - lift
        val tuftTop = tip - 14.dp.toPx()
        val ferTop = tuftTop - 11.dp.toPx()
        val handleTop = ferTop - 46.dp.toPx()
        val fw = tw * 0.8f + 2.dp.toPx()
        shadowRect(x - fw / 2, handleTop, fw, tip - handleTop, fw / 2)
        // Пучок: краплина, кінчик змочений фарбою.
        val tuft = Path().apply {
            moveTo(x - tw / 2, tuftTop)
            cubicTo(x - tw / 2, tuftTop + 8.dp.toPx(), x - tw * 0.15f, tip - 3.dp.toPx(), x, tip)
            cubicTo(x + tw * 0.15f, tip - 3.dp.toPx(), x + tw / 2, tuftTop + 8.dp.toPx(), x + tw / 2, tuftTop)
            close()
        }
        drawPath(tuft, Brush.verticalGradient(0f to Color(0xFF8A7A62), 0.55f to Color(0xFF4A4034), 1f to ink.copy(alpha = 0.9f), startY = tuftTop, endY = tip))
        for (k in -2..2) drawLine(Color.Black.copy(alpha = 0.25f), Offset(x + k * tw * 0.12f, tuftTop), Offset(x + k * tw * 0.05f, tip - 4.dp.toPx()), 0.6.dp.toPx())
        drawRect(cylinder(x - fw / 2, fw, Color(0xFF4A4E55), Color(0xFF8E939B), Color(0xFFD9DDE3)), Offset(x - fw / 2, ferTop), Size(fw, tuftTop - ferTop))
        // Ручка звужується догори.
        val hw = fw * 0.9f
        val handle = Path().apply {
            moveTo(x - hw / 2, ferTop); lineTo(x + hw / 2, ferTop)
            lineTo(x + hw * 0.32f, handleTop); lineTo(x - hw * 0.32f, handleTop); close()
        }
        drawPath(handle, cylinder(x - hw / 2, hw, Color(0xFF1A1512), Color(0xFF3A2C24), Color(0xFF7A6050)))
    }
}

/**
 * Гумка — брусок: стирає нижній край (на ширину бруска — він і є розміром гумки). Нижня частина — сама гумка;
 * опущена (стирає) — ледь світиться.
 */
private fun DrawScope.drawEraserCursor(p: Offset, width: Float, lead: Float, glass: Boolean) {
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val bw = maxOf(width, 6.dp.toPx())
    val bh = maxOf(30.dp.toPx(), bw * 1.6f)
    val rubber = minOf(10.dp.toPx(), bh * 0.3f)
    val r = CornerRadius(minOf(3.dp.toPx(), bw / 4))
    rotate(TILT * 0.6f, p) {
        val left = p.x - bw / 2
        val top = p.y - bh
        shadowRect(left, top, bw, bh, r.x)
        // Корпус (тримач) — темний, об'ємний.
        drawRoundRect(cylinder(left, bw, Color(0xFF1E2024), Color(0xFF3A3D44), Color(0xFF6E737C)), Offset(left, top), Size(bw, bh - rubber + r.x), r)
        // Гумка.
        val rubberBase = if (glass) Color(0xFF8C8F96) else Color(0xFF9C968C)
        drawRoundRect(cylinder(left, bw, rubberBase.copy(red = rubberBase.red * 0.6f, green = rubberBase.green * 0.6f, blue = rubberBase.blue * 0.6f), rubberBase, Color(minOf(1f, rubberBase.red * 1.3f), minOf(1f, rubberBase.green * 1.3f), minOf(1f, rubberBase.blue * 1.3f))), Offset(left, p.y - rubber), Size(bw, rubber), r)
        if (lead > 0.01f) drawRoundRect(ink.copy(alpha = 0.32f * lead), Offset(left, p.y - rubber), Size(bw, rubber), r)
    }
}
