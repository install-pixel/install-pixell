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
 * Інструменти: твердий і м'який олівці, ручка (живе чорнило), маркер (напівпрозорий) і гумка (точна чи м'яка — ледь
 * підтирає тон). Товщина — три розміри; лінія — суцільна, пунктир, крапки чи хвиля.
 */
enum class InkTool(val kind: Int, val sizes: FloatArray) {
    Hard(InkKind.HARD, floatArrayOf(0.0026f, 0.0040f, 0.0058f)),
    Soft(InkKind.SOFT, floatArrayOf(0.0060f, 0.0090f, 0.0130f)),
    Pen(InkKind.PEN, floatArrayOf(0.0042f, 0.0072f, 0.0125f)),
    Marker(InkKind.MARKER, floatArrayOf(0.016f, 0.024f, 0.034f)),
    Eraser(InkKind.ERASER, floatArrayOf(0.010f, 0.022f, 0.045f)),
}

/** Вид штриха, що зберігається ([InkLine.kind]). */
object InkKind {
    const val PEN = 0
    const val HARD = 1
    const val SOFT = 2
    const val MARKER = 3
    const val ERASER = 10
    const val ERASER_SOFT = 11
}

/** Вигляд лінії ([InkLine.style]). */
object InkStyle {
    const val SOLID = 0
    const val DASHED = 1
    const val DOTTED = 2
    const val WAVY = 3
}

/**
 * Режим малювання на сторінці. Олівець — окремо від пальця: палець ставиш будь-де, олівець іде на стільки, на скільки
 * провів пальцем (як курсор на тачпаді), тож кінчик ніколи не закритий. Затримав палець — висувається стрижень і пише;
 * відпустив — стрижень ховається. Подвійний дотик — стрижень лишається висунутим (пише одразу).
 */
@Stable
class InkSession(val day: Long, val part: Int) {
    var tool by mutableStateOf(InkTool.Pen)
    private val sizes = HashMap<InkTool, Int>()
    var size: Int
        get() = sizes[tool] ?: 1
        set(v) { sizes[tool] = v; sizeTick++ }
    var sizeTick by mutableIntStateOf(0)
    var style by mutableIntStateOf(InkStyle.SOLID)
    var softEraser by mutableStateOf(false)
    /** Де кінчик олівця (пікселі сторінки); null — ще не ставили (стане посередині). */
    var pen by mutableStateOf<Offset?>(null)
    /** Стрижень висунутий (пише). */
    var lead by mutableStateOf(false)
    /** Подвійним дотиком зафіксовано: стрижень лишається висунутим. */
    var locked by mutableStateOf(false)
    internal var lastTapAt = 0L
    val undo = mutableStateListOf<List<InkLine>>()
    val redo = mutableStateListOf<List<InkLine>>()

    val width: Float get() { sizeTick; return tool.sizes[size.coerceIn(0, 2)] }
    val kind: Int get() = if (tool == InkTool.Eraser) (if (softEraser) InkKind.ERASER_SOFT else InkKind.ERASER) else tool.kind
}

private const val HOLD_MS = 260L
private const val STRAIGHTEN_MS = 600L

/**
 * Шар малювання над сторінкою: показує намальоване, а в режимі малювання ([session] не null) — олівець-курсор.
 * Гумка — теж штрих: стирає (точна) чи підтирає (м'яка) те, що під нею, не чіпаючи самої сторінки.
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
                        var finger = down.position
                        var moved = 0f
                        var stillSince = down.uptimeMillis
                        var drawing = false
                        var straight = false
                        val pts = ArrayList<Offset>()
                        val fs = ArrayList<Float>()
                        var smooth = s.pen!!
                        var factor = Ink.targetFactor(0f)
                        var lastTime = down.uptimeMillis
                        var lastEvent = down.uptimeMillis
                        fun begin() {
                            drawing = true
                            s.lead = true
                            smooth = s.pen!!
                            pts.clear(); fs.clear()
                            pts += smooth; fs += Ink.targetFactor(0f)
                            live = pts.toList(); liveFactors = fs.toList()
                        }
                        if (s.locked) begin()
                        while (true) {
                            // Чекаємо руху, але не довше, ніж до «затримки» (щоб висунути стрижень чи вирівняти лінію).
                            val nowT = android.os.SystemClock.uptimeMillis()
                            val wait = when {
                                !drawing -> HOLD_MS - (nowT - stillSince)
                                !straight && pts.size > 3 -> STRAIGHTEN_MS - (nowT - stillSince)
                                else -> 10_000L
                            }.coerceAtLeast(1L)
                            val ev = withTimeoutOrNull(wait) { awaitPointerEvent() }
                            if (ev == null) {
                                lastEvent = android.os.SystemClock.uptimeMillis()
                                if (!drawing) {
                                    // Затримав палець — клац, стрижень висувається: далі пише.
                                    haptics.keyDown()
                                    begin()
                                    stillSince = lastEvent
                                } else if (!straight && pts.size > 3) {
                                    // Затримав у кінці лінії — вона вирівнюється в пряму (далі можна ще підвести кінець).
                                    straight = true
                                    haptics.tick()
                                    val a = pts.first()
                                    pts.clear(); fs.clear()
                                    pts += a; pts += s.pen!!
                                    fs += 1f; fs += 1f
                                    live = pts.toList(); liveFactors = fs.toList()
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
                                s.pen = clampPen(s.pen!! + d)
                            }
                            if (dist > still * 0.25f) stillSince = c.uptimeMillis
                            if (!drawing) continue
                            val p = s.pen!!
                            if (straight) {
                                pts[pts.size - 1] = p
                                live = pts.toList()
                                continue
                            }
                            smooth += (p - smooth) * (1f - Ink.SMOOTH)
                            val step = hypot(smooth.x - pts.last().x, smooth.y - pts.last().y)
                            if (step >= stepPx) {
                                val dt = (c.uptimeMillis - lastTime).coerceAtLeast(1)
                                val speed = step / mm / dt
                                factor += (Ink.targetFactor(speed) - factor) * Ink.FACTOR_EASE * 3f
                                pts += smooth; fs += factor
                                lastTime = c.uptimeMillis
                                live = pts.toList(); liveFactors = fs.toList()
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
                        if (drawing && !straight) { pts += smooth; fs += factor }
                        live = emptyList(); liveFactors = emptyList()
                        if (!s.locked && s.lead) { s.lead = false; if (!tap) haptics.keyUp() }
                        if (s.locked) s.lead = true
                        if (pts.size >= 2) {
                            val line = InkLine(
                                page = part,
                                xs = FloatArray(pts.size) { pts[it].x / w },
                                ys = FloatArray(pts.size) { pts[it].y / w },
                                factors = FloatArray(fs.size) { fs[it] },
                                width = s.width,
                                time = System.currentTimeMillis(),
                                kind = s.kind,
                                style = if (s.tool == InkTool.Eraser) InkStyle.SOLID else s.style,
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
                if (session.tool == InkTool.Eraser) InkStyle.SOLID else session.style,
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

/** Штрих за видом і вигляд лінії. «Аркуш» — графіт, «Скло» — світло; висохлий — трохи тьмяніший. */
private fun DrawScope.drawStroke(xs: FloatArray, ys: FloatArray, factors: FloatArray, base: Float, w: Float, dry: Boolean, kind: Int, style: Int) {
    val n = xs.size
    if (n < 2) return
    var px = FloatArray(n) { xs[it] * w }
    var py = FloatArray(n) { ys[it] * w }
    val glass = AppStyle.glass
    val ink = if (glass) (if (dry) DryInk else FreshInk) else (if (dry) SheetInk.copy(alpha = 0.8f) else SheetInk)
    if (kind >= InkKind.ERASER) {
        val soft = kind == InkKind.ERASER_SOFT
        drawPath(polyline(px, py), Color.Black.copy(alpha = if (soft) 0.32f else 1f), style = Stroke(base, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
        return
    }
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
        InkKind.MARKER -> {
            // Маркер: широкий, напівпрозорий, рівний (без звуження).
            drawPath(polyline(px, py), ink.copy(alpha = ink.alpha * (if (glass) 0.24f else 0.3f)), style = Stroke(base, cap = StrokeCap.Square, join = StrokeJoin.Round))
        }
        InkKind.HARD -> {
            // Твердий олівець: тонкий, світліший, майже рівний.
            val widths = Ink.widths(px, py, null, base)
            drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.72f))
        }
        InkKind.SOFT -> {
            // М'який олівець: товстіший, із зерном — кілька зсунутих тонших штрихів.
            val widths = Ink.widths(px, py, factors.toList().takeIf { it.isNotEmpty() }, base)
            drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.55f))
            val o = base * 0.18f
            drawPath(variablePath(FloatArray(n) { px[it] + o }, FloatArray(n) { py[it] - o * 0.6f }, FloatArray(n) { widths[it] * 0.45f }), ink.copy(alpha = ink.alpha * 0.35f))
            drawPath(variablePath(FloatArray(n) { px[it] - o * 0.7f }, FloatArray(n) { py[it] + o }, FloatArray(n) { widths[it] * 0.35f }), ink.copy(alpha = ink.alpha * 0.3f))
        }
        else -> {
            // Ручка — «живе чорнило» (як і раніше): товщина від швидкості, кінці звужуються.
            val widths = Ink.widths(px, py, factors.toList().takeIf { it.isNotEmpty() }, base)
            val path = variablePath(px, py, widths)
            if (glass) {
                drawPath(variablePath(px, py, FloatArray(n) { widths[it] * 2.6f }), Oled.TextPrimary.copy(alpha = 0.14f))
                drawPath(path, ink)
            } else drawPath(path, ink)
        }
    }
}

private fun polyline(xs: FloatArray, ys: FloatArray): Path = Path().apply {
    moveTo(xs[0], ys[0])
    for (i in 1 until xs.size) lineTo(xs[i], ys[i])
}

/** Хвиляста лінія вздовж штриха: відступ по нормалі за синусом пройденої відстані. */
private fun wavy(xs: FloatArray, ys: FloatArray, amp: Float): Pair<FloatArray, FloatArray> {
    // Рівномірно перебрати точки (крок — чверть амплітуди), щоб хвиля була гладкою.
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
 * Олівець-курсор: тіло нахилене вгору-праворуч, кінчик — точно там, де пише. Стрижень висувається ([lead] 0…1);
 * захований — на місці кінчика тонке кільце-приціл. Гумка — коло її розміру.
 */
private fun DrawScope.drawPencilCursor(p: Offset, tool: InkTool, width: Float, lead: Float, locked: Boolean) {
    val glass = AppStyle.glass
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val body = if (glass) Color(0xFF2A2B30) else Color(0xFF15130F)
    if (tool == InkTool.Eraser) {
        val r = (width / 2f).coerceAtLeast(3.dp.toPx())
        drawCircle(ink.copy(alpha = 0.85f), r, p, style = Stroke(1.4.dp.toPx()))
        drawCircle(ink.copy(alpha = 0.12f + 0.15f * lead), r, p)
        // Брусок гумки над колом.
        rotate(-45f, p) {
            val y = p.y - r - 4.dp.toPx() - 30.dp.toPx()
            drawRoundRect(body, Offset(p.x - 8.dp.toPx(), y), androidx.compose.ui.geometry.Size(16.dp.toPx(), 30.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRoundRect(ink.copy(alpha = 0.85f), Offset(p.x - 8.dp.toPx(), y), androidx.compose.ui.geometry.Size(16.dp.toPx(), 30.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            drawLine(ink.copy(alpha = 0.4f), Offset(p.x - 8.dp.toPx(), y + 18.dp.toPx()), Offset(p.x + 8.dp.toPx(), y + 18.dp.toPx()), 1.dp.toPx())
        }
        return
    }
    val leadLen = 7.dp.toPx() * lead
    val coneLen = 9.dp.toPx()
    val bodyW = if (tool == InkTool.Marker) 13.dp.toPx() else 9.dp.toPx()
    val bodyLen = 34.dp.toPx()
    // Приціл: де опиниться кінчик (видно, поки стрижень захований).
    if (lead < 0.99f) drawCircle(ink.copy(alpha = 0.6f * (1f - lead)), maxOf(width / 2f, 2.dp.toPx()) + 2.dp.toPx(), p, style = Stroke(1.dp.toPx()))
    rotate(45f, p) {
        // Олівець «стоїть» над кінчиком: усе малюємо вгору від p, повернуте на 45° праворуч.
        val leadTip = p
        val coneTip = Offset(p.x, p.y - leadLen)
        val coneBase = coneTip.y - coneLen
        // Стрижень.
        if (leadLen > 0.5f) drawLine(ink, leadTip, coneTip, maxOf(width, 1.6.dp.toPx()).coerceAtMost(4.dp.toPx()), cap = StrokeCap.Round)
        // Конус (заточка).
        val cone = Path().apply {
            moveTo(coneTip.x - 1.6.dp.toPx(), coneTip.y)
            lineTo(coneTip.x + 1.6.dp.toPx(), coneTip.y)
            lineTo(p.x + bodyW / 2, coneBase)
            lineTo(p.x - bodyW / 2, coneBase)
            close()
        }
        drawPath(cone, if (glass) Color(0xFF8C8F96) else Color(0xFF2A251D))
        drawPath(cone, ink.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()))
        // Тіло.
        val top = coneBase - bodyLen
        drawRect(body, Offset(p.x - bodyW / 2, top), androidx.compose.ui.geometry.Size(bodyW, bodyLen))
        drawRect(ink.copy(alpha = 0.8f), Offset(p.x - bodyW / 2, top), androidx.compose.ui.geometry.Size(bodyW, bodyLen), style = Stroke(1.1.dp.toPx()))
        // Грані / кнопка механічного олівця.
        drawLine(ink.copy(alpha = 0.3f), Offset(p.x, top + 2.dp.toPx()), Offset(p.x, coneBase - 2.dp.toPx()), 0.8.dp.toPx())
        val clickH = 6.dp.toPx()
        drawRect(if (locked) ink.copy(alpha = 0.85f) else body, Offset(p.x - bodyW * 0.32f, top - clickH), androidx.compose.ui.geometry.Size(bodyW * 0.64f, clickH))
        drawRect(ink.copy(alpha = 0.8f), Offset(p.x - bodyW * 0.32f, top - clickH), androidx.compose.ui.geometry.Size(bodyW * 0.64f, clickH), style = Stroke(1.dp.toPx()))
        // Позначка інструмента на тілі: маркер — широка смуга, м'який — темна, твердий — світла.
        val mark = when (tool) {
            InkTool.Hard -> ink.copy(alpha = 0.25f)
            InkTool.Soft -> ink.copy(alpha = 0.6f)
            InkTool.Marker -> ink.copy(alpha = 0.4f)
            else -> ink.copy(alpha = 0.45f)
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
