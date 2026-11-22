package com.offlinekeyboard.settings

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
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
 * page, as many as fit the screen, and the page is turned as a real one (as the owner's diary turns it): it rolls
 * round an imagined cylinder from its free edge, its face darkening up the roll, its back showing past it, a
 * shadow on the page under it. By a swipe or the small ‹ › at the bottom; past the last page comes the first,
 * back from the first leaves the page ([onFirstBack]). Nothing scrolls, so the writing can follow the paper's
 * relief (bent with its creases, from Android 13) without floating over it.
 */
@Composable
internal fun NotebookPages(title: String, onFirstBack: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val st = remember { PagesState() }
    val turned = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val frame = LocalPaperFrame.current
    val light = Tone.light
    PaperState.ready
    val relief = PaperState.relief(light)
    val photo = PaperState.page(context, light)
    val inkTop = rememberGraphicsLayer()
    val inkUnder = rememberGraphicsLayer()
    val sheet = rememberGraphicsLayer()
    val sheetBack = rememberGraphicsLayer()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val density = LocalDensity.current
    val topInset = with(density) { insets.calculateTopPadding().roundToPx() }
    val bottomInset = with(density) { insets.calculateBottomPadding().roundToPx() }

    /** Starts turning towards [dir] (+1 the next page, −1 back): false when there is nowhere to go. */
    fun begin(dir: Int): Boolean {
        if (st.stage != 0) return false
        if (dir > 0) {
            val target = if (page < st.count - 1) page + 1 else 0
            if (target == page) return false
            st.top = page
            st.under = target
        } else {
            if (page == 0) return false
            st.top = page - 1
            st.under = page
        }
        st.dir = dir
        // The open page is taken first (it is what the screen shows meanwhile), then the other one.
        st.first = page
        st.second = if (st.top == page) st.under else st.top
        st.place = st.first
        st.stage = 1
        scope.launch { turned.snapTo(if (dir > 0) 0f else 1f) }
        return true
    }

    /** Lets the turning page go: it lies down turned ([complete]) or goes back. */
    fun finish(complete: Boolean) {
        scope.launch {
            snapshotFlow { st.stage }.first { it == 3 }
            val target = if (st.dir > 0) (if (complete) 1f else 0f) else (if (complete) 0f else 1f)
            turned.animateTo(target, tween((300 * kotlin.math.abs(target - turned.value) + 120).toInt()))
            if (complete) page = if (st.dir > 0) st.under else st.top
            st.stage = 0
            st.place = -2
        }
    }

    Layout(
        content = {
            androidx.compose.runtime.CompositionLocalProvider(LocalNotebook provides true) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
            }
            // The page turner at the bottom: ‹ number ›, as in the diary.
            Row(
                Modifier.layoutId(NAV).fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val dim = Tone.TextSecondary
                Text(
                    "‹",
                    color = if (page == 0 && onFirstBack == null) dim.copy(alpha = 0.3f) else dim,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clickable(interactionSource = null, indication = null) {
                            if (page == 0) onFirstBack?.invoke() else if (begin(-1)) finish(true)
                        }
                        .padding(horizontal = 22.dp, vertical = 4.dp),
                )
                Text("${page + 1}", color = dim, fontSize = 15.sp)
                Text(
                    "›",
                    color = if (st.count <= 1) dim.copy(alpha = 0.3f) else dim,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clickable(interactionSource = null, indication = null) { if (begin(1)) finish(true) }
                        .padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }
        },
        modifier = modifier
            .onGloballyPositioned {
                val p = it.positionInRoot()
                st.rootX = p.x
                st.rootY = p.y
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var dx = 0f
                var backOut = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        dx = 0f
                        backOut = false
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val w = size.width.toFloat()
                        when {
                            backOut -> if (dx > w * 0.25f || v > 800f) onFirstBack?.invoke()
                            st.stage != 0 -> finish(
                                if (st.dir > 0) turned.value > 0.3f || v < -800f else turned.value < 0.7f || v > 800f,
                            )
                        }
                    },
                    onDragCancel = { if (st.stage != 0) finish(false) },
                ) { change, amount ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    dx += amount
                    val w = size.width.toFloat()
                    if (st.stage == 0 && !backOut) {
                        if (dx < 0 && !begin(1)) return@detectHorizontalDragGestures
                        if (dx > 0 && !begin(-1)) {
                            backOut = page == 0
                            return@detectHorizontalDragGestures
                        }
                    }
                    if (st.stage != 0) {
                        val t = if (st.dir > 0) (-dx / w).coerceIn(0f, 1f) else 1f - (dx / w).coerceIn(0f, 1f)
                        scope.launch { turned.snapTo(t) }
                    }
                }
            }
            .then(bend(relief, photo, frame, st))
            .drawWithContent {
                when (st.stage) {
                    0 -> drawContent()
                    1, 2 -> {
                        // Taking the two pages, one a frame: each laid out alone (no page turner) and recorded.
                        val takingFirst = st.stage == 1
                        val wanted = if (takingFirst) st.first else st.second
                        if (st.place == wanted) {
                            val layer = if (wanted == st.top) inkTop else inkUnder
                            layer.record { this@drawWithContent.drawContent() }
                            if (takingFirst) {
                                st.stage = 2
                                st.place = st.second
                            } else {
                                sheet.record {
                                    if (photo != null) drawPaper(photo, frame, st.rootX, st.rootY) else drawRect(paperColor(light))
                                    drawLayer(inkTop)
                                }
                                sheetBack.record {
                                    // The sheet's back: the same paper seen from behind (mirrored), a little darker.
                                    withTransform({ scale(-1f, 1f, Offset(size.width / 2, size.height / 2)) }) {
                                        if (photo != null) drawPaper(photo, frame, st.rootX, st.rootY) else drawRect(paperColor(light))
                                    }
                                    drawRect(Color.Black.copy(alpha = if (light) 0.08f else 0.18f))
                                }
                                st.stage = 3
                                st.place = -1
                            }
                        }
                        drawLayer(if (st.first == st.top) inkTop else inkUnder)
                    }
                    else -> {
                        drawLayer(inkUnder)
                        val w = size.width
                        val r = w * 0.12f
                        val axis = w - turned.value * (w + Math.PI.toFloat() * r * 1.1f)
                        drawCurl(CurlGeometry(w, r, axis), sheet, sheetBack, light)
                        drawContent()
                    }
                }
            },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val side = 22.dp.roundToPx()
        val top = topInset + 20.dp.roundToPx()
        val navH = 44.dp.roundToPx()
        val bottom = bottomInset + navH + 6.dp.roundToPx()
        val rowGap = 10.dp.roundToPx()
        val sectionGap = 24.dp.roundToPx()
        val room = (h - top - bottom).coerceAtLeast(1)
        val rows = measurables.filter { it.layoutId != NAV }
        val nav = measurables.first { it.layoutId == NAV }.measure(Constraints.fixed(w, navH))
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
        st.count = p + 1
        val shown = if (st.place == -2) page.coerceIn(0, p) else st.place
        layout(w, h) {
            if (shown >= 0) for (i in placeables.indices) if (pageOf[i] == shown) placeables[i].place(side, yOf[i])
            // The turner only while no page is being taken (it is not part of a page).
            if (st.stage == 0 || st.stage == 3) nav.place(0, h - bottomInset - navH)
        }
    }
}

private const val NAV = "notebook-nav"

private fun paperColor(light: Boolean) = if (light) Color(0xFFE8D7B0) else Color(0xFF161513)

/**
 * The turning page rolled round the cylinder of [g] (as the diary's): the flat part as it lay, its face up the roll
 * darkening towards the top, a shadow on the page under the roll, and past the top its back (paper seen from
 * behind) with light along the bend and the flat part that has fallen back.
 */
private fun DrawScope.drawCurl(g: CurlGeometry, face: GraphicsLayer, back: GraphicsLayer, light: Boolean) {
    val w = size.width
    val h = size.height
    val a = g.axis
    val r = g.radius
    val pi = Math.PI.toFloat()
    // 1) The flat part, as it lay.
    val flatEnd = a.coerceIn(0f, w)
    if (flatEnd > 0f) clipRect(0f, 0f, flatEnd, h) { drawLayer(face) }
    // 2) The roll's shadow on the page under it.
    val rollRight = g.rightmost.coerceAtMost(w)
    if (rollRight < w && a < w) {
        val len = w * 0.07f + r * 0.6f
        val shade = (if (light) 0.32f else 0.55f) * minOf(((w - a) / (w * 0.1f)).coerceIn(0f, 1f), (rollRight / (w * 0.25f)).coerceIn(0f, 1f))
        drawRect(
            Brush.horizontalGradient(0f to Color.Black.copy(alpha = shade), 1f to Color.Transparent, startX = rollRight, endX = rollRight + len),
            topLeft = Offset(rollRight, 0f),
            size = androidx.compose.ui.geometry.Size(len, h),
        )
    }
    // 3) The face on the roll, from the axis to the top.
    val steps = 40
    for (i in 0 until steps) {
        val t0 = pi / 2f * i / steps
        val t1 = pi / 2f * (i + 1) / steps
        val x0 = a + r * t0
        val x1 = a + r * t1
        if (x1 <= a.coerceAtLeast(0f) || x0 >= w) continue
        curlStrip(face, x0.coerceAtLeast(0f), x1.coerceAtMost(w), g, h, darkness = 0.6f * (1f - kotlin.math.cos((t0 + t1) / 2f)), light = 0f)
    }
    // 4) The back: on the roll past its top, and the flat back that has fallen over.
    val backFrom = a + r * pi
    val freeEdge = g.map(w)
    if (!g.front(w)) {
        if (backFrom < w && freeEdge > 0f) {
            val len = w * 0.05f
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f * (freeEdge / (w * 0.15f)).coerceIn(0f, 1f)), startX = freeEdge - len, endX = freeEdge),
                topLeft = Offset(freeEdge - len, 0f),
                size = androidx.compose.ui.geometry.Size(len, h),
            )
        }
        for (i in 0 until steps) {
            val t0 = pi / 2f * (1f + i.toFloat() / steps)
            val t1 = pi / 2f * (1f + (i + 1).toFloat() / steps)
            val x0 = a + r * t0
            val x1 = a + r * t1
            if (x1 <= 0f || x0 >= w) continue
            curlStrip(back, x0.coerceAtLeast(0f), x1.coerceAtMost(w), g, h, darkness = 0f, light = kotlin.math.sin(2f * ((t0 + t1) / 2f - pi / 2f)))
        }
        if (backFrom < w) curlStrip(back, backFrom.coerceAtLeast(0f), w, g, h, darkness = 0f, light = 0f)
    }
}

/** One strip [x0, x1] of the sheet (sheet coordinates) moved to where it is on screen, darkened or lit. */
private fun DrawScope.curlStrip(layer: GraphicsLayer, x0: Float, x1: Float, g: CurlGeometry, h: Float, darkness: Float, light: Float) {
    if (x1 - x0 < 0.01f) return
    val d0 = g.map(x0)
    val d1 = g.map(x1)
    val left = minOf(d0, d1)
    val right = maxOf(d0, d1)
    if (right - left < 0.05f) return
    val k = (d1 - d0) / (x1 - x0)
    clipRect(left - 0.5f, 0f, right + 0.5f, h) {
        withTransform({
            translate(left = d0 - x0 * k)
            scale(scaleX = k, scaleY = 1f, pivot = Offset.Zero)
        }) { drawLayer(layer) }
        if (darkness > 0.005f) drawRect(Color.Black.copy(alpha = darkness), topLeft = Offset(left - 0.5f, 0f), size = androidx.compose.ui.geometry.Size(right - left + 1f, h))
        if (light > 0.005f) drawRect(Color.White.copy(alpha = 0.14f * light), topLeft = Offset(left - 0.5f, 0f), size = androidx.compose.ui.geometry.Size(right - left + 1f, h))
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

/** The pages' and the turn's state: what the measure found, which pages are taken, how far it is. */
private class PagesState {
    var count = 1
    /** 0 nothing turning; 1 taking the open page; 2 taking the other; 3 turning. */
    var stage by mutableIntStateOf(0)
    /** The page laid out now (−2 the open one, −1 none: only the turner). */
    var place by mutableIntStateOf(-2)
    var dir = 0
    var top = 0
    var under = 0
    var first = 0
    var second = 0
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
