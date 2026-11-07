package com.offlinekeyboard.settings

import android.os.Build
import android.view.Display
import android.view.Window
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the status bar (time, network), as the other apps of the family do; a swipe from the top shows
 * it for a moment.
 */
internal fun hideStatusBar(window: Window) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
    }
}

/**
 * Asks for the screen's highest refresh rate (120 Hz where it has it) at the current resolution, so
 * scrolling and animations are as smooth as the screen allows. Android may still lower it (battery saver).
 */
internal fun preferHighestRefreshRate(window: Window) {
    val display: Display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { window.context.display }.getOrNull() else null)
        ?: runCatching { @Suppress("DEPRECATION") window.windowManager.defaultDisplay }.getOrNull()
        ?: return
    val current = display.mode
    val best = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxByOrNull { it.refreshRate } ?: return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = best.modeId
        preferredRefreshRate = best.refreshRate
    }
}

/**
 * Fades the content out at the top and bottom edges while there is more to scroll that way, as in the
 * launcher: the page melts into the background instead of being cut by a hard line.
 */
internal fun Modifier.fadingEdges(scroll: ScrollState, length: Dp = 18.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = length.toPx()
        if (scroll.value > 0) {
            val top = edge * (scroll.value / edge).coerceAtMost(1f)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = top),
                size = Size(size.width, top),
                blendMode = BlendMode.DstIn,
            )
        }
        val left = scroll.maxValue - scroll.value
        if (left > 0) {
            val bottom = edge * (left / edge).coerceAtMost(1f)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - bottom, endY = size.height),
                topLeft = Offset(0f, size.height - bottom),
                size = Size(size.width, bottom),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * A light spring at the ends of a scroll: dragging past the top or the bottom (also on a page shorter than
 * the screen) pulls the content a little, with growing resistance, and it springs back on release; a fling
 * that hits an end gives a small bounce. Put it right before the `verticalScroll` it belongs to.
 */
internal fun Modifier.bounce(): Modifier = composed {
    var offset by remember { mutableFloatStateOf(0f) }
    val limit = with(LocalDensity.current) { 96.dp.toPx() }
    val connection = remember(limit) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val o = offset
                if (o == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                // Pulling back towards rest first undoes the stretch.
                if ((o > 0f && available.y < 0f) || (o < 0f && available.y > 0f)) {
                    val next = if (o > 0f) (o + available.y).coerceAtLeast(0f) else (o + available.y).coerceAtMost(0f)
                    offset = next
                    return Offset(0f, next - o)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                val resistance = 0.4f * (1f - abs(offset) / limit).coerceIn(0.05f, 1f)
                offset = (offset + available.y * resistance).coerceIn(-limit, limit)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset == 0f) return Velocity.Zero
                animate(offset, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f)) { v, _ -> offset = v }
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y == 0f) {
                    if (offset != 0f) animate(offset, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f)) { v, _ -> offset = v }
                    return Velocity.Zero
                }
                // The fling hit an end: a small bump in its direction, then back.
                animate(
                    offset, 0f,
                    initialVelocity = (available.y * 0.12f).coerceIn(-2500f, 2500f),
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                ) { v, _ -> offset = v.coerceIn(-limit, limit) }
                return available
            }
        }
    }
    this
        .nestedScroll(connection)
        .graphicsLayer { translationY = offset }
}
