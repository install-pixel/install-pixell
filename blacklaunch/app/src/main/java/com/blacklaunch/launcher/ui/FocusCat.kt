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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
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

/** Що котик робить зараз. */
private enum class CatAct {
    WALK,

    /** Удар носом: відсахується назад. */
    RECOIL,

    /** Трусить головою. */
    SHAKE,

    /** Сів на хвіст (після удару — з «зірочками»). */
    SIT,

    /** Присів, лапи на голові (удар маківкою знизу). */
    DUCK,

    /** Чухає голову передньою лапою. */
    SCRATCH,

    /** Стоїть, дивиться під ноги. */
    LOOK_DOWN,

    /** Роззирається. */
    LOOK_AROUND,
}

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

    fun bounds(cx: Float, cy: Float, u: Float) = Rect(cx - 16f * u, cy - 60f * u, cx + 16f * u, cy + 2f * u)

    private fun blocked(cx: Float, cy: Float, w: Float, h: Float, u: Float, fence: Rect?): Boolean {
        val box = bounds(cx, cy, u)
        if (box.left < 0f || box.top < 0f || box.right > w || box.bottom > h) return true
        return fence != null && box.overlaps(fence.inflate(6f * u))
    }
}

/** Поза на цей кадр: усе в «одиницях» (dp), кути в градусах (0 — донизу, додатні — вперед). */
private class CatPose {
    var drop = 0f
    var lean = 0f
    var legBack = 0f
    var legFront = 0f
    var sitting = 0f
    var armBack = 0f
    var armFront = 0f
    var headTilt = 0f
    var headDrop = 0f
    var tailWag = 0f
    var bob = 0f
    var legLen = 18f
    var armLen = 13f
}

/** Плавно сісти / присісти на початку кроку й підвестися наприкінці. */
private fun settle(p: Float, inPart: Float = 0.15f, outPart: Float = 0.15f): Float = when {
    p < inPart -> ease(p / inPart)
    p > 1f - outPart -> ease((1f - p) / outPart)
    else -> 1f
}

private fun ease(t: Float) = (1f - cos(t.coerceIn(0f, 1f) * PI.toFloat())) / 2f

private fun pose(cat: CatSim): CatPose = CatPose().apply {
    val p = cat.progress
    tailWag = sin(cat.time * 2.6f) * 5f
    when (cat.act) {
        CatAct.WALK -> {
            val s = sin(cat.walkPhase)
            legFront = 24f * s
            legBack = -24f * s
            armFront = -26f * s
            armBack = 26f * s
            bob = abs(sin(cat.walkPhase)) * 1.6f
        }
        CatAct.RECOIL -> {
            lean = -16f * sin(PI.toFloat() * p)
            armFront = 50f * sin(PI.toFloat() * p)
            armBack = 35f * sin(PI.toFloat() * p)
            legFront = 10f
            legBack = -10f
        }
        CatAct.SHAKE -> {
            headTilt = sin(cat.actTime * 26f) * 20f * exp(-cat.actTime * 2.2f)
            armFront = 8f
            armBack = -8f
        }
        CatAct.SIT -> {
            val s = settle(p, inPart = if (cat.dazed) 0.08f else 0.2f)
            sitting = s
            drop = 15f * s
            legFront = 82f * s
            legBack = 72f * s
            armFront = 20f * s
            armBack = -25f * s
            tailWag = sin(cat.time * (if (cat.dazed) 1.2f else 1.8f)) * 4f
            headTilt = if (cat.dazed) sin(cat.time * 3f) * 8f * s else 0f
        }
        CatAct.DUCK -> {
            val s = settle(p, inPart = 0.12f, outPart = 0.2f)
            // Присідає, згинаючи лапи (а не провалюючись), і дістає лапами до маківки.
            drop = 6f * s
            headDrop = 3f * s
            legLen = 18f - 5f * s
            legFront = 30f * s
            legBack = -26f * s
            armLen = 13f + 5f * s
            armFront = 168f * s
            armBack = 155f * s
        }
        CatAct.SCRATCH -> {
            val s = settle(p, inPart = 0.15f, outPart = 0.15f)
            armLen = 13f + 3f * s
            armFront = (150f + sin(cat.actTime * 22f) * 12f) * s
            armBack = 6f
            headTilt = 12f * s
            // Якщо до того сидів — чухається сидячи.
        }
        CatAct.LOOK_DOWN -> {
            headTilt = 18f * settle(p, 0.2f, 0.2f)
            armFront = 6f
            armBack = -6f
        }
        CatAct.LOOK_AROUND -> {
            headTilt = sin(p * PI.toFloat() * 2f) * 16f
            armFront = 4f
            armBack = -4f
        }
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

/** Кінець кінцівки довжини [len] під кутом [deg] від вертикалі (додатний — уперед). */
private fun limb(from: Offset, deg: Float, len: Float): Offset {
    val r = deg * PI.toFloat() / 180f
    return Offset(from.x + sin(r) * len, from.y + cos(r) * len)
}

/** Котик дивиться праворуч, (0, 0) — під ногами на землі; дзеркалимо, коли йде ліворуч. */
private fun DrawScope.drawCat(cat: CatSim, u: Float) {
    val pose = pose(cat)
    withTransform({
        translate(cat.x, cat.y)
        scale(cat.dirX, 1f, pivot = Offset.Zero)
        rotate(pose.lean, pivot = Offset.Zero)
    }) {
        val o = { dx: Float, dy: Float -> Offset(dx * u, dy * u) }
        val lift = -pose.bob + pose.drop
        val hip = o(0f, -19f + lift)
        // Хвіст: стоячи — вгору хвилею, сидячи — лежить на землі й закручується.
        val tail = Path().apply {
            moveTo(-6f * u, (-20f + lift) * u)
            if (pose.sitting > 0.5f) {
                cubicTo(-16f * u, -2f * u, (-26f + pose.tailWag) * u, -2f * u, (-28f + pose.tailWag) * u, -10f * u)
            } else {
                cubicTo(-16f * u, (-20f + lift) * u, (-22f + pose.tailWag) * u, (-30f + lift) * u, (-18f + pose.tailWag) * u, (-40f + lift) * u)
            }
        }
        pencilPath(tail, u)
        // Задні лапи: стегно → лапа, ступня вперед.
        listOf(pose.legBack, pose.legFront).forEach { angle ->
            val foot = limb(hip, angle, pose.legLen * u)
            pencilLine(hip, foot, u)
            pencilLine(foot, foot + o(4f, 0f), u)
        }
        // Тулуб стоячи.
        pencilOval(o(-9f, -41f + lift), Size(18f * u, 24f * u), u)
        // Передні лапи від плечей.
        val shoulder = o(2f, -35f + lift)
        listOf(pose.armBack, pose.armFront).forEach { angle ->
            val paw = limb(shoulder, angle, pose.armLen * u)
            pencilLine(shoulder, paw, u)
            drawCircle(Pencil.copy(alpha = 0.7f), radius = 1.4f * u, center = paw)
        }
        // Голова з вухами, оком, носиком і вусами — нахиляється / трусить навколо шиї.
        val neck = o(1f, -40f + lift)
        rotate(pose.headTilt, pivot = neck) {
            val cy = -48f + lift + pose.headDrop
            pencilOval(o(-7f, cy - 8f), Size(18f * u, 16f * u), u)
            pencilLine(o(-5f, cy - 5f), o(-4f, cy - 14f), u)
            pencilLine(o(-4f, cy - 14f), o(0f, cy - 7f), u)
            pencilLine(o(4f, cy - 7f), o(7f, cy - 14f), u)
            pencilLine(o(7f, cy - 14f), o(9f, cy - 4f), u)
            if (cat.dazed && cat.act == CatAct.SIT) {
                // Оглушило: очі «хрестиком» і зірочки довкола голови.
                pencilLine(o(4f, cy - 2f), o(7f, cy + 1f), u)
                pencilLine(o(7f, cy - 2f), o(4f, cy + 1f), u)
                for (i in 0 until 3) {
                    val a = cat.time * 3f + i * 2.1f
                    val c = o(1f + cos(a) * 14f, cy - 12f + sin(a) * 4f)
                    pencilLine(c + o(-2f, 0f), c + o(2f, 0f), u)
                    pencilLine(c + o(0f, -2f), c + o(0f, 2f), u)
                }
            } else if (cat.act == CatAct.DUCK) {
                // Зажмурився.
                pencilLine(o(4f, cy - 1f), o(7f, cy - 1f), u)
            } else {
                drawCircle(Pencil, radius = 1.3f * u, center = o(5.5f, cy - 1f))
            }
            drawCircle(Pencil.copy(alpha = 0.8f), radius = 0.9f * u, center = o(10.5f, cy + 2f))
            pencilLine(o(11f, cy + 3f), o(17f, cy + 2f), u)
            pencilLine(o(11f, cy + 5f), o(16f, cy + 6f), u)
        }
    }
}
