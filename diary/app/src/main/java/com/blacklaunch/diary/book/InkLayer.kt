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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.input.pointer.PointerType
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
 * Інструменти: олівець (вільне письмо), пензлик (мазок чи заливка), балончик (аерограф чи фарба з балончика),
 * підкреслення (рівні лінії й фігури), маркер (напівпрозорий), гумка (точна, м'яка чи цілими штрихами) і ласо
 * (виділити намальоване, посунути, змінити розмір чи колір). Товщина — плавно від [minW] до [maxW] (частки ширини
 * сторінки), рівномірно на око.
 */
enum class InkTool(val kind: Int, val minW: Float, val maxW: Float, val defSize: Float, val defAlpha: Float) {
    Pencil(InkKind.PENCIL, 0.0015f, 0.03f, 0.35f, 1f),
    Brush(InkKind.BRUSH, 0.005f, 0.09f, 0.45f, 0.9f),
    Spray(InkKind.SPRAY, 0.02f, 0.24f, 0.42f, 0.85f),
    Underline(InkKind.UNDERLINE, 0.002f, 0.03f, 0.4f, 1f),
    Marker(InkKind.MARKER, 0.01f, 0.085f, 0.4f, 0.32f),
    Eraser(InkKind.ERASER, 0.02f, 0.09f, 0.35f, 1f),
    Lasso(InkKind.LASSO, 0.004f, 0.004f, 0f, 1f);

    /** Товщина для положення повзунка [t] (0…1): логарифмічно — кожен крок однаково помітний. */
    fun widthAt(t: Float): Float = minW * Math.pow((maxW / minW).toDouble(), t.coerceIn(0f, 1f).toDouble()).toFloat()
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
    const val FILL = 7
    const val SPRAY = 8
    /** Межі (трафарет): область, за яку не виходить штрих, що на неї посилається ([InkLine.target]). Не малюється. */
    const val CLIP = 9
    const val ERASER = 10
    const val ERASER_SOFT = 11
    /** Позначка «у дня є полотно» (без точок, не малюється). */
    const val CANVAS = 30
    /** Ласо — не штрих, лише інструмент. */
    const val LASSO = -1
}

/**
 * Вигляд лінії ([InkLine.style]):
 *  - олівець: лінія (0 суцільна, 1 пунктир, 3 хвиля);
 *  - пензлик і заливка: `фактура (0 щетинки, 1 суцільна) + 2 × зафарбовування`;
 *  - балончик: 0 аерограф, 1 фарба з балончика;
 *  - підкреслення: `лінія + 4 × фігура (0 лінія, 1 рамка, 2 овал)`;
 *  - маркер: 100.
 * Колір і прозорість — у [InkLine.color]. Давні штрихи (color = 0) ще несуть відтінок і прозорість у style — їх
 * показуємо, як раніше.
 */
object InkStyle {
    const val SOLID = 0
    const val DASHED = 1
    const val DOTTED = 2
    const val WAVY = 3
    val LINES = intArrayOf(SOLID, DASHED, WAVY)
}

/** Номер «сторінки» полотна дня ([InkLine.page]): окремий чистий аркуш після сторінок дня. */
const val CANVAS_PART = 2

/** «Колір чорнила теми» (у [InkLine.color] — замість справжнього RGB): у «Склі» світле, на «Аркуші» — білий олівець. */
const val INK_RGB = 0x010101

/**
 * Палітра — приглушені кольори, що не кричать на темному склі й папері: чорнило теми, графіт, крейда, теплі (червоні,
 * теракота, охра, пісок), зелені, бірюза, сині, фіалка, рожевий, горіх.
 */
internal val Palette = intArrayOf(
    INK_RGB, 0x8C8F96, 0xE6E2DA, 0xD27A70,
    0xA8564F, 0xD39B66, 0xD6C277, 0xA9C27C,
    0x6FAF8A, 0x5FA9A6, 0x72A3CF, 0x7486C9,
    0x9884C9, 0xC985AE, 0xA4825F, 0xC7B498,
)

/** ARGB штриха з кольору палітри й прозорості (0…1). */
fun inkArgb(rgb: Int, alpha: Float): Int = ((alpha * 255f).toInt().coerceIn(8, 255) shl 24) or (rgb and 0xFFFFFF)

/** Давні таблиці (для штрихів, намальованих до палітри). */
internal val InkAlphas = floatArrayOf(1f, 0.6f, 0.32f)
internal val MarkerTints = listOf(Color.Unspecified, Color(0xFFFF8A80), Color(0xFF82B1FF))
internal val MarkerAlphas = floatArrayOf(0.16f, 0.28f, 0.42f)

/** Копія штриха з іншими точками / товщиною / кольором (ласо, перефарбування). */
internal fun InkLine.with(
    xs: FloatArray = this.xs, ys: FloatArray = this.ys, factors: FloatArray = this.factors,
    width: Float = this.width, time: Long = this.time, color: Int = this.color, target: Long = this.target,
): InkLine = InkLine(page, xs, ys, factors, width, time, kind, style, target, color)

/**
 * Режим малювання на сторінці. Пальцем — курсор, як на тачпаді: палець ставиш будь-де, інструмент іде на стільки, на
 * скільки провів. Затримав — клац, інструмент опускається і пише; відпустив — піднімається. Подвійний дотик — лишається
 * опущеним. Стилусом — пише одразу там, де торкнувся, з натиском (долоня в цей час не заважає).
 * Вибір інструментів, товщини, прозорості й кольорів запам'ятовується між сеансами.
 */
@Stable
class InkSession(val day: Long, val part: Int) {
    var tool by mutableStateOf(last.tool)
    private val sizes = mutableStateMapOf<InkTool, Float>().apply { putAll(last.sizes) }
    private val alphas = mutableStateMapOf<InkTool, Float>().apply { putAll(last.alphas) }
    /** Положення повзунка товщини вибраного інструмента (0…1). */
    var size: Float
        get() = sizes[tool] ?: tool.defSize
        set(v) { sizes[tool] = v; last.sizes[tool] = v }
    /** Прозорість вибраного інструмента (0.08…1). */
    var opacity: Float
        get() = alphas[tool] ?: tool.defAlpha
        set(v) { alphas[tool] = v; last.alphas[tool] = v }
    /** Колір (RGB або [INK_RGB]) — спільний для всіх інструментів. */
    var color by mutableIntStateOf(last.color)
    val recent = mutableStateListOf<Int>().apply { addAll(last.recent) }
    var pencilStyle by mutableIntStateOf(last.pencilStyle)
    var smooth by mutableIntStateOf(last.smooth)
    /** Олівець: затримав у кінці — коло, рамка, трикутник чи лінія вирівнюються. */
    var autoShapes by mutableStateOf(last.autoShapes)
    var brushMode by mutableIntStateOf(last.brushMode)
    /** Пензлик: 0 — мазок, 1 — зафарбовування (багато штришків, як щіточкою), 2 — заливка обрису. */
    var brushFill by mutableIntStateOf(last.brushFill)
    /** Пензлик і балончик: почав усередині замкненого обрису — фарба не виходить за його межі (як трафарет). */
    var clip by mutableStateOf(last.clip)
    var sprayMode by mutableIntStateOf(last.sprayMode)
    var lineStyle by mutableIntStateOf(last.lineStyle)
    var shape by mutableIntStateOf(last.shape)
    /** Гумка: 0 точна, 1 м'яка, 2 цілими штрихами. */
    var eraserMode by mutableIntStateOf(last.eraserMode)
    /** М'яка гумка: наскільки розмитий край (0 ледь, 1 помірно, 2 дуже). */
    var eraserSoft by mutableIntStateOf(last.eraserSoft)
    /** Гумка теж плавно згладжує рух (як пензлик); можна вимкнути — тоді точно за пальцем. */
    var eraserSmooth by mutableStateOf(last.eraserSmooth)
    /** Піпетка: наступне «опускання» бере колір з-під кінчика. */
    var picking by mutableStateOf(false)
    /** Шухлядка над панеллю: 0 — закрита, 1 — налаштування інструмента, 2 — кольори. */
    var drawer by mutableIntStateOf(0)
    /** Де кінчик (пікселі сторінки); null — ще не ставили (стане посередині). */
    var pen by mutableStateOf<Offset?>(null)
    var lead by mutableStateOf(false)
    var locked by mutableStateOf(false)
    internal var lastTapAt = 0L
    val undo = mutableStateListOf<List<InkLine>>()
    val redo = mutableStateListOf<List<InkLine>>()
    /** Наближення сторінки двома пальцями: масштаб (1…4) і зсув (пікселі екрана). */
    var zoom by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    /** Виділене ласо: time штрихів і їхня рамка (частки ширини сторінки). */
    var selection by mutableStateOf<Set<Long>>(emptySet())
    var selBounds by mutableStateOf<Rect?>(null)

    val width: Float get() = tool.widthAt(size)
    val kind: Int get() = if (tool == InkTool.Eraser) (if (eraserMode == 1) InkKind.ERASER_SOFT else InkKind.ERASER) else tool.kind
    val style: Int get() = when (tool) {
        InkTool.Pencil -> pencilStyle
        InkTool.Brush -> brushMode + 2 * brushFill
        InkTool.Spray -> sprayMode
        InkTool.Underline -> lineStyle + 4 * shape
        InkTool.Marker -> 100
        InkTool.Eraser -> if (eraserMode == 1) eraserSoft else 0
        else -> 0
    }
    val argb: Int get() = inkArgb(color, opacity)

    fun useColor(rgb: Int) {
        color = rgb
        recent.remove(rgb)
        recent.add(0, rgb)
        while (recent.size > 6) recent.removeAt(recent.size - 1)
        last.color = rgb
        last.recent = recent.toList()
    }

    fun clearSelection() { selection = emptySet(); selBounds = null }

    /** Запам'ятати вибір (для наступного сеансу). */
    fun remember() {
        last.tool = tool; last.pencilStyle = pencilStyle; last.smooth = smooth; last.autoShapes = autoShapes
        last.brushMode = brushMode; last.brushFill = brushFill; last.sprayMode = sprayMode; last.clip = clip
        last.lineStyle = lineStyle; last.shape = shape; last.eraserMode = eraserMode
        last.eraserSoft = eraserSoft; last.eraserSmooth = eraserSmooth
    }

    private object last {
        var tool = InkTool.Pencil
        val sizes = HashMap<InkTool, Float>()
        val alphas = HashMap<InkTool, Float>()
        var color = INK_RGB
        var recent: List<Int> = emptyList()
        var pencilStyle = InkStyle.SOLID
        var smooth = 1
        var autoShapes = true
        var brushMode = 0
        var brushFill = 0
        var sprayMode = 1
        var clip = false
        var lineStyle = InkStyle.SOLID
        var shape = 0
        var eraserMode = 0
        var eraserSoft = 1
        var eraserSmooth = true
    }
}

private const val HOLD_MS = 330L
/** Крапка — лише коли інструмент опустили й ще потримали нерухомо (випадково не ставиться). */
private const val DOT_MS = 420L
/** Затримав олівець у кінці лінії — фігура вирівнюється. */
private const val SNAP_MS = 900L

/**
 * Шар малювання над сторінкою: показує намальоване, а в режимі малювання ([session] не null) — інструмент-курсор.
 * Гумка — теж штрих: стирає те, що під нею, не чіпаючи самої сторінки.
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
    // Ласо, поки обводиш (пікселі), і штрихи, що зараз пересуваються (уже перетворені).
    var lassoPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var moving by remember { mutableStateOf<List<InkLine>?>(null) }
    // Гумка цілими штрихами: що вже зачепила (зникне, коли відпустиш).
    var hidden by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // Межі для штриха, що зараз малюється (почав усередині обрису).
    var liveClip by remember { mutableStateOf<InkLine?>(null) }
    val stylusAt = remember { longArrayOf(0L) }
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
                        val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        if (stylus) stylusAt[0] = down.uptimeMillis
                        else if (down.uptimeMillis - stylusAt[0] < 1500L) {
                            // Долоня, поки пишеш стилусом, — не малює й нічого не рухає.
                            down.consume()
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                                if (e.changes.none { it.pressed }) break
                            }
                            return@awaitEachGesture
                        }
                        down.consume()
                        val w = pageWidth.coerceAtLeast(1f)
                        val h = size.height.toFloat()
                        // Кінчик — лише в межах сторінки (і того її шматка, що видно, коли наближено).
                        fun clampPen(p: Offset): Offset {
                            val z = s.zoom
                            val m = 2.dp.toPx()
                            val l = -s.tx / z
                            val t = -s.ty / z
                            return Offset(p.x.coerceIn(l + m, l + size.width / z - m), p.y.coerceIn(t + m, t + h / z - m))
                        }
                        if (s.pen == null) s.pen = Offset(size.width * 0.5f, h * 0.45f)
                        val mm = density * 160f / 25.4f
                        val stepPx = Ink.STEP_MM * mm
                        val still = 4.dp.toPx()
                        val eraserTip = down.type == PointerType.Eraser
                        val tool = if (eraserTip) InkTool.Eraser else s.tool
                        val kind = if (eraserTip) InkKind.ERASER else s.kind
                        val style = if (eraserTip) 0 else s.style
                        val widthN = if (eraserTip) InkTool.Eraser.widthAt(0.45f) else s.width
                        val argb = s.argb
                        val shapes = tool == InkTool.Underline
                        val filling = tool == InkTool.Brush && s.brushFill == 2
                        val clipOn = s.clip && ((tool == InkTool.Brush && s.brushFill != 2) || tool == InkTool.Spray)
                        val strokeEraser = tool == InkTool.Eraser && !eraserTip && s.eraserMode == 2
                        val lasso = tool == InkTool.Lasso
                        val spray = tool == InkTool.Spray
                        // «Нитка»: на скільки кінчик може відійти від лінії, перш ніж потягне її за собою.
                        val string = if (stylus) mm * 0.12f else mm * when (tool) {
                            InkTool.Pencil -> floatArrayOf(0.15f, 1.1f, 2.2f)[s.smooth.coerceIn(0, 2)]
                            InkTool.Brush -> 1.2f
                            InkTool.Marker -> 1.4f
                            InkTool.Eraser -> if (s.eraserSmooth) 1.2f else 0.3f
                            InkTool.Spray -> 0.6f
                            else -> 0f
                        }
                        // Ще й усереднення сусідніх точок (півширина вікна в точках, крок 0,4 мм): дрібний зигзаг зникає.
                        val avg = if (stylus) 1 else when (tool) {
                            InkTool.Pencil -> intArrayOf(0, 3, 6)[s.smooth.coerceIn(0, 2)]
                            InkTool.Brush, InkTool.Marker -> 3
                            InkTool.Eraser -> if (s.eraserSmooth) 3 else 0
                            else -> 0
                        }
                        fun shown(pts: List<Offset>): List<Offset> = if (avg == 0 || pts.size < 3) pts.toList() else List(pts.size) { i ->
                            val k = minOf(avg, i, pts.size - 1 - i)
                            var sx = 0f
                            var sy = 0f
                            for (j in i - k..i + k) { sx += pts[j].x; sy += pts[j].y }
                            Offset(sx / (2 * k + 1), sy / (2 * k + 1))
                        }
                        var finger = down.position
                        var target = if (stylus) clampPen(down.position) else s.pen!!
                        if (stylus) s.pen = target
                        var moved = 0f
                        var movedDrawing = 0f
                        var stillSince = down.uptimeMillis
                        // «Стоїть» — коли палець не відійшов далі ніж на півтора міліметра від точки, де зупинився
                        // (повільне точне ведення не вважається зупинкою — інструмент не опускається сам).
                        var stillAt = down.position
                        var snapTried = false
                        // Що було до вирівнювання (повів далі — повертаємо) і де стояв палець у ту мить.
                        var rawPts: List<Offset> = emptyList()
                        var rawFs: List<Float> = emptyList()
                        var snapAt = Offset.Zero
                        var drawing = false
                        var beganAt = 0L
                        var snapped = false
                        var anchor = target
                        val pts = ArrayList<Offset>()
                        val fs = ArrayList<Float>()
                        var brush = target
                        var factor = 1f
                        var lastTime = down.uptimeMillis
                        var lastEvent = down.uptimeMillis
                        // Ласо: 0 — обводимо, 1 — посуваємо виділене, 2 — змінюємо розмір (тягнемо за ріжок).
                        var lassoMode = 0
                        val selStart = s.selBounds
                        fun pressureFactor(p: Float) = (0.35f + 0.9f * p).coerceIn(0.3f, 1.3f)
                        fun hideAt(p: Offset) {
                            val q = Offset(p.x / w, p.y / w)
                            val r = widthN / 2f
                            val hit = current.filter { it.page == part && it.kind in DRAWN && it.time !in hidden && hits(it, q, r) }
                            if (hit.isNotEmpty()) { hidden = hidden + hit.map { it.time }; haptics.tick() }
                        }
                        fun begin() {
                            if (s.drawer != 0) s.drawer = 0
                            drawing = true
                            beganAt = android.os.SystemClock.uptimeMillis()
                            s.lead = true
                            brush = target
                            anchor = target
                            movedDrawing = 0f
                            snapped = false
                            pts.clear(); fs.clear()
                            if (s.picking) return
                            if (lasso) {
                                val q = Offset(target.x / w, target.y / w)
                                val b = s.selBounds
                                val grab = 4f * mm / w
                                lassoMode = when {
                                    b != null && s.selection.isNotEmpty() && (q - b.bottomRight).getDistance() < grab * 1.6f -> 2
                                    b != null && s.selection.isNotEmpty() && b.inflate(grab).contains(q) -> 1
                                    else -> { s.clearSelection(); 0 }
                                }
                                if (lassoMode == 0) lassoPath = listOf(target)
                                return
                            }
                            pts += brush; fs += if (stylus) pressureFactor(down.pressure) else 1f
                            if (shapes) { pts += brush; fs += 1f }
                            if (strokeEraser) hideAt(brush)
                            if (clipOn) liveClip = floodFill(current, part, w, h, brush, density, 0, 0, InkKind.CLIP)
                            live = pts.toList(); liveFactors = fs.toList()
                        }
                        // Виділене ласо: тягни пальцем — посуваєш (одразу, без затримки), за ріжок — змінюєш розмір,
                        // торкнувся — зняти виділення.
                        val b0 = s.selBounds
                        if (lasso && s.selection.isNotEmpty() && b0 != null) {
                            val corner = Offset(b0.right * w, b0.bottom * w)
                            val resize = (down.position - corner).getDistance() < 30.dp.toPx() / s.zoom
                            val sel = current.filter { it.time in s.selection && it.page == part }
                            var travelled = 0f
                            var f = down.position
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed || e.changes.count { it.pressed } >= 2) break
                                c.consume()
                                travelled += (c.position - f).getDistance()
                                f = c.position
                                val dragged = (f - down.position) / w
                                val sc = if (resize) ((b0.width + dragged.x) / b0.width.coerceAtLeast(1e-4f)).coerceIn(0.15f, 6f) else 1f
                                val off = if (resize) Offset.Zero else dragged
                                moving = sel.map { transform(it, b0.topLeft, sc, off) }
                                s.selBounds = Rect(b0.left + off.x, b0.top + off.y, b0.left + off.x + b0.width * sc, b0.top + off.y + b0.height * sc)
                            }
                            val mv = moving
                            moving = null
                            if (travelled < 10.dp.toPx()) { s.selBounds = b0; s.clearSelection(); haptics.tick(); return@awaitEachGesture }
                            if (mv != null) {
                                val byTime = mv.associateBy { it.time }
                                s.undo += current
                                s.redo.clear()
                                change(current.map { byTime[it.time] ?: it })
                                s.selBounds = boundsOf(mv)
                            }
                            return@awaitEachGesture
                        }
                        if (stylus || s.locked) begin()
                        while (true) {
                            // Чекаємо руху; без нього — до «затримки» (опустити інструмент), до вирівнювання фігури
                            // чи до наступного «пшику» балончика (фарба набирається, поки тримаєш).
                            val nowT = android.os.SystemClock.uptimeMillis()
                            val wait = when {
                                !drawing -> (HOLD_MS - (nowT - stillSince)).coerceAtLeast(1L)
                                spray && !s.picking -> 28L
                                tool == InkTool.Pencil && s.autoShapes && !snapped && !snapTried && !stylus && pts.size > 8 -> (SNAP_MS - (nowT - stillSince)).coerceAtLeast(1L)
                                else -> 10_000L
                            }
                            val ev = withTimeoutOrNull(wait) { awaitPointerEvent() }
                            if (ev == null) {
                                lastEvent = android.os.SystemClock.uptimeMillis()
                                if (!drawing) {
                                    haptics.keyDown()
                                    begin()
                                    stillSince = lastEvent
                                } else if (spray && !s.picking) {
                                    if (pts.size < 6000) { pts += brush; fs += 1f; live = pts.toList(); liveFactors = fs.toList() }
                                } else if (tool == InkTool.Pencil && s.autoShapes && !snapped && !snapTried && pts.size > 8 && lastEvent - stillSince >= SNAP_MS - 5) {
                                    // Не впізнали фігуру — малюємо далі як є (і не пробуємо знову, доки не рушиш).
                                    snapTried = true
                                    val shape = recognize(shown(pts), mm)
                                    if (shape != null) {
                                        snapped = true
                                        rawPts = pts.toList(); rawFs = fs.toList(); snapAt = finger
                                        haptics.tick()
                                        pts.clear(); pts.addAll(shape)
                                        fs.clear(); repeat(pts.size) { fs += 1f }
                                        live = pts.toList(); liveFactors = fs.toList()
                                    }
                                }
                                continue
                            }
                            // Другий палець, поки малюєш, — не скасовує штрих (просто не зважаємо на нього); наближення — лише
                            // коли інструмент піднятий.
                            if (ev.changes.count { it.pressed } >= 2 && !stylus && !drawing) {
                                // Два пальці: розвів — наближення (до ×4, не менше за сторінку), повів — зсув. Малювання
                                // при цьому скасовується.
                                live = emptyList(); liveFactors = emptyList(); lassoPath = emptyList(); moving = null; hidden = emptySet()
                                if (!s.locked) s.lead = false
                                var e: androidx.compose.ui.input.pointer.PointerEvent = ev
                                var prevC: Offset? = null
                                var prevD = 0f
                                while (true) {
                                    val pr = e.changes.filter { it.pressed }
                                    if (pr.isEmpty()) break
                                    if (pr.size >= 2) {
                                        val z = s.zoom
                                        val t0 = Offset(s.tx, s.ty)
                                        val s0 = pr[0].position * z + t0
                                        val s1 = pr[1].position * z + t0
                                        val cc = (s0 + s1) / 2f
                                        val dd = (s0 - s1).getDistance()
                                        val pc = prevC
                                        if (pc != null && prevD > 0f) {
                                            val nz = (z * dd / prevD).coerceIn(1f, 4f)
                                            val pt = (pc - t0) / z
                                            val nt = cc - pt * nz
                                            s.zoom = nz
                                            s.tx = nt.x.coerceIn(size.width - size.width * nz, 0f)
                                            s.ty = nt.y.coerceIn(h - h * nz, 0f)
                                        }
                                        prevC = cc; prevD = dd
                                    } else prevC = null
                                    e.changes.forEach { it.consume() }
                                    e = awaitPointerEvent()
                                }
                                if (s.zoom < 1.04f) { s.zoom = 1f; s.tx = 0f; s.ty = 0f }
                                s.pen = clampPen(s.pen ?: target)
                                return@awaitEachGesture
                            }
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            lastEvent = c.uptimeMillis
                            if (!c.pressed) break
                            c.consume()
                            val d = if (stylus) c.position - target else c.position - finger
                            finger = c.position
                            val dist = d.getDistance()
                            if (dist > 0f) {
                                moved += dist
                                if (drawing) movedDrawing += dist
                                target = clampPen(target + d)
                            }
                            if ((c.position - stillAt).getDistance() > 1.5.dp.toPx() * (if (stylus) 1f else 1f) && dist > 0f) {
                                stillAt = c.position
                                stillSince = c.uptimeMillis
                                snapTried = false
                            }
                            if (!drawing || filling || s.picking) { s.pen = target; continue }
                            if (lasso) {
                                s.pen = target
                                when (lassoMode) {
                                    0 -> lassoPath = lassoPath + target
                                    else -> {
                                        val b0 = selStart ?: continue
                                        val off = Offset((target.x - anchor.x) / w, (target.y - anchor.y) / w)
                                        val sc = if (lassoMode == 2) ((b0.width + off.x) / b0.width.coerceAtLeast(1e-4f)).coerceIn(0.15f, 6f) else 1f
                                        val move = if (lassoMode == 1) off else Offset.Zero
                                        moving = current.filter { it.time in s.selection && it.page == part }.map { transform(it, b0.topLeft, sc, move) }
                                        s.selBounds = Rect(b0.left + move.x, b0.top + move.y, b0.left + move.x + b0.width * sc, b0.top + move.y + b0.height * sc)
                                    }
                                }
                                continue
                            }
                            if (shapes) {
                                // Підкреслення: від точки, де опустили, до кінчика — рівно.
                                s.pen = target
                                pts[1] = if (s.shape == 0) snapLine(anchor, target) else target
                                live = pts.toList()
                                continue
                            }
                            // Фігуру вирівняно — далі лише відпустити (кінчик лишається в кінці фігури).
                            if (snapped && tool == InkTool.Pencil) {
                                // Вирівнювання лишається, лише якщо відпустити; повів далі — штрих як був, малюємо далі.
                                if ((finger - snapAt).getDistance() < mm * 2.5f) continue
                                snapped = false
                                pts.clear(); pts.addAll(rawPts)
                                fs.clear(); fs.addAll(rawFs)
                                haptics.tick()
                            }
                            val gapV = target - brush
                            val gapLen = gapV.getDistance()
                            if (gapLen > string) brush += gapV * (1f - string / gapLen)
                            s.pen = brush
                            if (strokeEraser) { hideAt(brush); continue }
                            val step = hypot(brush.x - pts.last().x, brush.y - pts.last().y)
                            if (step >= stepPx) {
                                if (stylus) factor = pressureFactor(c.pressure) else {
                                    val dt = (c.uptimeMillis - lastTime).coerceAtLeast(1)
                                    val speed = step / mm / dt
                                    val tf = 1.1f - 0.35f * (speed / Ink.FAST).coerceIn(0f, 1f)
                                    factor += (tf - factor) * 0.25f
                                }
                                pts += brush; fs += factor
                                lastTime = c.uptimeMillis
                                live = shown(pts); liveFactors = fs.toList()
                            }
                        }
                        // Відпустив палець (чи підняв стилус).
                        val tap = !stylus && moved < 10.dp.toPx() && (lastEvent - down.uptimeMillis) < 230
                        if (tap) {
                            if (lasso && s.selection.isNotEmpty()) { s.clearSelection(); haptics.tick() }
                            // Зафіксований (опущений) інструмент — дотик його піднімає: ніколи не «застрягає».
                            else if (s.locked) { s.locked = false; s.lead = false; s.lastTapAt = 0L; haptics.keyUp() }
                            else if (down.uptimeMillis - s.lastTapAt < 330) {
                                s.locked = !s.locked
                                s.lastTapAt = 0L
                                haptics.longPress()
                            } else s.lastTapAt = down.uptimeMillis
                            pts.clear()
                        }
                        val heldStill = drawing && !tap && movedDrawing < still * 0.5f && lastEvent - beganAt >= DOT_MS
                        live = emptyList(); liveFactors = emptyList()
                        if (!s.locked && s.lead) { s.lead = false; if (!tap) haptics.keyUp() }
                        if (s.locked) s.lead = true
                        if (stylus) s.pen = target
                        // Піпетка: колір з-під кінчика.
                        if (s.picking) {
                            if (drawing && !tap) {
                                val rgb = sampleColor(current, part, w, h, s.pen ?: target, density)
                                if (rgb != null) { s.useColor(rgb); s.picking = false; haptics.longPress() } else haptics.tick()
                            }
                            return@awaitEachGesture
                        }
                        // Ласо: обвів — виділив; посунув / розтягнув — записати.
                        if (lasso) {
                            if (lassoMode == 0) {
                                val poly = lassoPath.map { Offset(it.x / w, it.y / w) }
                                lassoPath = emptyList()
                                if (drawing && poly.size > 4) {
                                    val drawn = current.filter { it.page == part && it.kind in DRAWN && inside(it, poly) }
                                    // Разом зі штрихами — і їхні межі (щоб не лишились на старому місці).
                                    val clipIds = drawn.map { it.target }.filter { it != 0L }.toSet()
                                    val sel = drawn + current.filter { it.kind == InkKind.CLIP && it.time in clipIds }
                                    if (sel.isNotEmpty()) {
                                        s.selection = sel.map { it.time }.toSet()
                                        s.selBounds = boundsOf(sel)
                                        s.drawer = 1
                                        haptics.longPress()
                                    }
                                }
                            } else {
                                val mv = moving
                                moving = null
                                if (mv != null) {
                                    val byTime = mv.associateBy { it.time }
                                    s.undo += current
                                    s.redo.clear()
                                    change(current.map { byTime[it.time] ?: it })
                                    s.selBounds = boundsOf(mv)
                                }
                            }
                            return@awaitEachGesture
                        }
                        // Гумка цілими штрихами: прибрати все, що зачепила.
                        if (strokeEraser) {
                            val gone = hidden
                            hidden = emptySet()
                            if (gone.isNotEmpty()) {
                                s.undo += current
                                s.redo.clear()
                                change(current.filter { it.time !in gone })
                            }
                            return@awaitEachGesture
                        }
                        // Заливка: пензлик опустили й підняли, не рухаючи, — зафарбувати замкнене місце під кінчиком.
                        if (filling) {
                            if (drawing && !tap && movedDrawing < still) {
                                val fill = floodFill(current, part, w, h, s.pen ?: brush, density, style, argb)
                                if (fill != null) {
                                    s.undo += current
                                    s.redo.clear()
                                    change(current + fill)
                                } else haptics.tick()
                            }
                            return@awaitEachGesture
                        }
                        // Крапка: опустили, ще потримали нерухомо й підняли (олівець, пензлик, маркер).
                        if (!shapes && !spray && tool != InkTool.Eraser && pts.size == 1) {
                            if (heldStill) {
                                pts += pts[0] + Offset(0.01f, 0f); fs += 1f
                                haptics.tick()
                            } else pts.clear()
                        }
                        val clipLine = liveClip
                        liveClip = null
                        val enough = if (shapes) pts.size == 2 && (pts[1] - pts[0]).getDistance() > mm * 2f else pts.size >= 2
                        if (enough) {
                            if (!shapes && !snapped) { val sm = shown(pts); pts.clear(); pts.addAll(sm) }
                            val line = InkLine(
                                page = part,
                                xs = FloatArray(pts.size) { pts[it].x / w },
                                ys = FloatArray(pts.size) { pts[it].y / w },
                                factors = if (shapes) FloatArray(0) else FloatArray(fs.size) { fs[it] },
                                width = widthN,
                                time = System.currentTimeMillis(),
                                kind = kind,
                                style = style,
                                color = if (kind >= InkKind.ERASER) 0 else argb,
                                target = clipLine?.time ?: 0L,
                            )
                            if (line.kind >= InkKind.ERASER) {
                                val hitDry = current.any { it.page == part && it.kind < InkKind.ERASER && dry(it) }
                                if (hitDry) onDryErase()
                            }
                            s.undo += current
                            s.redo.clear()
                            change(current + listOfNotNull(clipLine) + line)
                        }
                    }
                },
            ),
    ) {
        val w = size.width
        val shownLines = moving?.let { mv -> val m = mv.associateBy { it.time }; lines.map { m[it.time] ?: it } } ?: lines
        // Увесь малюнок — в окремому шарі: гумка стирає лише намальоване, а не папір під ним.
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        drawInk(shownLines, part, w, dry, hidden)
        if (live.size >= 2 && session != null) {
            val tl = session.tool
            val lc = liveClip
            if (lc != null) drawContext.canvas.save()
            if (lc != null) drawContext.canvas.clipPath(regionPath(lc, w))
            drawLayered(
                FloatArray(live.size) { live[it].x / w },
                FloatArray(live.size) { live[it].y / w },
                FloatArray(liveFactors.size) { liveFactors[it] },
                session.width * w, w, false, session.kind, session.style,
                if (tl == InkTool.Eraser) 0 else session.argb,
            )
            if (lc != null) drawContext.canvas.restore()
        }
        drawContext.canvas.restore()
        if (session != null) {
            val ink = if (AppStyle.glass) Oled.TextPrimary else SheetInk
            val z = session.zoom
            // Ласо, поки обводиш, і рамка виділеного з ріжком для розміру.
            if (lassoPath.size > 1) {
                drawPath(polyline(FloatArray(lassoPath.size) { lassoPath[it].x }, FloatArray(lassoPath.size) { lassoPath[it].y }), ink.copy(alpha = 0.8f), style = Stroke(1.4.dp.toPx() / z, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx() / z, 4.dp.toPx() / z))))
            }
            session.selBounds?.takeIf { session.selection.isNotEmpty() }?.let { b ->
                val r = Rect(b.left * w, b.top * w, b.right * w, b.bottom * w).inflate(4.dp.toPx() / z)
                drawRect(ink.copy(alpha = 0.75f), r.topLeft, r.size, style = Stroke(1.2.dp.toPx() / z, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx() / z, 4.dp.toPx() / z))))
                drawCircle(ink.copy(alpha = 0.9f), 6.dp.toPx() / z, r.bottomRight)
                drawCircle(Color.Black.copy(alpha = 0.6f), 3.5.dp.toPx() / z, r.bottomRight)
            }
            // Інструмент-курсор: не виходить за сторінку і не росте, коли сторінку наближено.
            val p = session.pen ?: Offset(size.width * 0.5f, size.height * 0.45f)
            clipRect {
                scale(1f / z, 1f / z, p) { drawToolCursor(p, session, session.width * w * z, leadAnim) }
            }
        }
    }
}

/** Що малюється (і що можна виділити ласо чи стерти цілим штрихом). */
private val DRAWN = setOf(InkKind.PEN, InkKind.HARD, InkKind.SOFT, InkKind.MARKER, InkKind.PENCIL, InkKind.UNDERLINE, InkKind.BRUSH, InkKind.FILL, InkKind.SPRAY)

// ---------- Геометрія штрихів (частки ширини сторінки) ----------

/** Відстань від точки до ламаної. */
private fun distTo(xs: FloatArray, ys: FloatArray, q: Offset): Float {
    if (xs.isEmpty()) return Float.MAX_VALUE
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

/** Чи зачіпає коло [q], [r] штрих [l]. */
private fun hits(l: InkLine, q: Offset, r: Float): Boolean {
    if (l.kind == InkKind.FILL) {
        val hh = l.width / 2f
        for (i in l.xs.indices) if (kotlin.math.abs(l.ys[i] - q.y) <= hh + r && q.x >= l.xs[i] - r && q.x <= (l.factors.getOrNull(i) ?: l.xs[i]) + r) return true
        return false
    }
    val (xs, ys) = shapeOf(l)
    val reach = if (l.kind == InkKind.SPRAY) l.width / 2f else l.width / 2f
    return distTo(xs, ys, q) <= reach + r
}

/** Точка в многокутнику (парність перетинів). */
private fun inPoly(p: Offset, poly: List<Offset>): Boolean {
    var c = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-9f) + a.x) c = !c
        j = i
    }
    return c
}

/** Штрих у ласо: щонайменше 60 % його точок усередині. */
private fun inside(l: InkLine, poly: List<Offset>): Boolean {
    val (xs, ys) = if (l.kind == InkKind.FILL) FloatArray(l.xs.size) { (l.xs[it] + (l.factors.getOrNull(it) ?: l.xs[it])) / 2f } to l.ys else shapeOf(l)
    if (xs.isEmpty()) return false
    val stepI = maxOf(1, xs.size / 60)
    var inn = 0
    var all = 0
    var i = 0
    while (i < xs.size) { all++; if (inPoly(Offset(xs[i], ys[i]), poly)) inn++; i += stepI }
    return inn >= all * 0.6f
}

/** Рамка навколо штрихів (з їхньою товщиною). */
private fun boundsOf(ls: List<InkLine>): Rect? {
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    ls.forEach { ln ->
        val (xs, ys) = shapeOf(ln)
        val hw = ln.width / 2f
        for (i in xs.indices) {
            val x0 = xs[i]
            val x1 = if (ln.kind == InkKind.FILL || ln.kind == InkKind.CLIP) ln.factors.getOrNull(i) ?: x0 else x0
            l = minOf(l, x0 - hw); r = maxOf(r, x1 + hw); t = minOf(t, ys[i] - hw); b = maxOf(b, ys[i] + hw)
        }
    }
    return if (l == Float.MAX_VALUE) null else Rect(l, t, r, b)
}

/** Перетворити штрих: масштаб [sc] від точки [pivot], потім зсув [off]. */
private fun transform(l: InkLine, pivot: Offset, sc: Float, off: Offset): InkLine {
    fun tx(x: Float) = (x - pivot.x) * sc + pivot.x + off.x
    fun ty(y: Float) = (y - pivot.y) * sc + pivot.y + off.y
    return l.with(
        xs = FloatArray(l.xs.size) { tx(l.xs[it]) },
        ys = FloatArray(l.ys.size) { ty(l.ys[it]) },
        factors = if (l.kind == InkKind.FILL || l.kind == InkKind.CLIP) FloatArray(l.factors.size) { tx(l.factors[it]) } else l.factors,
        width = l.width * sc,
    )
}

/**
 * Розпізнати фігуру в ламаній (пікселі): майже пряма — лінія; замкнена з трьома кутами — трикутник, з чотирма —
 * прямокутник (рівний, якщо сторони майже горизонтальні й вертикальні); кругла — коло чи овал. Інакше — null (лишається,
 * як намальовано).
 */
private fun recognize(pts: List<Offset>, mm: Float): List<Offset>? {
    if (pts.size < 8) return null
    var len = 0f
    for (i in 1 until pts.size) len += (pts[i] - pts[i - 1]).getDistance()
    if (len < mm * 14f) return null
    val a = pts.first()
    val b = pts.last()
    fun dense(poly: List<Offset>): List<Offset> {
        val xs = FloatArray(poly.size) { poly[it].x }
        val ys = FloatArray(poly.size) { poly[it].y }
        val (rx, ry) = resample(xs, ys, Ink.STEP_MM * mm)
        return List(rx.size) { Offset(rx[it], ry[it]) }
    }
    // Лінія.
    val chord = (b - a).getDistance()
    if (chord > len * 0.9f) {
        val dir = (b - a) / chord.coerceAtLeast(1e-3f)
        val dev = pts.maxOf { kotlin.math.abs((it.x - a.x) * dir.y - (it.y - a.y) * dir.x) }
        if (dev < maxOf(len * 0.05f, mm * 1.2f)) return dense(listOf(a, snapLine(a, b)))
        return null
    }
    val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
    val diag = hypot(maxX - minX, maxY - minY)
    if (chord > maxOf(diag * 0.28f, mm * 5f)) return null
    // Замкнена: кути (спрощення ламаної).
    val closed = pts + a
    val simp = douglasPeucker(closed, diag * 0.09f)
    val corners = simp.size - 1
    if (corners == 3 || corners == 4) {
        val poly = simp.dropLast(1)
        if (corners == 4) {
            val axis = (0 until 4).all { i ->
                val p = poly[i]; val q = poly[(i + 1) % 4]
                val ang = Math.toDegrees(kotlin.math.atan2((q.y - p.y).toDouble(), (q.x - p.x).toDouble())).let { ((it % 90) + 90) % 90 }
                ang < 14 || ang > 76
            }
            val quad = if (axis) listOf(Offset(minX, minY), Offset(maxX, minY), Offset(maxX, maxY), Offset(minX, maxY)) else poly
            return dense(quad + quad.first() + quad[1].let { quad.first() + (it - quad.first()) * 0.06f })
        }
        return dense(poly + poly.first() + (poly.first() + (poly[1] - poly.first()) * 0.06f))
    }
    // Коло / овал.
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    val rx = ((maxX - minX) / 2f).coerceAtLeast(1f)
    val ry = ((maxY - minY) / 2f).coerceAtLeast(1f)
    val err = pts.sumOf { kotlin.math.abs(hypot((it.x - cx) / rx, (it.y - cy) / ry) - 1f).toDouble() } / pts.size
    if (err < 0.11) {
        val n = 96
        return List(n + 6) { i ->
            val ang = -PI.toFloat() / 2 + 2f * PI.toFloat() * i / n
            Offset(cx + rx * cos(ang), cy + ry * sin(ang))
        }
    }
    return null
}

/** Спрощення ламаної (Дуглас — Пекер). */
private fun douglasPeucker(p: List<Offset>, eps: Float): List<Offset> {
    if (p.size < 3) return p
    val a = p.first()
    val b = p.last()
    var idx = 0
    var dmax = 0f
    val ab = b - a
    val l = ab.getDistance()
    for (i in 1 until p.size - 1) {
        val d = if (l < 1e-3f) (p[i] - a).getDistance() else kotlin.math.abs(ab.x * (a.y - p[i].y) - ab.y * (a.x - p[i].x)) / l
        if (d > dmax) { dmax = d; idx = i }
    }
    if (l < 1e-3f && idx == 0) return listOf(a, b)
    return if (dmax > eps) douglasPeucker(p.subList(0, idx + 1), eps).dropLast(1) + douglasPeucker(p.subList(idx, p.size), eps) else listOf(a, b)
}

/** Колір малюнка під точкою (піпетка) — RGB, або [INK_RGB], якщо це колір чорнила теми; null — там порожньо. */
private fun sampleColor(lines: List<InkLine>, part: Int, pageW: Float, pageH: Float, at: Offset, density: Float): Int? {
    val cols = FILL_COLS
    val cell = pageW / cols
    val rows = kotlin.math.ceil(pageH / cell).toInt().coerceAtLeast(1)
    val img = androidx.compose.ui.graphics.ImageBitmap(cols, rows)
    val canvas = androidx.compose.ui.graphics.Canvas(img)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(density / cell), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, Size(cols.toFloat(), rows.toFloat()),
    ) {
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        drawInk(lines, part, cols.toFloat(), { false }, emptySet())
        drawContext.canvas.restore()
    }
    val x = (at.x / cell).toInt().coerceIn(1, cols - 2)
    val y = (at.y / cell).toInt().coerceIn(1, rows - 2)
    val px = IntArray(9)
    img.readPixels(px, x - 1, y - 1, 3, 3)
    val best = px.maxByOrNull { it ushr 24 } ?: return null
    if ((best ushr 24) < 40) return null
    val rgb = best and 0xFFFFFF
    val ink = (if (AppStyle.glass) FreshInk else SheetInk).let { (it.red * 255).toInt() shl 16 or ((it.green * 255).toInt() shl 8) or (it.blue * 255).toInt() }
    fun ch(v: Int, s: Int) = (v shr s) and 0xFF
    val near = kotlin.math.abs(ch(rgb, 16) - ch(ink, 16)) + kotlin.math.abs(ch(rgb, 8) - ch(ink, 8)) + kotlin.math.abs(ch(rgb, 0) - ch(ink, 0)) < 40
    return if (near) INK_RGB else rgb
}

/**
 * Усі штрихи сторінки по черзі (гумка стирає те, що під нею). Давні гумки, що стирали лише один штрих, — до свого
 * штриха. [hidden] — штрихи, які зараз стирає гумка цілими штрихами (не малюємо).
 */
private fun DrawScope.drawInk(lines: List<InkLine>, part: Int, w: Float, dry: (InkLine) -> Boolean, hidden: Set<Long>) {
    val targeted = HashMap<Long, MutableList<InkLine>>()
    lines.forEach { if (it.page == part && it.kind >= InkKind.ERASER && it.kind != InkKind.CANVAS && it.target != 0L) targeted.getOrPut(it.target) { ArrayList() } += it }
    val clips = HashMap<Long, InkLine>()
    lines.forEach { if (it.page == part && it.kind == InkKind.CLIP) clips[it.time] = it }
    lines.forEach { l ->
        if (l.page != part || l.kind == InkKind.CANVAS || l.kind == InkKind.CLIP || l.time in hidden) return@forEach
        // Штрих у межах обрису — обрізаний по них.
        val clip = if (l.kind < InkKind.ERASER && l.target != 0L) clips[l.target] else null
        if (clip != null) {
            drawContext.canvas.save()
            drawContext.canvas.clipPath(regionPath(clip, w))
            drawLayered(l.xs, l.ys, l.factors, l.width * w, w, dry(l), l.kind, l.style, l.color)
            drawContext.canvas.restore()
            return@forEach
        }
        if (l.kind >= InkKind.ERASER) {
            if (l.target == 0L) drawStroke(l.xs, l.ys, l.factors, l.width * w, w, false, l.kind, l.style)
            return@forEach
        }
        val ts = targeted[l.time]
        if (ts == null) {
            drawLayered(l.xs, l.ys, l.factors, l.width * w, w, dry(l), l.kind, l.style, l.color)
        } else {
            drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
            drawStroke(l.xs, l.ys, l.factors, l.width * w, w, dry(l), l.kind, l.style, color = l.color)
            ts.forEach { e -> drawStroke(e.xs, e.ys, e.factors, e.width * w, w, false, e.kind, e.style) }
            drawContext.canvas.restore()
        }
    }
}

/**
 * Штрих із прозорістю — окремим шаром: усередині він непрозорий (не темніє там, де перетинає сам себе чи складається з
 * кількох смуг), а вже весь шар лягає з потрібною прозорістю — різні штрихи накладаються рівно, як у малювальних
 * програмах. Заливка лягає ПІД уже намальоване (під обвідку), а не поверх.
 */
private fun DrawScope.drawLayered(xs: FloatArray, ys: FloatArray, factors: FloatArray, base: Float, w: Float, dry: Boolean, kind: Int, style: Int, color: Int) {
    val op = if (color != 0) (color ushr 24) / 255f else strokeOpacity(kind, style, AppStyle.glass)
    if (kind != InkKind.FILL && kind != InkKind.SPRAY && op >= 0.999f) { drawStroke(xs, ys, factors, base, w, dry, kind, style, color = color); return }
    val paint = Paint().apply {
        alpha = op
        if (kind == InkKind.FILL) blendMode = BlendMode.DstOver
    }
    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
    drawStroke(xs, ys, factors, base, w, dry, kind, style, layered = true, color = color)
    drawContext.canvas.restore()
}

/** Прозорість давнього штриха (до палітри) — з його style. */
private fun strokeOpacity(kind: Int, style: Int, glass: Boolean): Float = when (kind) {
    InkKind.PENCIL, InkKind.BRUSH, InkKind.SPRAY, InkKind.FILL -> InkAlphas[((style % 100) / 10).coerceIn(0, 2)]
    InkKind.UNDERLINE -> InkAlphas[(style / 100).coerceIn(0, 2)]
    InkKind.MARKER -> if (style >= 100) MarkerAlphas[((style - 100) % 10).coerceIn(0, 2)] * (if (glass) 0.85f else 1f) else 1f
    else -> 1f
}

/** Відтінок давнього штриха (до палітри); Unspecified — колір чорнила. */
private fun tintOf(kind: Int, style: Int): Color = when (kind) {
    InkKind.MARKER -> if (style >= 100) MarkerTints.getOrNull((style - 100) / 10) ?: Color.Unspecified else Color.Unspecified
    InkKind.BRUSH, InkKind.SPRAY, InkKind.FILL -> MarkerTints.getOrNull((style / 100).coerceIn(0, 2)) ?: Color.Unspecified
    else -> Color.Unspecified
}

/** Межі ([InkKind.CLIP]) чи заливка — як контур із рядків. */
private fun regionPath(l: InkLine, w: Float): Path {
    val path = Path()
    val hh = l.width * w / 2f
    for (i in l.xs.indices) {
        val y = l.ys[i] * w
        path.addRect(Rect(l.xs[i] * w, y - hh - 0.5f, (l.factors.getOrNull(i) ?: l.xs[i]) * w, y + hh + 0.5f))
    }
    return path
}

/** Клітинок заливки на ширину сторінки (дрібніше — заливка дістає вузькі місця). */
private const val FILL_COLS = 420

/**
 * Заливка: малюнок сторінки — у дрібну сітку; від клітинки під кінчиком розтікається порожнім, доки не впреться в
 * лінії. Залите трохи заходить під обвідку (щоб не лишалось щілини). Якщо фарба розтеклась майже на всю сторінку —
 * обрис не замкнений, нічого не заливаємо. Зберігається рядками: [InkLine.xs] — початок, [InkLine.factors] — кінець
 * відрізка, [InkLine.ys] — середина рядка, [InkLine.width] — висота рядка (усе в частках ширини сторінки).
 */
private fun floodFill(lines: List<InkLine>, part: Int, pageW: Float, pageH: Float, at: Offset, density: Float, style: Int, argb: Int, outKind: Int = InkKind.FILL): InkLine? {
    val cols = FILL_COLS
    val cell = pageW / cols
    val rows = kotlin.math.ceil(pageH / cell).toInt().coerceAtLeast(1)
    val img = androidx.compose.ui.graphics.ImageBitmap(cols, rows)
    val canvas = androidx.compose.ui.graphics.Canvas(img)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(density / cell),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        canvas,
        Size(cols.toFloat(), rows.toFloat()),
    ) {
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        drawInk(lines, part, cols.toFloat(), { false }, emptySet())
        drawContext.canvas.restore()
    }
    val px = IntArray(cols * rows)
    img.readPixels(px)
    // Стіни — усе намальоване (і напівпрозоре); крихітні щілини обрису (до клітинки) закриваються, щоб фарба не витікала.
    val ink0 = BooleanArray(px.size) { (px[it] ushr 24) > 18 }
    val wall = BooleanArray(px.size)
    for (i in px.indices) if (ink0[i]) {
        val x = i % cols
        val y = i / cols
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until cols && ny in 0 until rows) wall[ny * cols + nx] = true
        }
    }
    val sx = (at.x / cell).toInt().coerceIn(0, cols - 1)
    val sy = (at.y / cell).toInt().coerceIn(0, rows - 1)
    // Опустили просто на лінію — шукаємо найближче порожнє місце поруч (до трьох клітинок).
    var start = sy * cols + sx
    if (wall[start]) {
        var found = -1
        loop@ for (rr in 1..3) for (dy in -rr..rr) for (dx in -rr..rr) {
            val nx = sx + dx
            val ny = sy + dy
            if (nx in 0 until cols && ny in 0 until rows && !wall[ny * cols + nx]) { found = ny * cols + nx; break@loop }
        }
        if (found < 0) return null
        start = found
    }
    val sx2 = start % cols
    val sy2 = start / cols
    val fill = BooleanArray(px.size)
    val queue = IntArray(px.size)
    var head = 0
    var tail = 0
    queue[tail++] = sy2 * cols + sx2
    fill[sy2 * cols + sx2] = true
    while (head < tail) {
        val i = queue[head++]
        val x = i % cols
        val y = i / cols
        fun push(j: Int) { if (!fill[j] && !wall[j]) { fill[j] = true; queue[tail++] = j } }
        if (x > 0) push(i - 1)
        if (x < cols - 1) push(i + 1)
        if (y > 0) push(i - cols)
        if (y < rows - 1) push(i + cols)
    }
    if (tail > px.size * 0.6f) return null
    // Під обвідку: на дві клітинки в усі боки (стіни товстіші на одну — і ще одна, щоб не лишалось щілини).
    val grown = fill.copyOf()
    for (i in px.indices) if (fill[i]) {
        val x = i % cols
        val y = i / cols
        for (dy in -2..2) for (dx in -2..2) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until cols && ny in 0 until rows) grown[ny * cols + nx] = true
        }
    }
    val x0 = ArrayList<Float>()
    val x1 = ArrayList<Float>()
    val ys = ArrayList<Float>()
    for (y in 0 until rows) {
        var x = 0
        while (x < cols) {
            if (!grown[y * cols + x]) { x++; continue }
            val st = x
            while (x < cols && grown[y * cols + x]) x++
            x0 += st.toFloat() / cols; x1 += x.toFloat() / cols; ys += (y + 0.5f) / cols
        }
    }
    if (x0.isEmpty()) return null
    return InkLine(
        page = part,
        xs = x0.toFloatArray(),
        ys = ys.toFloatArray(),
        factors = x1.toFloatArray(),
        width = 1f / cols,
        time = System.currentTimeMillis(),
        kind = outKind,
        style = style,
        color = argb,
    )
}

/** Залите місце: суцільно (м'яко) чи штрихами, як пензликом. */
private fun DrawScope.drawFill(xs: FloatArray, ys: FloatArray, x1: FloatArray, rowH: Float, w: Float, ink: Color, bristles: Boolean, glass: Boolean) {
    val path = Path()
    val hh = rowH * w / 2f
    for (i in xs.indices) {
        val y = ys[i] * w
        path.addRect(Rect(xs[i] * w, y - hh - 0.5f, (x1.getOrNull(i) ?: xs[i]) * w, y + hh + 0.5f))
    }
    if (!bristles) {
        drawPath(path, ink.copy(alpha = ink.alpha * (if (glass) 0.7f else 0.62f)))
        return
    }
    // Штрихи пензлика навскіс, обрізані по залитому.
    val b = path.getBounds()
    clipPath(path) {
        drawPath(path, ink.copy(alpha = ink.alpha * 0.12f))
        val gap = 3.2.dp.toPx()
        var t = b.left - b.height
        var k = 0
        while (t < b.right) {
            val a = 0.35f + 0.25f * ((k * 7) % 5) / 4f
            drawLine(ink.copy(alpha = ink.alpha * a), Offset(t, b.bottom), Offset(t + b.height, b.top), (1.2f + (k % 3) * 0.5f).dp.toPx(), cap = StrokeCap.Round)
            t += gap
            k++
        }
    }
}

/** Майже горизонтальна лінія стає рівно горизонтальною (підкреслення тексту). */
private fun snapLine(a: Offset, b: Offset): Offset {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return if (kotlin.math.abs(dy) < kotlin.math.abs(dx) * 0.12f) Offset(b.x, a.y) else b
}

// ---------- Малювання штрихів ----------

/**
 * Штрих за видом і вигляд лінії. «Аркуш» — графіт, «Скло» — світло; висохлий — трохи тьмяніший. [layered] — прозорість
 * уже дає шар ([drawLayered]), тож тут малюємо непрозоро.
 */
private fun DrawScope.drawStroke(xs: FloatArray, ys: FloatArray, factors: FloatArray, base: Float, w: Float, dry: Boolean, kind: Int, style: Int, layered: Boolean = false, color: Int = 0) {
    val n = xs.size
    val glass = AppStyle.glass
    val ink0 = if (glass) (if (dry) DryInk else FreshInk) else (if (dry) SheetInk.copy(alpha = 0.8f) else SheetInk)
    // Колір із палітри (новий штрих) чи давній відтінок із style.
    val rgb = color and 0xFFFFFF
    val op = if (layered) 1f else if (color != 0) (color ushr 24) / 255f else strokeOpacity(kind, style, glass)
    val tint = if (color != 0) (if (rgb == INK_RGB) Color.Unspecified else Color(0xFF000000.toInt() or rgb)) else tintOf(kind, style)
    val paint0 = if (tint == Color.Unspecified) ink0 else tint.copy(alpha = if (dry) 0.75f else 1f)
    val c = paint0.copy(alpha = paint0.alpha * op)
    if (kind == InkKind.FILL) {
        if (n == 0) return
        drawFill(xs, ys, factors, base / w, w, c, style % 2 == 0, glass)
        return
    }
    if (n < 2) return
    var px = FloatArray(n) { xs[it] * w }
    var py = FloatArray(n) { ys[it] * w }
    if (kind == InkKind.SPRAY) { sprayLine(px, py, base, c, xs[0] + ys[0], if (color != 0) style % 2 else 1); return }
    if (kind == InkKind.PENCIL || kind == InkKind.BRUSH || kind == InkKind.MARKER) {
        // Крапка (штрих без довжини) — кружечок на товщину інструмента.
        val ext = maxOf(px.max() - px.min(), py.max() - py.min())
        if (ext < base * 0.3f) {
            val o = Offset(px[0], py[0])
            if (kind == InkKind.MARKER) drawRoundRect(c, Offset(o.x - base * 0.35f, o.y - base / 2f), Size(base * 0.7f, base), CornerRadius(base * 0.12f))
            else {
                val rr = base * (if (kind == InkKind.BRUSH) 0.55f else 0.62f)
                if (glass && tint == Color.Unspecified) drawCircle(Oled.TextPrimary.copy(alpha = 0.1f * op), rr * 2f, o)
                drawCircle(c.copy(alpha = c.alpha * (if (glass) 0.92f else 0.7f)), rr, o)
            }
            return
        }
    }
    if (kind >= InkKind.ERASER) {
        val path = polyline(px, py)
        if (kind == InkKind.ERASER_SOFT) {
            // М'яка гумка: посередині стирає начисто, край розмитий — кілька смуг, кожна ширша й слабша (не ширше бруска).
            // Ступінь розмиття — зі style: що більший, то ширша м'яка смуга й менше начисто стертої середини.
            val prof = when (style) {
                0 -> listOf(1f to 0.35f, 0.85f to 1f)
                2 -> listOf(1f to 0.08f, 0.88f to 0.14f, 0.76f to 0.22f, 0.62f to 0.32f, 0.48f to 0.5f, 0.32f to 1f)
                else -> listOf(1f to 0.16f, 0.86f to 0.3f, 0.72f to 0.5f, 0.55f to 1f)
            }
            for ((k, a) in prof) {
                drawPath(path, Color.Black.copy(alpha = a), style = Stroke(base * k, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
            }
        } else {
            drawPath(path, Color.Black, style = Stroke(base, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
        }
        return
    }
    when (kind) {
        InkKind.PENCIL -> {
            pencilLine(px, py, factors.takeIf { it.size == n }, base, c, glass, style % 10, 0.14f, false)
            return
        }
        InkKind.BRUSH -> {
            brushLine(px, py, factors.takeIf { it.size == n }, base, c, glass, style % 2, (style % 10) / 2 == 1, xs[0])
            return
        }
        InkKind.UNDERLINE -> {
            drawFigure(Offset(px[0], py[0]), Offset(px[n - 1], py[n - 1]), base, c, glass, style % 4, (style % 100) / 4)
            return
        }
        InkKind.MARKER -> if (style >= 100) {
            // Маркер — плаский скошений кінчик: кінці рівні, не круглі.
            drawPath(polyline(px, py), c, style = Stroke(base, cap = StrokeCap.Butt, join = StrokeJoin.Round))
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
        drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.92f))
        return
    }
    drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * 0.62f))
    val o = (widths.maxOrNull() ?: 0f) * 0.16f
    drawPath(variablePath(FloatArray(n) { px[it] + o }, FloatArray(n) { py[it] - o * 0.6f }, FloatArray(n) { widths[it] * 0.45f }), ink.copy(alpha = ink.alpha * 0.32f))
    drawPath(variablePath(FloatArray(n) { px[it] - o * 0.7f }, FloatArray(n) { py[it] + o }, FloatArray(n) { widths[it] * 0.35f }), ink.copy(alpha = ink.alpha * 0.28f))
}

/**
 * Мазок пензлика: кінці круглі (пензлик м'який, а не загострений). Щетинки — кілька тонких ліній поруч, кожна своєї
 * сили, на кінцях сходяться; суцільний — рівна фарба з м'якими краями. [scrub] — зафарбовування: широко й рівно, без
 * звуження, щільніше (як гумкою, тільки фарбує).
 */
private fun DrawScope.brushLine(px: FloatArray, py: FloatArray, factors: FloatArray?, base: Float, ink: Color, glass: Boolean, mode: Int, scrub: Boolean, seedX: Float) {
    val n = px.size
    val widths = if (scrub) FloatArray(n) { base * 1.6f } else {
        // Мазок починається вузеньким кінчиком і розходиться; кінець — заокруглений.
        val t = taper(px, py, factors, base, 0.12f)
        FloatArray(n) { t[it] }
    }
    if (mode == 1) {
        if (scrub) {
            drawPath(polyline(px, py), ink.copy(alpha = ink.alpha * (if (glass) 0.7f else 0.62f)), style = Stroke(base * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            return
        }
        drawPath(variablePath(px, py, widths), ink.copy(alpha = ink.alpha * (if (glass) 0.8f else 0.7f)))
        return
    }
    drawPath(variablePath(px, py, FloatArray(n) { widths[it] * 0.9f }), ink.copy(alpha = ink.alpha * (if (scrub) 0.12f else 0.06f)))
    // Щетинки — помітно окремі (з проміжками), щоб фактура відрізнялась від суцільної.
    val bristles = if (scrub) 11 else 6
    val rnd = java.util.Random((seedX * 100000).toLong() + n)
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
        val off = (k / (bristles - 1f) - 0.5f) * 0.88f
        val bw = (if (scrub) 0.07f else 0.09f) + rnd.nextFloat() * 0.06f
        val alpha = 0.35f + rnd.nextFloat() * 0.6f
        val wob = (rnd.nextFloat() - 0.5f) * 0.06f
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

/**
 * Балончик. Фарба лягає там, де балончик «пшикав»: точки штриха йдуть і за рухом, і за часом (тримаєш на місці — фарби
 * більшає), тож повільно — густо, швидко — легко. [mode] 0 — аерограф: м'яка хмарка, що поступово густішає;
 * 1 — фарба з балончика: густий туман дрібних цяток різного розміру, щільніший посередині, з рідкими бризками по краях.
 * Цятки для того самого штриха завжди ті самі (насіння — з його точок).
 */
private fun DrawScope.sprayLine(px: FloatArray, py: FloatArray, base: Float, c: Color, seed: Float, mode: Int) {
    val rad = base / 2f
    // Точки «пшику»: між далекими — додати проміжні, однакові (тримав на місці) — лишити.
    val step = maxOf(rad * 0.22f, 1.5f)
    val sx = ArrayList<Float>(px.size * 2)
    val sy = ArrayList<Float>(px.size * 2)
    sx += px[0]; sy += py[0]
    for (i in 1 until px.size) {
        val dx = px[i] - px[i - 1]
        val dy = py[i] - py[i - 1]
        val d = hypot(dx, dy)
        val k = (d / step).toInt()
        for (j in 1..k) { sx += px[i - 1] + dx * j / (k + 1); sy += py[i - 1] + dy * j / (k + 1) }
        sx += px[i]; sy += py[i]
    }
    val rnd = java.util.Random((seed * 1_000_000).toLong())
    if (mode == 0) {
        // Аерограф: кожен «пшик» — кілька м'яких кіл (зовні слабші), що накладаються й густішають.
        for (i in sx.indices) {
            val o = Offset(sx[i] + (rnd.nextFloat() - 0.5f) * rad * 0.1f, sy[i] + (rnd.nextFloat() - 0.5f) * rad * 0.1f)
            drawCircle(c.copy(alpha = c.alpha * 0.035f), rad, o)
            drawCircle(c.copy(alpha = c.alpha * 0.05f), rad * 0.66f, o)
            drawCircle(c.copy(alpha = c.alpha * 0.07f), rad * 0.36f, o)
        }
        return
    }
    val fine = ArrayList<Offset>(sx.size * 10)
    val mid = ArrayList<Offset>(sx.size * 4)
    val big = ArrayList<Offset>(sx.size)
    for (i in sx.indices) {
        // Туман під цятками і щільніше ядро — як справжня фарба з балончика на стіні.
        drawCircle(c.copy(alpha = c.alpha * 0.03f), rad * 0.85f, Offset(sx[i], sy[i]))
        drawCircle(c.copy(alpha = c.alpha * 0.07f), rad * 0.38f, Offset(sx[i], sy[i]))
        repeat(22) { k ->
            val ang = rnd.nextFloat() * 2f * PI.toFloat()
            val g = kotlin.math.abs(rnd.nextGaussian()).toFloat()
            val d = (g * 0.42f).coerceAtMost(1.05f) * rad
            val o = Offset(sx[i] + cos(ang) * d, sy[i] + sin(ang) * d)
            when {
                k < 15 -> fine += o
                k < 21 -> mid += o
                else -> if (rnd.nextFloat() < 0.35f) big += Offset(sx[i] + cos(ang) * rad * (0.75f + rnd.nextFloat() * 0.5f), sy[i] + sin(ang) * rad * (0.75f + rnd.nextFloat() * 0.5f))
            }
        }
    }
    drawPoints(fine, PointMode.Points, c.copy(alpha = c.alpha * 0.55f), strokeWidth = 0.8.dp.toPx(), cap = StrokeCap.Round)
    drawPoints(mid, PointMode.Points, c.copy(alpha = c.alpha * 0.8f), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
    drawPoints(big, PointMode.Points, c, strokeWidth = 2.3.dp.toPx(), cap = StrokeCap.Round)
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
    val tone = if (s.color == INK_RGB) Color.Unspecified else Color(0xFF000000.toInt() or s.color)
    if (s.picking) { drawPickerCursor(p, lead, glass); return }
    when (s.tool) {
        InkTool.Lasso -> drawLassoCursor(p, lead, glass)
        InkTool.Eraser -> drawEraserCursor(p, width, lead, glass)
        InkTool.Marker -> drawMarkerCursor(p, width, lead, glass, tone)
        InkTool.Brush -> drawBrushCursor(p, width, lead, glass)
        InkTool.Spray -> drawSprayCursor(p, width, lead, glass, tone)
        else -> drawMechPencil(p, width, lead, s.locked, glass, s.tool == InkTool.Underline)
    }
}

/**
 * Балончик фарби: металевий циліндр із блиском і кольоровою етикеткою, заокруглене плече, а на ньому — лише кнопка-
 * розпилювач із соплом (там, де фарбує). Поки натиснутий, кнопка трохи втоплена.
 */
private fun DrawScope.drawSprayCursor(p: Offset, width: Float, lead: Float, glass: Boolean, tint: Color) {
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val tone = if (tint == Color.Unspecified) ink else tint
    val bw = 24.dp.toPx()
    val x = p.x
    rotate(TILT, p) {
        val btnTop = p.y - 6.dp.toPx() + 1.5.dp.toPx() * lead
        val shoulder = btnTop - 2.dp.toPx()
        val bodyTop = shoulder - 72.dp.toPx()
        shadowRect(x - bw / 2, bodyTop, bw, shoulder - bodyTop, 6.dp.toPx())
        // Корпус із заокругленим плечем біля кнопки.
        val body = Path().apply {
            moveTo(x - bw / 2, bodyTop + 3.dp.toPx())
            lineTo(x - bw / 2, shoulder - bw * 0.45f)
            quadraticTo(x - bw / 2, shoulder, x - bw * 0.18f, shoulder)
            lineTo(x + bw * 0.18f, shoulder)
            quadraticTo(x + bw / 2, shoulder, x + bw / 2, shoulder - bw * 0.45f)
            lineTo(x + bw / 2, bodyTop + 3.dp.toPx())
            quadraticTo(x + bw / 2, bodyTop, x + bw / 2 - 3.dp.toPx(), bodyTop)
            lineTo(x - bw / 2 + 3.dp.toPx(), bodyTop)
            quadraticTo(x - bw / 2, bodyTop, x - bw / 2, bodyTop + 3.dp.toPx())
            close()
        }
        drawPath(body, cylinder(x - bw / 2, bw, Color(0xFF5A5E66), Color(0xFFB9BDC4), Color(0xFFF2F4F7)))
        // Етикетка кольору фарби, з відблиском.
        val labelTop = bodyTop + 14.dp.toPx()
        drawRect(tone.copy(alpha = 0.88f), Offset(x - bw / 2, labelTop), Size(bw, 36.dp.toPx()))
        drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.3f), 0.25f to Color.White.copy(alpha = 0.28f), 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f), startX = x - bw / 2, endX = x + bw / 2), Offset(x - bw / 2, labelTop), Size(bw, 36.dp.toPx()))
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(x - bw / 2, bodyTop + 3.dp.toPx()), Offset(x + bw / 2, bodyTop + 3.dp.toPx()), 1.dp.toPx())
        // Кнопка-розпилювач і сопло.
        drawRoundRect(cylinder(x - 5.dp.toPx(), 10.dp.toPx(), Color(0xFF1E1F22), Color(0xFF3C3E43), Color(0xFF6E7178)), Offset(x - 5.dp.toPx(), btnTop), Size(10.dp.toPx(), p.y - btnTop), CornerRadius(2.5.dp.toPx()))
        drawCircle(Color.Black, 1.4.dp.toPx(), Offset(x, p.y - 1.6.dp.toPx()))
    }
}

/** Ласо: перехрестя з петлею — де почнеш обводити (опущене — петля яскравіша). */
private fun DrawScope.drawLassoCursor(p: Offset, lead: Float, glass: Boolean) {
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val a = 0.55f + 0.4f * lead
    val r = 5.dp.toPx()
    drawLine(ink.copy(alpha = a), p - Offset(r * 1.6f, 0f), p + Offset(r * 1.6f, 0f), 1.2.dp.toPx())
    drawLine(ink.copy(alpha = a), p - Offset(0f, r * 1.6f), p + Offset(0f, r * 1.6f), 1.2.dp.toPx())
    drawOval(ink.copy(alpha = a), Offset(p.x + r * 0.6f, p.y - r * 3.4f), Size(r * 3.4f, r * 2.2f), style = Stroke(1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
}

/** Піпетка: скляна трубочка з гумовою грушкою; кінчик — де береться колір. */
private fun DrawScope.drawPickerCursor(p: Offset, lead: Float, glass: Boolean) {
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val x = p.x
    rotate(TILT, p) {
        val tipTop = p.y - 10.dp.toPx()
        val bulbTop = tipTop - 34.dp.toPx()
        shadowRect(x - 5.dp.toPx(), bulbTop - 14.dp.toPx(), 10.dp.toPx(), p.y - bulbTop + 14.dp.toPx(), 5.dp.toPx())
        val tube = Path().apply {
            moveTo(x - 1.dp.toPx(), p.y); lineTo(x + 1.dp.toPx(), p.y); lineTo(x + 4.dp.toPx(), tipTop); lineTo(x - 4.dp.toPx(), tipTop); close()
        }
        drawPath(tube, ink.copy(alpha = 0.35f))
        drawRect(ink.copy(alpha = 0.3f), Offset(x - 4.dp.toPx(), bulbTop), Size(8.dp.toPx(), tipTop - bulbTop))
        drawRect(ink.copy(alpha = 0.7f), Offset(x - 4.dp.toPx(), bulbTop), Size(8.dp.toPx(), tipTop - bulbTop), style = Stroke(1.dp.toPx()))
        drawRoundRect(cylinder(x - 6.dp.toPx(), 12.dp.toPx(), Color(0xFF2A1F1C), Color(0xFF5A3A33), Color(0xFF8E625A)), Offset(x - 6.dp.toPx(), bulbTop - 14.dp.toPx() * (1f - 0.3f * lead)), Size(12.dp.toPx(), 15.dp.toPx()), CornerRadius(6.dp.toPx()))
    }
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
    // Стрижень короткий: лінія починається просто від кінчика олівця — одразу видно, де писатиме.
    val maxLead = 2.6.dp.toPx()
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
        // Тінь — лише під корпусом (не тягнеться під стрижень, щоб нічого не «прилипало» до кінчика).
        shadowRect(x - bw / 2, capTop, bw, coneTop - capTop, bw / 2)
        // Стрижень: висувається з гільзи; тьмяно-сірий, загострений.
        val leadLen = maxLead * lead
        if (leadLen > 0.4f) {
            val lw = (width * 0.9f).coerceIn(1.2.dp.toPx(), 7.dp.toPx())
            val tipY = y0 + leadLen
            val body = Path().apply {
                moveTo(x - lw / 2, y0); lineTo(x - lw / 2, y0 + leadLen * 0.45f); lineTo(x, tipY)
                lineTo(x + lw / 2, y0 + leadLen * 0.45f); lineTo(x + lw / 2, y0); close()
            }
            drawPath(body, if (glass) Color(0xFF6E7178) else Color(0xFF5C5852))
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
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val tone = if (tint == Color.Unspecified) ink else tint
    val tipW = width.coerceIn(3.dp.toPx(), 44.dp.toPx())
    val bw = maxOf(15.dp.toPx(), tipW + 6.dp.toPx())
    val lift = 1.5.dp.toPx() * (1f - lead)
    val x = p.x
    rotate(TILT, p) {
        val tipTop = p.y - 6.dp.toPx() - lift
        val collarTop = tipTop - 5.dp.toPx()
        // Довжина — як в олівця й пензлика (інструменти однакові на вигляд за розміром).
        val bodyTop = collarTop - 62.dp.toPx()
        val capTop = bodyTop - 12.dp.toPx()
        shadowRect(x - bw / 2, capTop, bw, collarTop - capTop, 4.dp.toPx())
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
    val ink = if (glass) Oled.TextPrimary else SheetInk
    // Пучок росте разом із товщиною мазка (і довшає), щоб було видно, яким буде мазок.
    val tw = width.coerceIn(3.dp.toPx(), 46.dp.toPx())
    val lift = 1.5.dp.toPx() * (1f - lead)
    val x = p.x
    rotate(TILT, p) {
        val tip = p.y - lift
        val tuftTop = tip - (9.dp.toPx() + tw * 0.7f)
        val ferTop = tuftTop - 11.dp.toPx()
        val handleTop = ferTop - 46.dp.toPx()
        val fw = tw * 0.8f + 2.dp.toPx()
        shadowRect(x - fw / 2, handleTop, fw, tip - handleTop, fw / 2)
        // Пучок: округлий, кінчик заокруглений і змочений фарбою (м'який пензлик, не загострений).
        val tuft = Path().apply {
            moveTo(x - tw / 2, tuftTop)
            cubicTo(x - tw * 0.56f, tuftTop + 7.dp.toPx(), x - tw * 0.5f, tip - tw * 0.35f, x - tw * 0.3f, tip - tw * 0.12f)
            quadraticTo(x, tip + tw * 0.06f, x + tw * 0.3f, tip - tw * 0.12f)
            cubicTo(x + tw * 0.5f, tip - tw * 0.35f, x + tw * 0.56f, tuftTop + 7.dp.toPx(), x + tw / 2, tuftTop)
            close()
        }
        drawPath(tuft, Brush.verticalGradient(0f to Color(0xFF8A7A62), 0.5f to Color(0xFF4A4034), 1f to ink.copy(alpha = 0.9f), startY = tuftTop, endY = tip))
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
 * Гумка — брусок рівно тієї ширини, яку стирає, з однаковими пропорціями на будь-якому розмірі. Її кінець заокруглений
 * півколом, і стирає саме він (коло стирання збігається з кінчиком гумки — край видно точно). Опущена — кінчик ледь
 * світиться.
 */
private fun DrawScope.drawEraserCursor(p: Offset, width: Float, lead: Float, glass: Boolean) {
    val ink = if (glass) Oled.TextPrimary else SheetInk
    val bw = maxOf(width, 4.dp.toPx())
    val bh = bw * 1.9f
    val rubber = bw * 0.75f
    val hr = bw / 2f
    rotate(TILT * 0.6f, p) {
        val left = p.x - hr
        val bottom = p.y + hr
        val top = bottom - bh
        shadowRect(left, top, bw, bh - hr, minOf(3.dp.toPx(), bw / 4))
        // Тримач.
        drawRoundRect(cylinder(left, bw, Color(0xFF1E2024), Color(0xFF3A3D44), Color(0xFF6E737C)), Offset(left, top), Size(bw, bh - rubber + 2f), CornerRadius(minOf(3.dp.toPx(), bw / 4)))
        // Гумка з півкруглим кінчиком (центр півкола — там, де стирає).
        val rubberPath = Path().apply {
            moveTo(left, bottom - rubber)
            lineTo(left + bw, bottom - rubber)
            lineTo(left + bw, p.y)
            arcTo(Rect(p, hr), 0f, 180f, false)
            close()
        }
        val rubberBase = if (glass) Color(0xFF8C8F96) else Color(0xFF9C968C)
        drawPath(rubberPath, cylinder(left, bw, rubberBase.copy(red = rubberBase.red * 0.6f, green = rubberBase.green * 0.6f, blue = rubberBase.blue * 0.6f), rubberBase, Color(minOf(1f, rubberBase.red * 1.3f), minOf(1f, rubberBase.green * 1.3f), minOf(1f, rubberBase.blue * 1.3f))))
        if (lead > 0.01f) drawPath(rubberPath, ink.copy(alpha = 0.28f * lead))
    }
}
