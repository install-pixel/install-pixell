package com.blacklaunch.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Котик, ніби намальований білим олівцем: ходить на двох лапах, помахуючи передніми. Плашка з відліком для нього —
 * огорожа. Реагує залежно від того, чим і з якого боку вдарився:
 * - носом у стінку чи огорожу збоку — відсахується назад, трусить головою, іноді сідає на хвіст і чухає потилицю;
 * - головою знизу в огорожу — присідає, хапається лапами за голову, чухає маківку;
 * - лапами згори на огорожу (чи нижній край) — зупиняється, дивиться під ноги й іде інакше.
 * Сам собою іноді сідає відпочити, чухає голову чи роззирається. Малюється лише поки екран таймера видно (~30 кадрів/с).
 * [fence] — плашка відліку в координатах цього самого полотна (воно на весь екран).
 */
@Composable
fun FocusCat(fence: Rect?, modifier: Modifier = Modifier) {
    val unit = LocalDensity.current.density
    val cat = remember { CatSim() }
    val currentFence by rememberUpdatedState(fence)
    var size by remember { mutableLongStateOf(0L) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(size) {
        val w = (size shr 32).toFloat()
        val h = (size and 0xFFFFFFFF).toFloat()
        if (w <= 0f || h <= 0f) return@LaunchedEffect
        cat.place(w, h, unit, currentFence)
        var last = System.nanoTime()
        while (true) {
            delay(FRAME_MS)
            val now = System.nanoTime()
            cat.step(((now - last) / 1e9f).coerceAtMost(0.1f), w, h, unit, currentFence)
            last = now
            frame++
        }
    }
    Canvas(
        modifier.fillMaxSize().onSizeChanged { s: IntSize -> size = (s.width.toLong() shl 32) or s.height.toLong() },
    ) {
        frame // перемальовувати щокадру
        drawCat(cat, unit)
    }
}

private const val FRAME_MS = 33L

private class CatStep(val act: CatAct, val seconds: Float, val dazed: Boolean = false, val then: (() -> Unit)? = null)

/** Звідки прийшов удар. */
private enum class CatHit { SIDE, HEAD, FEET }

private class CatSim {
    var x = 0f
    var y = 0f
    var dirX = 1f
    var slope = 0f
    var act = CatAct.WALK
    var actTime = 0f
    var actLength = 1f
    var dazed = false
    var walkPhase = 0f
    var time = 0f
    private var onEnd: (() -> Unit)? = null
    private val queue = ArrayDeque<CatStep>()
    private var nextIdle = 4f
    private val rnd = Random(System.nanoTime())

    val progress get() = (actTime / actLength).coerceIn(0f, 1f)

    fun place(w: Float, h: Float, u: Float, fence: Rect?) {
        for (attempt in 0 until 30) {
            x = w * (0.15f + rnd.nextFloat() * 0.7f)
            y = h * (0.72f + rnd.nextFloat() * 0.2f)
            if (!blocked(x, y, w, h, u, fence)) break
        }
        dirX = if (rnd.nextBoolean()) 1f else -1f
        slope = (rnd.nextFloat() - 0.5f) * 0.5f
        queue.clear()
        start(CatStep(CatAct.WALK, 0f))
    }

    fun step(dt: Float, w: Float, h: Float, u: Float, fence: Rect?) {
        time += dt
        actTime += dt
        when (act) {
            CatAct.WALK -> walk(dt, w, h, u, fence)
            // Відсахується: кілька сантиметрів назад, поки хитається.
            CatAct.RECOIL -> {
                val back = x - dirX * 26f * u * dt * (1f - progress)
                if (!blocked(back, y, w, h, u, fence)) x = back
            }
            else -> Unit
        }
        if (act != CatAct.WALK && actTime >= actLength) next()
    }

    private fun walk(dt: Float, w: Float, h: Float, u: Float, fence: Rect?) {
        val speed = 30f * u
        val nx = x + dirX * speed * dt
        val ny = y + slope * speed * dt
        // Огорожа з'явилась, коли котик уже стояв на її місці, — спершу просто виходить звідти.
        val stuck = blocked(x, y, w, h, u, fence)
        val outside = bounds(nx, ny, u).let { it.left < 0f || it.top < 0f || it.right > w || it.bottom > h }
        if (if (stuck) outside else blocked(nx, ny, w, h, u, fence)) {
            react(hitKind(nx, ny, w, h, u, fence))
            return
        }
        x = nx
        y = ny
        walkPhase += dt * 6.5f
        nextIdle -= dt
        if (nextIdle <= 0f) {
            nextIdle = 3f + rnd.nextFloat() * 5f
            val roll = rnd.nextFloat()
            when {
                roll < 0.14f -> plan(CatStep(CatAct.SIT, 2.5f + rnd.nextFloat() * 2.5f))
                roll < 0.26f -> plan(CatStep(CatAct.SCRATCH, 1.6f))
                roll < 0.36f -> plan(CatStep(CatAct.LOOK_AROUND, 1.4f))
                roll < 0.5f -> dirX = -dirX
                else -> slope = (rnd.nextFloat() - 0.5f) * 0.7f
            }
        }
    }

    /** Що саме вдарилось: бік (ніс), маківка (знизу під огорожею / верхній край) чи лапи (згори на огорожу / нижній край). */
    private fun hitKind(nx: Float, ny: Float, w: Float, h: Float, u: Float, fence: Rect?): CatHit {
        val box = bounds(nx, ny, u)
        if (box.left < 0f || box.right > w) return CatHit.SIDE
        if (box.top < 0f) return CatHit.HEAD
        if (box.bottom > h) return CatHit.FEET
        val wall = fence?.inflate(6f * u) ?: return CatHit.SIDE
        val now = bounds(x, y, u)
        return when {
            now.right <= wall.left || now.left >= wall.right -> CatHit.SIDE
            now.top >= wall.bottom -> CatHit.HEAD
            else -> CatHit.FEET
        }
    }

    /** Реакція на удар — кілька кроків поспіль, а наприкінці — інший напрям. */
    private fun react(hit: CatHit) {
        when (hit) {
            CatHit.SIDE -> {
                val turn = { dirX = -dirX; slope = (rnd.nextFloat() - 0.5f) * 0.6f }
                if (rnd.nextFloat() < 0.4f) {
                    // Сильно: сів на хвіст, оговтався, почухав потилицю.
                    plan(CatStep(CatAct.RECOIL, 0.35f), CatStep(CatAct.SIT, 1.8f, dazed = true), CatStep(CatAct.SCRATCH, 1.4f, then = turn))
                } else {
                    plan(CatStep(CatAct.RECOIL, 0.35f), CatStep(CatAct.SHAKE, 0.8f, then = turn))
                }
            }
            CatHit.HEAD -> {
                val down = { slope = 0.35f + rnd.nextFloat() * 0.3f; if (rnd.nextBoolean()) dirX = -dirX }
                if (rnd.nextFloat() < 0.5f) {
                    plan(CatStep(CatAct.DUCK, 1.3f), CatStep(CatAct.SCRATCH, 1.2f, then = down))
                } else {
                    plan(CatStep(CatAct.DUCK, 1.3f), CatStep(CatAct.SHAKE, 0.6f, then = down))
                }
            }
            CatHit.FEET -> plan(CatStep(CatAct.LOOK_DOWN, 0.8f, then = { slope = -(0.3f + rnd.nextFloat() * 0.3f) }))
        }
    }

    private fun plan(vararg steps: CatStep) {
        queue.clear()
        queue.addAll(steps)
        next()
    }

    private fun next() {
        onEnd?.invoke()
        onEnd = null
        start(queue.removeFirstOrNull() ?: CatStep(CatAct.WALK, 0f))
    }

    private fun start(step: CatStep) {
        act = step.act
        actTime = 0f
        actLength = step.seconds.coerceAtLeast(0.01f)
        dazed = step.dazed
        onEnd = step.then
    }

    fun bounds(cx: Float, cy: Float, u: Float) = Rect(cx - CAT_HALF_WIDTH * u, cy - CAT_HEIGHT * u, cx + CAT_HALF_WIDTH * u, cy + 2f * u)

    private fun blocked(cx: Float, cy: Float, w: Float, h: Float, u: Float, fence: Rect?): Boolean {
        val box = bounds(cx, cy, u)
        if (box.left < 0f || box.top < 0f || box.right > w || box.bottom > h) return true
        return fence != null && box.overlaps(fence.inflate(6f * u))
    }
}

private val Pencil = Color(0xFFEDEDED)

/** Фігури з [catShapes] — олівцем: чорна заливка закриває те, що позаду; штрих двічі — основний і ледь зсунутий. */
private fun DrawScope.drawCat(cat: CatSim, u: Float) {
    val frame = CatFrame(cat.act, cat.progress, cat.actTime, cat.time, cat.walkPhase, cat.dazed)
    withTransform({
        translate(cat.x, cat.y)
        scale(cat.dirX * u, u, pivot = Offset.Zero)
    }) {
        catShapes(frame).forEach { shape ->
            val path = Path().apply {
                shape.segs.forEach { seg ->
                    when (seg) {
                        is CatSeg.Move -> moveTo(seg.x, seg.y)
                        is CatSeg.Line -> lineTo(seg.x, seg.y)
                        is CatSeg.Cubic -> cubicTo(seg.x1, seg.y1, seg.x2, seg.y2, seg.x, seg.y)
                        CatSeg.Close -> close()
                    }
                }
            }
            when (shape.fill) {
                CatFill.BLACK -> drawPath(path, Color.Black)
                CatFill.PENCIL -> drawPath(path, Pencil.copy(alpha = shape.alpha))
                CatFill.NONE -> Unit
            }
            if (shape.width > 0f) {
                drawPath(path, Pencil.copy(alpha = shape.alpha * 0.9f), style = Stroke(width = shape.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                withTransform({ translate(0.35f, 0.25f) }) {
                    drawPath(path, Pencil.copy(alpha = shape.alpha * 0.28f), style = Stroke(width = shape.width * 0.6f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
