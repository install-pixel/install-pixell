package com.blacklaunch.launcher.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Котик, ніби намальований білим олівцем: повільно гуляє екраном таймера. Плашка з відліком для нього — огорожа:
 * наштовхнувся на неї чи на край екрана — трусить головою, іноді падає, встає й іде в інший бік.
 * Малюється лише поки екран таймера видно, ~30 кадрів на секунду.
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

private enum class CatState { WALK, BUMP, FALL, SIT }

/** Проста «фізика» котика: напрям по горизонталі, невеликий нахил по вертикалі, стани. */
private class CatSim {
    var x = 0f
    var y = 0f
    var dirX = 1f
    var slope = 0f
    var state = CatState.WALK
    var stateTime = 0f
    var walkPhase = 0f
    var time = 0f
    private var nextTurn = 3f
    private var sitFor = 0f
    private val rnd = Random(System.nanoTime())

    fun place(w: Float, h: Float, u: Float, fence: Rect?) {
        for (attempt in 0 until 30) {
            x = w * (0.15f + rnd.nextFloat() * 0.7f)
            y = h * (0.7f + rnd.nextFloat() * 0.22f)
            if (!blocked(x, y, w, h, u, fence)) break
        }
        dirX = if (rnd.nextBoolean()) 1f else -1f
        slope = (rnd.nextFloat() - 0.5f) * 0.6f
        state = CatState.WALK
        stateTime = 0f
    }

    fun step(dt: Float, w: Float, h: Float, u: Float, fence: Rect?) {
        time += dt
        stateTime += dt
        when (state) {
            CatState.WALK -> {
                val speed = 34f * u
                val nx = x + dirX * speed * dt
                val ny = y + slope * speed * dt
                // Огорожа з'явилась, коли котик уже стояв на її місці, — спершу просто виходить звідти.
                val stuck = blocked(x, y, w, h, u, fence)
                val outside = bounds(nx, ny, u).let { it.left < 0f || it.top < 0f || it.right > w || it.bottom > h }
                if (if (stuck) outside else blocked(nx, ny, w, h, u, fence)) {
                    turnAway(nx, ny, w, h, u, fence)
                    enter(CatState.BUMP)
                    return
                }
                x = nx
                y = ny
                walkPhase += dt * 7f
                nextTurn -= dt
                if (nextTurn <= 0f) {
                    nextTurn = 2f + rnd.nextFloat() * 5f
                    when {
                        rnd.nextFloat() < 0.18f -> {
                            sitFor = 2f + rnd.nextFloat() * 3f
                            enter(CatState.SIT)
                        }
                        rnd.nextFloat() < 0.3f -> dirX = -dirX
                        else -> slope = ((rnd.nextFloat() - 0.5f) * 0.8f)
                    }
                }
            }
            // Удар — трусить головою; іноді від подиву падає.
            CatState.BUMP -> if (stateTime > 0.9f) enter(if (rnd.nextFloat() < 0.3f) CatState.FALL else CatState.WALK)
            CatState.FALL -> if (stateTime > 1.8f) enter(CatState.WALK)
            CatState.SIT -> if (stateTime > sitFor) enter(CatState.WALK)
        }
    }

    private fun enter(next: CatState) {
        state = next
        stateTime = 0f
    }

    /** Розвертаємось від того, у що вдарились: бік — по горизонталі, верх/низ — по вертикалі. */
    private fun turnAway(nx: Float, ny: Float, w: Float, h: Float, u: Float, fence: Rect?) {
        val box = bounds(nx, ny, u)
        val hitSide = box.left < 0f || box.right > w ||
            (fence != null && box.overlaps(fence.inflate(6f * u)) && (x < fence.left || x > fence.right))
        if (hitSide) dirX = -dirX
        if (!hitSide || box.top < 0f || box.bottom > h) {
            slope = if (abs(slope) < 0.15f) (if (ny > y || box.bottom > h) -0.4f else 0.4f) else -slope
        }
    }

    fun bounds(cx: Float, cy: Float, u: Float) = Rect(cx - 36f * u, cy - 46f * u, cx + 36f * u, cy + 2f * u)

    private fun blocked(cx: Float, cy: Float, w: Float, h: Float, u: Float, fence: Rect?): Boolean {
        val box = bounds(cx, cy, u)
        if (box.left < 0f || box.top < 0f || box.right > w || box.bottom > h) return true
        return fence != null && box.overlaps(fence.inflate(6f * u))
    }
}

private val Pencil = Color(0xFFEDEDED)

/** Олівцем: основна лінія й ледь зсунута друга, тьмяніша, — як штрих від руки. */
private fun DrawScope.pencilLine(a: Offset, b: Offset, u: Float) {
    drawLine(Pencil.copy(alpha = 0.85f), a, b, strokeWidth = 1.8f * u, cap = StrokeCap.Round)
    drawLine(Pencil.copy(alpha = 0.3f), a + Offset(0.7f * u, 0.5f * u), b + Offset(0.4f * u, -0.3f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
}

private fun DrawScope.pencilPath(path: Path, u: Float) {
    drawPath(path, Pencil.copy(alpha = 0.85f), style = Stroke(width = 1.8f * u, cap = StrokeCap.Round))
    withTransform({ translate(0.7f * u, 0.5f * u) }) {
        drawPath(path, Pencil.copy(alpha = 0.3f), style = Stroke(width = 1.2f * u, cap = StrokeCap.Round))
    }
}

private fun DrawScope.pencilOval(topLeft: Offset, size: Size, u: Float) {
    drawOval(Pencil.copy(alpha = 0.85f), topLeft, size, style = Stroke(width = 1.8f * u))
    drawOval(Pencil.copy(alpha = 0.3f), topLeft + Offset(0.8f * u, 0.6f * u), size, style = Stroke(width = 1.2f * u))
}

/** Котик дивиться праворуч, (0, 0) — між лапами на землі; дзеркалимо, коли йде ліворуч. */
private fun DrawScope.drawCat(cat: CatSim, u: Float) {
    val falling = cat.state == CatState.FALL
    val sitting = cat.state == CatState.SIT
    // Падає на бік за чверть секунди, потім лежить і встає.
    val fallAngle = if (falling) {
        val t = cat.stateTime
        when {
            t < 0.25f -> 90f * (t / 0.25f)
            t < 1.5f -> 90f
            else -> 90f * (1f - (t - 1.5f) / 0.3f).coerceIn(0f, 1f)
        }
    } else {
        0f
    }
    withTransform({
        translate(cat.x, cat.y)
        scale(cat.dirX, 1f, pivot = Offset.Zero)
        rotate(-fallAngle, pivot = Offset.Zero)
    }) {
        val p = { dx: Float, dy: Float -> Offset(dx * u, dy * u) }
        val lift = if (sitting) 6f else 0f
        // Хвіст: хвиля повільніша, коли сидить.
        val wag = sin(cat.time * (if (sitting) 1.5f else 3f)) * 4f
        val tail = Path().apply {
            moveTo(-22f * u, (-20f + lift) * u)
            cubicTo(-32f * u, (-22f + lift) * u, (-36f + wag) * u, (-30f + lift) * u, (-32f + wag) * u, (-40f + lift) * u)
        }
        pencilPath(tail, u)
        // Тулуб.
        pencilOval(p(-24f, -27f + lift), Size(42f * u, 17f * u), u)
        // Лапи: під час ходи — по черзі, сидячи — підібрані.
        val legTop = -12f + lift
        listOf(-15f to 0f, -10f to PI.toFloat(), 9f to PI.toFloat(), 14f to 0f).forEach { (lx, shift) ->
            val swing = if (cat.state == CatState.WALK) sin(cat.walkPhase + shift) * 4f else 0f
            val footY = if (sitting) -3f else 0f
            pencilLine(p(lx, legTop), p(lx + swing, footY), u)
        }
        // Голова (з вухами, оком і вусами) — трусить після удару.
        val shake = if (cat.state == CatState.BUMP) sin(cat.stateTime * 28f) * 22f * exp(-cat.stateTime * 2.5f) else 0f
        rotate(shake, pivot = p(14f, -24f + lift)) {
            val headY = -30f + lift
            pencilOval(p(12f, headY - 9f), Size(18f * u, 17f * u), u)
            pencilLine(p(14f, headY - 6f), p(15f, headY - 15f), u)
            pencilLine(p(15f, headY - 15f), p(19f, headY - 8f), u)
            pencilLine(p(23f, headY - 8f), p(26f, headY - 15f), u)
            pencilLine(p(26f, headY - 15f), p(28f, headY - 5f), u)
            if (falling && cat.stateTime in 0.25f..1.5f) {
                // Очі «хрестиком» — оглушило.
                pencilLine(p(22f, headY - 2f), p(25f, headY + 1f), u)
                pencilLine(p(25f, headY - 2f), p(22f, headY + 1f), u)
            } else {
                drawCircle(Pencil, radius = 1.3f * u, center = p(24f, headY - 1f))
            }
            pencilLine(p(29f, headY + 3f), p(36f, headY + 2f), u)
            pencilLine(p(29f, headY + 5f), p(35f, headY + 6f), u)
        }
    }
}
