package com.blacklaunch.diary.book

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacklaunch.diary.ui.AppStyle
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.SheetInk
import com.blacklaunch.diary.ui.pencilTick
import com.blacklaunch.diary.ui.taperedPath
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Простір під сторінкою — там з книжки звисає кінчик стрічки. */
val RibbonBottom = 0.dp

/** Де лежить стрічка (частка ширини сторінки): ліве поле, де не пишеться текст. */
private const val RIBBON_X = 0.042f
private val RibbonWidth = 12.dp

/** Рух стрічки — спільний для кінчика (під книжкою) і самої стрічки (на сторінці). */
@Stable
class RibbonMotion(here: Boolean) {
    /** Яка частка сторінки вкрита стрічкою: 0 — лише кінчик знизу, 1 — лежить на всю висоту. */
    internal val laid = Animatable(if (here) 1f else 0f)
    /** Наскільки витягли кінчик униз. */
    internal val pull = Animatable(0f)
    internal val sway = Animatable(0f)
    /** День, до якого стосується [laid] (поточна сторінка); на інших сторінках стрічка просто лежить чи ні. */
    internal var day by mutableStateOf<Long?>(null)
}

/**
 * Стрічка на сторінці — малюється як частина самої сторінки (у лівому полі, в межах контуру), тож перегортається разом
 * із нею, а не висить поверх книжки. [laid] — яка частка висоти вкрита (0…1).
 */
internal fun DrawScope.drawRibbonStrip(laid: Float, glass: Boolean) {
    if (laid <= 0.01f) return
    val x = size.width * RIBBON_X
    val w = RibbonWidth.toPx()
    val top = 3.dp.toPx()
    val bottom = top + (size.height - 2 * top) * laid
    val p = Path().apply { addRect(androidx.compose.ui.geometry.Rect(x - w / 2, top, x + w / 2, bottom)) }
    ribbonBody(p, x, w, top, bottom, glass)
}

/**
 * Кінчик закладки, що звисає з-під книжки ліворуч. Малюється ПІД книжкою: він виходить з-поміж сторінок і не
 * перетинає контур щоденника.
 */
@Composable
fun RibbonTail(motion: RibbonMotion, pageW: Dp, pageH: Dp) {
    val glass = AppStyle.glass
    Canvas(Modifier.size(pageW, pageH + RibbonBottom)) {
        val x = size.width * RIBBON_X
        val w = RibbonWidth.toPx()
        val tailTop = pageH.toPx() - 12.dp.toPx()
        val tailEnd = pageH.toPx() + 24.dp.toPx() + motion.pull.value
        val notch = w * 0.55f
        rotate(motion.sway.value, pivot = Offset(x, pageH.toPx())) {
            val p = Path().apply {
                moveTo(x - w / 2, tailTop); lineTo(x + w / 2, tailTop); lineTo(x + w / 2, tailEnd)
                lineTo(x, tailEnd - notch); lineTo(x - w / 2, tailEnd); close()
            }
            ribbonBody(p, x, w, tailTop, tailEnd - notch, glass)
            if (!glass) {
                drawPath(taperedPath(listOf(Offset(x - w / 2, tailEnd), Offset(x, tailEnd - notch), Offset(x + w / 2, tailEnd)), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.85f))
            }
        }
    }
}

/**
 * Закладка — тканинна стрічка, пришита до корінця, як у справжньому щоденнику. Кінчик — [RibbonTail] (під книжкою),
 * сама стрічка — на сторінці ([drawRibbonStrip]); тут — лише рух і те, де її брати.
 *  - Береться лише за кінчик під книжкою, тож стрічка не заважає тексту й клавіатурі.
 *  - Смикнув кінчик униз — стрічка лягла на цю сторінку (у лівому полі, всередині контуру сторінки).
 *    Смикнув, коли лежить тут, — прибрав. Лежить на іншій сторінці — дотик до кінчика перегортає до неї.
 * [day] — поточна сторінка: при перегортанні стрічка не «їде», а одразу лежить чи ні.
 */
@Composable
fun BookmarkRibbon(
    motion: RibbonMotion,
    pageW: Dp,
    pageH: Dp,
    day: Long?,
    here: Boolean,
    elsewhere: Boolean,
    enabled: Boolean,
    onPlace: () -> Unit,
    onRemove: () -> Unit,
    onJump: () -> Unit,
    description: String,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(day, here) {
        if (day != motion.day) {
            motion.laid.snapTo(if (here) 1f else 0f)
            motion.day = day
        } else {
            motion.laid.animateTo(if (here) 1f else 0f, tween(420))
            motion.sway.snapTo(if (here) 3f else -3f)
            motion.sway.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 60f))
        }
    }
    val xDp = pageW * RIBBON_X
    Box(Modifier.size(pageW, pageH + RibbonBottom)) {
        // Узяти можна лише кінчик під книжкою.
        if (enabled) {
            Box(
                Modifier
                    .offset(xDp - 22.dp, pageH)
                    .size(44.dp, RibbonBottom)
                    .semantics { contentDescription = description }
                    .pointerInput(here, elsewhere) {
                        detectTapGestures { if (elsewhere && !here) { haptics.tap(); onJump() } }
                    }
                    .pointerInput(here) {
                        var pulled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { pulled = 0f; haptics.tick() },
                            onVerticalDrag = { c, dy ->
                                c.consume()
                                pulled += dy
                                scope.launch {
                                    motion.pull.snapTo((pulled * 0.35f).coerceIn(0f, 28.dp.toPx()))
                                    motion.sway.snapTo((pulled / 40f).coerceIn(-4f, 4f) * 0.3f)
                                }
                            },
                            onDragEnd = {
                                scope.launch { motion.pull.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
                                scope.launch { motion.sway.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 80f)) }
                                if (pulled > 36.dp.toPx()) {
                                    haptics.longPress()
                                    if (here) onRemove() else onPlace()
                                }
                            },
                        )
                    },
            )
        }
    }
}

/** Тіло стрічки: «Скло» — темне скло з гранями й відблиском; «Аркуш» — суцільна чорна тканина, обведена олівцем. */
private fun DrawScope.ribbonBody(p: Path, x: Float, w: Float, top: Float, bottom: Float, glass: Boolean, solid: Boolean = false) {
    if (glass) {
        if (solid) drawPath(p, Color(0xFF0E0E0E))
        drawPath(p, Brush.horizontalGradient(0f to Color(0xFF2C2C2C), 0.5f to Color(0xFF141414), 1f to Color(0xFF262626), startX = x - w / 2, endX = x + w / 2))
        drawPath(p, Color(0x80FFFFFF), style = Stroke(1.dp.toPx()))
        if (bottom - top > 6f) drawLine(Color(0x33FFFFFF), Offset(x - w * 0.18f, top + 2f), Offset(x - w * 0.18f, bottom - 2f), 1.dp.toPx())
    } else {
        drawPath(p, Color(0xFF050505))
        drawPath(taperedPath(listOf(Offset(x - w / 2, top), Offset(x - w / 2 + 0.6f, (top + bottom) / 2), Offset(x - w / 2, bottom)), 1.8.dp.toPx()), SheetInk.copy(alpha = 0.85f))
        drawPath(taperedPath(listOf(Offset(x + w / 2, top), Offset(x + w / 2 - 0.6f, (top + bottom) / 2), Offset(x + w / 2, bottom)), 1.8.dp.toPx()), SheetInk.copy(alpha = 0.85f))
        // Тканина: рідке косе штрихування.
        var y = top + 6.dp.toPx()
        var i = 0
        while (y < bottom - 4.dp.toPx()) {
            pencilTick(Offset(x - w * 0.35f, y + w * 0.3f), Offset(x + w * 0.35f, y - w * 0.3f), 0.9.dp.toPx(), SheetInk.copy(alpha = 0.28f), i++)
            y += 9.dp.toPx() + sin(i * 1.7f) * 1.5f
        }
    }
}

private val SearchRibbonWidth = 18.dp
private val SearchRibbonLength = 80.dp
/** Де висить (середина): помітно правіше за клаптики вирваних сторінок біля корінця. */
val SearchRibbonX = 36.dp

/** Стан стрічки-пошуку, спільний для всіх сторінок: як далеко потягнули, хитання й видимість лупи. */
object SearchRibbonState {
    var pull by mutableFloatStateOf(0f)
    var sway by mutableFloatStateOf(0f)
    var glyph by mutableFloatStateOf(1f)
}

/**
 * Стрічка-пошук на сторінці: малюється як частина КОЖНОЇ сторінки (не обкладинки) поверх усього на ній — тож завжди на
 * місці: що лежить зверху (обкладинка, сторінка, що перегортається), те її й закриває.
 */
fun DrawScope.drawSearchRibbon(glass: Boolean) {
    val x = SearchRibbonX.toPx()
    val w = SearchRibbonWidth.toPx()
    val end = SearchRibbonLength.toPx() + SearchRibbonState.pull
    val notch = w * 0.42f
    rotate(SearchRibbonState.sway, pivot = Offset(x, 0f)) {
        val p = Path().apply {
            moveTo(x - w / 2, -2f); lineTo(x + w / 2, -2f); lineTo(x + w / 2, end)
            lineTo(x, end - notch); lineTo(x - w / 2, end); close()
        }
        drawPath(Path().apply { addPath(p, Offset(1.5.dp.toPx(), 1.5.dp.toPx())) }, Color.Black.copy(alpha = 0.5f))
        if (glass) {
            drawPath(p, Brush.horizontalGradient(0f to Color(0xFF2A2A2A), 0.5f to Color(0xFF131313), 1f to Color(0xFF242424), startX = x - w / 2, endX = x + w / 2))
            drawPath(p, Color(0x59FFFFFF), style = Stroke(0.8.dp.toPx()))
        } else {
            drawPath(p, Color(0xFF060606))
            drawPath(taperedPath(listOf(Offset(x - w / 2, 0f), Offset(x - w / 2 + 0.6f, end / 2), Offset(x - w / 2, end)), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.8f))
            drawPath(taperedPath(listOf(Offset(x + w / 2, 0f), Offset(x + w / 2 - 0.6f, end / 2), Offset(x + w / 2, end)), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.8f))
            drawPath(taperedPath(listOf(Offset(x - w / 2, end), Offset(x, end - notch), Offset(x + w / 2, end)), 1.6.dp.toPx()), SheetInk.copy(alpha = 0.8f))
        }
        clipPath(p) {
            drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.White.copy(alpha = if (glass) 0.10f else 0.06f), 0.55f to Color.Transparent, startX = x - w / 2, endX = x + w / 2), topLeft = Offset(x - w / 2, 0f), size = androidx.compose.ui.geometry.Size(w, end))
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent, startY = 0f, endY = 10.dp.toPx()), topLeft = Offset(x - w / 2, 0f), size = androidx.compose.ui.geometry.Size(w, 10.dp.toPx()))
        }
        val g = SearchRibbonState.glyph
        if (g > 0.01f) {
            val c = Offset(x - 0.8.dp.toPx(), end - notch - 9.dp.toPx())
            val r = 3.5.dp.toPx()
            val ink = (if (glass) Color.White else SheetInk).copy(alpha = 0.85f * g)
            drawCircle(ink, r, c, style = Stroke(1.3.dp.toPx()))
            drawLine(ink, c + Offset(r * 0.7f, r * 0.7f), c + Offset(r * 1.7f, r * 1.7f), 1.5.dp.toPx())
        }
    }
}

/**
 * Закладка-пошук: лише дотики (малює її сама сторінка — [drawSearchRibbon]). Потягнув униз (або торкнувся) — пошук.
 * Лупа на кінчику з'являється, як нижня панель: після дотику, і тане.
 */
@Composable
fun SearchRibbon(gear: GearReveal, pageW: Dp, pageH: Dp, enabled: Boolean, onOpen: () -> Unit, description: String) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f) }
    val sway = remember { Animatable(0f) }
    var glyphOn by remember { mutableStateOf(true) }
    LaunchedEffect(gear.pokes) {
        glyphOn = true
        kotlinx.coroutines.delay(3_500)
        glyphOn = false
    }
    val glyph by androidx.compose.animation.core.animateFloatAsState(if (glyphOn) 1f else 0f, tween(if (glyphOn) 250 else 800), label = "ribbonGlyph")
    androidx.compose.runtime.SideEffect {
        SearchRibbonState.glyph = glyph
        SearchRibbonState.pull = pull.value
        SearchRibbonState.sway = sway.value
    }
    Box(Modifier.size(pageW, pageH)) {
        if (enabled) {
            Box(
                Modifier
                    .offset(SearchRibbonX - 22.dp, 0.dp)
                    .size(44.dp, SearchRibbonLength + 16.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(Unit) { detectTapGestures { haptics.tap(); onOpen() } }
                    .pointerInput(Unit) {
                        var pulled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { pulled = 0f; haptics.tick() },
                            onVerticalDrag = { ch, dy ->
                                ch.consume()
                                pulled += dy
                                scope.launch {
                                    pull.snapTo((pulled * 0.45f).coerceIn(0f, 40.dp.toPx()))
                                    sway.snapTo((pulled / 40f).coerceIn(-4f, 4f) * 0.3f)
                                }
                            },
                            onDragEnd = {
                                scope.launch { pull.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
                                scope.launch { sway.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 80f)) }
                                if (pulled > 40.dp.toPx()) { haptics.longPress(); onOpen() }
                            },
                        )
                    },
            )
        }
    }
}
