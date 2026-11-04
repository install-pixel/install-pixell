package com.offlinekeyboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.offlinekeyboard.R
import com.offlinekeyboard.keyboard.Accent
import com.offlinekeyboard.keyboard.KeyboardTheme
import com.offlinekeyboard.keyboard.Pencil
import com.offlinekeyboard.keyboard.ThemeMode

/**
 * The settings screens in the two shared styles of THEMES.md: «Скло» (black glass lit from above, Exo 2)
 * and «Аркуш» (white pencil on black paper, Caveat ×1.22). Components read [LocalLook] and draw themselves
 * accordingly; PLAIN is the Material look of the system, light and dark themes.
 */
enum class Look { PLAIN, GLASS, SHEET }

val LocalLook = staticCompositionLocalOf { Look.PLAIN }

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

internal fun lookOf(mode: ThemeMode?): Look = when (mode) {
    ThemeMode.GLASS -> Look.GLASS
    ThemeMode.SHEET -> Look.SHEET
    else -> Look.PLAIN
}

/** The settings theme for [mode]: Material colours for the plain themes, black tokens and the style's font otherwise. */
@Composable
internal fun SettingsTheme(mode: ThemeMode?, accent: Accent, content: @Composable () -> Unit) {
    val look = lookOf(mode)
    if (look == Look.PLAIN) {
        val dark = when (mode ?: ThemeMode.SYSTEM) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            else -> isSystemInDarkTheme()
        }
        MaterialTheme(colorScheme = settingsColors(dark, accent)) {
            CompositionLocalProvider(LocalLook provides Look.PLAIN, content = content)
        }
        return
    }
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
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography.withFont(font)) {
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
private val GlassRim = Brush.verticalGradient(
    0f to Color(0x80FFFFFF), 0.25f to Color(0x24FFFFFF), 0.8f to Color(0x12FFFFFF), 1f to Color(0x26FFFFFF),
)
private val GlassRimChosen = Brush.verticalGradient(0f to Color(0xB3FFFFFF), 0.3f to Color(0x40FFFFFF), 1f to Color(0x33FFFFFF))

/** Soft light inside the top of a large glass surface, gone by half height (at most 120 dp). */
private fun Modifier.glassSheen(): Modifier = drawWithCache {
    val h = minOf(size.height / 2, 120.dp.toPx())
    val brush = Brush.verticalGradient(0f to Color(0x14FFFFFF), 1f to Color.Transparent, startY = 0f, endY = h)
    onDrawWithContent {
        drawRect(brush, size = size.copy(height = h))
        drawContent()
    }
}

/**
 * A card, button or field surface: black glass with a rim bright on top ([chosen]: brighter), or a pencil
 * frame drawn twice, different for every [seed]. PLAIN draws nothing (the Material component draws itself).
 */
@Composable
internal fun Modifier.lookSurface(radius: Dp, seed: Int = 0, chosen: Boolean = false, sheen: Boolean = false, ink: Color = Tone.Ink): Modifier =
    when (LocalLook.current) {
        Look.PLAIN -> this
        Look.GLASS -> {
            val shape = RoundedCornerShape(radius)
            clip(shape).background(GlassSurface).let { if (sheen) it.glassSheen() else it }
                .border(1.dp, if (chosen) GlassRimChosen else GlassRim, shape)
        }
        Look.SHEET -> pencilFrame(radius, seed, if (chosen) 1f else 0.8f, if (chosen) 2.6.dp else 2.2.dp, ink)
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

/** Text colour of a button by what it does (THEMES.md: one blue main action per window, red for the irreversible). */
@Composable
internal fun toneColor(tone: ButtonTone): Color = when (tone) {
    ButtonTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    ButtonTone.CONFIRM -> if (LocalLook.current == Look.PLAIN) MaterialTheme.colorScheme.primary else Tone.Confirm
    ButtonTone.DANGER -> if (LocalLook.current == Look.PLAIN) MaterialTheme.colorScheme.error else Tone.Danger
}

/** Colours of a text button (dialog actions, a row's "Delete") by what it does. */
@Composable
internal fun toneTextColors(tone: ButtonTone) = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = toneColor(tone))

// ------------------------------------------------------------------ switch, check box, radio

/** The glass capsule switch (52×30 dp, a 24 dp ball) or a pencil-drawn one; PLAIN never calls this. */
@Composable
internal fun LookSwitch(checked: Boolean) {
    val look = LocalLook.current
    Box(
        Modifier.size(52.dp, 30.dp).drawWithCache {
            val ball = 24.dp.toPx()
            val pad = (size.height - ball) / 2
            val x = if (checked) size.width - pad - ball else pad
            val capsule = CornerRadius(size.height / 2)
            if (look == Look.GLASS) {
                val body = if (checked) {
                    Brush.verticalGradient(listOf(Color(0xFF4A4A4A), Color(0xFF262626)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF141414), Color(0xFF080808)))
                }
                val knob = if (checked) {
                    Brush.radialGradient(listOf(Color.White, Color(0xFFBDBDBD)), Offset(x + ball / 2, pad + ball / 2), ball / 2)
                } else {
                    Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF101010)), Offset(x + ball / 2, pad + ball / 2), ball / 2)
                }
                onDrawBehind {
                    drawRoundRect(body, cornerRadius = capsule)
                    drawRoundRect(if (checked) GlassRimChosen else GlassRim, cornerRadius = capsule, style = Stroke(1.dp.toPx()))
                    drawCircle(knob, ball / 2, Offset(x + ball / 2, pad + ball / 2))
                }
            } else {
                val frame = Pencil.tapered(Pencil.loop(size.width, size.height, size.height / 2, 3, 1.dp.toPx()), 2.dp.toPx()).asComposePath()
                val dot = Pencil.tapered(Pencil.loop(ball, ball, ball / 2, if (checked) 11 else 5, 0.8.dp.toPx()), 2.dp.toPx()).asComposePath()
                onDrawBehind {
                    drawPath(frame, Tone.Ink.copy(alpha = if (checked) 0.95f else 0.55f))
                    translate(x, pad) { drawPath(dot, Tone.Ink.copy(alpha = if (checked) 1f else 0.55f)) }
                    if (checked) drawCircle(Tone.Ink.copy(alpha = 0.85f), ball / 4, Offset(x + ball / 2, pad + ball / 2))
                }
            }
        },
    )
}

private inline fun DrawScope.translate(x: Float, y: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(x, y)
    block()
    drawContext.transform.translate(-x, -y)
}

/** A check box (square) or radio (round): an empty frame, or a frame with a ✓ when chosen. */
@Composable
internal fun LookMark(checked: Boolean, round: Boolean) {
    val look = LocalLook.current
    val radius = if (round) 12.dp else 6.dp
    Box(
        Modifier.size(24.dp).lookSurface(radius, seed = if (round) 21 else 13, chosen = checked).drawWithCache {
            val tick = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.53f)
                lineTo(size.width * 0.43f, size.height * 0.70f)
                lineTo(size.width * 0.76f, size.height * 0.32f)
            }
            onDrawWithContent {
                drawContent()
                if (checked) {
                    val ink = if (look == Look.SHEET) Tone.Ink else Tone.TextPrimary
                    drawPath(tick, ink, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
        },
    )
}
