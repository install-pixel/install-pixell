package com.blacklaunch.qrcodes.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/*
 * Однакове пружне «гойдання» на краях у всіх вікнах і списках. Система дає його лише тоді, коли вмісту більше,
 * ніж місця; коли все поміщається — край просто стоїть. Тут у такому разі жест іде прямо в той самий системний
 * ефект, тож маленьке вікно гойдається так само, як велике.
 */

/** [verticalScroll] із гойданням і тоді, коли вміст поміщається повністю. */
@Composable
internal fun Modifier.bouncyScroll(state: ScrollState = rememberScrollState()): Modifier {
    // До першого вимірювання maxValue = Int.MAX_VALUE, тож спершу — звичайна прокрутка.
    val fits = state.maxValue == 0
    val dragged = remember { MutableInteractionSource() }
    val pulling by dragged.collectIsDraggedAsState()
    // Краї: у спокої верх чіткий; поки гортаєш чи смикаєш (і вміст гойдається) — плавно тануть.
    val active = edgeActivity(state.isScrollInProgress || pulling)
    return this.fadingEdges(state, active).bounceWhenFits(fits, dragged).verticalScroll(state, enabled = !fits)
}

/**
 * 0…1: наскільки краї мають танути через сам жест (гойдання, прокрутка). Вмикається швидко, вимикається плавно —
 * поки край повертається на місце після гойдання.
 */
@Composable
internal fun edgeActivity(active: Boolean): () -> Float {
    val k = androidx.compose.animation.core.animateFloatAsState(
        if (active) 1f else 0f,
        androidx.compose.animation.core.tween(if (active) 120 else 450),
        label = "edgeActivity",
    )
    return { k.value }
}

/** Для списків: гойдання, коли список поміщається (у самого списку тоді ставте userScrollEnabled = !fits). */
internal val LazyListState.fits: Boolean get() = !canScrollForward && !canScrollBackward

/** Коли вміст поміщається ([fits]), вертикальний жест тягне край системним ефектом розтягування. */
@Composable
internal fun Modifier.bounceWhenFits(fits: Boolean, interaction: MutableInteractionSource? = null): Modifier {
    val effect = rememberOverscrollEffect()
    val drag = rememberDraggableState { dy ->
        effect?.applyToScroll(Offset(0f, dy), NestedScrollSource.UserInput) { Offset.Zero }
    }
    if (!fits || effect == null) return this
    return this
        .overscroll(effect)
        .draggable(
            drag,
            Orientation.Vertical,
            interactionSource = interaction,
            onDragStopped = { velocity -> effect.applyToFling(Velocity(0f, velocity)) { Velocity.Zero } },
        )
}
