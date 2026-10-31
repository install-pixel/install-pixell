package com.blacklaunch.launcher.ui

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
    return this.bounceWhenFits(fits).verticalScroll(state, enabled = !fits)
}

/** Для списків: гойдання, коли список поміщається (у самого списку тоді ставте userScrollEnabled = !fits). */
internal val LazyListState.fits: Boolean get() = !canScrollForward && !canScrollBackward

/** Коли вміст поміщається ([fits]), вертикальний жест тягне край системним ефектом розтягування. */
@Composable
internal fun Modifier.bounceWhenFits(fits: Boolean): Modifier {
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
            onDragStopped = { velocity -> effect.applyToFling(Velocity(0f, velocity)) { Velocity.Zero } },
        )
}
