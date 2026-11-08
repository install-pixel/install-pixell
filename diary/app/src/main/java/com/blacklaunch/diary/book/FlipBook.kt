package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.LocalHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Стан гортання: яка сторінка відкрита і що зараз «у руці». Сторінку беруть не від самого краю (там системний жест
 * «Назад»), а в зовнішній третині — і перегортають пальцем; відпустив — вона або лягає, або повертається.
 */
@Stable
class FlipState(initial: Int) {
    var index by mutableStateOf(initial)

    /** Сторінка, що перегортається зараз (зверху), і та, що відкривається під нею; null — нічого не рухається. */
    internal var top by mutableStateOf<Int?>(null)
    internal var under by mutableStateOf<Int?>(null)
    internal var forward by mutableStateOf(true)
    internal var grip by mutableStateOf(Offset.Zero)
    internal val corner = Animatable(Offset.Zero, Offset.VectorConverter)
    internal var size by mutableStateOf(androidx.compose.ui.geometry.Size.Zero)

    /** Чи рухається зараз сторінка (поки рухається — інші жести на сторінці не діють). */
    val moving: Boolean get() = top != null

    internal var onJump: (suspend (Int) -> Unit)? = null

    /** Перегорнути до сторінки [target] (пошук, календар, закладка) — одним плавним рухом. */
    suspend fun flipTo(target: Int) {
        onJump?.invoke(target) ?: run { index = target }
    }
}

/**
 * Книга, що гортається пальцем. [page] малює сторінку за номером (0 — обкладинка). [heavy] — сторінка товща й
 * повільніша (обкладинка). [backOf] — колір звороту сторінки. [blocked] — ділянки, де жест гортання не починається
 * (фото й аудіо — там свайп уліво видаляє).
 */
@Composable
fun FlipBook(
    state: FlipState,
    count: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    heavy: (Int) -> Boolean = { false },
    canFlipForward: (Int) -> Boolean = { true },
    nextOf: (Int) -> Int = { it + 1 },
    prevOf: (Int) -> Int = { it - 1 },
    blocked: () -> List<Rect> = { emptyList() },
    onFlipped: (Int) -> Unit = {},
    page: @Composable (Int) -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val currentCount by rememberUpdatedState(count)
    val isEnabled by rememberUpdatedState(enabled)
    val isHeavy by rememberUpdatedState(heavy)
    val mayForward by rememberUpdatedState(canFlipForward)
    val blockers by rememberUpdatedState(blocked)
    val next by rememberUpdatedState(nextOf)
    val prev by rememberUpdatedState(prevOf)
    val flipped by rememberUpdatedState(onFlipped)
    val layer = rememberGraphicsLayer()

    suspend fun settle(complete: Boolean, velocity: Float = 0f) {
        val top = state.top ?: return
        val g = state.grip
        val target = if (complete == state.forward) Offset(-g.x, g.y) else g
        val ms = if (isHeavy(top) || isHeavy(state.under ?: top)) 460 else 300
        // Швидкий кидок — швидше лягає.
        val fast = (abs(velocity) / 4000f).coerceIn(0f, 0.45f)
        state.corner.animateTo(target, tween((ms * (1f - fast)).toInt(), easing = FastOutSlowInEasing))
        if (complete) {
            state.index = if (state.forward) state.under!! else top
            haptics.tick()
            flipped(state.index)
        }
        state.top = null
        state.under = null
    }

    state.onJump = jump@{ target ->
        if (target == state.index || target !in 0 until currentCount || state.size.width <= 0f) {
            state.index = target.coerceIn(0, (currentCount - 1).coerceAtLeast(0))
            return@jump
        }
        val w = state.size.width
        val h = state.size.height
        val g = Offset(w * 0.92f, h * 0.62f)
        state.grip = g
        if (target > state.index) {
            state.forward = true
            state.top = state.index
            state.under = target
            state.corner.snapTo(g)
            state.corner.animateTo(Offset(-g.x, g.y - h * 0.06f), tween(520, easing = FastOutSlowInEasing))
        } else {
            state.forward = false
            state.top = target
            state.under = state.index
            state.corner.snapTo(Offset(-g.x, g.y))
            state.corner.animateTo(g, tween(520, easing = FastOutSlowInEasing))
        }
        state.index = target
        haptics.tick()
        flipped(target)
        state.top = null
        state.under = null
    }

    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Чекаємо дотик (лише дивимось — дітям він теж іде, доки ми не почали гортати).
                        val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed && it.previousPressed.not() } ?: continue
                        if (!isEnabled || state.moving) continue
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val edge = 22.dp.toPx()
                        val p = down.position
                        if (blockers().any { it.contains(p) }) continue
                        val fwdZone = p.x > w * 0.6f && p.x < w - edge && state.index < currentCount - 1 && mayForward(state.index)
                        val backZone = p.x < w * 0.4f && p.x > edge && state.index > 0
                        if (!fwdZone && !backZone) continue
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, p)
                        var started = false
                        var forward = fwdZone
                        var last = p
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                if (started) {
                                    val vx = tracker.calculateVelocity().x
                                    val g = FlipGeometry(w, h, state.grip, state.corner.value)
                                    val complete = if (state.forward) (g.progress > 0.38f || vx < -900f) && vx < 600f
                                    else (g.progress < 0.62f || vx > 900f) && vx > -600f
                                    ch.consume()
                                    scope.launch { settle(complete, vx) }
                                }
                                break
                            }
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            last = ch.position
                            val dx = last.x - p.x
                            val dy = last.y - p.y
                            if (!started) {
                                if (abs(dy) > slop * 1.2f && abs(dy) > abs(dx)) break // вертикальний рух — не гортання
                                val rightWay = if (fwdZone) dx < -slop else dx > slop
                                if (rightWay && abs(dx) > abs(dy)) {
                                    started = true
                                    forward = fwdZone
                                    state.forward = forward
                                    if (forward) {
                                        state.top = state.index
                                        state.under = next(state.index).coerceIn(0, currentCount - 1)
                                        state.grip = Offset(p.x, p.y)
                                    } else {
                                        state.top = prev(state.index).coerceIn(0, currentCount - 1)
                                        state.under = state.index
                                        state.grip = Offset(w * 0.9f, p.y)
                                    }
                                    scope.launch {
                                        state.corner.snapTo(
                                            if (forward) state.grip else Offset(-state.grip.x, state.grip.y),
                                        )
                                    }
                                    haptics.tick()
                                }
                            }
                            if (started) {
                                ch.consume()
                                val target = if (forward) FlipGeometry.forwardCorner(state.grip, p, last, h)
                                else FlipGeometry.backwardCorner(state.grip, p, last, w, h)
                                scope.launch { state.corner.snapTo(target) }
                            } else if (ch.positionChange() != Offset.Zero && abs(dx) > slop * 3) {
                                break
                            }
                        }
                    }
                }
            },
    ) {
        val top = state.top
        val under = state.under
        if (top == null || under == null) {
            key(state.index) { Box(Modifier.fillMaxSize()) { page(state.index) } }
        } else {
            // Знизу — сторінка, що відкривається; зверху — та, що перегортається, обрізана по згину, з клаптем-звортом.
            key(under) { Box(Modifier.fillMaxSize()) { page(under) } }
            key(top) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            state.size = size
                            layer.record { this@drawWithContent.drawContent() }
                            val g = FlipGeometry(size.width, size.height, state.grip, state.corner.value)
                            drawFlip(g, layer, isHeavy(top))
                        },
                ) { page(top) }
            }
        }
        // Розмір книги потрібен і без руху (для переходу «до сторінки»).
        Box(Modifier.fillMaxSize().drawWithContent { state.size = size })
    }
}

private fun polygon(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

private fun DrawScope.drawFlip(g: FlipGeometry, layer: GraphicsLayer, heavy: Boolean) {
    val glass = AppStyle.glass
    val stay = polygon(g.stay)
    val lifted = polygon(g.lifted)
    val flap = polygon(g.flap)
    val n = g.normal
    val m = g.mid
    val p = g.progress

    // 1) Те, що лежить, — як є.
    clipPath(stay) { drawLayer(layer) }

    // 2) Тінь від піднятого аркуша на сторінці під ним — густа біля згину, тане вглиб.
    val shadowLen = (if (heavy) 70.dp else 48.dp).toPx() * (0.4f + p)
    clipPath(lifted) {
        drawRect(
            Brush.linearGradient(
                0f to Color.Black.copy(alpha = 0.55f * (1f - p * 0.5f)),
                1f to Color.Transparent,
                start = m,
                end = m + n * shadowLen,
            ),
        )
    }

    if (g.flap.size < 3) return
    // 3) Тінь клаптя на тому, що лежить: біля згину аркуш піднятий — під ним темніше.
    clipPath(stay) {
        drawRect(
            Brush.linearGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                1f to Color.Transparent,
                start = m,
                end = m - n * (28.dp.toPx() * (0.5f + p)),
            ),
        )
    }

    // 4) Клапоть — зворот сторінки: папір, крізь який ледь просвічує написане (дзеркально).
    clipPath(flap) {
        val back = if (glass) Color(0xFF0C0C0C) else Color(0xFF060606)
        drawRect(if (heavy) Color(0xFF101010) else back)
        val mirror = Matrix().apply {
            val k = 2f * (m.x * n.x + m.y * n.y)
            values[Matrix.ScaleX] = 1f - 2f * n.x * n.x
            values[Matrix.SkewX] = -2f * n.x * n.y
            values[Matrix.SkewY] = -2f * n.x * n.y
            values[Matrix.ScaleY] = 1f - 2f * n.y * n.y
            values[Matrix.TranslateX] = k * n.x
            values[Matrix.TranslateY] = k * n.y
        }
        if (!heavy) {
            drawIntoCanvas { c ->
                c.saveLayer(Rect(-size.width, -size.height, size.width * 2, size.height * 2), Paint().apply { alpha = 0.10f })
                c.concat(mirror)
                drawLayer(layer)
                c.restore()
            }
        }
        // Вигин аркуша: біля згину папір загинається — там найсвітліше (світло падає на вигин), трохи далі — тінь
        // (аркуш іде вниз), до краю клаптя — знову рівніше. Так пласка площина читається як вигнутий аркуш.
        val bend = 46.dp.toPx() * (0.7f + 0.6f * (1f - p))
        drawRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (glass) 0.22f else 0.14f),
                0.18f to Color.White.copy(alpha = 0.05f),
                0.45f to Color.Black.copy(alpha = 0.32f),
                1f to Color.Black.copy(alpha = 0.12f),
                start = m,
                end = m - n * bend * 3f,
            ),
        )
        if (glass) {
            // «Скло»: по звороту пробігає відблиск — смуга, що рухається разом зі згином.
            val c = m - n * bend * (0.6f + p)
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    start = c + n * 18.dp.toPx(),
                    end = c - n * 18.dp.toPx(),
                ),
            )
        }
    }
    // 5) Край клаптя: скляна грань чи лінія олівця.
    drawPath(flap, Color.White.copy(alpha = if (glass) 0.35f else 0.55f), style = Stroke(if (glass) 1.dp.toPx() else 1.6.dp.toPx()))
}
