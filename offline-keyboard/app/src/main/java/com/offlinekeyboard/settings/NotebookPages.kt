package com.offlinekeyboard.settings

import androidx.compose.ui.text.drawText

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.layout.positionInWindow
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val openPage = rememberGraphicsLayer()
    val inkUnder = rememberGraphicsLayer()
    val sheet = rememberGraphicsLayer()
    val sheetBack = rememberGraphicsLayer()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val density = LocalDensity.current
    val topInset = with(density) { insets.calculateTopPadding().roundToPx() }
    val bottomInset = with(density) { insets.calculateBottomPadding().roundToPx() }
    val imeOpen = WindowInsets.isImeVisible

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
        scope.launch { turned.snapTo(if (dir > 0) 0f else BACK_START) }
        return true
    }

    /** Lets the turning page go: it lies down turned ([complete]) or goes back. */
    fun finish(complete: Boolean) {
        scope.launch {
            snapshotFlow { st.stage }.first { it == 3 }
            val target = if (st.dir > 0) (if (complete) 1f else 0f) else (if (complete) 0f else BACK_START)
            turned.animateTo(target, tween((300 * kotlin.math.abs(target - turned.value) + 120).toInt()))
            if (complete) page = if (st.dir > 0) st.under else st.top
            st.stage = 0
            st.place = -2
        }
    }

    val numbers = androidx.compose.ui.text.rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.bodyLarge.copy(color = Tone.TextPrimary.copy(alpha = 0.8f), fontSize = 17.sp)

    /** Page [index]'s number, written on it between the turner's arrows. */
    fun DrawScope.drawNumber(index: Int) {
        if (st.navHeight <= 0f) return
        // «1 / 3»: how many pages and where the end is (one page: just «1»).
        val text = numbers.measure(if (st.count > 1) "${index + 1} / ${st.count}" else "${index + 1}", numberStyle)
        drawText(
            text,
            topLeft = Offset((size.width - text.size.width) / 2f, st.navTop + (st.navHeight - text.size.height) / 2f),
        )
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
                // In pencil, as the page's writing (pale grey it was lost on the parchment). The arrows say where they
                // lead: full ink to another page of this menu; pale where the menu ends (back from its first page to
                // the menu before, on from its last page round to the first). Per page, as they are recorded with it.
                val dim = Tone.TextPrimary.copy(alpha = 0.8f)
                val pale = dim.copy(alpha = 0.3f)
                val index = when (st.stage) {
                    1, 2 -> st.place.coerceAtLeast(0)
                    3 -> st.under
                    else -> page
                }
                Text(
                    "‹",
                    color = if (index == 0) pale else dim,
                    fontSize = 30.sp,
                    modifier = Modifier
                        .clickable(interactionSource = null, indication = null) {
                            if (page == 0) onFirstBack?.invoke() else if (begin(-1)) finish(true)
                        }
                        .padding(horizontal = 22.dp, vertical = 4.dp),
                )
                // The number is written on the page itself (see drawNumber), so it turns with its sheet; here
                // only its place between the arrows.
                androidx.compose.foundation.layout.Spacer(Modifier.width(64.dp))
                Text(
                    "›",
                    color = if (index >= st.count - 1) pale else dim,
                    fontSize = 30.sp,
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
                        // Back: from where the sheet's edge is just at the screen's edge, so it comes under the finger
                        // at once (from fully turned it stayed out of sight for the first centimetres: a delay).
                        val t = if (st.dir > 0) (-dx / w).coerceIn(0f, 1f) else BACK_START * (1f - (dx / w).coerceIn(0f, 1f))
                        scope.launch { turned.snapTo(t) }
                    }
                }
            }
            // The turning sheet is drawn over the bent page, outside its bend: inside it the sheet's paper bent and
            // shaded with the relief as the writing does (blotchy, the parchment's burnt edge pulled down). Only
            // its writing is bent, by the same effect given to it.
            .drawWithContent {
                // Every page on its own sheet: the same paper turned another way, so its creases and stains are
                // elsewhere (all pages were one sheet). Under the writing, and not bent with it.
                val shownPaper = when (st.stage) {
                    0 -> page
                    1, 2 -> st.first
                    else -> st.under
                }
                if (photo != null) drawPaper(photo, frame, st.rootX, st.rootY, variantOf(shownPaper))
                drawContent()
                if (st.stage == 3) {
                    val w = size.width
                    val r = w * 0.12f
                    val axis = w - turned.value * (w + Math.PI.toFloat() * r * 1.1f)
                    drawCurl(CurlGeometry(w, r, axis), sheet, sheetBack, light)
                }
            }
            .drawWithContent {
                when (st.stage) {
                    // The open page drawn as the turning pages are: its picture, bent by itself. (A bend on the page's
                    // own layer and one on each taken picture were two different things: the writing jumped when a
                    // page was taken.)
                    0 -> {
                        openPage.record {
                            this@drawWithContent.drawContent()
                            drawNumber(page)
                        }
                        openPage.renderEffect = bendEffect(relief, photo, frame, st, this.density, variantOf(page))
                        drawLayer(openPage)
                    }
                    1, 2 -> {
                        // Taking the two pages, one a frame: each laid out alone (no page turner) and recorded.
                        val takingFirst = st.stage == 1
                        val wanted = if (takingFirst) st.first else st.second
                        if (st.place == wanted) {
                            val layer = if (wanted == st.top) inkTop else inkUnder
                            // The page with its number on it: one sheet, written once (the number drawn apart changed
                            // the moment a page was taken, before the sheet hid it).
                            layer.record {
                                this@drawWithContent.drawContent()
                                drawNumber(wanted)
                            }
                            // Each page's picture carries its own bend: drawn inside the page's bent layer a picture
                            // is not bent by it (the writing jumped by the bend's offset the moment a page was taken),
                            // so while turning that layer bends nothing and every picture bends itself.
                            layer.renderEffect = bendEffect(relief, photo, frame, st, this.density, variantOf(wanted))
                            if (takingFirst) {
                                st.stage = 2
                                st.place = st.second
                            } else {
                                // Its writing bent as it lay on the page (the sheet is drawn outside the bend).
                                val topPaper = variantOf(st.top)
                                sheet.record {
                                    if (photo != null) drawPaper(photo, frame, st.rootX, st.rootY, topPaper) else drawRect(paperColor(light))
                                    drawLayer(inkTop)
                                }
                                // The sheet's back: this very sheet from behind. Every point of it where it is on the
                                // face (the turn shows it mirrored, as seen from behind: mirrored here as well, its creases
                                // were not under the face's), its creases raised where the face's sink (their light and
                                // shadow swapped), a little darker. Opaque: nothing of the writing shows through (it looked
                                // like an X-ray of the page).
                                sheetBack.renderEffect = backEffect(relief, photo, frame, st, topPaper, light)
                                sheetBack.record {
                                    if (photo != null) drawPaper(photo, frame, st.rootX, st.rootY, topPaper) else drawRect(paperColor(light))
                                    drawRect(Color.Black.copy(alpha = if (light) 0.08f else 0.18f))
                                }
                                st.stage = 3
                                st.place = -1
                            }
                        }
                        drawLayer(if (st.first == st.top) inkTop else inkUnder)
                    }
                    // The lower page as recorded, its number with it (not drawn twice).
                    else -> drawLayer(inkUnder)
                }
            },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val side = 22.dp.roundToPx()
        val top = topInset + 20.dp.roundToPx()
        val navH = 44.dp.roundToPx()
        // On the parchment above its burnt lower edge; over the keyboard clear of its soft top edge (which lies
        // over the app: the number stood in it, on the keyboard's line).
        val navLift = maxOf(if (light) 26.dp.roundToPx() else 0, if (imeOpen) 18.dp.roundToPx() else 0)
        val bottom = bottomInset + navLift + navH + 6.dp.roundToPx()
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
            // The turner is part of every page (its number turns with the sheet).
            st.navTop = (h - bottomInset - navLift - navH).toFloat()
            st.navHeight = navH.toFloat()
            nav.place(0, h - bottomInset - navLift - navH)
        }
    }
}

private const val NAV = "notebook-nav"

/** Where a turn back starts: the sheet's roll just at the screen's left edge (its share of a whole turn). */
private const val BACK_START = 0.8f

/**
 * The sketch's menu: going to another page turns the sheet as the notebook's own pages turn. The page shown is
 * taken as the screen shows it and turned away over the new one: forward to the left, back to the right (the
 * mirrored turn). Taken whole, nothing under it changes while it turns.
 */
@Composable
internal fun <T> PageTurnHost(target: T, forward: (from: T, to: T) -> Boolean, content: @Composable (T) -> Unit) {
    var shown by androidx.compose.runtime.remember { mutableStateOf(target) }
    val face = rememberGraphicsLayer()
    val back = rememberGraphicsLayer()
    val turned = remember { Animatable(0f) }
    var turning by remember { mutableStateOf(false) }
    var mirrored by remember { mutableStateOf(false) }
    val light = Tone.light
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val photo = PaperState.page(context, light)
    val hostDensity = androidx.compose.ui.platform.LocalDensity.current
    val hostDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    var box by remember { mutableStateOf(android.graphics.Rect()) }

    androidx.compose.runtime.LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        val ahead = forward(shown, target)
        // The page as the screen shows it (a layer's own picture came out empty: the writing's bend is drawn by
        // the GPU only).
        val old = windowPixels(view, box)
        if (old == null) {
            shown = target
            return@LaunchedEffect
        }
        val image = old.asImageBitmap()
        val size = androidx.compose.ui.unit.IntSize(old.width, old.height)
        face.record(hostDensity, hostDirection, size) {
            if (ahead) {
                drawImage(image)
            } else {
                // Drawn mirrored, turned in a mirror: it shows the right way round, turning to the right.
                withTransform({ scale(-1f, 1f, Offset(this.size.width / 2, this.size.height / 2)) }) { drawImage(image) }
            }
        }
        back.record(hostDensity, hostDirection, size) {
            val frame = PaperFrame().apply { width = this@record.size.width; height = this@record.size.height }
            if (photo != null) drawPaper(photo, frame, 0f, 0f, 1) else drawRect(paperColor(light))
            drawRect(Color.Black.copy(alpha = if (light) 0.08f else 0.18f))
        }
        mirrored = !ahead
        turned.snapTo(0f)
        turning = true
        shown = target
        // From the next frame: the turn starts at its start (the frame clock may still hold an earlier time).
        androidx.compose.runtime.withFrameNanos { }
        turned.animateTo(1f, tween(TURN_MS))
        turning = false
        old.recycle()
    }

    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val p = it.positionInWindow()
                box = android.graphics.Rect(p.x.toInt(), p.y.toInt(), p.x.toInt() + it.size.width, p.y.toInt() + it.size.height)
            }
            .drawWithContent {
                drawContent()
                if (turning) {
                    val w = size.width
                    val r = w * 0.12f
                    val axis = w - turned.value * (w + Math.PI.toFloat() * r * 1.1f)
                    if (mirrored) {
                        withTransform({ scale(-1f, 1f, Offset(w / 2, size.height / 2)) }) { drawCurl(CurlGeometry(w, r, axis), face, back, light) }
                    } else {
                        drawCurl(CurlGeometry(w, r, axis), face, back, light)
                    }
                }
            },
    ) {
        content(shown)
    }
}

/** The window's pixels in [box] as the screen shows them (null when the phone cannot give them). */
private suspend fun windowPixels(view: android.view.View, box: android.graphics.Rect): android.graphics.Bitmap? {
    val window = (view.context as? android.app.Activity)?.window ?: return null
    if (box.width() <= 0 || box.height() <= 0) return null
    val bitmap = android.graphics.Bitmap.createBitmap(box.width(), box.height(), android.graphics.Bitmap.Config.ARGB_8888)
    return kotlinx.coroutines.suspendCancellableCoroutine { done ->
        runCatching {
            android.view.PixelCopy.request(window, box, bitmap, { result ->
                done.resumeWith(Result.success(if (result == android.view.PixelCopy.SUCCESS) bitmap else null))
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }.onFailure { done.resumeWith(Result.success(null)) }
    }
}

private const val TURN_MS = 750

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
    /** Where the page turner's row is: the page's number is written in its middle. */
    var navTop = 0f
    var navHeight = 0f
}

/** The page photograph drawn as [paperPage] draws it, for the part of the screen at root ([x], [y]). */
private fun DrawScope.drawPaper(photo: android.graphics.Bitmap, frame: PaperFrame, x: Float, y: Float, variant: Int = 0) {
    val (fx, fy) = flipOf(variant)
    if (fx < 0f || fy < 0f) {
        withTransform({ scale(fx, fy, Offset(size.width / 2, size.height / 2)) }) { drawPaper(photo, frame, x, y, 0) }
        return
    }
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

/** Which way a page's sheet lies (one photograph, turned four ways): page by page. */
private fun variantOf(page: Int) = ((page % 4) + 4) % 4

/** The mirroring of [variant]: x and y, 1 or −1. */
private fun flipOf(variant: Int): Pair<Float, Float> = when (variant) {
    1 -> -1f to 1f
    2 -> 1f to -1f
    3 -> -1f to -1f
    else -> 1f to 1f
}


/** The back of a sheet: its paper with the relief's light and shadow swapped (a crease sunk on the face is raised). */
private const val BACK_SHADER = """
uniform shader content;
uniform shader relief;
uniform float2 origin;
uniform float2 flip;
uniform float2 area;
uniform float strength;
half4 main(float2 p) {
    float2 q = float2(flip.x < 0.0 ? area.x - p.x : p.x, flip.y < 0.0 ? area.y - p.y : p.y);
    half4 r = relief.eval(q + origin);
    float s = r.b * 2.0 - 1.0;
    half4 c = content.eval(p);
    // The face's light is in the photograph: taken out and turned the other way.
    float k = clamp(1.0 - strength * s, 0.4, 1.6);
    return half4(clamp(c.rgb * k, 0.0, c.a), c.a);
}
"""

private const val BEND_SHADER = """
uniform shader content;
uniform shader relief;
uniform float2 origin;
uniform float2 flip;
uniform float2 area;
uniform float bend;
uniform float fade;
uniform float shade;
half4 main(float2 p) {
    // The page's sheet may lie mirrored: its relief is read where the mirrored paper is, its slopes turned.
    float2 q = float2(flip.x < 0.0 ? area.x - p.x : p.x, flip.y < 0.0 ? area.y - p.y : p.y);
    half4 r = relief.eval(q + origin);
    float2 g = (float2(r.r, r.g) - 0.5) * 3.0 * flip;
    half4 c = content.eval(p - g * bend);
    float s = r.b * 2.0 - 1.0;
    float light = 1.0 + shade * s;
    // In the crease's shadow the pencil barely touched the paper: paler there.
    float a = c.a * (1.0 - fade * clamp(-s, 0.0, 1.0));
    float k = c.a > 0.0 ? a / c.a : 0.0;
    return half4(clamp(c.rgb * light * k, 0.0, a), a);
}
"""

/** The bend as an effect for a layer (null before Android 13 or while the paper is not loaded). */
private fun bendEffect(
    relief: android.graphics.Bitmap?,
    photo: android.graphics.Bitmap?,
    frame: PaperFrame,
    state: PagesState,
    density: Float,
    variant: Int,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < 33 || relief == null || photo == null) return null
    val shader = android.graphics.RuntimeShader(BEND_SHADER)
    setRelief(shader, relief, photo, frame, state, variant)
    run {
        val v = NotebookBend.current
        shader.setFloatUniform("bend", v.bend * density)
        shader.setFloatUniform("fade", v.fade)
        shader.setFloatUniform("shade", v.shade)
    }
    return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

/** The back of a sheet as an effect (null before Android 13 or while the paper is not loaded). */
private fun backEffect(
    relief: android.graphics.Bitmap?,
    photo: android.graphics.Bitmap?,
    frame: PaperFrame,
    state: PagesState,
    variant: Int,
    light: Boolean,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < 33 || relief == null || photo == null) return null
    val shader = android.graphics.RuntimeShader(BACK_SHADER)
    setRelief(shader, relief, photo, frame, state, variant)
    // The parchment's relief is strong: turned at full strength its back came out blotchy.
    shader.setFloatUniform("strength", if (light) 0.3f else 0.9f)
    return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

/** The relief under the page, laid as [drawPaper] lays the photograph (turned as page [variant]'s sheet). */
@androidx.annotation.RequiresApi(33)
private fun setRelief(
    shader: android.graphics.RuntimeShader,
    relief: android.graphics.Bitmap,
    photo: android.graphics.Bitmap,
    frame: PaperFrame,
    state: PagesState,
    variant: Int,
) {
    run {
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
        val (fx, fy) = flipOf(variant)
        shader.setFloatUniform("flip", fx, fy)
        shader.setFloatUniform("area", frame.width, frame.height)
    }
}
