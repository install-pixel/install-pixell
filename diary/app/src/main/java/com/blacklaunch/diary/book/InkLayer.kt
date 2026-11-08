package com.blacklaunch.diary.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.blacklaunch.diary.data.InkLine
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.theme.Oled
import kotlin.math.hypot

/** Інструмент пера: три товщини (частки ширини сторінки) і гумка. */
enum class InkTool(val width: Float) { Thin(0.0042f), Medium(0.0072f), Thick(0.0125f), Eraser(0f) }

/** Режим пера на сторінці: вибраний інструмент, «назад» / «вперед». */
@Stable
class InkSession(val day: Long, val part: Int) {
    var tool by mutableStateOf(InkTool.Medium)
    val undo = mutableStateListOf<List<InkLine>>()
    val redo = mutableStateListOf<List<InkLine>>()
}

/**
 * Шар пера над сторінкою: показує намальоване, а в режимі пера ([session] не null) — малює пальцем.
 * [dry] — чи висох штрих (висохлий гумка не стирає; [onDryErase] — підказати про це).
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
    val current by rememberUpdatedState(lines)
    val change by rememberUpdatedState(onChange)
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var liveFactors by remember { mutableStateOf<List<Float>>(emptyList()) }
    val active = session != null
    Canvas(
        modifier
            .fillMaxSize()
            .then(
                if (!active) Modifier else Modifier.pointerInput(session, pageWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val s = session!!
                        val w = pageWidth.coerceAtLeast(1f)
                        if (s.tool == InkTool.Eraser) {
                            var warned = false
                            fun eraseAt(p: Offset) {
                                val r = 0.022f
                                val x = p.x / w
                                val y = p.y / w
                                val hit = current.filter { it.page == part && it.xs.indices.any { i -> hypot(it.xs[i] - x, it.ys[i] - y) < r } }
                                if (hit.isEmpty()) return
                                val wet = hit.filterNot(dry)
                                if (wet.size < hit.size && !warned) { warned = true; onDryErase() }
                                if (wet.isNotEmpty()) {
                                    s.undo += current
                                    s.redo.clear()
                                    change(current - wet.toSet())
                                }
                            }
                            eraseAt(down.position)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                eraseAt(c.position)
                            }
                            return@awaitEachGesture
                        }
                        // «Живе перо»: точка лінії йде трохи позаду пальця (згладжування), товщина — від швидкості.
                        val pts = arrayListOf(down.position)
                        val fs = arrayListOf(Ink.targetFactor(0f))
                        var smooth = down.position
                        var factor = Ink.targetFactor(0f)
                        var lastTime = down.uptimeMillis
                        val mm = density * 160f / 25.4f
                        val stepPx = Ink.STEP_MM * mm
                        live = pts.toList()
                        liveFactors = fs.toList()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            val p = c.position
                            smooth += (p - smooth) * (1f - Ink.SMOOTH)
                            val dt = (c.uptimeMillis - lastTime).coerceAtLeast(1)
                            val dist = hypot(smooth.x - pts.last().x, smooth.y - pts.last().y)
                            if (dist >= stepPx) {
                                val speed = dist / mm / dt
                                factor += (Ink.targetFactor(speed) - factor) * Ink.FACTOR_EASE * 3f
                                pts += smooth
                                fs += factor
                                lastTime = c.uptimeMillis
                                live = pts.toList()
                                liveFactors = fs.toList()
                            }
                        }
                        pts += smooth
                        fs += factor
                        live = emptyList()
                        liveFactors = emptyList()
                        if (pts.size >= 2) {
                            val line = InkLine(
                                page = part,
                                xs = FloatArray(pts.size) { pts[it].x / w },
                                ys = FloatArray(pts.size) { pts[it].y / w },
                                factors = FloatArray(fs.size) { fs[it] },
                                width = s.tool.width,
                                time = System.currentTimeMillis(),
                            )
                            s.undo += current
                            s.redo.clear()
                            change(current + line)
                        }
                    }
                },
            ),
    ) {
        val w = size.width
        lines.forEach { l -> if (l.page == part) drawInk(l.xs, l.ys, l.factors, l.width * w, w, dry(l)) }
        if (live.size >= 2 && session != null) {
            drawInk(
                FloatArray(live.size) { live[it].x / w },
                FloatArray(live.size) { live[it].y / w },
                FloatArray(liveFactors.size) { liveFactors[it] },
                session.tool.width * w,
                w,
                false,
            )
        }
    }
}

/** Штрих змінної товщини: «Аркуш» — олівець; «Скло» — світна лінія з м'яким ореолом. Висохлий — трохи тьмяніший. */
private fun DrawScope.drawInk(xs: FloatArray, ys: FloatArray, factors: FloatArray, base: Float, w: Float, dry: Boolean) {
    val n = xs.size
    if (n < 2) return
    val px = FloatArray(n) { xs[it] * w }
    val py = FloatArray(n) { ys[it] * w }
    val widths = Ink.widths(px, py, factors.toList().takeIf { it.isNotEmpty() }, base)
    val path = variablePath(px, py, widths)
    val color = if (dry) DryInk else FreshInk
    if (AppStyle.glass) {
        drawPath(variablePath(px, py, FloatArray(n) { widths[it] * 2.6f }), Oled.TextPrimary.copy(alpha = 0.14f))
        drawPath(path, color)
    } else {
        drawPath(path, if (dry) SheetInk.copy(alpha = 0.8f) else SheetInk)
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
    // Круглі кінчики (дуже тонкі через звуження, але без «рубаних» країв).
    path.addOval(androidx.compose.ui.geometry.Rect(Offset(xs[0], ys[0]), widths[0] / 2f))
    path.addOval(androidx.compose.ui.geometry.Rect(Offset(xs[n - 1], ys[n - 1]), widths[n - 1] / 2f))
    return path
}

@Suppress("unused")
private val Transparent = Color.Transparent
