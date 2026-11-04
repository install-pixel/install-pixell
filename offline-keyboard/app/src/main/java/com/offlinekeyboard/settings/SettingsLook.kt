package com.offlinekeyboard.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinekeyboard.R
import com.offlinekeyboard.keyboard.KeyboardTheme
import com.offlinekeyboard.keyboard.Pencil
import com.offlinekeyboard.keyboard.ThemeMode
import kotlin.math.sin

/**
 * The settings screens in the two styles shared by all the user's apps (THEMES.md), drawn like the launcher's
 * settings: «Скло» (black glass lit from above, Exo 2) and «Аркуш» (white pencil on black paper, Caveat ×1.22).
 * Components read [LocalLook] and draw themselves accordingly.
 */
enum class Look { GLASS, SHEET }

val LocalLook = staticCompositionLocalOf { Look.GLASS }

/** Tokens of THEMES.md, section 2. */
internal object Tone {
    val TextPrimary = Color(0xFFE6E6E6)
    val TextSecondary = Color(0xFF8C8C8C)
    val Accent = Color(0xFFBDBDBD)
    val Confirm = Color(KeyboardTheme.CONFIRM)
    val Danger = Color(KeyboardTheme.DANGER)
    val Ink = Color(0xFFEDEDED)
}

/** What a button does, for its colour: the one main finishing action, an irreversible one, or anything else. */
enum class ButtonTone { NEUTRAL, CONFIRM, DANGER }

private val GlassFont = FontFamily(
    Font(R.font.exo2_light, FontWeight.Light),
    Font(R.font.exo2_regular, FontWeight.Normal),
    Font(R.font.exo2_medium, FontWeight.Medium),
    Font(R.font.exo2_semibold, FontWeight.SemiBold),
    Font(R.font.exo2_semibold, FontWeight.Bold),
)

private val HandFont = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_semibold, FontWeight.Medium),
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
    Font(R.font.caveat_semibold, FontWeight.Bold),
)

/** Handwriting reads smaller: all text of the sheet style is this much larger. */
private const val HAND_SCALE = 1.22f

internal fun lookOf(mode: ThemeMode?): Look = if (mode == ThemeMode.SHEET) Look.SHEET else Look.GLASS

/** The settings theme for [mode]: black tokens and the style's font for all text. */
@Composable
internal fun SettingsTheme(mode: ThemeMode?, content: @Composable () -> Unit) {
    val look = lookOf(mode)
    val colors = darkColorScheme(
        primary = Tone.Accent, onPrimary = Color.Black,
        primaryContainer = Color(0xFF1B1B1B), onPrimaryContainer = Tone.TextPrimary,
        secondaryContainer = Color(0xFF141414), onSecondaryContainer = Tone.TextPrimary,
        background = Color.Black, surface = Color.Black,
        surfaceVariant = Color(0xFF0D0D0D), surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerLow = Color(0xFF080808), surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF1B1B1B),
        onBackground = Tone.TextPrimary, onSurface = Tone.TextPrimary, onSurfaceVariant = Tone.TextSecondary,
        outline = Color(0x38FFFFFF), outlineVariant = Color(0x24FFFFFF),
        error = Tone.Danger,
    )
    val font = TextStyle(fontFamily = if (look == Look.SHEET) HandFont else GlassFont)
    val density = LocalDensity.current
    // Fields and small surfaces are rounded 16 dp (THEMES.md: no rectangular frames).
    val shapes = MaterialTheme.shapes.copy(
        extraSmall = RoundedCornerShape(16.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
    )
    // Sizes as in the launcher's settings: headings 17–19 sp, text 15–17 sp, hints 13 sp; no bold.
    val type = MaterialTheme.typography.withFont(font).let {
        it.copy(
            titleMedium = it.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Normal),
            titleSmall = it.titleSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal),
            bodyLarge = it.bodyLarge.copy(fontSize = 17.sp),
            bodyMedium = it.bodyMedium.copy(fontSize = 15.sp),
            bodySmall = it.bodySmall.copy(fontSize = 13.sp),
        )
    }
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes) {
        CompositionLocalProvider(
            LocalLook provides look,
            LocalTextStyle provides LocalTextStyle.current.merge(font),
            LocalDensity provides if (look == Look.SHEET) Density(density.density, density.fontScale * HAND_SCALE) else density,
            content = content,
        )
    }
}

private fun Typography.withFont(font: TextStyle) = Typography(
    displayLarge = displayLarge.merge(font), displayMedium = displayMedium.merge(font), displaySmall = displaySmall.merge(font),
    headlineLarge = headlineLarge.merge(font), headlineMedium = headlineMedium.merge(font), headlineSmall = headlineSmall.merge(font),
    titleLarge = titleLarge.merge(font), titleMedium = titleMedium.merge(font), titleSmall = titleSmall.merge(font),
    bodyLarge = bodyLarge.merge(font), bodyMedium = bodyMedium.merge(font), bodySmall = bodySmall.merge(font),
    labelLarge = labelLarge.merge(font), labelMedium = labelMedium.merge(font), labelSmall = labelSmall.merge(font),
)

// ------------------------------------------------------------------ surfaces

private val GlassSurface = Brush.verticalGradient(0f to Color(0xFF1B1B1B), 0.45f to Color(0xFF0D0D0D), 1f to Color(0xFF080808))
// The rim colours of THEMES.md (white at 50/14/7/15 %, chosen 70/25/20 %) flattened onto the black page as
// opaque greys, and at most three stops: the Android 14/15 emulator renderer dies on a four-stop gradient
// while the page scrolls (CI bisect), so the dim middle and the faint bottom light are merged into one stop.
/** A chosen option or a small button: glass lit from inside (the launcher's knob, as a two-stop gradient). */
private val GlassLit = Brush.verticalGradient(0f to Color(0xFF2E2E2E), 1f to Color(0xFF121212))
private val GlassRim = Brush.verticalGradient(0f to Color(0xFF808080), 0.25f to Color(0xFF242424), 1f to Color(0xFF1A1A1A))
private val GlassRimChosen = Brush.verticalGradient(0f to Color(0xFFB3B3B3), 0.3f to Color(0xFF404040), 1f to Color(0xFF333333))

/**
 * Glass drawn in one pass behind the content: the 1 dp rim as a filled rounded rectangle with the body
 * (and the top light of large surfaces, [sheen]) filled 1 dp inside it.
 */
private fun Modifier.glassFrame(radius: Dp, chosen: Boolean, sheen: Boolean, lit: Boolean): Modifier = drawWithCache {
    val r = radius.toPx()
    val rim = 1.dp.toPx()
    val inner = CornerRadius(maxOf(0f, r - rim))
    val innerSize = androidx.compose.ui.geometry.Size(size.width - 2 * rim, size.height - 2 * rim)
    val light = minOf(size.height / 2, 120.dp.toPx())
    val sheenBrush = Brush.verticalGradient(0f to Color(0x14FFFFFF), 1f to Color.Transparent, startY = 0f, endY = light)
    onDrawBehind {
        drawRoundRect(if (chosen) GlassRimChosen else GlassRim, cornerRadius = CornerRadius(r))
        drawRoundRect(if (lit) GlassLit else GlassSurface, Offset(rim, rim), innerSize, inner)
        if (sheen) drawRoundRect(sheenBrush, Offset(rim, rim), innerSize, inner)
    }
}

/**
 * A card, button or field surface: black glass with a rim bright on top ([chosen]: brighter rim; [lit]: glass
 * lit from inside, for chosen options and small buttons), or a pencil frame drawn twice, different per [seed].
 */
@Composable
internal fun Modifier.lookSurface(
    radius: Dp,
    seed: Int = 0,
    chosen: Boolean = false,
    sheen: Boolean = false,
    ink: Color = Tone.Ink,
    lit: Boolean = false,
    alpha: Float = 0.8f,
): Modifier = when (LocalLook.current) {
    Look.GLASS -> glassFrame(radius, chosen, sheen, lit)
    Look.SHEET -> pencilFrame(radius, seed, if (chosen) 1f else alpha, if (chosen) 2.6.dp else 2.2.dp, ink)
}

/** The pencil frame of the sheet style (the keyboard's [Pencil] outline as a Compose path). */
internal fun Modifier.pencilFrame(radius: Dp, seed: Int, alpha: Float = 0.8f, width: Dp = 2.2.dp, ink: Color = Tone.Ink): Modifier =
    drawWithCache {
        val w = width.toPx()
        val r = radius.toPx()
        val main = Pencil.tapered(Pencil.loop(size.width, size.height, r, seed, 1.3.dp.toPx()), w).asComposePath()
        val ghost = Pencil.tapered(Pencil.loop(size.width, size.height, r, seed + 101, 1.6.dp.toPx()), w * 0.55f).asComposePath()
        onDrawBehind {
            drawPath(main, ink.copy(alpha = alpha))
            drawPath(ghost, ink.copy(alpha = alpha * 0.35f))
        }
    }

/**
 * A wavy pencil line across the text at [y] from [from] to [to] (a switched-off setting is crossed out; an
 * action is underlined), as the launcher's `pencilStroke`.
 */
internal fun DrawScope.pencilStroke(y: Float, from: Float, to: Float, seed: Int, alpha: Float = 0.8f, ink: Color = Tone.Ink) {
    val wob = 1.1.dp.toPx()
    val steps = 24
    val ax = from
    val ay = y + sin(seed * 1.3f) * wob
    val cx = (from + to) / 2
    val cy = y + sin(seed * 2.1f + 1f) * wob * 2f
    val bx = to
    val by = y + sin(seed * 0.7f + 2f) * wob
    val points = FloatArray((steps + 1) * 2)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val u = 1 - t
        points[i * 2] = u * u * ax + 2 * u * t * cx + t * t * bx
        points[i * 2 + 1] = u * u * ay + 2 * u * t * cy + t * t * by
    }
    drawPath(Pencil.tapered(points, 2.2.dp.toPx()).asComposePath(), ink.copy(alpha = alpha))
}

/** Text colour of a button by what it does (THEMES.md: one blue main action per window, red for the irreversible). */
@Composable
internal fun toneColor(tone: ButtonTone): Color = when (tone) {
    ButtonTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    ButtonTone.CONFIRM -> Tone.Confirm
    ButtonTone.DANGER -> Tone.Danger
}

/** Colours of a text button (dialog actions, a row's "Delete") by what it does. */
@Composable
internal fun toneTextColors(tone: ButtonTone) = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = toneColor(tone))

// ------------------------------------------------------------------ switch, check box

/**
 * The glass switch of the launcher: a 52×30 capsule of black glass with a rim and a glass ball inside. On, the
 * capsule is lit and the bright ball sits right; off, it is dark and the dim ball sits left. The ball slides.
 * (The sheet style has no switch: the setting is written, or crossed out — see SwitchRow.)
 */
@Composable
internal fun LookSwitch(checked: Boolean) {
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "switch")
    Box(
        Modifier.size(52.dp, 30.dp).drawWithCache {
            val ball = 24.dp.toPx()
            val pad = (size.height - ball) / 2
            val x = pad + (size.width - 2 * pad - ball) * position
            val capsule = CornerRadius(size.height / 2)
            val body = if (checked) {
                Brush.verticalGradient(listOf(Color(0xFF4A4A4A), Color(0xFF262626)))
            } else {
                GlassSurface
            }
            val knob = if (checked) {
                Brush.verticalGradient(listOf(Color.White, Color(0xFFBDBDBD)), startY = pad, endY = pad + ball)
            } else {
                Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101010)), startY = pad, endY = pad + ball)
            }
            val rim = 1.dp.toPx()
            val innerSize = androidx.compose.ui.geometry.Size(size.width - 2 * rim, size.height - 2 * rim)
            onDrawBehind {
                // Rim as a fill under the body, not a stroke (see glassFrame).
                drawRoundRect(if (checked) GlassRimChosen else GlassRim, cornerRadius = capsule)
                drawRoundRect(body, Offset(rim, rim), innerSize, CornerRadius(innerSize.height / 2))
                drawCircle(Color(0xFF555555), ball / 2, Offset(x + ball / 2, pad + ball / 2))
                drawCircle(knob, ball / 2 - rim, Offset(x + ball / 2, pad + ball / 2))
            }
        },
    )
}

/** A glass check box: an empty glass square, or a lit one with a ✓. */
@Composable
internal fun LookMark(checked: Boolean) {
    Box(
        Modifier.size(24.dp).lookSurface(7.dp, chosen = checked, lit = checked).drawWithCache {
            val tick = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.53f)
                lineTo(size.width * 0.43f, size.height * 0.70f)
                lineTo(size.width * 0.76f, size.height * 0.32f)
            }
            onDrawWithContent {
                drawContent()
                if (checked) drawPath(tick, Tone.TextPrimary, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        },
    )
}
