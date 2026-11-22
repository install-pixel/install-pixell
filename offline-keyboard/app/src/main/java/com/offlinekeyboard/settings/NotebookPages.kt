package com.offlinekeyboard.settings

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * True inside the sketch's notebook pages: a [Section] there writes its title and rows straight onto the page
 * (each row a line of the notebook that can go on to the next page), not inside a column of its own.
 */
internal val LocalNotebook = staticCompositionLocalOf { false }

/** The layout id of a section's title line: more room above it than between rows. */
internal const val NOTEBOOK_SECTION = "notebook-section"

private const val COVER = "notebook-cover"

/** Where the screen's paper lies (the [paperPage] it is drawn on), in root coordinates: the pages match it. */
internal class PaperFrame {
    var left = 0f
    var top = 0f
    var width = 1f
    var height = 1f
}

internal val LocalPaperFrame = staticCompositionLocalOf { PaperFrame() }

/**
 * The sketch's settings as the pages of a notebook instead of a scrolled list: the rows are laid out page by
 * page, as many as fit the screen, and a swipe turns the page — the next one slides in from the right over this
 * one (a new sheet with a soft shadow at its edge), back slides this one away to the right. Nothing scrolls, so
 * the writing can follow the paper's relief (bent with its creases, from Android 13) without floating over it.
 */
@Composable
internal fun NotebookPages(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val state = remember { PagesState() }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val frame = LocalPaperFrame.current
    val light = Tone.light
    val ink = Tone.TextSecondary
    val numbers = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.bodySmall.copy(color = ink)
    PaperState.ready
    val relief = PaperState.relief(light)
    val photo = PaperState.page(context, light)

    Layout(
        content = {
            androidx.compose.runtime.CompositionLocalProvider(LocalNotebook provides true) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
            }
            // The next page's blank sheet, slid over the page under it while it turns.
            Box(
                Modifier
                    .layoutId(COVER)
                    .fillMaxSize()
                    .graphicsLayer { }
                    .then(
                        Modifier.drawCover(photo, frame, state, numbers, numberStyle, light),
                    ),
            )
        },
        modifier = modifier
            .onGloballyPositioned {
                val p = it.positionInRoot()
                state.rootX = p.x
                state.rootY = p.y
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val w = size.width.toFloat()
                        val now = drag.value
                        val target = when {
                            now < 0 && page < state.count - 1 && (now < -w * 0.25f || v < -800f) -> -w
                            now > 0 && page > 0 && (now > w * 0.25f || v > 800f) -> w
                            else -> 0f
                        }
                        scope.launch {
                            drag.animateTo(target, tween(if (target == 0f) 180 else 260))
                            if (target != 0f) {
                                page = (page + if (target < 0) 1 else -1).coerceIn(0, state.count - 1)
                                drag.snapTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, tween(180)) } },
                ) { change, amount ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val w = size.width.toFloat()
                    // Only towards a page that is there; a little give at the first and the last.
                    val next = drag.value + amount
                    val limited = when {
                        next < 0 && page >= state.count - 1 -> maxOf(next, -w * 0.04f)
                        next > 0 && page <= 0 -> minOf(next, w * 0.04f)
                        else -> next.coerceIn(-w, w)
                    }
                    change.consume()
                    scope.launch { drag.snapTo(limited) }
                }
            }
            .then(bend(relief, photo, frame, state))
            .drawBehindNumber(state, numbers, numberStyle),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val side = 22.dp.roundToPx()
        val top = 20.dp.roundToPx()
        val bottom = 34.dp.roundToPx()
        val rowGap = 10.dp.roundToPx()
        val sectionGap = 24.dp.roundToPx()
        val room = (h - top - bottom).coerceAtLeast(1)
        val rows = measurables.filter { it.layoutId != COVER }
        val cover = measurables.firstOrNull { it.layoutId == COVER }
        val item = Constraints(maxWidth = (w - 2 * side).coerceAtLeast(0))
        val placeables = rows.map { it.measure(item) }
        // Page by page: a row that does not fit what is left of the page starts the next one; a section's title
        // never ends a page (it goes over with its first row).
        val pageOf = IntArray(placeables.size)
        val yOf = IntArray(placeables.size)
        var p = 0
        var y = 0
        for (i in placeables.indices) {
            val isSection = rows[i].layoutId == NOTEBOOK_SECTION
            val gap = if (y == 0) 0 else if (isSection) sectionGap else rowGap
            val need = placeables[i].height + if (isSection && i + 1 < placeables.size) rowGap + placeables[i + 1].height else 0
            if (y > 0 && y + gap + minOf(need, room) > room) {
                p++
                y = 0
            }
            val g = if (y == 0) 0 else gap
            pageOf[i] = p
            yOf[i] = top + y + g
            y += g + placeables[i].height
        }
        state.count = p + 1
        val current = page.coerceIn(0, p)
        val d = drag.value.toInt()
        // The page under (still) and the page on top (moving with the finger).
        val (under, over, overX) = when {
            d < 0 && current < p -> Triple(current, current + 1, w + d)
            d > 0 && current > 0 -> Triple(current - 1, current, d)
            else -> Triple(current, -1, 0)
        }
        state.under = under
        state.over = over
        state.overX = overX.toFloat()
        val coverPlaceable = cover?.measure(Constraints.fixed(w, h))
        layout(w, h) {
            for (i in placeables.indices) if (pageOf[i] == under) placeables[i].place(side, yOf[i], 0f)
            if (over >= 0) {
                coverPlaceable?.place(overX, 0, 1f)
                for (i in placeables.indices) if (pageOf[i] == over) placeables[i].place(overX + side, yOf[i], 2f)
            }
        }
    }
}

/**
 * How strongly the writing follows the paper: how far it is pushed along the slope ([bend], dp), how much paler
 * the pencil is in a crease's shadow ([fade]) and how much the creases light and shade it ([shade]).
 */
internal class NotebookBend(val bend: Float, val fade: Float, val shade: Float, val smooth: Int) {
    companion object {
        val variants = listOf(
            // 0: 1.21.0 (every crease bends the writing: letters stretched at the sharp ones).
            NotebookBend(5.5f, 0.45f, 0.45f, 2),
            // 1: only the folds bend it, the creases fade the pencil.
            NotebookBend(4.5f, 0.45f, 0.3f, 7),
            // 2: only the big folds, further.
            NotebookBend(6f, 0.45f, 0.3f, 12),
            // 3: hardly bent, the creases only fade the pencil and shade it.
            NotebookBend(1.2f, 0.55f, 0.35f, 7),
        )
        /** The one in use (a launch extra «bend» picks another, to compare them). */
        var current = variants[1]
    }
}

/** What the measure found, for the drawing and the swipe (not composition state: it changes as it lays out). */
private class PagesState {
    var count = 1
    var under by mutableIntStateOf(0)
    var over by mutableIntStateOf(-1)
    var overX = 0f
    var rootX by androidx.compose.runtime.mutableFloatStateOf(0f)
    var rootY by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/** The page photograph drawn as [paperPage] draws it, for the part of the screen at root ([x], [y]). */
private fun DrawScope.drawPaper(photo: android.graphics.Bitmap, frame: PaperFrame, x: Float, y: Float) {
    val scale = maxOf(frame.width / photo.width, frame.height / photo.height)
    val srcW = (frame.width / scale)
    val srcH = (frame.height / scale)
    val srcLeft = (photo.width - srcW) / 2 + (x - frame.left) / scale
    val srcTop = (photo.height - srcH) / 2 + (y - frame.top) / scale
    drawImage(
        photo.asImageBitmap(),
        srcOffset = IntOffset(srcLeft.toInt().coerceAtLeast(0), srcTop.toInt().coerceAtLeast(0)),
        srcSize = IntSize((size.width / scale).toInt().coerceAtMost(photo.width), (size.height / scale).toInt().coerceAtMost(photo.height)),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
    )
}

/**
 * The turning page's sheet: the same paper (laid as it will lie once the page is turned, so it lands exactly on
 * the page under it), its number, and a soft shadow at its left edge on the page below.
 */
private fun Modifier.drawCover(
    photo: android.graphics.Bitmap?,
    frame: PaperFrame,
    state: PagesState,
    numbers: androidx.compose.ui.text.TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
    light: Boolean,
): Modifier = drawBehind {
    val shadow = 18.dp.toPx()
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = if (light) 0.22f else 0.5f), startX = -shadow, endX = 0f),
        topLeft = Offset(-shadow, 0f),
        size = androidx.compose.ui.geometry.Size(shadow, size.height),
    )
    if (photo != null) drawPaper(photo, frame, state.rootX, state.rootY) else drawRect(if (light) Color(0xFFE8D7B0) else Color(0xFF161513))
    // The sheet's edge catches a little light, and its first few dp curl into a soft fold.
    drawRect(
        Brush.horizontalGradient(0f to Color.White.copy(alpha = if (light) 0.18f else 0.08f), 1f to Color.Transparent, startX = 0f, endX = 10.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(10.dp.toPx(), size.height),
    )
    drawNumber(state.over, numbers, style)
}

private fun Modifier.drawBehindNumber(
    state: PagesState,
    numbers: androidx.compose.ui.text.TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
): Modifier = drawBehind { drawNumber(state.under, numbers, style) }

/** The page's number, small at the bottom in the middle, as a notebook's (only when there is more than one). */
private fun DrawScope.drawNumber(index: Int, numbers: androidx.compose.ui.text.TextMeasurer, style: androidx.compose.ui.text.TextStyle) {
    if (index < 0) return
    val text = numbers.measure("${index + 1}", style)
    drawText(text, topLeft = Offset((size.width - text.size.width) / 2, size.height - text.size.height - 8.dp.toPx()))
}

private const val BEND_SHADER = """
uniform shader content;
uniform shader relief;
uniform float2 origin;
uniform float bend;
uniform float fade;
uniform float shade;
half4 main(float2 p) {
    half4 r = relief.eval(p + origin);
    float2 g = (float2(r.r, r.g) - 0.5) * 3.0;
    half4 c = content.eval(p - g * bend);
    float s = r.b * 2.0 - 1.0;
    float light = 1.0 + shade * s;
    // In the crease's shadow the pencil barely touched the paper: paler there.
    float a = c.a * (1.0 - fade * clamp(-s, 0.0, 1.0));
    float k = c.a > 0.0 ? a / c.a : 0.0;
    return half4(clamp(c.rgb * light * k, 0.0, a), a);
}
"""

/**
 * The writing bent with the paper's relief (Android 13+): every point of the page's ink is taken from a little
 * up or down the paper's slope there, and lit or shaded with its creases, as the keyboard's letters are.
 */
@Composable
private fun bend(relief: android.graphics.Bitmap?, photo: android.graphics.Bitmap?, frame: PaperFrame, state: PagesState): Modifier {
    if (Build.VERSION.SDK_INT < 33 || relief == null || photo == null) return Modifier
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shader = remember { android.graphics.RuntimeShader(BEND_SHADER) }
    return Modifier.graphicsLayer {
        val w = photo.width.toFloat()
        val h = photo.height.toFloat()
        // Root point -> page photograph pixel -> relief pixel (as drawPaper lays the photograph).
        val scale = maxOf(frame.width / w, frame.height / h)
        val srcLeft = (w - frame.width / scale) / 2
        val srcTop = (h - frame.height / scale) / 2
        val k = 1f / PaperState.reliefScale
        val matrix = android.graphics.Matrix().apply {
            setTranslate(-frame.left, -frame.top)
            postScale(1f / scale, 1f / scale)
            postTranslate(srcLeft, srcTop)
            postScale(k, k)
        }
        // The shader's matrix takes the relief picture to the screen: the inverse of the walk above.
        matrix.invert(matrix)
        val reliefShader = android.graphics.BitmapShader(relief, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        reliefShader.setLocalMatrix(matrix)
        // Smoothly between the relief's points: sampled nearest, the bend jumps in steps and tears the strokes.
        reliefShader.filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("relief", reliefShader)
        shader.setFloatUniform("origin", state.rootX, state.rootY)
        val v = NotebookBend.current
        shader.setFloatUniform("bend", with(density) { v.bend.dp.toPx() })
        shader.setFloatUniform("fade", v.fade)
        shader.setFloatUniform("shade", v.shade)
        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
