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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
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
 * [fences] — плашка відліку, кнопки й позначка вгорі в координатах цього самого полотна (воно на весь екран).
 * Котика можна взяти за шкірку пальцем і перенести — звисає, бовтає лапами, а відпущений приземляється й іде далі.
 */
@Composable
fun FocusCat(fences: List<Rect>, modifier: Modifier = Modifier) {
    val unit = LocalDensity.current.density
    val cat = remember { CatSim() }
    val currentFences by rememberUpdatedState(fences)
    var size by remember { mutableLongStateOf(0L) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(size) {
        val w = (size shr 32).toFloat()
        val h = (size and 0xFFFFFFFF).toFloat()
        if (w <= 0f || h <= 0f) return@LaunchedEffect
        cat.place(w, h, unit, currentFences)
        var last = System.nanoTime()
        while (true) {
            delay(FRAME_MS)
            val now = System.nanoTime()
            cat.step(((now - last) / 1e9f).coerceAtMost(0.1f), w, h, unit, currentFences)
            last = now
            frame++
        }
    }
    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { s: IntSize -> size = (s.width.toLong() shl 32) or s.height.toLong() }
            // Котика можна взяти за шкірку й перенести: тягнемо, лише якщо натиснули саме на нього.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!cat.touches(down.position, unit)) return@awaitEachGesture
                    down.consume()
                    cat.grab(down.position, unit)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        cat.dragTo(change.position, (size shr 32).toFloat(), (size and 0xFFFFFFFF).toFloat(), unit)
                        change.consume()
                    }
                    cat.release((size shr 32).toFloat(), (size and 0xFFFFFFFF).toFloat(), unit, currentFences)
                }
            },
    ) {
        frame // перемальовувати щокадру
        drawCat(cat, unit)
    }
}

private const val FRAME_MS = 33L

/** Де шкірка (за неї тримають) — над потилицею, від лап угору. */
private const val SCRUFF_Y = 50f

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
    /** Скільки ще йти до наступного «рішення» і з якою швидкістю (щоразу інакше). */
    private var walkLeft = 3f
    private var speedFactor = 1f
    /** Роззираючись, повернеться в інший бік посередині. */
    private var lookTurn = false
    private var lookTurned = false
    private val rnd = Random(System.nanoTime())

    /** Випадкова тривалість: частіше коротка, іноді довга (квадрат рівномірного). */
    private fun span(min: Float, max: Float): Float {
        val r = rnd.nextFloat()
        return min + (max - min) * r * r
    }

    private fun chance(p: Float) = rnd.nextFloat() < p

    val progress get() = (actTime / actLength).coerceIn(0f, 1f)

    fun place(w: Float, h: Float, u: Float, fences: List<Rect>) {
        for (attempt in 0 until 30) {
            x = w * (0.15f + rnd.nextFloat() * 0.7f)
            y = h * (0.3f + rnd.nextFloat() * 0.6f)
            if (!blocked(x, y, w, h, u, fences)) break
        }
        dirX = if (rnd.nextBoolean()) 1f else -1f
        slope = randomSlope()
        queue.clear()
        start(CatStep(CatAct.WALK, 0f))
    }

    /** Нахил ходи: і вгору, і вниз однаково часто (раніше котик здебільшого сповзав донизу). */
    private fun randomSlope() = (if (rnd.nextBoolean()) 1f else -1f) * (0.15f + rnd.nextFloat() * 0.55f)

    fun touches(p: Offset, u: Float) = act != CatAct.HELD && bounds(x, y, u).inflate(12f * u).contains(p)

    fun grab(p: Offset, u: Float) {
        queue.clear()
        onEnd = null
        start(CatStep(CatAct.HELD, Float.MAX_VALUE))
        x = p.x
        y = p.y + SCRUFF_Y * u
    }

    fun dragTo(p: Offset, w: Float, h: Float, u: Float) {
        x = p.x.coerceIn(CAT_HALF_WIDTH * u, w - CAT_HALF_WIDTH * u)
        y = (p.y + SCRUFF_Y * u).coerceIn(CAT_HEIGHT * u, h - 2f * u)
    }

    /**
     * Відпустили: приземлився, струсився, трохи подумав і пішов. Поставили на таймер чи кнопку —
     * переноситься на найближче вільне місце (за перешкодою котику не місце).
     */
    fun release(w: Float, h: Float, u: Float, fences: List<Rect>) {
        if (act != CatAct.HELD) return
        if (blocked(x, y, w, h, u, fences)) nearestFree(w, h, u, fences)?.let { (fx, fy) -> x = fx; y = fy }
        dirX = if (rnd.nextBoolean()) 1f else -1f
        slope = randomSlope()
        plan(CatStep(CatAct.LAND, 0.4f), CatStep(CatAct.SHAKE, span(0.4f, 0.9f)), look(span(0.5f, 2.5f)))
    }

    /** Найближче місце, де котик ні на що не налазить: кола довкола, дедалі ширші. */
    private fun nearestFree(w: Float, h: Float, u: Float, fences: List<Rect>): Pair<Float, Float>? {
        var r = 8f * u
        while (r < maxOf(w, h)) {
            for (i in 0 until 24) {
                val a = i * (2f * kotlin.math.PI.toFloat() / 24f)
                val cx = x + kotlin.math.cos(a) * r
                val cy = y + kotlin.math.sin(a) * r
                if (!blocked(cx, cy, w, h, u, fences)) return cx to cy
            }
            r += 8f * u
        }
        return null
    }

    /** Роззирається; іноді посередині повертається глянути в інший бік. */
    private fun look(seconds: Float, then: (() -> Unit)? = null): CatStep {
        return CatStep(CatAct.LOOK_AROUND, seconds, then = then)
    }

    fun step(dt: Float, w: Float, h: Float, u: Float, fences: List<Rect>) {
        time += dt
        if (act == CatAct.HELD) return
        actTime += dt
        when (act) {
            CatAct.WALK -> walk(dt, w, h, u, fences)
            // Відсахується: кілька сантиметрів назад, поки хитається.
            CatAct.RECOIL -> {
                val back = x - dirX * 26f * u * dt * (1f - progress)
                if (!blocked(back, y, w, h, u, fences)) x = back
            }
            else -> Unit
        }
        // Роззирається: посередині, бува, повертається в інший бік — і так і лишається, і йде вже туди.
        if (act == CatAct.LOOK_AROUND && lookTurn && !lookTurned && progress > 0.45f) {
            lookTurned = true
            dirX = -dirX
        }
        if (act != CatAct.WALK && actTime >= actLength) next()
    }

    private fun walk(dt: Float, w: Float, h: Float, u: Float, fences: List<Rect>) {
        val speed = 30f * u * speedFactor
        val nx = x + dirX * speed * dt
        val ny = y + slope * speed * dt
        // Огорожа з'явилась (чи котика поставили) там, де він уже стоїть, — спершу просто виходить звідти.
        val stuck = blocked(x, y, w, h, u, fences)
        val outside = bounds(nx, ny, u).let { it.left < 0f || it.top < 0f || it.right > w || it.bottom > h }
        if (if (stuck) outside else blocked(nx, ny, w, h, u, fences)) {
            react(hitKind(nx, ny, w, h, u, fences))
            return
        }
        x = nx
        y = ny
        walkPhase += dt * 6.5f * speedFactor
        walkLeft -= dt
        if (walkLeft <= 0f) decide()
    }

    /** Прогулянку завершено — що далі. Ймовірності й тривалості щоразу інші, тож поведінка не повторюється шаблоном. */
    private fun decide() {
        val roll = rnd.nextFloat()
        when {
            // Зупинився подумати й роззирнутися.
            roll < 0.24f -> plan(look(span(0.8f, 3.5f)))
            // Почухався, потім ще постояв, подумав.
            roll < 0.36f -> plan(CatStep(CatAct.SCRATCH, span(0.7f, 2.6f)), look(span(0.6f, 2.8f)))
            // Сів відпочити — від секунди до десяти; іноді потім ліг.
            roll < 0.52f -> if (chance(0.3f)) {
                plan(CatStep(CatAct.SIT, span(0.8f, 3f)), CatStep(CatAct.LIE, span(2f, 15f)), CatStep(CatAct.SIT, span(0.6f, 2f)), look(span(0.5f, 2f)))
            } else {
                plan(CatStep(CatAct.SIT, span(1f, 10f)), look(span(0.4f, 2f)))
            }
            // Ліг подрімати.
            roll < 0.62f -> plan(CatStep(CatAct.SIT, span(0.6f, 1.5f)), CatStep(CatAct.LIE, span(1f, 14f)), CatStep(CatAct.SIT, span(0.5f, 2f)))
            // Розвернувся й пішов назад.
            roll < 0.74f -> dirX = -dirX
            // Просто пішов трохи інакше.
            else -> slope = randomSlope()
        }
        walkLeft = span(1.2f, 8f)
        speedFactor = 0.65f + rnd.nextFloat() * 0.6f
    }

    /** Що саме вдарилось: бік (ніс), маківка (знизу під перешкодою / верхній край) чи лапи (згори / нижній край). */
    private fun hitKind(nx: Float, ny: Float, w: Float, h: Float, u: Float, fences: List<Rect>): CatHit {
        val box = bounds(nx, ny, u)
        if (box.left < 0f || box.right > w) return CatHit.SIDE
        if (box.top < 0f) return CatHit.HEAD
        if (box.bottom > h) return CatHit.FEET
        val wall = fences.map { it.inflate(6f * u) }.firstOrNull { box.overlaps(it) } ?: return CatHit.SIDE
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
                val turn = { dirX = -dirX; slope = randomSlope() }
                when {
                    // Сильно: сів на хвіст, оговтався, почухав потилицю, подумав.
                    chance(0.35f) -> plan(
                        CatStep(CatAct.RECOIL, 0.35f), CatStep(CatAct.SIT, span(1.2f, 3.5f), dazed = true),
                        CatStep(CatAct.SCRATCH, span(0.8f, 2.2f)), look(span(0.5f, 2.5f), then = turn),
                    )
                    chance(0.5f) -> plan(CatStep(CatAct.RECOIL, 0.35f), CatStep(CatAct.SHAKE, span(0.5f, 1f)), look(span(0.6f, 2.5f), then = turn))
                    else -> plan(CatStep(CatAct.RECOIL, 0.35f), CatStep(CatAct.SHAKE, span(0.5f, 1f), then = turn))
                }
            }
            CatHit.HEAD -> {
                // Далі — вниз і вбік, щоб обійти перешкоду з краю, а не битися знову.
                val down = { slope = 0.3f + rnd.nextFloat() * 0.3f; if (rnd.nextBoolean()) dirX = -dirX }
                if (chance(0.5f)) {
                    plan(CatStep(CatAct.DUCK, span(0.9f, 1.8f)), CatStep(CatAct.SCRATCH, span(0.7f, 2f)), look(span(0.4f, 2f), then = down))
                } else {
                    plan(CatStep(CatAct.DUCK, span(0.9f, 1.8f)), CatStep(CatAct.SHAKE, span(0.4f, 0.8f), then = down))
                }
            }
            CatHit.FEET -> plan(CatStep(CatAct.LOOK_DOWN, span(0.5f, 1.6f), then = { slope = -(0.3f + rnd.nextFloat() * 0.4f) }))
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
        if (step.act == CatAct.LOOK_AROUND) {
            lookTurn = chance(0.45f)
            lookTurned = false
        }
        if (step.act == CatAct.WALK) {
            walkLeft = span(1.2f, 8f)
            speedFactor = 0.65f + rnd.nextFloat() * 0.6f
        }
        act = step.act
        actTime = 0f
        actLength = step.seconds.coerceAtLeast(0.01f)
        dazed = step.dazed
        onEnd = step.then
    }

    fun bounds(cx: Float, cy: Float, u: Float) = Rect(cx - CAT_HALF_WIDTH * u, cy - CAT_HEIGHT * u, cx + CAT_HALF_WIDTH * u, cy + 2f * u)

    private fun blocked(cx: Float, cy: Float, w: Float, h: Float, u: Float, fences: List<Rect>): Boolean {
        val box = bounds(cx, cy, u)
        if (box.left < 0f || box.top < 0f || box.right > w || box.bottom > h) return true
        return fences.any { box.overlaps(it.inflate(6f * u)) }
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
