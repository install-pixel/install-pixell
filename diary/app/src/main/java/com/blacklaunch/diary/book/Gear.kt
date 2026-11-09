package com.blacklaunch.diary.book

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacklaunch.diary.R
import com.blacklaunch.diary.ui.Glyph
import com.blacklaunch.diary.ui.GlyphIcon
import com.blacklaunch.diary.ui.LocalHaptics
import com.blacklaunch.diary.ui.theme.Oled
import kotlinx.coroutines.delay

/** Місце під шестерню внизу екрана — однакове в щоденнику й на полиці. */
val GearRow = 48.dp

/** Шестерня не висить постійно: з'являється на кілька секунд після будь-якого дотику до екрана. */
@Stable
class GearReveal {
    internal var pokes by mutableIntStateOf(0)
    fun poke() { pokes++ }
}

@Composable
fun rememberGearReveal() = remember { GearReveal() }

/** Будь-який дотик до цієї ділянки (не забираючи його в інших) показує шестерню. */
fun Modifier.revealsGear(gear: GearReveal): Modifier = pointerInput(gear) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.changes.any { it.pressed && !it.previousPressed }) gear.poke()
        }
    }
}

/** Шестерня — замість стандартного «⋯»: відкриває сторінку меню. Видно її спершу й після дотику, потім тане. */
@Composable
fun GearSpot(gear: GearReveal, modifier: Modifier = Modifier, size: Dp = 48.dp, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(gear.pokes) {
        visible = true
        delay(3_500)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 250 else 800), label = "gear")
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .clickable(enabled = visible) { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Settings, stringResource(R.string.menu), tint = Oled.TextSecondary, size = size * 0.55f)
    }
}

/** Інструмент нижньої панелі. [active] — увімкнений режим (підсвічено). */
class Tool(val glyph: Glyph, val description: String, val active: Boolean = false, val onClick: () -> Unit)

/**
 * Нижня панель під щоденником — замість меню для щоденних дій (перо, закреслити, фото, голос, місце, пошук, календар,
 * шестерня). З'являється так само, як шестерня: після дотику, і тане; поки якийсь режим увімкнено ([pinned]) — видно.
 */
@Composable
fun ToolPanel(gear: GearReveal, tools: List<Tool>, pinned: Boolean = false, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(gear.pokes, pinned) {
        visible = true
        if (!pinned) {
            delay(3_500)
            visible = false
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 250 else 800), label = "tools")
    androidx.compose.foundation.layout.Row(
        modifier.graphicsLayer { this.alpha = alpha },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tools.forEach { t ->
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(if (t.active) Modifier.background(Oled.TextPrimary.copy(alpha = 0.14f)) else Modifier)
                    .clickable(enabled = visible) { haptics.tap(); t.onClick() },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(t.glyph, t.description, tint = if (t.active) Oled.TextPrimary else Oled.TextSecondary, size = 23.dp)
            }
        }
    }
}
