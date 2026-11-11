package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Сторінку малюють лише як силует під прозорою верхньою (нічого не реєструє й не редагується). */
val LocalPagePreview = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Сторінка лежить під тією, яку виривають: її власні розриви й скотч сховані під клаптиком, не плутаються з новим. */
val LocalUnderTear = androidx.compose.runtime.staticCompositionLocalOf { false }

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

    /** Виривання поточної сторінки: наскільки відірвано (0…1) і «падіння» вирваної (0…1); [tearSeed] — для рваного краю. */
    var tear by mutableFloatStateOf(0f)
    var tearFall by mutableFloatStateOf(0f)
    /** Де зараз палець, що тягне нижній лівий кут (координати сторінки); null — не виривають. */
    var tearFinger by mutableStateOf<Offset?>(null)
    /** Колишній заклеєний розрив сторінки, що її виривають: скотч відклеюється й піднімається з кутом. */
    var tapeK by mutableFloatStateOf(0f)
    /** Виривають свіжу сторінку: вона оновиться, тож і клаптик на корінці тане разом із нею. */
    var tearRenew by mutableStateOf(false)
    var tapeSeed by mutableStateOf(0)
    var tapeMargin by mutableFloatStateOf(0f)
    var tearSeed by mutableStateOf(0)

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
    paperSeed: (Int) -> Int = { 0 },
    canFlipForward: (Int) -> Boolean = { true },
    nextOf: (Int) -> Int = { it + 1 },
    prevOf: (Int) -> Int = { it - 1 },
    blocked: () -> List<Rect> = { emptyList() },
    onStart: () -> Unit = {},
    /** Що лежить під сторінкою, яку виривають, коли за нею нічого немає: порожня сторінка (а не обкладинка). */
    blankUnder: (@Composable () -> Unit)? = null,
    onFlipped: (Int) -> Unit = {},
    page: @Composable (Int) -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val currentCount by rememberUpdatedState(count)
    val isEnabled by rememberUpdatedState(enabled)
    val isHeavy by rememberUpdatedState(heavy)
    val seedOf by rememberUpdatedState(paperSeed)
    val mayForward by rememberUpdatedState(canFlipForward)
    val blockers by rememberUpdatedState(blocked)
    val next by rememberUpdatedState(nextOf)
    val prev by rememberUpdatedState(prevOf)
    val flipped by rememberUpdatedState(onFlipped)
    val onStarted by rememberUpdatedState(onStart)
    val layer = rememberGraphicsLayer()
    val tearLayer = rememberGraphicsLayer()
    // «Скло»: розмиті копії сторінки під низом і верхньої — крізь товсте скло видно лише розмиті обриси.
    val underBlur = rememberGraphicsLayer()
    val topBlur = rememberGraphicsLayer()

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
                        val fwdZone = p.x > w * 0.6f && p.x < w - edge && (state.index < currentCount - 1 || next(state.index) in 0 until state.index) && mayForward(state.index)
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
            // «Скло»: аркуші ледь прозорі ([GLASS_PAGE_ALPHA], обкладинка — [GLASS_COVER_ALPHA]) — під поточною сторінкою
            // лежить справжня наступна, і її силует трохи просвічує. Під час гортання вона та сама, тож прозорість
            // природно росте разом із тим, наскільки сторінку піднято. Дотики до неї не доходять.
            val tearing = state.tear > 0f || state.tearFall > 0f
            val peek = when {
                // Виривають сторінку — під нею чітко видно ту, що лишиться: наступну (або попередню, якщо це остання).
                tearing -> if (state.index < currentCount - 1) state.index + 1 else null
                AppStyle.glass && (state.index < currentCount - 1 || next(state.index) in 0 until state.index) && mayForward(state.index) -> next(state.index).coerceIn(0, currentCount - 1)
                else -> null
            }
            if (tearing) {
                // Під вирваною — наступна сторінка, а якщо її немає — порожня (та, що з'явиться після виривання).
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(BookCorner))
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
                ) {
                    CompositionLocalProvider(LocalPagePreview provides true, LocalUnderTear provides true) {
                        if (peek != null) key(peek) { page(peek) } else blankUnder?.invoke()
                    }
                }
            } else if (peek != null && peek != state.index) key(peek) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(BookCorner))
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                        .blur(GlassBlur, BlurredEdgeTreatment.Unbounded),
                ) { CompositionLocalProvider(LocalPagePreview provides true) { page(peek) } }
            }
            key(state.index) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val f = state.tearFinger
                            if (!tearing || f == null) { drawContent(); return@drawWithContent }
                            // Виривають: нижній лівий кут загинається за пальцем (видно зворот), корінець рветься знизу
                            // до лінії загину, верх сторінки лежить на місці. Вирвана — тане.
                            tearLayer.record { this@drawWithContent.drawContent() }
                            drawPeel(TearGeometry(size.width, size.height, f), tearLayer, seedOf(state.index), state.tearSeed, alpha = 1f - state.tearFall, state)
                        },
                ) { page(state.index) }
            }
        } else {
            // Знизу — сторінка, що відкривається; зверху — та, що перегортається: намотується на циліндр.
            // Сторінка під низом — у тій самій формі книжки ([BookCorner]): нічого не визирає з-під верхньої.
            key(under) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(BookCorner))
                        .drawWithContent {
                            drawContent()
                            if (AppStyle.glass) {
                                underBlur.record { this@drawWithContent.drawContent() }
                                underBlur.renderEffect = androidx.compose.ui.graphics.BlurEffect(GlassBlur.toPx(), GlassBlur.toPx())
                            }
                        },
                ) { page(under) }
            }
            key(top) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            state.width = size.width
                            layer.record { this@drawWithContent.drawContent() }
                            val glassNow = AppStyle.glass
                            if (glassNow) {
                                topBlur.record { this@drawWithContent.drawContent() }
                                topBlur.renderEffect = androidx.compose.ui.graphics.BlurEffect(GlassBlur.toPx(), GlassBlur.toPx())
                            }
                            val hv = isHeavy(top)
                            drawCurl(CurlGeometry.at(state.progress.value, size.width, hv), layer, hv, if (glassNow) Blurred(underBlur, topBlur) else null, seedOf(top))
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
private fun DrawScope.drawCurl(g: CurlGeometry, layer: GraphicsLayer, heavy: Boolean, blur: Blurred?, seed: Int) {
    // Усе — лише в межах книжки (із заокругленими кутами): аркуш пришитий до корінця й за книжку не виходить. Біля
    // корінця аркуш лягає ПОВЕРХ рамки сторінки під ним (на півтовщини лінії лівіше), а не під неї.
    val corner = BookCorner.toPx()
    val left = -1.5.dp.toPx()
    val bounds = Path().apply { addRoundRect(RoundRect(left, 0f, size.width, size.height, CornerRadius(corner))) }
    clipPath(bounds) { curlContent(g, layer, heavy, corner, blur, seed) }
}

private fun DrawScope.curlContent(g: CurlGeometry, layer: GraphicsLayer, heavy: Boolean, corner: Float, blur: Blurred?, seed: Int) {
    val glass = AppStyle.glass
    val w = size.width
    val h = size.height
    val a = g.axis
    val r = g.radius

    // 1) Плаский шматок лицем — як лежав.
    val flatEnd = a.coerceIn(0f, w)
    if (flatEnd > 0f) clipRect(0f, 0f, flatEnd, h) { blur?.under(this); drawLayer(layer) }

    // 2) Тінь від валика на сторінці під ним (праворуч від валика).
    val rollRight = g.rightmost.coerceAtMost(w)
    if (rollRight < w && a < w) {
        val len = (w * 0.07f + r * 0.6f)
        // Тінь тане на початку й у кінці руху: коли сторінка лягає (валик іде за корінець), під нею не «спалахує» світло.
        val shade = 0.5f * minOf(((w - a) / (w * 0.1f)).coerceIn(0f, 1f), (rollRight / (w * 0.25f)).coerceIn(0f, 1f))
        drawRect(
            Brush.horizontalGradient(0f to Color.Black.copy(alpha = shade), 1f to Color.Transparent, startX = rollRight, endX = rollRight + len),
            topLeft = Offset(rollRight, 0f),
            size = androidx.compose.ui.geometry.Size(len, h),
        )
    }

    // 3) Лице на валику: від осі до вершини (θ 0…π/2), смужками.
    val steps = 40
    val frontFrom = a.coerceAtLeast(0f)
    for (i in 0 until steps) {
        val t0 = Math.PI.toFloat() / 2f * i / steps
        val t1 = Math.PI.toFloat() / 2f * (i + 1) / steps
        val x0 = a + r * t0
        val x1 = a + r * t1
        if (x1 <= frontFrom || x0 >= w) continue
        strip(layer, x0.coerceAtLeast(0f), x1.coerceAtMost(w), g, h, darkness = 0.6f * (1f - cos((t0 + t1) / 2f)), back = false, heavy = heavy, glass = glass, blur = blur)
    }

    // 4) Зворот на валику (θ π/2…π) і плаский зворот, що ліг назад, — він вище за все інше. Обрізаний тією самою формою,
    //    що й контур (заокруглені кути з боку вільного краю), — щоб сірий кут не виступав за обводку.
    val backFrom = a + r * Math.PI.toFloat()
    val freeEdge = g.map(w)
    if (backFrom < w && freeEdge > 0f) {
        // Тінь від пласкої частини звороту на лиці під нею (біля її краю).
        val len = w * 0.05f
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f * (freeEdge / (w * 0.15f)).coerceIn(0f, 1f)), startX = freeEdge - len, endX = freeEdge),
            topLeft = Offset(freeEdge - len, 0f),
            size = androidx.compose.ui.geometry.Size(len, h),
        )
    }
    if (!g.front(w)) {
        val c = corner.coerceAtMost(((g.rightmost - freeEdge) / 2f).coerceAtLeast(0f))
        val backShape = Path().apply {
            addRoundRect(
                RoundRect(
                    left = freeEdge, top = 0f, right = g.rightmost + 2f, bottom = h,
                    topLeftCornerRadius = CornerRadius(c), topRightCornerRadius = CornerRadius.Zero,
                    bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius(c),
                ),
            )
        }
        clipPath(backShape) {
            for (i in 0 until steps) {
                val t0 = Math.PI.toFloat() / 2f * (1f + i.toFloat() / steps)
                val t1 = Math.PI.toFloat() / 2f * (1f + (i + 1).toFloat() / steps)
                val x0 = a + r * t0
                val x1 = a + r * t1
                if (x1 <= 0f || x0 >= w) continue
                // Світло на звороті валика: найяскравіше посередині вигину — так око бачить, що аркуш круглий.
                val mid = (t0 + t1) / 2f
                strip(layer, x0.coerceAtLeast(0f), x1.coerceAtMost(w), g, h, darkness = 0f, back = true, heavy = heavy, glass = glass, light = sin(2f * (mid - Math.PI.toFloat() / 2f)), blur = blur, seed = seed)
            }
            if (backFrom < w) strip(layer, backFrom.coerceAtLeast(0f), w, g, h, darkness = 0f, back = true, heavy = heavy, glass = glass, blur = blur, seed = seed)
        }
    }

    // 5) Контур звороту: верхній край, вільний край (із заокругленими кутами) і нижній край — як рамка самої сторінки,
    //    тож зворот виглядає аркушем, а не намальованою лінією. Тьмяно, як рамка, і з'являється поступово. Коли видно лише
    //    лице (край ще не перевалив), контуру не треба — рамка сторінки й так стиснута разом із нею.
    val fade = ((w - a) / (w * 0.08f)).coerceIn(0f, 1f)
    val edge = g.map(w)
    val crest = g.rightmost
    if (fade > 0.01f && !g.front(w) && crest - edge > 1f) {
        val inset = 0.5.dp.toPx()
        val c = corner.coerceAtMost((crest - edge) / 2f)
        val outline = Path().apply {
            moveTo(crest, inset)
            lineTo(edge + c, inset)
            quadraticTo(edge + inset, inset, edge + inset, c)
            lineTo(edge + inset, h - c)
            quadraticTo(edge + inset, h - inset, edge + c, h - inset)
            lineTo(crest, h - inset)
        }
        val ink = if (glass) Color.White.copy(alpha = 0.16f * fade) else SheetInk.copy(alpha = 0.4f * fade)
        drawPath(outline, ink, style = Stroke(if (heavy) 1.4.dp.toPx() else 1.dp.toPx()))
    }
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
    blur: Blurred? = null,
    seed: Int = 0,
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
            blur?.under(this)
            val k = (d1 - d0) / (x1 - x0)
            withTransform({
                translate(left = d0 - x0 * k)
                scale(scaleX = k, scaleY = 1f, pivot = Offset.Zero)
            }) { drawLayer(layer) }
        } else {
            val k = (d1 - d0) / (x1 - x0)
            val stripRect = Offset(left - 0.5f, 0f) to androidx.compose.ui.geometry.Size(right - left + 1f, h)
            val tex = if (heavy) PaperTexture.cover else PaperTexture.page
            if (!glass && tex != null) {
                // «Аркуш»: зворот — той самий старий папір (дзеркально), а не рівна заливка.
                withTransform({
                    translate(left = d0 - x0 * k)
                    scale(scaleX = k, scaleY = 1f, pivot = Offset.Zero)
                }) {
                    val dst = androidx.compose.ui.unit.IntSize(size.width.toInt() + 1, size.height.toInt() + 1)
                    drawImage(tex, dstSize = dst)
                    // Зворот — той самий аркуш з другого боку: його власні заломи (той самий варіант, що й на лиці),
                    // дзеркально — геометрія звороту вже віддзеркалює.
                    // Обкладинка теж: її картон пожмаканий так само — на звороті горбки там, де на лиці вм'ятини.
                    run {
                        val v = PaperTexture.Variant.of(seed)
                        withTransform({ scale(if (v.flipX) -1f else 1f, if (v.flipY) -1f else 1f, pivot = Offset(size.width / 2, size.height / 2)) }) {
                            // Рельєф навпаки: вм'ятина з лиця тут — горбок (місце те саме, бо зворот віддзеркалено).
                            PaperTexture.backShadeOf(v)?.let { drawImage(it, dstSize = dst, blendMode = androidx.compose.ui.graphics.BlendMode.Multiply) }
                            PaperTexture.backLightOf(v)?.let { drawImage(it, dstSize = dst, blendMode = androidx.compose.ui.graphics.BlendMode.Screen) }
                        }
                        if (!heavy) drawAging(DiaryViewModel_ageOf(seed), seed * 31 + 7, glass = false)
                    }
                }
            } else {
                // «Скло»: зворот — темна товста плівка, крізь яку розмито видно, що під нею; і ледь — саму сторінку зі
                // зворотного боку (обкладинку — помітніше: фото під склом видно й ззаду).
                blur?.under(this)
                drawRect(
                    when {
                        heavy -> if (glass) Color(0xFF15161A).copy(alpha = 0.82f) else Color(0xFF121212)
                        glass -> Color(0xFF111215).copy(alpha = 0.72f)
                        else -> Color(0xFF0E0E0E)
                    },
                    topLeft = stripRect.first,
                    size = stripRect.second,
                )
                if (glass) {
                    val canvas = drawContext.canvas
                    canvas.saveLayer(Rect(left - 0.5f, 0f, right + 0.5f, h), Paint().apply { alpha = if (heavy) 0.3f else 0.22f })
                    withTransform({
                        translate(left = d0 - x0 * k)
                        scale(scaleX = k, scaleY = 1f, pivot = Offset.Zero)
                    }) { drawLayer(blur?.top ?: layer) }
                    canvas.restore()
                }
            }
        }
        if (light > 0.005f) drawRect(Color.White.copy(alpha = (if (glass) 0.16f else 0.11f) * light), topLeft = Offset(left - 0.5f, 0f), size = androidx.compose.ui.geometry.Size(right - left + 1f, h))
        if (darkness > 0.005f) drawRect(Color.Black.copy(alpha = darkness.coerceIn(0f, 0.9f)), topLeft = Offset(left - 0.5f, 0f), size = androidx.compose.ui.geometry.Size(right - left + 1f, h))
    }
}

/** Наскільки розмите те, що видно крізь товсте скло. */
private val GlassBlur = 9.dp

/** Розмиті копії сторінки під низом ([under]) і верхньої ([top]) — для «Скла». */
private class Blurred(private val underLayer: GraphicsLayer, val top: GraphicsLayer) {
    /** Під прозорим аркушем: замість чіткої сторінки під низом — розмита (поточне обрізання лишається). */
    fun under(scope: DrawScope) = with(scope) {
        drawRect(Color.Black)
        drawLayer(underLayer)
    }
}

/** Загин нижнього лівого кута, що виривається: лежить решта, клапоть — зворот, тінь, рваний край на клапті. */
private fun DrawScope.drawPeel(g: TearGeometry, layer: GraphicsLayer, paperSeed: Int, tearSeed: Int, alpha: Float, state: FlipState) {
    val glass = AppStyle.glass
    fun poly(pts: List<Offset>) = Path().apply {
        if (pts.isEmpty()) return@apply
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
    val canvas = drawContext.canvas
    // 0) Клаптик, що лишається на корінці (ліворуч від розриву, де аркуш уже піднявся), — сама сторінка, під клаптем,
    //    що загинається. Лежить разом із рештою сторінки ОДНИМ шматком (одна межа обрізання), тож по лінії загину на ньому
    //    немає шва. Поки вирвана падає — на всю висоту окремо (решта тане); свіжа оновлюється — тоді тане й він.
    val falling = state.tearFall > 0f
    val kk = if (falling) 1f else g.tearHeight
    val tearX = TEAR_X.toPx()
    val liftedPath = poly(g.lifted)
    val strip = run {
        val pts = tearPoints(tearX, size.height * (1f - kk) - 40.dp.toPx(), size.height, tearSeed, pointed = false)
        Path().apply {
            moveTo(0f, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(0f, pts.lastOrNull()?.y ?: size.height)
            close()
        }
    }
    val hasRemnant = kk > 0.005f
    if (falling && hasRemnant) {
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { this.alpha = if (state.tearRenew) 1f - state.tearFall else 1f })
        clipPath(strip) { drawLayer(layer) }
        drawTornRemnant(tearX, 0f, size.height, tearSeed, glass, fill = true, pointed = false) { }
        canvas.restore()
    }
    canvas.saveLayer(Rect(-size.width, -size.height, size.width * 2, size.height * 2), Paint().apply { this.alpha = alpha })
    // 1) Решта сторінки — як лежала (разом зі смужкою біля корінця).
    val stayArea = if (!falling && hasRemnant) {
        Path.combine(androidx.compose.ui.graphics.PathOperation.Union, poly(g.stay), Path.combine(androidx.compose.ui.graphics.PathOperation.Intersect, strip, liftedPath))
    } else poly(g.stay)
    clipPath(stayArea) { drawLayer(layer) }
    // Розрив біля корінця: тінь смужки на сторінці під нею і сам рваний край — лише там, де аркуш піднявся.
    if (!falling && hasRemnant) clipPath(liftedPath) {
        drawTornRemnant(tearX, size.height * (1f - kk), size.height, tearSeed, glass, fill = true, pointed = false) { }
    }
    if (g.flap.size >= 3) {
        val n = g.normal
        val m = g.mid
        // 2) Тінь клаптя на тому, що під ним, — біля загину густіше.
        //    Лише праворуч від розриву: смужка біля корінця під клаптем не лежить — лінії загину на ній не видно.
        clipPath(poly(g.stay)) {
            clipRect(left = TEAR_X.toPx() + 3.5f * density) {
                drawRect(Brush.linearGradient(0f to Color.Black.copy(alpha = 0.4f), 1f to Color.Transparent, start = m, end = m - n * 26.dp.toPx()))
            }
        }
        // 3) Клапоть — зворот того самого аркуша.
        val mirror = Matrix().apply {
            val k = 2f * (m.x * n.x + m.y * n.y)
            values[Matrix.ScaleX] = 1f - 2f * n.x * n.x
            values[Matrix.SkewX] = -2f * n.x * n.y
            values[Matrix.SkewY] = -2f * n.x * n.y
            values[Matrix.ScaleY] = 1f - 2f * n.y * n.y
            values[Matrix.TranslateX] = k * n.x
            values[Matrix.TranslateY] = k * n.y
        }
        // Клапоть — лише те, що праворуч від лінії розриву: смужка біля корінця лишається на місці (клаптик), тож
        // ні крайня лінія книжки, ні корінець на клапоть не потрапляють. Усе нижче — в координатах самого аркуша, а
        // [mirror] перекладає їх на зворот (дзеркально відносно лінії загину).
        val tearX = TEAR_X.toPx()
        val line = tearPoints(tearX, size.height * (1f - g.tearHeight), size.height, tearSeed).ifEmpty { listOf(Offset(tearX, size.height), Offset(tearX, size.height - 1f)) }
        val far = maxOf(size.width, size.height) * 3f
        fun side(right: Boolean) = Path().apply {
            val edgeX = if (right) far else -far
            moveTo(line.first().x, far)
            line.forEach { lineTo(it.x, it.y) }
            lineTo(line.last().x, -far)
            lineTo(edgeX, -far)
            lineTo(edgeX, far)
            close()
        }
        val rightOfTear = side(true)
        val leftOfTear = side(false)
        val lifted = poly(g.lifted)
        drawIntoCanvas { it.save(); it.concat(mirror) }
        clipPath(lifted) {
            clipPath(rightOfTear) {
                val tex = PaperTexture.page
                if (!glass && tex != null) {
                    val dst = androidx.compose.ui.unit.IntSize(size.width.toInt() + 1, size.height.toInt() + 1)
                    drawImage(tex, dstSize = dst)
                    val v = PaperTexture.Variant.of(paperSeed)
                    withTransform({ scale(if (v.flipX) -1f else 1f, if (v.flipY) -1f else 1f, pivot = Offset(size.width / 2, size.height / 2)) }) {
                        PaperTexture.backShadeOf(v)?.let { drawImage(it, dstSize = dst, blendMode = androidx.compose.ui.graphics.BlendMode.Multiply) }
                        PaperTexture.backLightOf(v)?.let { drawImage(it, dstSize = dst, blendMode = androidx.compose.ui.graphics.BlendMode.Screen) }
                    }
                } else {
                    // «Скло» прозоре: крізь зворот ледь видно написане з того боку.
                    drawRect(Color(0xFF111215).copy(alpha = 0.78f), topLeft = Offset(-far, -far), size = Size(far * 2, far * 2))
                    drawIntoCanvas { c ->
                        c.saveLayer(Rect(-size.width, -size.height, size.width * 2, size.height * 2), Paint().apply { this.alpha = 0.16f })
                        drawLayer(layer)
                        c.restore()
                    }
                }
                // Вигин біля загину: світліше на самому загині, далі тінь.
                drawRect(
                    Brush.linearGradient(0f to Color.White.copy(alpha = if (glass) 0.16f else 0.10f), 0.25f to Color.Transparent, 0.7f to Color.Black.copy(alpha = 0.25f), start = m, end = m + n * 60.dp.toPx()),
                    topLeft = Offset(-far, -far), size = Size(far * 2, far * 2),
                )
                drawPath(lifted, Color.White.copy(alpha = if (glass) 0.22f else 0.3f), style = Stroke(1.6f * density))
            }
            // 3а) Скотч, що тримав розрив, відклеївся й піднявся з кутом. Він заходив за розрив — ця частина виступає
            //     за край клаптя (видно клейкий бік); на самому аркуші скотч з того боку: «Аркуш» його не пропускає,
            //     крізь «Скло» видно тьмяно.
            if (state.tapeK > 0.01f) {
                clipPath(leftOfTear) { drawTapes(state.tapeK, state.tapeSeed, glass, state.tapeMargin, peeled = true) }
                if (glass) clipPath(rightOfTear) {
                    drawIntoCanvas { c ->
                        c.saveLayer(Rect(-size.width, -size.height, size.width * 2, size.height * 2), Paint().apply { this.alpha = 0.3f })
                        drawTapes(state.tapeK, state.tapeSeed, glass, state.tapeMargin, peeled = false)
                        c.restore()
                    }
                }
            }
            // 4) Рваний край клаптя — та сама лінія розриву (серцевина й тінь), з гострим кінчиком угорі.
            for (i in 1 until line.size) {
                val o = ((line[i].y - line.last().y) / 22.dp.toPx()).coerceIn(0f, 1f)
                drawLine(if (glass) Color(0xFF4A505A) else Color(0xFF7D7465), line[i - 1], line[i], (0.7f + 1.9f * o) * density)
                drawLine(Color.Black.copy(alpha = 0.45f), line[i - 1], line[i], (0.4f + 0.4f * o) * density)
            }
        }
        drawIntoCanvas { it.restore() }
    }
    canvas.restore()
}
