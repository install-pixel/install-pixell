package com.offlinekeyboard.settings

import android.os.Build
import android.view.Display
import android.view.Window
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
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
