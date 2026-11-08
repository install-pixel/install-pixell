package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.blacklaunch.diary.ui.SheetInk
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

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

    /** Наскільки верхня сторінка перегорнута: 0 — лежить, 1 — повністю пішла за корінець. */
    internal val progress = Animatable(0f)
    internal var width by mutableFloatStateOf(0f)

    /** Чи рухається зараз сторінка (поки рухається — інші жести на сторінці не діють). */
    val moving: Boolean get() = top != null

    internal var onJump: (suspend (Int) -> Unit)? = null

    /** Перегорнути до сторінки [target] (пошук, календар, закладка, шестерня) — одним плавним рухом. */
    suspend fun flipTo(target: Int) {
        onJump?.invoke(target) ?: run { index = target }
    }
}

/**
 * Єдині правила руху сторінки — однакові всюди: під пальцем, коли відпустив, і коли щоденник гортає сам (шестерня,
 * календар, пошук, закладка). Повний перегорт — сталий час; рух видно від початку до кінця (сторінка докочується за
 * край, а не зникає). Обкладинка — важча й повільніша.
 */
internal object FlipMotion {
    private const val PAGE_MS = 560
    private const val COVER_MS = 820
    private const val MIN_MS = 220

    /** З місця (щоденник гортає сам): плавний розгін і плавне гальмування, симетрично. */
    val FromRest = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** Сторінка вже рухалась під пальцем: без повторного розгону, лише м'яко догальмовує. */
    val FromHand = CubicBezierEasing(0.2f, 0.4f, 0.3f, 1f)

    /** Час на шлях [distance] (частка повного перегорту, 0…1). */
    fun millis(heavy: Boolean, distance: Float): Int {
        val full = if (heavy) COVER_MS else PAGE_MS
        return (full * distance.coerceIn(0f, 1f)).toInt().coerceIn(MIN_MS, full)
    }

    /** Під пальцем: сторінка наздоганяє руку «пружиною» (без ривків від тремтіння), обкладинка — повільніше. */
    fun follow(x: Float, v: Float, target: Float, dt: Float, heavy: Boolean): Pair<Float, Float> {
        val w = if (heavy) 11f else 20f
        val n = ceil(dt / 0.004f).toInt().coerceAtLeast(1)
        val h = dt / n
        var p = x
        var vel = v
        repeat(n) {
            val a = (target - p) * (w * w) - vel * (2f * w)
            vel += a * h
            p += vel * h
        }
        return p to vel
    }
}

/**
 * Книга, що гортається пальцем. [page] малює сторінку за номером (0 — обкладинка). [heavy] — сторінка товща й
 * повільніша (обкладинка). [blocked] — ділянки, де жест гортання не починається (фото й аудіо — там свайп уліво
 * видаляє).
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
    onStart: () -> Unit = {},
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
    val onStarted by rememberUpdatedState(onStart)
    val layer = rememberGraphicsLayer()

    fun heavyNow() = isHeavy(state.top ?: 0) || isHeavy(state.under ?: 0)

    suspend fun settle(complete: Boolean) {
        val top = state.top ?: return
        val target = if (complete == state.forward) 1f else 0f
        val left = abs(target - state.progress.value)
        state.progress.animateTo(target, tween(FlipMotion.millis(heavyNow(), left), easing = FlipMotion.FromHand))
        if (complete) {
            state.index = if (state.forward) state.under!! else top
            haptics.tick()
            flipped(state.index)
        }
        state.top = null
        state.under = null
    }

    state.onJump = jump@{ target ->
        if (target == state.index || target !in 0 until currentCount || state.width <= 0f) {
            state.index = target.coerceIn(0, (currentCount - 1).coerceAtLeast(0))
            return@jump
        }
        val forward = target > state.index
        state.forward = forward
        state.top = if (forward) state.index else target
        state.under = if (forward) target else state.index
        val ms = FlipMotion.millis(heavyNow(), 1f)
        state.progress.snapTo(if (forward) 0f else 1f)
        state.progress.animateTo(if (forward) 1f else 0f, tween(ms, easing = FlipMotion.FromRest))
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
                        val edge = 22.dp.toPx()
                        val p = down.position
                        if (blockers().any { it.contains(p) }) continue
                        val fwdZone = p.x > w * 0.6f && p.x < w - edge && state.index < currentCount - 1 && mayForward(state.index)
                        val backZone = p.x < w * 0.4f && p.x > edge && state.index > 0
                        if (!fwdZone && !backZone) continue
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, p)
                        var started = false
                        var settled = false
                        var forward = fwdZone
                        // Куди тягне палець (у частках перегорту); сторінка щокадру плавно наздоганяє.
                        var target = 0f
                        var speed = 0f
                        var follow: Job? = null
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                if (started) {
                                    val vx = tracker.calculateVelocity().x
                                    val complete = if (state.forward) (target > 0.3f || vx < -800f) && vx < 600f
                                    else (target < 0.7f || vx > 800f) && vx > -600f
                                    ch.consume()
                                    follow?.cancel()
                                    settled = true
                                    scope.launch { settle(complete) }
                                }
                                break
                            }
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            val dx = ch.position.x - p.x
                            val dy = ch.position.y - p.y
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
                                    } else {
                                        state.top = prev(state.index).coerceIn(0, currentCount - 1)
                                        state.under = state.index
                                    }
                                    target = if (forward) 0f else 1f
                                    speed = 0f
                                    val heavy = heavyNow()
                                    follow = scope.launch {
                                        state.progress.snapTo(target)
                                        var before = withFrameNanos { it }
                                        while (true) {
                                            val now = withFrameNanos { it }
                                            val dt = ((now - before) / 1e9f).coerceIn(0f, 0.05f)
                                            before = now
                                            val (pr, v) = FlipMotion.follow(state.progress.value, speed, target, dt, heavy)
                                            speed = v
                                            state.progress.snapTo(pr.coerceIn(0f, 1f))
                                        }
                                    }
                                    haptics.tick()
                                    onStarted()
                                }
                            }
                            if (started) {
                                ch.consume()
                                // Лише вбік: вертикальний рух пальця сторінку не нахиляє (тож вона не «ламається»).
                                target = if (forward) CurlGeometry.forwardProgress(p.x, ch.position.x, w)
                                else CurlGeometry.backwardProgress(p.x, ch.position.x, w)
                            } else if (ch.positionChange() != Offset.Zero && abs(dx) > slop * 3) {
                                break
                            }
                        }
                        // Жест обірвався без «відпустив» (наприклад, система забрала дотик) — сторінка лягає назад.
                        if (started && !settled) {
                            follow?.cancel()
                            scope.launch { settle(false) }
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
            // Знизу — сторінка, що відкривається; зверху — та, що перегортається: намотується на циліндр.
            key(under) { Box(Modifier.fillMaxSize()) { page(under) } }
            key(top) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            state.width = size.width
                            layer.record { this@drawWithContent.drawContent() }
                            val hv = isHeavy(top)
                            drawCurl(CurlGeometry.at(state.progress.value, size.width, hv), layer, hv)
                        },
                ) { page(top) }
            }
        }
        // Розмір книги потрібен і без руху (для переходу «до сторінки»).
        Box(Modifier.fillMaxSize().drawWithContent { state.width = size.width })
    }
}

/**
 * Малює сторінку, загнуту на циліндр: плаский шматок біля корінця, лице на валику (темніє до вершини), зворот на
 * валику й пласкою частиною, що лягла назад. Сторінку смужками перерозміщено по горизонталі — без нахилу, тож згин
 * завжди вертикальний. Торці книжки (риски збоку) верхньої сторінки не малюються — видно лише ті, що під нею.
 */
private fun DrawScope.drawCurl(g: CurlGeometry, layer: GraphicsLayer, heavy: Boolean) {
    val glass = AppStyle.glass
    val w = size.width
    val h = size.height
    val a = g.axis
    val r = g.radius

    // 1) Плаский шматок лицем — як лежав.
    val flatEnd = a.coerceIn(0f, w)
    if (flatEnd > 0f) clipRect(0f, 0f, flatEnd, h) { drawLayer(layer) }

    // 2) Тінь від валика на сторінці під ним (праворуч від валика).
    val rollRight = (a + r).coerceAtMost(w)
    if (rollRight < w && a < w) {
        val len = (w * 0.07f + r * 0.6f)
        drawRect(
            Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent, startX = rollRight, endX = rollRight + len),
            topLeft = Offset(rollRight, 0f),
            size = androidx.compose.ui.geometry.Size(len, h),
        )
    }

    // 3) Лице на валику: від осі до вершини (θ 0…π/2), смужками.
    val steps = 18
    val frontFrom = a.coerceAtLeast(0f)
    for (i in 0 until steps) {
        val t0 = Math.PI.toFloat() / 2f * i / steps
        val t1 = Math.PI.toFloat() / 2f * (i + 1) / steps
        val x0 = a + r * t0
        val x1 = a + r * t1
        if (x1 <= frontFrom || x0 >= w) continue
        strip(layer, x0.coerceAtLeast(0f), x1.coerceAtMost(w), g, h, darkness = 0.6f * (1f - cos((t0 + t1) / 2f)), back = false, heavy = heavy, glass = glass)
    }

    // 4) Зворот на валику (θ π/2…π) і плаский зворот, що ліг назад, — він вище за все інше.
    for (i in 0 until steps) {
        val t0 = Math.PI.toFloat() / 2f * (1f + i.toFloat() / steps)
        val t1 = Math.PI.toFloat() / 2f * (1f + (i + 1).toFloat() / steps)
        val x0 = a + r * t0
        val x1 = a + r * t1
        if (x1 <= 0f || x0 >= w) continue
        // Світло на звороті валика: найяскравіше посередині вигину — так око бачить, що аркуш круглий.
        val mid = (t0 + t1) / 2f
        strip(layer, x0.coerceAtLeast(0f), x1.coerceAtMost(w), g, h, darkness = 0f, back = true, heavy = heavy, glass = glass, light = sin(2f * (mid - Math.PI.toFloat() / 2f)))
    }
    val backFrom = a + r * Math.PI.toFloat()
    if (backFrom < w) {
        // Тінь від пласкої частини звороту на лиці під нею (біля її краю).
        val edge = g.map(w)
        if (edge > 0f) {
            val len = w * 0.05f
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startX = edge - len, endX = edge),
                topLeft = Offset(edge - len, 0f),
                size = androidx.compose.ui.geometry.Size(len, h),
            )
        }
        strip(layer, backFrom.coerceAtLeast(0f), w, g, h, darkness = 0f, back = true, heavy = heavy, glass = glass)
    }

    // 5) Край аркуша: вершина валика й край звороту — тонка лінія (скляна грань чи олівець).
    val line = if (glass) Color.White.copy(alpha = 0.32f) else SheetInk.copy(alpha = 0.6f)
    val stroke = if (heavy) 2.4.dp.toPx() else 1.2.dp.toPx()
    val crest = a + r
    if (crest in 0f..w && a < w) drawLine(line, Offset(crest, 0f), Offset(crest, h), stroke)
    val edge = g.map(w)
    if (backFrom < w && edge in 0f..w) drawLine(line, Offset(edge, 0f), Offset(edge, h), stroke)
}

/**
 * Одна смужка аркуша [x0, x1] (координати на аркуші), перенесена туди, де вона на екрані. Лице — сама сторінка;
 * зворот — папір: у «Аркуші» непрозорий, у «Склі» крізь нього ледь видно написане (дзеркально); обкладинка непрозора.
 */
private fun DrawScope.strip(
    layer: GraphicsLayer,
    x0: Float,
    x1: Float,
    g: CurlGeometry,
    h: Float,
    darkness: Float,
    back: Boolean,
    heavy: Boolean,
    glass: Boolean,
    light: Float = 0f,
) {
    if (x1 - x0 < 0.01f) return
    val d0 = g.map(x0)
    val d1 = g.map(x1)
    val left = minOf(d0, d1)
    val right = maxOf(d0, d1)
    if (right - left < 0.05f) return
    // Смужка трохи ширша (на піксель) — щоб між сусідніми не просвічували щілини.
    clipRect(left - 0.5f, 0f, right + 0.5f, h) {
        if (!back) {
            val k = (d1 - d0) / (x1 - x0)
            withTransform({
                translate(left = d0 - x0 * k)
                scale(scaleX = k, scaleY = 1f, pivot = Offset.Zero)
            }) { drawLayer(layer) }
        } else {
            drawRect(
                when {
                    heavy -> if (glass) Color(0xFF181818) else Color(0xFF121212)
                    glass -> Color(0xFF111111)
                    else -> Color(0xFF0E0E0E)
                },
                topLeft = Offset(left - 0.5f, 0f),
                size = androidx.compose.ui.geometry.Size(right - left + 1f, h),
            )
            if (glass && !heavy) {
                val k = (d1 - d0) / (x1 - x0)
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(left - 0.5f, 0f, right + 0.5f, h), Paint().apply { alpha = 0.12f })
                withTransform({
                    translate(left = d0 - x0 * k)
                    scale(scaleX = k, scaleY = 1f, pivot = Offset.Zero)
                }) { drawLayer(layer) }
                canvas.restore()
            }
        }
        if (light > 0.005f) drawRect(Color.White.copy(alpha = (if (glass) 0.16f else 0.11f) * light), topLeft = Offset(left - 0.5f, 0f), size = androidx.compose.ui.geometry.Size(right - left + 1f, h))
        if (darkness > 0.005f) drawRect(Color.Black.copy(alpha = darkness.coerceIn(0f, 0.9f)), topLeft = Offset(left - 0.5f, 0f), size = androidx.compose.ui.geometry.Size(right - left + 1f, h))
    }
}
